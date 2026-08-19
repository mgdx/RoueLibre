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
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from city_config import (
    DEFAULT_CITY_CONFIG,
    METRES_PER_DEGREE_LATITUDE,
    BoundingBox,
    CityConfig,
    OpeningView,
    spread_through,
)

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


def station_name(station: dict) -> str:
    """What to call a station in the log.

    GBFS 3.0 publishes a name as a list of translations rather than as a
    string, so the first one is taken — the log names a station so that a human
    can go and look at it, and any of its languages does that. A station whose
    feed gives it no name at all is called by its identifier.
    """
    name = station.get("name")
    if isinstance(name, list):
        name = next(
            (
                translation.get("text")
                for translation in name
                if isinstance(translation, dict) and translation.get("text")
            ),
            None,
        )
    return str(name or station.get("station_id") or "unnamed")


# Two stations no further apart than this belong to the same cluster, and a
# cluster is joined by chaining: a station joins it as soon as ONE of its
# members is within reach. The stations of a docked network are a few hundred
# metres apart and a few kilometres at most; twenty-five leaves room for the
# widest real gap — a network serving two towns across a valley — and separates
# what is not part of the network at all. Valenbisi publishes a station called
# "LABMAD", three hundred kilometres from Valencia, in Madrid: its rectangle
# measured 33,645 km² instead of 150, and the network was set aside as "not a
# conurbation" on the strength of it.
CLUSTER_LINK_KILOMETRES = 25.0

# Cell of the grid the neighbour search uses, in degrees of latitude: a little
# over the distance above, so that a station's neighbours can only be in its
# own cell or in the eight around it. Without it the clustering would compare
# every pair, and sharedmobility.ch publishes 12,898 stations.
CLUSTER_GRID_DEGREES = 0.25

# How far a cluster must stand from the main one, edge to edge, before it can
# be read as another conurbation rather than an outskirt of this one. A hundred
# kilometres is well beyond any commute and beyond the widest network served —
# Blue-bike stands at the railway stations of the whole of Belgium, 139 km
# across, and no two of its clusters are a hundred kilometres apart.
OUTLYING_CLUSTER_KILOMETRES = 100.0

# And how small it must be at the same time. Distance alone would tear real
# networks apart: Nicosia spreads 14 % of its stations over the far side of
# Cyprus and San Jose 13 % over the Bay Area, and both are one network. What
# gets dropped is what is both far and marginal — Careem BIKE's six stations in
# Medina are 2.8 % of its feed, 1,580 km from Dubai, and sharedmobility.ch has
# one called "Chrysler Pt cruiser 2015" standing in Montreal.
OUTLYING_CLUSTER_SHARE = 0.10


def kilometres_between(
    first: tuple[float, float], second: tuple[float, float]
) -> float:
    """Ground distance between two positions, on the local flat approximation.

    Over the tens of kilometres this module measures, the error against a
    great-circle distance is far below the precision any of these thresholds
    needs.
    """
    latitude, longitude = first
    other_latitude, other_longitude = second
    north = (other_latitude - latitude) * METRES_PER_DEGREE_LATITUDE / 1000.0
    east = (
        (other_longitude - longitude)
        * METRES_PER_DEGREE_LATITUDE
        / 1000.0
        * math.cos(math.radians((latitude + other_latitude) / 2.0))
    )
    return math.hypot(north, east)


def station_clusters(positions: list[tuple[float, float]]) -> list[list[int]]:
    """Group the positions into clusters of neighbours, by index.

    Two stations are in the same cluster when a chain of stations no more than
    ``CLUSTER_LINK_KILOMETRES`` apart joins them — the connected components of
    that neighbourhood graph. A station standing alone is a cluster of one,
    which is what lets one rule cover both the isolated station and the distant
    group: they differ in size, not in kind.

    The pairs are drawn from a grid of cells wider than the link distance, so
    only a station's own cell and the eight around it are examined, and a pair
    already known to belong to the same cluster is not measured at all.

    Returns:
        the clusters as lists of indices, most populous first.
    """
    if not positions:
        return []
    parent = list(range(len(positions)))

    def root(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    grid: dict[tuple[int, int], list[int]] = {}
    for index, (latitude, longitude) in enumerate(positions):
        cell = (
            int(latitude // CLUSTER_GRID_DEGREES),
            int(longitude // CLUSTER_GRID_DEGREES),
        )
        grid.setdefault(cell, []).append(index)

    # Half the neighbourhood: the other four cells see this one when their own
    # turn comes, and a pair measured twice costs twice.
    offsets = ((0, 0), (0, 1), (1, -1), (1, 0), (1, 1))
    for (row, column), members in grid.items():
        for offset_row, offset_column in offsets:
            same_cell = (offset_row, offset_column) == (0, 0)
            others = (
                members
                if same_cell
                else grid.get((row + offset_row, column + offset_column))
            )
            if not others:
                continue
            for rank, index in enumerate(members):
                for other in others[rank + 1:] if same_cell else others:
                    first, second = root(index), root(other)
                    if first == second:
                        continue
                    if kilometres_between(
                        positions[index], positions[other]
                    ) <= CLUSTER_LINK_KILOMETRES:
                        parent[first] = second

    clusters: dict[int, list[int]] = {}
    for index in range(len(positions)):
        clusters.setdefault(root(index), []).append(index)
    return sorted(clusters.values(), key=len, reverse=True)


def rectangle_around(positions: list[tuple[float, float]]) -> BoundingBox:
    """The tight rectangle enclosing these positions.

    Raises:
        ValueError: if no position is given.
    """
    if not positions:
        raise ValueError("No position to draw a rectangle around.")
    return BoundingBox(
        south=min(latitude for latitude, _ in positions),
        west=min(longitude for _, longitude in positions),
        north=max(latitude for latitude, _ in positions),
        east=max(longitude for _, longitude in positions),
    )


def rectangle_gap_kilometres(first: BoundingBox, second: BoundingBox) -> float:
    """How far two rectangles stand apart, edge to edge, in kilometres.

    Edge to edge and not centre to centre: the centre of a regional network and
    the centre of a town inside it can be a hundred kilometres apart while the
    two overlap. Rectangles that overlap are zero apart.

    The east-west gap is measured at the latitude furthest from the equator of
    the two, where a degree of longitude is shortest: of the readings available
    that is the smallest, and a rule that sets a cluster aside had better
    under-state how far away it is.
    """
    north_gap = max(first.south - second.north, second.south - first.north, 0.0)
    east_gap = max(first.west - second.east, second.west - first.east, 0.0)
    furthest_latitude = max(
        abs(first.south), abs(first.north), abs(second.south), abs(second.north)
    )
    north_kilometres = north_gap * METRES_PER_DEGREE_LATITUDE / 1000.0
    east_kilometres = (
        east_gap
        * METRES_PER_DEGREE_LATITUDE
        / 1000.0
        * math.cos(math.radians(furthest_latitude))
    )
    return math.hypot(north_kilometres, east_kilometres)


def outlying_clusters(
    positions: list[tuple[float, float]],
    clusters: list[list[int]] | None = None,
) -> list[tuple[list[int], float]]:
    """The clusters that are not really there, each with its distance.

    A cluster is set aside when it is BOTH far from the most populous one and
    marginal in the network it claims to belong to — the two conditions
    together, never one alone. Far and substantial is a regional network, which
    §4 serves whole; near and small is an outskirt.

    A network of one or two stations is left alone: with nothing to be far
    from, the question does not arise.

    Args:
        clusters: the grouping of ``positions``, when the caller has already
            worked it out. Grouping twelve thousand stations is the expensive
            part of this module and doing it twice buys nothing.
    """
    if len(positions) < 3:
        return []
    if clusters is None:
        clusters = station_clusters(positions)
    main_rectangle = rectangle_around([positions[index] for index in clusters[0]])
    outlying = []
    for cluster in clusters[1:]:
        if len(cluster) >= OUTLYING_CLUSTER_SHARE * len(positions):
            continue
        distance = rectangle_gap_kilometres(
            main_rectangle,
            rectangle_around([positions[index] for index in cluster]),
        )
        if distance > OUTLYING_CLUSTER_KILOMETRES:
            outlying.append((cluster, distance))
    return outlying


def outlying_positions(positions: list[tuple[float, float]]) -> set[int]:
    """The indices of the positions that do not belong to the network."""
    return {index for cluster, _ in outlying_clusters(positions) for index in cluster}


@dataclass(frozen=True)
class StationSurvey:
    """What a feed's stations say about themselves, once read (SPEC.md §4).

    Attributes:
        stations: the stations the reference box is drawn on, the ones that are
            not really there already dropped.
        main_cluster_positions: the positions of the most populous cluster,
            which is what the opening framing is read from: that is where the
            stations the user came to see are.
    """

    stations: list[dict]
    main_cluster_positions: list[tuple[float, float]]

    @property
    def positions(self) -> list[tuple[float, float]]:
        """The retained stations' positions, in feed order."""
        return [(station["lat"], station["lon"]) for station in self.stations]

    @property
    def main_cluster_box(self) -> BoundingBox:
        """The rectangle of the most populous cluster, which sets the zoom."""
        return rectangle_around(self.main_cluster_positions)


def survey_stations(stations: list[dict]) -> StationSurvey:
    """Read a station list: what to keep, and where the network really is.

    A feed losing positions is a feed whose next regeneration deserves a look,
    so the count is printed rather than swallowed. Same for the clusters set
    aside: what is dropped from the box is named — how many stations, how far,
    and one of their names — never swallowed.

    Raises:
        ValueError: if not one station carries a usable position.
    """
    positioned = [station for station in stations if has_usable_position(station)]
    if not positioned:
        raise ValueError("No usable station in station_information.")
    dropped = len(stations) - len(positioned)
    if dropped:
        print(f"Stations without a usable position, ignored: {dropped}")

    coordinates = [(station["lat"], station["lon"]) for station in positioned]
    clusters = station_clusters(coordinates)
    # Read before the outlying clusters are dropped: they never hold the most
    # populous one, so it is the same cluster either way and the grouping is
    # paid for once.
    main_cluster_positions = [coordinates[index] for index in clusters[0]]
    outlying = outlying_clusters(coordinates, clusters)
    for cluster, distance in outlying:
        example = positioned[cluster[0]]
        print(
            f"Cluster of {len(cluster)} station(s) ignored, {distance:.0f} km "
            f"from the network: {station_name(example)} "
            f"({example['lat']}, {example['lon']})"
        )
    if outlying:
        ignored = {index for cluster, _ in outlying for index in cluster}
        positioned = [
            station for index, station in enumerate(positioned) if index not in ignored
        ]

    return StationSurvey(
        stations=positioned, main_cluster_positions=main_cluster_positions
    )


def positioned_stations(stations: list[dict]) -> list[dict]:
    """The stations the reference box is drawn on, the others said out loud."""
    return survey_stations(stations).stations


def bounding_box_of_stations(stations: list[dict]) -> BoundingBox:
    """Compute the tight rectangle enclosing every station given.

    The stations are expected to have been read by ``survey_stations`` first:
    the rectangle is drawn around what that hands back, and around nothing else.

    Raises:
        ValueError: if the list is empty or holds no usable coordinates.
    """
    positioned = [station for station in stations if has_usable_position(station)]
    if not positioned:
        raise ValueError("No usable station in station_information.")
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

    survey = survey_stations(
        load_stations(config.gbfs_discovery_url, arguments.stations_file)
    )
    stations = survey.stations
    tight_box = bounding_box_of_stations(stations)
    reference_box = tight_box.expanded_by_metres(margin)
    opening_view = OpeningView.from_stations(
        survey.main_cluster_positions, survey.main_cluster_box, margin
    )

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
    print(
        f"Framing on the stations: {opening_view.latitude:.6f}, "
        f"{opening_view.longitude:.6f} at zoom {opening_view.zoom}"
    )

    if arguments.dry_run:
        print("\n--dry-run: configuration left unchanged.")
        return 0

    config.document["boundingBox"]["marginMeters"] = margin
    framing_moved = config.update_bounding_box(
        reference_box,
        station_count=len(stations),
        generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        opening=opening_view,
        # Judged on a handful of points spread through the main cluster, and
        # not on every position this run happens to hold: the question is
        # whether the map opens on the network, not whether one station of it
        # is somewhere within reach.
        station_positions=spread_through(survey.main_cluster_positions),
    )
    if framing_moved:
        opening = config.document["map"]
        print(
            f"Opening framing moved : {opening['defaultCenterLatitude']:.6f}, "
            f"{opening['defaultCenterLongitude']:.6f} at zoom "
            f"{opening['defaultZoom']} — the former one showed no station"
        )
    config.save()
    print(f"\nWritten to {config.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
