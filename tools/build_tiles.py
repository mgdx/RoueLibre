#!/usr/bin/env python3
"""Build the offline vector tile file, in MBTiles format (SPEC.md §4.2).

Pipeline, from an OpenStreetMap regional extract down to one MBTiles file:

    1. osmium extract      cut the regional extract down to the reference
                           bounding box (§4)
    2. osmium tags-filter  keep, per layer, only the objects on the whitelist
                           of tools/map_features.yaml
    3. osmium export       convert to line-delimited GeoJSON, keeping only the
                           handful of tags each layer actually needs
    4. tippecanoe          build one MBTiles per minimum-zoom group
    5. tile-join           assemble the groups into the final file

Filtering happens at generation time rather than in the map style, because
what is not kept costs nothing. See the header of map_features.yaml.

Usage:
    python3 tools/build_tiles.py --osm-extract data/osm/<region>.osm.pbf
                                 [--config PATH] [--output PATH]
                                 [--work-dir PATH] [--keep-intermediate]
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path

import yaml

from city_config import DEFAULT_CITY_CONFIG, BoundingBox, CityConfig

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
FEATURES_FILE = TOOLS_DIR / "map_features.yaml"
DEFAULT_OUTPUT = REPO_ROOT / "data" / "out" / "tiles.mbtiles"
DEFAULT_WORK_DIR = REPO_ROOT / "data" / "work" / "tiles"

OSM_ATTRIBUTION = "© les contributeurs OpenStreetMap"

REQUIRED_EXECUTABLES = ("osmium", "tippecanoe", "tile-join")


class GenerationError(RuntimeError):
    """A step of the pipeline failed; the message says which one and why."""


def check_executables() -> None:
    """Fail early and with a usable message if the toolchain is incomplete."""
    missing = [name for name in REQUIRED_EXECUTABLES if shutil.which(name) is None]
    if missing:
        raise GenerationError(
            "Missing tools: "
            + ", ".join(missing)
            + ".\nInstall them with: sudo apt install osmium-tool tippecanoe"
        )


def run(command: list[str], step: str) -> None:
    """Run a subprocess, turning a non-zero exit into a GenerationError."""
    print(f"  $ {' '.join(str(part) for part in command)}")
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode != 0:
        raise GenerationError(
            f"Step \"{step}\" failed (code {completed.returncode}).\n"
            f"{completed.stderr.strip()[:2000]}"
        )


def human_size(path: Path) -> str:
    size = path.stat().st_size
    for unit in ("B", "kB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.1f} {unit}" if unit != "B" else f"{size} {unit}"
        size /= 1024
    return f"{size:.1f} GB"


def load_layers() -> tuple[dict, int]:
    """Read the feature whitelist. Returns the layers and the global max zoom."""
    with FEATURES_FILE.open(encoding="utf-8") as stream:
        document = yaml.safe_load(stream)
    return document["layers"], int(document["globalMaxZoom"])


def cut_provenance(regional_extract: Path, bounding_box: BoundingBox) -> dict:
    """What a cut was made of, in the terms that decide whether it can serve.

    The extract is identified by size and modification time beside its path:
    two cities can be cut from the same file name and get different data, a
    Geofabrik extract refreshed between two runs being exactly that case.
    """
    stat = regional_extract.stat()
    return {
        "sourceExtract": str(regional_extract.resolve()),
        "sourceSizeBytes": stat.st_size,
        "sourceModifiedNanoseconds": stat.st_mtime_ns,
        "boundingBox": {
            "south": bounding_box.south,
            "west": bounding_box.west,
            "north": bounding_box.north,
            "east": bounding_box.east,
        },
    }


def cut_to_bounding_box(
    regional_extract: Path, bounding_box: BoundingBox, work_dir: Path
) -> Path:
    """Clip the regional OSM extract down to the reference bounding box.

    The ``smart`` strategy keeps ways that cross the boundary whole, which
    avoids ragged edges on roads and rivers, and — unlike ``complete_ways`` —
    also keeps multipolygon and boundary relations complete. Without it, every
    commune whose outline pokes outside the bounding box fails to assemble and
    silently disappears from the map: measured on this extract, 41 communes
    came through instead of 95.

    The cut is reused across runs — it costs minutes on a large region — but
    **only when it was made of the same extract for the same box**, which the
    file beside it records. Existence alone used to be the test, and a run
    interrupted after this step left its cut to the next city: the map of one
    conurbation would then be built from the data of another, clipped to a box
    it does not describe, and nothing downstream would notice. The stamp is
    written only once osmium has succeeded, so a cut left half-written is
    re-made rather than trusted.
    """
    area_extract = work_dir / "area.osm.pbf"
    stamp = work_dir / "area.provenance.json"
    expected = cut_provenance(regional_extract, bounding_box)
    if area_extract.exists() and stamp.exists():
        try:
            recorded = json.loads(stamp.read_text())
        except json.JSONDecodeError:
            recorded = None
        if recorded == expected:
            print(f"[1/5] Cut already done: {area_extract} "
                  f"({human_size(area_extract)})")
            return area_extract
        print("[1/5] The cut lying here was made of something else — cutting "
              "again.")
    print("[1/5] Cutting the regional extract down to the box…")
    stamp.unlink(missing_ok=True)
    run(
        [
            "osmium", "extract",
            "--bbox", bounding_box.as_osmium_extract_argument(),
            "--strategy", "smart",
            "--option", "types=multipolygon,boundary",
            "--overwrite",
            "-o", str(area_extract),
            str(regional_extract),
        ],
        "osmium extract",
    )
    stamp.write_text(json.dumps(expected, indent=1))
    print(f"      → {area_extract.name} : {human_size(area_extract)}")
    return area_extract


def filter_layer(area_extract: Path, name: str, layer: dict, work_dir: Path) -> Path:
    """Keep only the objects a single layer is made of."""
    filtered = work_dir / f"{name}.osm.pbf"
    run(
        ["osmium", "tags-filter", "--overwrite", "-o", str(filtered),
         str(area_extract), *layer["filters"]],
        f"osmium tags-filter ({name})",
    )
    return filtered


def export_layer(filtered: Path, name: str, layer: dict, work_dir: Path) -> Path:
    """Convert one filtered extract to line-delimited GeoJSON.

    Only the tags listed in ``keepTags`` survive. Dropping everything else here
    rather than in tippecanoe matters: unused attributes would otherwise be
    carried through every tile of every zoom level.
    """
    export_config = work_dir / f"{name}.export.json"
    with export_config.open("w", encoding="utf-8") as stream:
        json.dump(
            {
                # Object type and id are of no use to the renderer.
                "attributes": {"type": False, "id": False, "version": False,
                               "changeset": False, "timestamp": False,
                               "uid": False, "user": False, "way_nodes": False},
                "include_tags": layer["keepTags"],
                # An object stripped of all its tags is still worth drawing:
                # building footprints and bus stops carry no attribute at all.
                "linear_tags": True,
                "area_tags": True,
            },
            stream,
        )

    exported = work_dir / f"{name}.geojsonseq"
    run(
        ["osmium", "export", "--overwrite", "-f", "geojsonseq",
         "--geometry-types", layer["geometry"],
         "--config", str(export_config),
         "-o", str(exported), str(filtered)],
        f"osmium export ({name})",
    )
    return exported


def apply_property_filter(exported: Path, layer: dict) -> Path:
    """Drop features an osmium tag filter cannot express.

    ``osmium tags-filter`` ORs its expressions, so a condition such as
    "administrative boundary AND admin_level 8" has to be applied afterwards.
    Commune outlines are the only case, but keeping the mechanism generic
    costs nothing.
    """
    allowed_levels = layer.get("onlyAdminLevels")
    if not allowed_levels:
        return exported

    filtered = exported.with_suffix(".filtered.geojsonseq")
    kept = dropped = 0
    with exported.open(encoding="utf-8") as source, \
            filtered.open("w", encoding="utf-8") as sink:
        for line in source:
            line = line.strip()
            if not line:
                continue
            # osmium's GeoJSON Text Sequence output prefixes records with the
            # RS control character (U+001E), which json cannot parse.
            record = json.loads(line.lstrip("\x1e"))
            if str(record.get("properties", {}).get("admin_level")) in allowed_levels:
                sink.write(json.dumps(record, ensure_ascii=False) + "\n")
                kept += 1
            else:
                dropped += 1
    print(f"      admin_level filter: {kept} kept, {dropped} dropped")
    return filtered


def count_features(path: Path) -> int:
    with path.open(encoding="utf-8") as stream:
        return sum(1 for line in stream if line.strip())


def build_zoom_group(
    group_min_zoom: int,
    layer_files: list[tuple[str, Path]],
    global_max_zoom: int,
    layers: dict,
    bounding_box: BoundingBox,
    work_dir: Path,
) -> Path:
    """Run tippecanoe once for every layer sharing a minimum zoom.

    tippecanoe applies one zoom range per invocation, so layers are grouped by
    minimum zoom and the resulting files are assembled by tile-join. This is
    what lets buildings and bus stops start at zoom 15 while rivers and main
    roads exist from zoom 10.
    """
    group_max_zoom = max(
        int(layers[name].get("maxZoom", global_max_zoom)) for name, _ in layer_files
    )
    output = work_dir / f"group-z{group_min_zoom}.mbtiles"
    command = [
        "tippecanoe",
        "--output", str(output),
        "--force",
        "--minimum-zoom", str(group_min_zoom),
        "--maximum-zoom", str(group_max_zoom),
        # Keep tiles under the 500 kB budget by thinning the densest areas
        # rather than by failing: the city centre is far denser than the rest
        # of the bounding box.
        "--drop-densest-as-needed",
        "--extend-zooms-if-still-dropping",
        # Geometry precision beyond a few screen pixels is invisible and only
        # costs bytes.
        "--simplification", "4",
        # The `smart` extract strategy deliberately keeps relations whole, so
        # the input holds objects reaching far past the bounding box — long
        # rivers, motorways, large administrative areas. Left unclipped they
        # would inflate the file and draw a ragged fringe of half-data outside
        # the area the application claims to cover.
        "--clip-bounding-box",
        f"{bounding_box.west},{bounding_box.south},"
        f"{bounding_box.east},{bounding_box.north}",
        "--attribution", OSM_ATTRIBUTION,
        "--read-parallel",
    ]
    for name, path in layer_files:
        command += ["--named-layer", f"{name}:{path}"]
    run(command, f"tippecanoe (zoom {group_min_zoom}+)")
    return output


def join_groups(group_files: list[Path], output: Path) -> None:
    """Merge the per-zoom-group MBTiles into the single delivered file."""
    output.parent.mkdir(parents=True, exist_ok=True)
    run(
        ["tile-join", "--force", "--output", str(output),
         "--attribution", OSM_ATTRIBUTION,
         "--name", "Roue Libre — fond de carte",
         *[str(path) for path in group_files]],
        "tile-join",
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--osm-extract", type=Path, required=True,
                        help="regional OpenStreetMap extract in .osm.pbf format")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR,
                        help="intermediate files; defaults to a directory of "
                             "the network's own under data/work/tiles/")
    parser.add_argument("--keep-intermediate", action="store_true",
                        help="keep the working files for inspection")
    parser.add_argument("--only-layers", nargs="*", default=None,
                        help="process only these layers (for debugging)")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        check_executables()
        if not arguments.osm_extract.exists():
            raise GenerationError(
                f"OSM extract not found: {arguments.osm_extract}\n"
                "Download it from https://download.geofabrik.de/"
            )

        config = CityConfig.load(arguments.config)
        bounding_box = config.bounding_box

        # One working directory per network, so that generating a catalogue
        # in one go never has two cities meet in the same intermediate files.
        # An explicit --work-dir is left exactly as it was given.
        if arguments.work_dir == DEFAULT_WORK_DIR:
            arguments.work_dir = DEFAULT_WORK_DIR / config.network_id

        layers, global_max_zoom = load_layers()
        if arguments.only_layers:
            layers = {k: v for k, v in layers.items() if k in arguments.only_layers}

        arguments.work_dir.mkdir(parents=True, exist_ok=True)
        started = time.monotonic()

        print(f"Box     : {bounding_box}")
        print(f"Zooms   : {config.document['map']['minZoom']} to {global_max_zoom}")
        print(f"Layers  : {', '.join(layers)}\n")

        area_extract = cut_to_bounding_box(
            arguments.osm_extract, bounding_box, arguments.work_dir
        )

        print(f"\n[2/5] and [3/5] Filtering and exporting the {len(layers)} layers…")
        by_zoom: dict[int, list[tuple[str, Path]]] = defaultdict(list)
        feature_counts: dict[str, int] = {}
        for name, layer in layers.items():
            print(f"  · {name}")
            filtered = filter_layer(area_extract, name, layer, arguments.work_dir)
            exported = export_layer(filtered, name, layer, arguments.work_dir)
            exported = apply_property_filter(exported, layer)
            count = count_features(exported)
            feature_counts[name] = count
            print(f"      {count} features, {human_size(exported)}")
            if count == 0:
                print("      (empty layer, skipped)")
                continue
            by_zoom[int(layer["minZoom"])].append((name, exported))

        if not by_zoom:
            raise GenerationError("No non-empty layer: nothing to produce.")

        print(f"\n[4/5] Building the tiles ({len(by_zoom)} zoom groups)…")
        group_files = [
            build_zoom_group(
                zoom, files, global_max_zoom, layers, bounding_box, arguments.work_dir
            )
            for zoom, files in sorted(by_zoom.items())
        ]

        print("\n[5/5] Joining…")
        join_groups(group_files, arguments.output)

        elapsed = time.monotonic() - started
        print(f"\n{'':=<60}")
        print(f"File produced : {arguments.output}")
        print(f"Size            : {human_size(arguments.output)}")
        print(f"Duration        : {elapsed / 60:.1f} min")
        print(f"{'':=<60}")
        print("Features per layer:")
        for name, count in sorted(feature_counts.items(), key=lambda item: -item[1]):
            print(f"  {name:14} {count:>9}")

        if not arguments.keep_intermediate:
            shutil.rmtree(arguments.work_dir, ignore_errors=True)
            print("\nWorking files removed "
                  "(--keep-intermediate to keep them).")
        return 0

    except GenerationError as error:
        print(f"\nError: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
