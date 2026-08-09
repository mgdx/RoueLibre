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
            "Outils manquants : "
            + ", ".join(missing)
            + ".\nInstalle-les avec : sudo apt install osmium-tool tippecanoe"
        )


def run(command: list[str], step: str) -> None:
    """Run a subprocess, turning a non-zero exit into a GenerationError."""
    print(f"  $ {' '.join(str(part) for part in command)}")
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode != 0:
        raise GenerationError(
            f"Échec de l'étape « {step} » (code {completed.returncode}).\n"
            f"{completed.stderr.strip()[:2000]}"
        )


def human_size(path: Path) -> str:
    size = path.stat().st_size
    for unit in ("o", "ko", "Mo", "Go"):
        if size < 1024 or unit == "Go":
            return f"{size:.1f} {unit}" if unit != "o" else f"{size} {unit}"
        size /= 1024
    return f"{size:.1f} Go"


def load_layers() -> tuple[dict, int]:
    """Read the feature whitelist. Returns the layers and the global max zoom."""
    with FEATURES_FILE.open(encoding="utf-8") as stream:
        document = yaml.safe_load(stream)
    return document["layers"], int(document["globalMaxZoom"])


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
    """
    area_extract = work_dir / "area.osm.pbf"
    if area_extract.exists():
        print(f"[1/5] Découpe déjà faite : {area_extract} ({human_size(area_extract)})")
        return area_extract
    print(f"[1/5] Découpe de l'extrait régional sur l'emprise…")
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
    print(f"      filtre admin_level : {kept} gardés, {dropped} écartés")
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
                        help="extrait OpenStreetMap régional au format .osm.pbf")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    parser.add_argument("--keep-intermediate", action="store_true",
                        help="conserver les fichiers de travail pour inspection")
    parser.add_argument("--only-layers", nargs="*", default=None,
                        help="ne traiter que ces couches (mise au point)")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        check_executables()
        if not arguments.osm_extract.exists():
            raise GenerationError(
                f"Extrait OSM introuvable : {arguments.osm_extract}\n"
                "Télécharge-le depuis https://download.geofabrik.de/"
            )

        config = CityConfig.load(arguments.config)
        bounding_box = config.bounding_box
        layers, global_max_zoom = load_layers()
        if arguments.only_layers:
            layers = {k: v for k, v in layers.items() if k in arguments.only_layers}

        arguments.work_dir.mkdir(parents=True, exist_ok=True)
        started = time.monotonic()

        print(f"Emprise : {bounding_box}")
        print(f"Zooms   : {config.document['map']['minZoom']} à {global_max_zoom}")
        print(f"Couches : {', '.join(layers)}\n")

        area_extract = cut_to_bounding_box(
            arguments.osm_extract, bounding_box, arguments.work_dir
        )

        print(f"\n[2/5] et [3/5] Filtrage et export des {len(layers)} couches…")
        by_zoom: dict[int, list[tuple[str, Path]]] = defaultdict(list)
        feature_counts: dict[str, int] = {}
        for name, layer in layers.items():
            print(f"  · {name}")
            filtered = filter_layer(area_extract, name, layer, arguments.work_dir)
            exported = export_layer(filtered, name, layer, arguments.work_dir)
            exported = apply_property_filter(exported, layer)
            count = count_features(exported)
            feature_counts[name] = count
            print(f"      {count} objets, {human_size(exported)}")
            if count == 0:
                print(f"      (couche vide, ignorée)")
                continue
            by_zoom[int(layer["minZoom"])].append((name, exported))

        if not by_zoom:
            raise GenerationError("Aucune couche non vide : rien à produire.")

        print(f"\n[4/5] Construction des tuiles ({len(by_zoom)} groupes de zoom)…")
        group_files = [
            build_zoom_group(
                zoom, files, global_max_zoom, layers, bounding_box, arguments.work_dir
            )
            for zoom, files in sorted(by_zoom.items())
        ]

        print(f"\n[5/5] Assemblage…")
        join_groups(group_files, arguments.output)

        elapsed = time.monotonic() - started
        print(f"\n{'':=<60}")
        print(f"Fichier produit : {arguments.output}")
        print(f"Taille          : {human_size(arguments.output)}")
        print(f"Durée           : {elapsed / 60:.1f} min")
        print(f"{'':=<60}")
        print("Objets par couche :")
        for name, count in sorted(feature_counts.items(), key=lambda item: -item[1]):
            print(f"  {name:14} {count:>9}")

        if not arguments.keep_intermediate:
            shutil.rmtree(arguments.work_dir, ignore_errors=True)
            print(f"\nFichiers de travail supprimés "
                  f"(--keep-intermediate pour les garder).")
        return 0

    except GenerationError as error:
        print(f"\nErreur : {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
