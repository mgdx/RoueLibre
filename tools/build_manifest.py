#!/usr/bin/env python3
"""Describe a published data release in a manifest file (SPEC.md §4.4).

The three offline datasets are published together as one coherent, versioned
release, separate from the application's own releases, so that refreshing the
map does not force a new build of the application, nor the other way round.

The manifest is what makes partial updates possible. The application keeps the
checksum of each installed file, fetches this document, and downloads only the
datasets whose checksum changed: refreshing the address index must never mean
downloading the map again.

Usage:
    python3 tools/build_manifest.py --release-tag data-2026-08
                                    [--config PATH] [--data-dir PATH]
                                    [--base-url URL] [--output PATH]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from city_config import DEFAULT_CITY_CONFIG, CityConfig

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DATA_DIR = REPO_ROOT / "data" / "out"
DEFAULT_OUTPUT = DEFAULT_DATA_DIR / "manifest.json"

DEFAULT_BASE_URL = "https://github.com/mgdx/RoueLibre/releases/download"


@dataclass(frozen=True)
class Dataset:
    """One downloadable dataset, as the application knows it."""

    identifier: str
    description: str
    # Where to find it under the data directory. A dataset may be a single
    # file or, for the routing graph, a directory of them.
    source: str
    single_file: bool = True


DATASETS = (
    Dataset("tiles", "Fond de carte vectoriel", "tiles.mbtiles"),
    Dataset("routing", "Graphe de routage", "routing", single_file=False),
    Dataset("addresses", "Index d'adresses", "addresses.sqlite"),
)


class ManifestError(RuntimeError):
    """The release cannot be described; the message says what is missing."""


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def describe(dataset: Dataset, data_dir: Path, base_url: str,
             release_tag: str, network_id: str) -> dict:
    """Build the manifest entry for one dataset.

    The published name and the local name differ. A release holds the data of
    every city in one flat namespace, where three `tiles.mbtiles` would collide;
    on the device the file keeps its bare name, because BRouter recognises its
    segments by name and would not find `vlille-E0_N50.rd5`.

    Raises:
        ManifestError: if the dataset has not been generated yet.
    """
    source = data_dir / dataset.source

    if dataset.single_file:
        if not source.is_file():
            raise ManifestError(
                f"Jeu de données « {dataset.identifier} » absent : {source}\n"
                "Lance d'abord le script de génération correspondant."
            )
        files = [source]
    else:
        if not source.is_dir() or not any(source.iterdir()):
            raise ManifestError(
                f"Jeu de données « {dataset.identifier} » absent : {source}"
            )
        files = sorted(path for path in source.iterdir() if path.is_file())

    return {
        "id": dataset.identifier,
        "description": dataset.description,
        "files": [
            {
                "name": path.name,
                "url": f"{base_url}/{release_tag}/{network_id}-{path.name}",
                "sizeBytes": path.stat().st_size,
                "sha256": sha256_of(path),
            }
            for path in files
        ],
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-tag", required=True,
                        help="étiquette de la release de données, "
                             "par exemple data-2026-08")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--data-dir", type=Path, default=DEFAULT_DATA_DIR)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        config = CityConfig.load(arguments.config)
        box = config.bounding_box

        entries = [
            describe(dataset, arguments.data_dir, arguments.base_url,
                     arguments.release_tag, config.network_id)
            for dataset in DATASETS
        ]

        manifest = {
            # Checked by the application before anything else: a manifest
            # announcing a format it cannot read must produce a clear
            # invitation to update, not a failure when opening a file (§4.4).
            "formatVersion": config.format_version,
            "releaseTag": arguments.release_tag,
            "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "network": config.network_id,
            "boundingBox": {
                "south": box.south, "west": box.west,
                "north": box.north, "east": box.east,
            },
            "datasets": entries,
        }

        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        with arguments.output.open("w", encoding="utf-8") as stream:
            json.dump(manifest, stream, ensure_ascii=False, indent=2)
            stream.write("\n")

        total = sum(
            file["sizeBytes"] for entry in entries for file in entry["files"]
        )
        print(f"Manifeste écrit : {arguments.output}")
        for entry in entries:
            size = sum(file["sizeBytes"] for file in entry["files"])
            print(f"  {entry['id']:10} {size / 1e6:>7.1f} Mo  "
                  f"({len(entry['files'])} fichier(s))")
        print(f"  {'total':10} {total / 1e6:>7.1f} Mo")
        return 0

    except ManifestError as error:
        print(f"\nErreur : {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
