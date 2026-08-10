#!/usr/bin/env python3
"""Write the city configuration of a surveyed network (SPEC.md §15).

Adding a conurbation must be a matter of a configuration file, never of code.
This script writes that file from what `tools/discover_networks.py` observed:
the verified auto-discovery address, the network's name, the authority behind
it, its licence, and the reference bounding box read from its own stations.

It writes nothing it has not seen. The bounding box is recomputed here against
the live feed rather than copied from the survey, so that a configuration is
never further from the network than the moment it was written.

An existing configuration is left alone: those three were settled by hand, and
the survey has no business rewriting them. `--overwrite` says otherwise,
explicitly.

Usage:
    python3 tools/add_city.py --all               # every eligible network
    python3 tools/add_city.py --network zebullo   # one, by survey identifier
    python3 tools/add_city.py --list              # what would be written
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

from city_config import BoundingBox, CityConfig
from compute_bbox import bounding_box_of_stations, load_stations, positioned_stations

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_SURVEY = REPO_ROOT / "data" / "networks-fr.json"
DEFAULT_CITIES_DIR = REPO_ROOT / "config" / "cities"

# Where the data of a city is published (§4.4). Written into each
# configuration, and changeable in the settings: the host must never be a
# single point of failure, which is why the application can also be fed by hand.
MANIFEST_URL_TEMPLATE = (
    "https://github.com/mgdx/RoueLibre/releases/latest/download/manifest-{id}.json"
)

# The margin §4 puts around the stations, so that a walking leg towards the
# edge of the network stays inside the routing graph.
DEFAULT_MARGIN_METRES = 3000.0

# The format of the datasets the application must be able to read. Same value
# as the cities already configured: nothing about the format changes here.
DATA_FORMAT_VERSION = 2

# Opening zoom. MapLibre counts in 512-pixel tiles, which puts its zoom about
# one step off the 256-tile convention. The three conurbations settled by hand
# open between 11.2 and 11.5 over boxes around 21 km wide; the rule below
# reproduces that and tightens the framing for a smaller network, so that a
# ten-station town does not open on a quarter of its department.
REFERENCE_ZOOM = 11.4
REFERENCE_WIDTH_KILOMETRES = 21.0
MINIMUM_ZOOM, MAXIMUM_ZOOM = 10.5, 14.0

CONFIGURATION_COMMENT = [
    "City configuration — SPEC.md §15.",
    "This file is the ONLY source of the data specific to a conurbation.",
    "No URL, coordinate or network name may appear anywhere else in the code.",
    "Written by tools/add_city.py from the survey of tools/discover_networks.py,",
    "which read the auto-discovery address from transport.data.gouv.fr or from",
    "MobilityData's catalogue and verified it by a real request (§4.1).",
    "The 'boundingBox' block is RECOMPUTED by tools/compute_bbox.py on every",
    "regeneration of the data (§4) — do not edit it by hand.",
]


def slug(text: str) -> str:
    """Reduce a name to an identifier: lowercase, unaccented, hyphenated."""
    decomposed = unicodedata.normalize("NFD", text.lower())
    letters = "".join(
        character if character.isalnum() else " "
        for character in decomposed
        if unicodedata.category(character) != "Mn"
    )
    return "-".join(letters.split())


def unique(candidate: str, taken: set[str], qualifier: str) -> str:
    """Return an identifier free of collision, qualified by place if need be.

    Two conurbations do run networks of the same name — "VéloCité" is both
    Mulhouse's and Besançon's. The brand alone would then name two directories
    of data and two manifests, and the second would silently overwrite the
    first.
    """
    if candidate not in taken:
        return candidate
    qualified = f"{candidate}-{qualifier}" if qualifier else candidate
    if qualified not in taken:
        return qualified
    index = 2
    while f"{qualified}-{index}" in taken:
        index += 1
    return f"{qualified}-{index}"


def default_zoom(box: BoundingBox) -> float:
    """The opening zoom that frames a network of this size."""
    width = max(box.width_kilometres, 0.5)
    zoom = REFERENCE_ZOOM - math.log2(width / REFERENCE_WIDTH_KILOMETRES)
    return round(min(max(zoom, MINIMUM_ZOOM), MAXIMUM_ZOOM), 1)


def build_document(survey: dict, network_id: str, box: BoundingBox,
                   station_count: int) -> dict:
    """Assemble a city configuration from a surveyed network and its box."""
    centre_latitude = round((box.south + box.north) / 2, 6)
    centre_longitude = round((box.west + box.east) / 2, 6)
    versions = survey.get("declaredVersions") or survey.get("gbfsVersion", "")
    return {
        "$comment": CONFIGURATION_COMMENT,
        "configVersion": 1,
        "network": {
            "id": network_id,
            "displayName": survey["displayName"],
            "operator": survey.get("operator") or survey["displayName"],
            "city": survey.get("mainCity") or survey.get("location") or "",
            "defaultLanguage": "fr",
        },
        "gbfs": {
            "$comment": [
                "URL of the auto-discovery file, and of that alone (§4.1).",
                f"Answered GBFS {survey.get('gbfsVersion', '?')} on "
                f"{survey.get('surveyedAt', '')}, with "
                f"{station_count} stations.",
                f"Source: {survey.get('source', '')}."
                + (f" Declared versions: {versions}." if versions else ""),
                "The station_information and station_status URLs are NEVER",
                "hard-coded: they are read from this auto-discovery file.",
            ],
            "discoveryUrl": survey["discoveryUrl"],
            "attribution": survey.get("attribution", ""),
            "attributionUrl": survey.get("attributionUrl", ""),
        },
        "boundingBox": {
            "$comment": [
                "Reference box shared by the three datasets (§4).",
                "Derived from the stations' enclosing rectangle, widened by 3 km,",
                "which is why it covers the conurbation and not the municipality",
                "the network is named after.",
                "Generated by tools/compute_bbox.py — DO NOT EDIT BY HAND.",
            ],
            "marginMeters": DEFAULT_MARGIN_METRES,
            "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "stationCount": station_count,
            "south": round(box.south, 6),
            "west": round(box.west, 6),
            "north": round(box.north, 6),
            "east": round(box.east, 6),
        },
        "map": {
            "$comment": [
                "Opening centre and zoom, used when no position is available.",
                "The centre is that of the reference box: it is where the",
                "stations are, which is what the user opens the map to see.",
                "MapLibre counts in 512-pixel tiles: its zoom is off by about",
                "one step from the 256-tile convention.",
            ],
            "defaultCenterLatitude": centre_latitude,
            "defaultCenterLongitude": centre_longitude,
            "defaultZoom": default_zoom(box),
            "minZoom": 10,
            "maxZoom": 16,
        },
        "dataSources": {
            "$comment": [
                "What tools/generate_all.sh needs in order to produce this",
                "city's data in one command: the OpenStreetMap extract to cut",
                "the box out of, and the Base Adresse Nationale departments the",
                "box reaches. Both are read from the stations themselves, not",
                "from an administrative boundary — a network routinely spills",
                "into a neighbouring department.",
            ],
            "osmRegions": survey.get("osmRegions", []),
            "banDepartments": survey.get("departments", []),
        },
        "dataRelease": {
            "$comment": [
                "Default URL of the manifest describing the published dataset (§4.4).",
                "Changeable in the settings, and bypassable by manual import: the",
                "host must never be a single point of failure.",
            ],
            "manifestUrl": MANIFEST_URL_TEMPLATE.format(id=network_id),
            "formatVersion": DATA_FORMAT_VERSION,
        },
    }


def existing_configurations(cities_dir: Path) -> dict[str, dict]:
    """The configurations already present, indexed by their network identifier."""
    configurations = {}
    for path in sorted(cities_dir.glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        configurations[document["network"]["id"]] = {"path": path, "document": document}
    return configurations


def already_served(survey: dict, configurations: dict[str, dict]) -> str | None:
    """The identifier of the configuration already covering this network, if any.

    Matched on the auto-discovery address first, then on the stations: the
    three conurbations settled by hand predate the survey and may be listed
    under another of the addresses the catalogues publish.
    """
    addresses = {survey["discoveryUrl"], *survey.get("alternateUrls", [])}
    box = survey.get("boundingBox")
    for identifier, existing in configurations.items():
        document = existing["document"]
        if document["gbfs"]["discoveryUrl"] in addresses:
            return identifier
        stored = document.get("boundingBox") or {}
        if not box or stored.get("south") is None:
            continue
        # Position alone is not enough: two networks share a conurbation —
        # Nantes runs Naolib and Naolib Micromob' side by side — and matching
        # on the box would have one overwrite the other. The fleet must agree
        # too. The stored box carries the 3 km margin the survey's does not,
        # hence containment rather than equality.
        centre = ((box["south"] + box["north"]) / 2, (box["west"] + box["east"]) / 2)
        inside = (stored["south"] <= centre[0] <= stored["north"]
                  and stored["west"] <= centre[1] <= stored["east"])
        counts = (stored.get("stationCount") or 0, survey.get("stationCount") or 0)
        comparable = abs(counts[0] - counts[1]) <= max(3, 0.1 * max(counts))
        if inside and comparable:
            return identifier
    return None


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--survey", type=Path, default=DEFAULT_SURVEY)
    parser.add_argument("--cities-dir", type=Path, default=DEFAULT_CITIES_DIR)
    parser.add_argument("--all", action="store_true", help="write every eligible network")
    parser.add_argument("--network", action="append", default=[],
                        help="survey identifier of a single network, repeatable")
    parser.add_argument("--list", action="store_true",
                        help="show what would be written, and write nothing")
    parser.add_argument("--overwrite", action="store_true",
                        help="rewrite a configuration that already exists")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if not (arguments.all or arguments.network or arguments.list):
        print("Nothing to do: pass --all, --network or --list.", file=sys.stderr)
        return 1

    survey_document = json.loads(arguments.survey.read_text(encoding="utf-8"))
    surveyed_at = survey_document.get("generatedAt", "")
    networks = [
        network for network in survey_document["networks"]
        if network["verdict"] == "eligible"
        and (not arguments.network or network.get("systemId") in arguments.network)
    ]
    networks.sort(key=lambda network: -network.get("stationCount", 0))

    configurations = existing_configurations(arguments.cities_dir)
    taken_identifiers = set(configurations)
    taken_files = {path.stem for path in arguments.cities_dir.glob("*.json")}

    written = skipped = failed = 0
    for network in networks:
        network["surveyedAt"] = surveyed_at
        served_by = already_served(network, configurations)
        if served_by and not arguments.overwrite:
            skipped += 1
            print(f"  = {network['displayName']:<26} already served by {served_by}")
            continue

        if served_by:
            # Rewriting in place: a new file name would leave the old
            # configuration behind and the catalogue would list the city twice.
            identifier = served_by
            file_name = configurations[served_by]["path"].stem
        else:
            place = slug(network.get("mainCity") or network.get("location") or "")
            identifier = unique(slug(network["displayName"]), taken_identifiers, place)
            file_name = unique(place or identifier, taken_files, identifier)

        if arguments.list:
            print(f"  + {network['displayName']:<26} → {file_name}.json  (id {identifier})")
            taken_identifiers.add(identifier)
            taken_files.add(file_name)
            continue

        try:
            stations = positioned_stations(load_stations(network["discoveryUrl"], None))
            box = bounding_box_of_stations(stations).expanded_by_metres(
                DEFAULT_MARGIN_METRES
            )
        except Exception as error:  # noqa: BLE001 — reported, then the next city
            failed += 1
            print(f"  ! {network['displayName']:<26} {type(error).__name__}: {error}")
            continue

        document = build_document(network, identifier, box, len(stations))
        path = arguments.cities_dir / f"{file_name}.json"
        with path.open("w", encoding="utf-8") as stream:
            json.dump(document, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        taken_identifiers.add(identifier)
        taken_files.add(file_name)
        written += 1
        print(
            f"  + {network['displayName']:<26} {path.name:<28} "
            f"{len(stations):>4} stations, {box.area_square_kilometres:>5.0f} km²"
        )

    print(f"\n{written} written, {skipped} already served, {failed} failed")
    if written:
        print("Next: python3 tools/build_catalogue.py")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
