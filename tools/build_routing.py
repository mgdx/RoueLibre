#!/usr/bin/env python3
"""Build the offline routing graph, in BRouter rd5 format (SPEC.md §5).

BRouter names its data files on a fixed 5°×5° grid, but a file only ever holds
what the input contained. Feeding the map creator an extract clipped to the
reference bounding box therefore yields a graph covering just our area, and
nothing forces us to ship the segments BRouter distributes, which span a good
part of northern Europe. Measured on the Lille bounding box, that is the
difference between roughly 150 MB and 1.6 MB.

Pipeline:

    0. download           fetch and verify the official BRouter release, which
                          carries both the map creator and the routing library
    1. osmium extract     clip the regional extract to the bounding box
    2. OsmFastCutter      split OSM into node and way tiles, read turn
                          restrictions
    3. PosUnifier         merge positions and attach elevation
    4. WayLinker          assemble the routable graph into an rd5 file

Elevation is optional but cheap and enabled by default: it costs about 0.1 MB
in the graph and lets the cycling profile avoid the few real slopes of an
otherwise flat metropolitan area.

Usage:
    python3 tools/build_routing.py --osm-extract data/osm/<region>.osm.pbf
                                   [--config PATH] [--output-dir PATH]
                                   [--no-elevation] [--keep-intermediate]
"""

from __future__ import annotations

import argparse
import hashlib
import math
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

from city_config import DEFAULT_CITY_CONFIG, BoundingBox, CityConfig

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "data" / "out" / "routing"
DEFAULT_WORK_DIR = REPO_ROOT / "data" / "work" / "routing"
DEFAULT_CACHE_DIR = REPO_ROOT / "data" / "cache"

# Pinned so that a rebuild uses the very same map creator, which is a
# precondition for the reproducible build required by SPEC.md §11.15.
BROUTER_VERSION = "1.7.10"
BROUTER_RELEASE_URL = (
    f"https://github.com/abrensch/brouter/releases/download/"
    f"v{BROUTER_VERSION}/brouter-{BROUTER_VERSION}.zip"
)
BROUTER_RELEASE_SHA256 = (
    "023fec3ba997758e8cd7ab9e1bae52e962af3f00b57683e3de86b84ffad01532"
)

# Profiles the map creator itself needs. They decide which OSM ways enter the
# graph at all, and are not the profiles used at routing time.
MAP_CREATION_PROFILES = ("all.brf", "trekking.brf", "softaccess.brf")
BROUTER_PROFILE_BASE_URL = (
    "https://raw.githubusercontent.com/abrensch/brouter/master/misc/profiles2/"
)

# Public, authentication-free mirror of the SRTM 1 arc-second tiles.
ELEVATION_TILE_URL = (
    "https://s3.amazonaws.com/elevation-tiles-prod/skadi/{latitude}/{tile}.hgt.gz"
)

USER_AGENT = "RoueLibre-tools/1.0 (+https://github.com/mgdx/RoueLibre)"
JAVA_HEAP = "-Xmx4g"


class GenerationError(RuntimeError):
    """A step of the pipeline failed; the message says which one and why."""


def run_java(jar: Path, main_class: str, arguments: list[str], step: str,
             extra_flags: list[str] | None = None,
             working_directory: Path | None = None) -> str:
    """Run a BRouter map-creator class, raising on failure.

    ``working_directory`` matters: WayLinker drops a ``badtrs.txt`` report of
    the turn restrictions it could not resolve into the current directory, and
    that report belongs with the other intermediate files, not at the root of
    the repository.
    """
    command = ["java", JAVA_HEAP, "-DuseDenseMaps=true",
               *(extra_flags or []), "-cp", str(jar.resolve()),
               main_class, *arguments]
    completed = subprocess.run(
        command, capture_output=True, text=True, cwd=working_directory
    )
    if completed.returncode != 0:
        raise GenerationError(
            f"Échec de l'étape « {step} » (code {completed.returncode}).\n"
            f"{(completed.stderr or completed.stdout).strip()[-2000:]}"
        )
    # The map creator writes its progress report to either stream depending on
    # the class, so callers that look for a counter get both.
    return completed.stdout + completed.stderr


def run_command(command: list[str], step: str) -> None:
    completed = subprocess.run(
        [str(part) for part in command], capture_output=True, text=True
    )
    if completed.returncode != 0:
        raise GenerationError(
            f"Échec de l'étape « {step} » (code {completed.returncode}).\n"
            f"{completed.stderr.strip()[:2000]}"
        )


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=300) as response, \
            destination.open("wb") as sink:
        shutil.copyfileobj(response, sink)


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def ensure_brouter(cache_dir: Path) -> tuple[Path, Path]:
    """Download and unpack the pinned BRouter release.

    Returns:
        The path of the map-creator jar and of the profile directory.

    Raises:
        GenerationError: if the download does not match the pinned checksum.
    """
    archive = cache_dir / f"brouter-{BROUTER_VERSION}.zip"
    unpacked = cache_dir / f"brouter-{BROUTER_VERSION}"
    jar = unpacked / f"brouter-{BROUTER_VERSION}-all.jar"
    profiles = cache_dir / "profiles"

    if not archive.exists():
        print(f"[0/4] Téléchargement de BRouter {BROUTER_VERSION}…")
        download(BROUTER_RELEASE_URL, archive)

    actual = sha256_of(archive)
    if actual != BROUTER_RELEASE_SHA256:
        archive.unlink()
        raise GenerationError(
            "L'archive BRouter téléchargée ne correspond pas à l'empreinte "
            f"attendue.\n  attendu : {BROUTER_RELEASE_SHA256}\n"
            f"  obtenu  : {actual}\nArchive supprimée ; relance le script."
        )

    if not jar.exists():
        with zipfile.ZipFile(archive) as archive_file:
            archive_file.extractall(cache_dir)
    if not jar.exists():
        raise GenerationError(f"Jar BRouter introuvable après extraction : {jar}")

    profiles.mkdir(parents=True, exist_ok=True)
    shutil.copy(unpacked / "profiles2" / "lookups.dat", profiles / "lookups.dat")
    for name in MAP_CREATION_PROFILES:
        target = profiles / name
        if not target.exists():
            download(BROUTER_PROFILE_BASE_URL + name, target)

    print(f"[0/4] BRouter {BROUTER_VERSION} prêt (empreinte vérifiée).")
    return jar, profiles


def elevation_tiles_for(box: BoundingBox) -> list[str]:
    """Name the one-degree SRTM tiles covering a bounding box."""
    tiles = []
    for latitude in range(math.floor(box.south), math.floor(box.north) + 1):
        for longitude in range(math.floor(box.west), math.floor(box.east) + 1):
            latitude_part = f"{'N' if latitude >= 0 else 'S'}{abs(latitude):02d}"
            longitude_part = f"{'E' if longitude >= 0 else 'W'}{abs(longitude):03d}"
            tiles.append(f"{latitude_part}{longitude_part}")
    return tiles


def brouter_elevation_tile_name(box: BoundingBox) -> str:
    """Name the 5°×5° elevation tile BRouter expects for a bounding box.

    BRouter reuses the CGIAR SRTM grid: column 1 starts at 180° W, row 1 at
    60° N, both counting in five-degree steps.
    """
    column = int((box.west + 180.0) / 5.0) + 1
    row = int((60.0 - box.north) / 5.0) + 1
    return f"srtm_{column:02d}_{row:02d}"


def prepare_elevation(box: BoundingBox, cache_dir: Path, work_dir: Path) -> Path:
    """Fetch SRTM tiles and convert them to the format BRouter reads."""
    hgt_dir = cache_dir / "srtm-hgt"
    hgt_dir.mkdir(parents=True, exist_ok=True)
    for tile in elevation_tiles_for(box):
        hgt_file = hgt_dir / f"{tile}.hgt"
        if hgt_file.exists():
            continue
        print(f"      téléchargement de l'altimétrie {tile}…")
        compressed = hgt_dir / f"{tile}.hgt.gz"
        download(
            ELEVATION_TILE_URL.format(latitude=tile[:3], tile=tile), compressed
        )
        run_command(["gunzip", "-f", str(compressed)], f"gunzip {tile}")
    return hgt_dir


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--osm-extract", type=Path, required=True,
                        help="extrait OpenStreetMap régional au format .osm.pbf")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--no-elevation", action="store_true",
                        help="ne pas attacher l'altimétrie au graphe")
    parser.add_argument("--keep-intermediate", action="store_true")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        if shutil.which("osmium") is None:
            raise GenerationError(
                "osmium est absent. Installe-le : sudo apt install osmium-tool"
            )
        if shutil.which("java") is None:
            raise GenerationError("java est absent (JDK 17 ou plus requis).")
        if not arguments.osm_extract.exists():
            raise GenerationError(
                f"Extrait OSM introuvable : {arguments.osm_extract}"
            )

        # Resolved because the map creator runs with its own working
        # directory, where a relative path would point somewhere else.
        arguments.work_dir = arguments.work_dir.resolve()
        arguments.cache_dir = arguments.cache_dir.resolve()
        arguments.output_dir = arguments.output_dir.resolve()
        arguments.osm_extract = arguments.osm_extract.resolve()

        config = CityConfig.load(arguments.config)
        box = config.bounding_box
        started = time.monotonic()
        print(f"Emprise : {box}\n")

        jar, profiles = ensure_brouter(arguments.cache_dir)

        work = arguments.work_dir
        for directory in ("nodetiles", "waytiles", "nodes55", "waytiles55",
                          "unodes55", "segments"):
            shutil.rmtree(work / directory, ignore_errors=True)
            (work / directory).mkdir(parents=True, exist_ok=True)

        # `complete_ways` rather than `smart`: keeping route relations whole
        # would drag in every way of the long-distance cycle routes that merely
        # pass through, all the way across the country. Turn restrictions are
        # local to a junction and survive this strategy intact.
        area_extract = work / "area.osm.pbf"
        print("[1/4] Découpe de l'extrait régional sur l'emprise…")
        run_command(
            ["osmium", "extract", "--bbox", box.as_osmium_extract_argument(),
             "--strategy", "complete_ways", "--overwrite",
             "-o", area_extract, arguments.osm_extract],
            "osmium extract",
        )
        print(f"      → {area_extract.stat().st_size / 1e6:.1f} Mo")

        print("[2/4] Découpe OSM en tuiles de nœuds et de chemins…")
        output = run_java(
            jar, "btools.mapcreator.OsmFastCutter",
            [str(profiles / "lookups.dat"),
             str(work / "nodetiles"), str(work / "waytiles"),
             str(work / "nodes55"), str(work / "waytiles55"),
             str(work / "bordernids.dat"), str(work / "relations.dat"),
             str(work / "restrictions.dat"),
             str(profiles / "all.brf"), str(profiles / "trekking.brf"),
             str(profiles / "softaccess.brf"), str(area_extract)],
            "OsmFastCutter",
        )
        for line in output.splitlines():
            if "turn-restrictions" in line:
                print(f"      {line.strip()}")

        print("[3/4] Unification des positions et altimétrie…")
        elevation_arguments: list[str] = []
        if arguments.no_elevation:
            print("      altimétrie désactivée (--no-elevation)")
            empty = work / "srtm-empty"
            empty.mkdir(exist_ok=True)
            elevation_arguments = [str(empty)]
        else:
            hgt_dir = prepare_elevation(box, arguments.cache_dir, work)
            bef_dir = arguments.cache_dir / "srtm-bef"
            bef_dir.mkdir(parents=True, exist_ok=True)
            tile_name = brouter_elevation_tile_name(box)
            if not (bef_dir / f"{tile_name}.bef").exists():
                print(f"      conversion de {tile_name} au format BRouter…")
                run_java(
                    jar, "btools.mapcreator.ElevationRasterTileConverter",
                    [tile_name, str(hgt_dir), str(bef_dir), "1"],
                    "ElevationRasterTileConverter",
                )
            elevation_arguments = [str(bef_dir)]

        run_java(
            jar, "btools.mapcreator.PosUnifier",
            [str(work / "nodes55"), str(work / "unodes55"),
             str(work / "bordernids.dat"), str(work / "bordernodes.dat"),
             *elevation_arguments],
            "PosUnifier",
        )

        print("[4/4] Assemblage du graphe routable…")
        run_java(
            jar, "btools.mapcreator.WayLinker",
            [str(work / "unodes55"), str(work / "waytiles55"),
             str(work / "bordernodes.dat"), str(work / "restrictions.dat"),
             str(profiles / "lookups.dat"), str(profiles / "all.brf"),
             str(work / "segments"), "rd5"],
            "WayLinker",
            extra_flags=["-DskipEncodingCheck=true"],
            working_directory=work,
        )

        segments = sorted((work / "segments").glob("*.rd5"))
        if not segments:
            raise GenerationError(
                "Aucun fichier rd5 produit : le graphe est vide. Vérifie que "
                "l'emprise recoupe bien l'extrait OSM fourni."
            )

        arguments.output_dir.mkdir(parents=True, exist_ok=True)
        total = 0
        for segment in segments:
            destination = arguments.output_dir / segment.name
            shutil.copy(segment, destination)
            total += destination.stat().st_size

        elapsed = time.monotonic() - started
        print(f"\n{'':=<60}")
        print(f"Graphe produit  : {arguments.output_dir}")
        for segment in segments:
            print(f"  {segment.name:16} "
                  f"{(arguments.output_dir / segment.name).stat().st_size / 1e6:.2f} Mo")
        print(f"Taille totale   : {total / 1e6:.2f} Mo")
        print(f"Durée           : {elapsed / 60:.1f} min")
        print(f"{'':=<60}")

        if not arguments.keep_intermediate:
            shutil.rmtree(work, ignore_errors=True)
        return 0

    except GenerationError as error:
        print(f"\nErreur : {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
