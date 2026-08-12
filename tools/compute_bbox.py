#!/usr/bin/env python3
"""Derive the reference bounding box from the live station list (SPEC.md §4).

The three offline datasets — vector tiles, routing graph, address index — all
share one bounding box. It is deliberately *not* the administrative boundary of
the metropolitan area: that would cover large rural areas holding no station
and would inflate all three datasets for nothing. It is instead derived from
the stations themselves, then widened by a margin so that walking legs to and
from the edge of the network stay inside the routing graph.

Recomputing it at every regeneration means the datasets follow the network
whenever it grows, with no manual step.

Usage:
    python3 tools/compute_bbox.py [--config PATH] [--stations-file PATH]
                                  [--margin-metres N] [--dry-run]
"""

from __future__ import annotations

import argparse
import json
import math
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from city_config import DEFAULT_CITY_CONFIG, BoundingBox, CityConfig

# Identifies the tooling to the data provider without carrying anything
# specific to a user or a device (§4.4).
USER_AGENT = "RoueLibre-tools/1.0 (+https://github.com/mgdx/RoueLibre)"
NETWORK_TIMEOUT_SECONDS = 60


def fetch_json(url: str) -> dict:
    """Fetch and parse a JSON document over HTTPS.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
        json.JSONDecodeError: if the response is not valid JSON.
    """
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=NETWORK_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


def resolve_feed_url(discovery_document: dict, feed_name: str) -> str:
    """Look a feed URL up in a GBFS auto-discovery document.

    Going through the discovery file rather than guessing the URL is the whole
    point of GBFS, and it protects us from the producer moving a feed.

    The discovery document nests its feed list under a language key. The
    standard does not mandate which language a producer publishes, and the
    V'lille feed uses ``en`` even though it serves a French network, so the
    language key is never assumed — the first one present is used. GBFS 3.0
    dropped the language level entirely and publishes the list directly.

    Raises:
        KeyError: if the document has no feed list or no such feed.
    """
    data = discovery_document["data"]
    if not data:
        raise KeyError("The GBFS auto-discovery file is empty.")
    # GBFS 3.0 publishes the list directly; earlier versions nest it under a
    # language key whose name is not guaranteed — the Lille feed uses "en"
    # even though it serves a French network.
    if "feeds" in data:
        feeds = data["feeds"]
    else:
        first_language = next(iter(data))
        feeds = data[first_language]["feeds"]
    for feed in feeds:
        if feed["name"] == feed_name:
            return feed["url"]
    available = ", ".join(feed["name"] for feed in feeds)
    raise KeyError(
        f"Feed \"{feed_name}\" is absent from the auto-discovery file. "
        f"Feeds available: {available}"
    )


def load_stations(discovery_url: str | None, stations_file: Path | None) -> list[dict]:
    """Return the station list, from the network or from a local file."""
    if stations_file is not None:
        print(f"Reading the stations from {stations_file}")
        with stations_file.open(encoding="utf-8") as stream:
            document = json.load(stream)
    else:
        print(f"GBFS auto-discovery : {discovery_url}")
        discovery = fetch_json(discovery_url)
        information_url = resolve_feed_url(discovery, "station_information")
        print(f"station_information : {information_url}")
        document = fetch_json(information_url)
    return document["data"]["stations"]


def has_usable_position(station: dict) -> bool:
    """True if a station carries a position that can be believed.

    Feeds do publish stations at latitude and longitude zero — a field left
    empty rather than omitted. Nantes's has one. A single such station stretches
    the reference box from the conurbation down to the Gulf of Guinea, and with
    it the three datasets §4 cuts out of that box: the Naolib box measured
    888,100 km² before this check, against 39.
    """
    latitude, longitude = station.get("lat"), station.get("lon")
    if latitude is None or longitude is None:
        return False
    if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
        return False
    # No bike-share station stands within a hundred metres of Null Island.
    return abs(latitude) > 0.0001 or abs(longitude) > 0.0001


# A station standing this far from every other one is not part of the network.
# The stations of a docked network are a few hundred metres apart and a few
# kilometres at most; twenty-five leaves room for the widest real gap — a
# network serving two towns across a valley — and catches what is not a station
# at all. Valenbisi publishes one called "LABMAD", three hundred kilometres
# from Valencia, in Madrid: its rectangle measured 33,645 km² instead of 150,
# and the network was set aside as "not a conurbation" on the strength of it.
STRAY_STATION_DISTANCE_KILOMETRES = 25.0

# Cell of the grid the neighbour search uses, in degrees of latitude: a little
# over the distance above, so that a station's neighbours can only be in its
# own cell or in the eight around it.
STRAY_GRID_DEGREES = 0.25


def stray_positions(positions: list[tuple[float, float]]) -> set[int]:
    """The indices of the positions standing alone, far from every other.

    Compared against the OTHER STATIONS rather than against the centre of the
    network: a network legitimately spread over a valley has no centre worth
    the name, whereas a station three hundred kilometres from its nearest
    neighbour is a mistake in the feed whichever way it is measured.

    A network of one or two stations is left alone: with nothing to be far
    from, the question does not arise.
    """
    if len(positions) < 3:
        return set()
    grid: dict[tuple[int, int], list[int]] = {}
    for index, (latitude, longitude) in enumerate(positions):
        cell = (int(latitude // STRAY_GRID_DEGREES), int(longitude // STRAY_GRID_DEGREES))
        grid.setdefault(cell, []).append(index)

    limit = STRAY_STATION_DISTANCE_KILOMETRES
    strays = set()
    for index, (latitude, longitude) in enumerate(positions):
        cell = (int(latitude // STRAY_GRID_DEGREES), int(longitude // STRAY_GRID_DEGREES))
        alone = True
        for row in (-1, 0, 1):
            for column in (-1, 0, 1):
                for other in grid.get((cell[0] + row, cell[1] + column), ()):
                    if other == index:
                        continue
                    other_latitude, other_longitude = positions[other]
                    north = (other_latitude - latitude) * 111.32
                    east = (other_longitude - longitude) * 111.32 * math.cos(
                        math.radians(latitude))
                    if north * north + east * east <= limit * limit:
                        alone = False
                        break
                if not alone:
                    break
            if not alone:
                break
        if alone:
            strays.add(index)
    return strays


def positioned_stations(stations: list[dict]) -> list[dict]:
    """The stations that can be placed on a map, the others said out loud.

    A feed losing positions is a feed whose next regeneration deserves a look,
    so the count is printed rather than swallowed. Same for the strays: what is
    dropped from the box is named, never swallowed.

    Raises:
        ValueError: if not one station carries a usable position.
    """
    positioned = [station for station in stations if has_usable_position(station)]
    if not positioned:
        raise ValueError("No usable station in station_information.")
    dropped = len(stations) - len(positioned)
    if dropped:
        print(f"Stations without a usable position, ignored: {dropped}")

    strays = stray_positions([(station["lat"], station["lon"]) for station in positioned])
    if strays:
        for index in sorted(strays):
            station = positioned[index]
            print(f"Station standing alone, ignored: "
                  f"{station.get('name') or station.get('station_id')} "
                  f"({station['lat']}, {station['lon']})")
        positioned = [
            station for index, station in enumerate(positioned) if index not in strays
        ]
    return positioned


def bounding_box_of_stations(stations: list[dict]) -> BoundingBox:
    """Compute the tight rectangle enclosing every positioned station.

    Raises:
        ValueError: if the list is empty or holds no usable coordinates.
    """
    positioned = positioned_stations(stations)
    return BoundingBox(
        south=min(station["lat"] for station in positioned),
        west=min(station["lon"] for station in positioned),
        north=max(station["lat"] for station in positioned),
        east=max(station["lon"] for station in positioned),
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CITY_CONFIG,
        help="city configuration file to update",
    )
    parser.add_argument(
        "--stations-file",
        type=Path,
        default=None,
        help="read station_information.json locally instead of over the network",
    )
    parser.add_argument(
        "--margin-metres",
        type=float,
        default=None,
        help="margin around the stations (default: the configuration's value)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="show the result without writing the configuration",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    config = CityConfig.load(arguments.config)
    margin = (
        arguments.margin_metres
        if arguments.margin_metres is not None
        else config.bounding_box_margin_metres
    )

    stations = positioned_stations(
        load_stations(config.gbfs_discovery_url, arguments.stations_file)
    )
    tight_box = bounding_box_of_stations(stations)
    reference_box = tight_box.expanded_by_metres(margin)

    print()
    print(f"Stations              : {len(stations)}")
    print(f"Station rectangle     : {tight_box}")
    print(f"Margin applied        : {margin:.0f} m")
    print(f"Reference box         : {reference_box}")
    print(
        f"Dimensions            : {reference_box.width_kilometres:.1f} km "
        f"× {reference_box.height_kilometres:.1f} km "
        f"= {reference_box.area_square_kilometres:.0f} km²"
    )

    if arguments.dry_run:
        print("\n--dry-run: configuration left unchanged.")
        return 0

    config.document["boundingBox"]["marginMeters"] = margin
    centre_moved = config.update_bounding_box(
        reference_box,
        station_count=len(stations),
        generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
    if centre_moved:
        opening = config.document["map"]
        print(
            f"Opening centre moved  : {opening['defaultCenterLatitude']:.6f}, "
            f"{opening['defaultCenterLongitude']:.6f} — the former one fell "
            "outside the new box"
        )
    config.save()
    print(f"\nWritten to {config.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
