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
            f"Step \"{step}\" failed (code {completed.returncode}).\n"
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
            f"Step \"{step}\" failed (code {completed.returncode}).\n"
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
        print(f"[0/4] Downloading BRouter {BROUTER_VERSION}…")
        download(BROUTER_RELEASE_URL, archive)

    actual = sha256_of(archive)
    if actual != BROUTER_RELEASE_SHA256:
        archive.unlink()
        raise GenerationError(
            "The downloaded BRouter archive does not match the expected "
            f"digest.\n  expected: {BROUTER_RELEASE_SHA256}\n"
            f"  got     : {actual}\nArchive deleted; run the script again."
        )

    if not jar.exists():
        with zipfile.ZipFile(archive) as archive_file:
            archive_file.extractall(cache_dir)
    if not jar.exists():
        raise GenerationError(f"BRouter jar not found after unpacking: {jar}")

    profiles.mkdir(parents=True, exist_ok=True)
    shutil.copy(unpacked / "profiles2" / "lookups.dat", profiles / "lookups.dat")
    for name in MAP_CREATION_PROFILES:
        target = profiles / name
        if not target.exists():
            download(BROUTER_PROFILE_BASE_URL + name, target)

    print(f"[0/4] BRouter {BROUTER_VERSION} ready (digest verified).")
    return jar, profiles


def elevation_tiles_for(box: BoundingBox) -> list[tuple[int, int]]:
    """The south-west corners of the one-degree SRTM tiles covering a box."""
    return [
        (latitude, longitude)
        for latitude in range(math.floor(box.south), math.floor(box.north) + 1)
        for longitude in range(math.floor(box.west), math.floor(box.east) + 1)
    ]


def elevation_tile_name(latitude: int, longitude: int) -> str:
    """Name the one-degree SRTM tile with that south-west corner."""
    latitude_part = f"{'N' if latitude >= 0 else 'S'}{abs(latitude):02d}"
    longitude_part = f"{'E' if longitude >= 0 else 'W'}{abs(longitude):03d}"
    return f"{latitude_part}{longitude_part}"


def brouter_elevation_tile_name(latitude: float, longitude: float) -> str:
    """Name the 5°×5° elevation tile BRouter expects for a point.

    BRouter reuses the CGIAR SRTM grid: column 1 starts at 180° W, row 1 at
    60° N, both counting in five-degree steps.
    """
    column = int((longitude + 180.0) / 5.0) + 1
    row = int((60.0 - latitude) / 5.0) + 1
    return f"srtm_{column:02d}_{row:02d}"


def brouter_elevation_tiles_for(
    box: BoundingBox, hgt_dir: Path
) -> dict[str, list[Path]]:
    """The BRouter tiles a box needs, each with the readings it is made of.

    A box is not bound to a single five-degree tile: a conurbation astride one
    of those lines — Lyon, Helsinki, Brussels — reaches two, and taking the
    tile of its north-west corner alone would leave the rest of it flat.
    """
    tiles: dict[str, list[Path]] = {}
    for latitude, longitude in elevation_tiles_for(box):
        # Named after the middle of the one-degree square rather than its
        # corner, so that a square whose edge lies on a five-degree line is
        # counted on its own side of it rather than on the neighbour's.
        tile = brouter_elevation_tile_name(latitude + 0.5, longitude + 0.5)
        reading = hgt_dir / f"{elevation_tile_name(latitude, longitude)}.hgt"
        tiles.setdefault(tile, []).append(reading)
    return tiles


def elevation_tile_holds(converted: Path, readings: list[Path]) -> bool:
    """Whether a converted tile was made from these readings.

    The converter reads whatever the cache holds when it runs, and names its
    output after the five-degree square alone. A square converted for one city
    therefore covers only the ground that city had downloaded, and the cities
    that came after it in the same square inherited a tile with a hole where
    they stand: the Paris graph carried no elevation at all because the square
    it shares with Lyon had been converted, months of readings earlier, for
    Lyon. A tile older than a reading it needs cannot have seen it.
    """
    if not converted.exists():
        return False
    converted_at = converted.stat().st_mtime
    return all(reading.stat().st_mtime <= converted_at for reading in readings)


def prepare_elevation(box: BoundingBox, cache_dir: Path) -> Path:
    """Fetch the SRTM readings a box needs, and say where they are."""
    hgt_dir = cache_dir / "srtm-hgt"
    hgt_dir.mkdir(parents=True, exist_ok=True)
    for latitude, longitude in elevation_tiles_for(box):
        tile = elevation_tile_name(latitude, longitude)
        hgt_file = hgt_dir / f"{tile}.hgt"
        if hgt_file.exists():
            continue
        print(f"      downloading elevation data {tile}…")
        compressed = hgt_dir / f"{tile}.hgt.gz"
        download(
            ELEVATION_TILE_URL.format(latitude=tile[:3], tile=tile), compressed
        )
        run_command(["gunzip", "-f", str(compressed)], f"gunzip {tile}")
    return hgt_dir


def prune_stale_segments(output_dir: Path, produced: set[str]) -> int:
    """Remove the rd5 files the current box no longer asks for.

    The output directory of a city is never wiped between runs — deliberately,
    because tiles.mbtiles and addresses.sqlite cost hours and a run resumed
    after a failed step must find them where it left them. The graph is the one
    dataset made of several files, named after the 5°×5° square each covers, so
    it is the one where a box that shrinks leaves files behind: correcting
    Careem BIKE's box from 1,612 km to 45 left the eight squares of the desert
    between Medina and the Emirates in place, and build_manifest.py, which
    lists what the directory holds, then announced 11.8 MB of graph instead of
    4.0 — 7.7 MB a Dubai user would have downloaded for nothing.

    The cleanup lives here rather than in generate_all.sh because this is the
    only place that knows, without guessing, which squares the box demands:
    they are the ones the map creator just produced. The orchestrator would
    have to re-derive the grid naming, and a directory emptied up front would
    take the other two datasets down with it.

    Only files this script itself writes are considered: what it did not
    produce, it does not remove. Sources and caches live elsewhere (data/osm,
    data/cache, data/work) and are never reached from here.

    Args:
        output_dir: the city's routing directory.
        produced: the names of the segments this run put there.

    Returns:
        the number of files removed.
    """
    stale = sorted(
        path for path in output_dir.glob("*.rd5")
        if path.is_file() and path.name not in produced
    )
    for path in stale:
        # Named rather than swallowed, as the stations the cluster filter sets
        # aside are (§4): a file leaving the release is a fact worth reading in
        # the log the next morning.
        size = path.stat().st_size
        print(f"  {path.name:16} {size / 1e6:.2f} MB removed, "
              f"outside the current box")
        path.unlink()
    return len(stale)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--osm-extract", type=Path, required=True,
                        help="regional OpenStreetMap extract in .osm.pbf format")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--work-dir", type=Path, default=DEFAULT_WORK_DIR)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--no-elevation", action="store_true",
                        help="do not attach elevation data to the graph")
    parser.add_argument("--keep-intermediate", action="store_true")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        if shutil.which("osmium") is None:
            raise GenerationError(
                "osmium is missing. Install it: sudo apt install osmium-tool"
            )
        if shutil.which("java") is None:
            raise GenerationError("java is missing (JDK 17 or later required).")
        if not arguments.osm_extract.exists():
            raise GenerationError(
                f"OSM extract not found: {arguments.osm_extract}"
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
        print("[1/4] Cutting the regional extract down to the box…")
        run_command(
            ["osmium", "extract", "--bbox", box.as_osmium_extract_argument(),
             "--strategy", "complete_ways", "--overwrite",
             "-o", area_extract, arguments.osm_extract],
            "osmium extract",
        )
        print(f"      → {area_extract.stat().st_size / 1e6:.1f} MB")

        print("[2/4] Cutting OSM into node and way tiles…")
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

        print("[3/4] Unifying positions and elevation…")
        elevation_arguments: list[str] = []
        if arguments.no_elevation:
            print("      elevation disabled (--no-elevation)")
            empty = work / "srtm-empty"
            empty.mkdir(exist_ok=True)
            elevation_arguments = [str(empty)]
        else:
            hgt_dir = prepare_elevation(box, arguments.cache_dir)
            bef_dir = arguments.cache_dir / "srtm-bef"
            bef_dir.mkdir(parents=True, exist_ok=True)
            tiles = brouter_elevation_tiles_for(box, hgt_dir)
            for tile_name, readings in tiles.items():
                if elevation_tile_holds(bef_dir / f"{tile_name}.bef", readings):
                    continue
                print(f"      converting {tile_name} to the BRouter format…")
                # The converter is handed the whole cache: it takes from it
                # every reading of the square it is asked for, this city's and
                # its neighbours', so one conversion serves them all.
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

        print("[4/4] Assembling the routable graph…")
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
                "No rd5 file produced: the graph is empty. Check that the box "
                "really overlaps the OSM extract given."
            )

        arguments.output_dir.mkdir(parents=True, exist_ok=True)
        total = 0
        for segment in segments:
            destination = arguments.output_dir / segment.name
            shutil.copy(segment, destination)
            total += destination.stat().st_size

        removed = prune_stale_segments(
            arguments.output_dir, {segment.name for segment in segments}
        )
        if removed:
            print(f"      {removed} segment(s) of a wider box removed")

        elapsed = time.monotonic() - started
        print(f"\n{'':=<60}")
        print(f"Graphe produit  : {arguments.output_dir}")
        for segment in segments:
            print(f"  {segment.name:16} "
                  f"{(arguments.output_dir / segment.name).stat().st_size / 1e6:.2f} MB")
        print(f"Total size      : {total / 1e6:.2f} MB")
        print(f"Duration        : {elapsed / 60:.1f} min")
        print(f"{'':=<60}")

        if not arguments.keep_intermediate:
            shutil.rmtree(work, ignore_errors=True)
        return 0

    except GenerationError as error:
        print(f"\nError: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
