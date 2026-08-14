#!/usr/bin/env python3
"""Build the catalogue of cities the application can serve (SPEC.md §15).

The application ships with one city configuration per network it knows how to
serve, and each of those files is the single source of everything specific to
an agglomeration. The catalogue is their index: a few kilobytes listing what
exists, where it is, and what its data weighs — enough for the application to
propose the right city from a position without downloading anything else.

It is deliberately derived rather than written by hand. A catalogue maintained
next to the configurations would drift from them, and the drift would show up
as a city that cannot be installed.

Usage:
    python3 tools/build_catalogue.py [--cities-dir PATH] [--data-dir PATH]
                                     [--output PATH]
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_CITIES_DIR = REPO_ROOT / "config" / "cities"
DEFAULT_DATA_DIR = REPO_ROOT / "data" / "out"
DEFAULT_OUTPUT = REPO_ROOT / "config" / "catalogue.json"

# Where the published catalogue lives. Written into the document itself so the
# application knows where to refresh from without carrying a URL in its code
# (SPEC.md §15) — and so that a fork republishes to its own address by
# regenerating the catalogue, not by patching Kotlin.
DEFAULT_CATALOGUE_URL = (
    "https://github.com/mgdx/RoueLibre-data/releases/latest/download/catalogue.json"
)

# Bumped when the shape of this document changes in a way older applications
# cannot read. They must then say so and invite an update, never fail silently.
CATALOGUE_VERSION = 1


class CatalogueError(RuntimeError):
    """A city configuration is unusable; the message says which one and why."""


def describe(config_path: Path, data_dir: Path) -> dict:
    """Turn one city configuration into a catalogue entry.

    The published size comes from the generated manifest when there is one.
    Without it the entry is still listed, size unknown: a city whose data has
    not been generated yet is a city one cannot install, and saying so is
    better than hiding it.
    """
    document = json.loads(config_path.read_text(encoding="utf-8"))
    network = document["network"]
    box = document.get("boundingBox") or {}
    for corner in ("south", "west", "north", "east"):
        if corner not in box:
            raise CatalogueError(
                f"{config_path.name}: incomplete box — run "
                f"tools/compute_bbox.py --config {config_path} first"
            )

    centre_latitude = document["map"]["defaultCenterLatitude"]
    centre_longitude = document["map"]["defaultCenterLongitude"]
    # The catalogue is what proposes a city and frames its map before anything
    # is downloaded. An opening centre outside its own box opens on an area the
    # tiles do not cover, and it is a symptom rather than a typo: the box was
    # recomputed and shrank past a centre nothing moved. Refusing to publish it
    # is what turns that into a fixable error instead of a blank map.
    if not (
        box["south"] <= centre_latitude <= box["north"]
        and box["west"] <= centre_longitude <= box["east"]
    ):
        raise CatalogueError(
            f"{config_path.name}: opening centre {centre_latitude}, "
            f"{centre_longitude} lies outside the box — re-run "
            f"tools/compute_bbox.py --config {config_path}"
        )

    manifest_path = data_dir / network["id"] / "manifest.json"
    size_bytes = None
    release_tag = None
    if manifest_path.is_file():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        release_tag = manifest.get("releaseTag")
        size_bytes = sum(
            file["sizeBytes"]
            for dataset in manifest.get("datasets", [])
            for file in dataset.get("files", [])
        )

    return {
        "id": network["id"],
        "displayName": network["displayName"],
        "operator": network["operator"],
        # The conurbation's own name. A network name says nothing about where
        # it runs to whoever has never been there: "Vélo'v" is Lyon, and only
        # the two together say so.
        "mainCity": network.get("city"),
        "country": document.get("country", "FR"),
        "stationCount": box.get("stationCount"),
        # Where the bikes actually are. The box of a regional network is mostly
        # empty — one station per town of a region encloses hundreds of
        # municipalities holding none — so the application measures how near a
        # network is on these points and never on the rectangle.
        "stationSamples": document.get("stationSamples", []),
        "boundingBox": {
            "south": box["south"], "west": box["west"],
            "north": box["north"], "east": box["east"],
        },
        "centreLatitude": centre_latitude,
        "centreLongitude": centre_longitude,
        "gbfsDiscoveryUrl": document["gbfs"]["discoveryUrl"],
        "manifestUrl": document["dataRelease"]["manifestUrl"],
        "dataSizeBytes": size_bytes,
        "releaseTag": release_tag,
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cities-dir", type=Path, default=DEFAULT_CITIES_DIR)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--catalogue-url", default=DEFAULT_CATALOGUE_URL)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        configs = sorted(arguments.cities_dir.glob("*.json"))
        if not configs:
            raise CatalogueError(f"No configuration in {arguments.cities_dir}")

        entries = [describe(path, arguments.data_dir) for path in configs]
        catalogue = {
            "catalogueVersion": CATALOGUE_VERSION,
            "catalogueUrl": arguments.catalogue_url,
            "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "cities": sorted(entries, key=lambda entry: entry["displayName"]),
        }
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        with arguments.output.open("w", encoding="utf-8") as stream:
            json.dump(catalogue, stream, ensure_ascii=False, indent=2)
            stream.write("\n")

        print(f"Catalogue written: {arguments.output}")
        for entry in catalogue["cities"]:
            size = entry["dataSizeBytes"]
            weight = f"{size / 1e6:>6.1f} MB" if size else "  not generated"
            print(f"  {entry['displayName']:<22} {entry['stationCount']:>5} stations "
                  f"{weight}")
        return 0

    except (CatalogueError, KeyError) as error:
        print(f"\nError: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
