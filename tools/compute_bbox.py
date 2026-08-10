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
        raise KeyError("Le fichier d'auto-découverte GBFS est vide.")
    # GBFS 3.0 publie la liste directement ; les versions antérieures
    # l'imbriquent sous une clé de langue, dont le nom n'est pas garanti — le
    # flux lillois emploie « en » alors qu'il sert un réseau français.
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
        f"Le flux « {feed_name} » est absent de l'auto-découverte. "
        f"Flux disponibles : {available}"
    )


def load_stations(discovery_url: str | None, stations_file: Path | None) -> list[dict]:
    """Return the station list, from the network or from a local file."""
    if stations_file is not None:
        print(f"Lecture des stations depuis {stations_file}")
        with stations_file.open(encoding="utf-8") as stream:
            document = json.load(stream)
    else:
        print(f"Auto-découverte GBFS : {discovery_url}")
        discovery = fetch_json(discovery_url)
        information_url = resolve_feed_url(discovery, "station_information")
        print(f"station_information : {information_url}")
        document = fetch_json(information_url)
    return document["data"]["stations"]


def bounding_box_of_stations(stations: list[dict]) -> BoundingBox:
    """Compute the tight rectangle enclosing every station.

    Raises:
        ValueError: if the list is empty or holds no usable coordinates.
    """
    latitudes = [station["lat"] for station in stations if "lat" in station]
    longitudes = [station["lon"] for station in stations if "lon" in station]
    if not latitudes or not longitudes:
        raise ValueError("Aucune station exploitable dans station_information.")
    return BoundingBox(
        south=min(latitudes),
        west=min(longitudes),
        north=max(latitudes),
        east=max(longitudes),
    )


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CITY_CONFIG,
        help="fichier de configuration de ville à mettre à jour",
    )
    parser.add_argument(
        "--stations-file",
        type=Path,
        default=None,
        help="lire station_information.json localement au lieu du réseau",
    )
    parser.add_argument(
        "--margin-metres",
        type=float,
        default=None,
        help="marge autour des stations (défaut : valeur de la configuration)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="afficher le résultat sans écrire la configuration",
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

    stations = load_stations(config.gbfs_discovery_url, arguments.stations_file)
    tight_box = bounding_box_of_stations(stations)
    reference_box = tight_box.expanded_by_metres(margin)

    print()
    print(f"Stations              : {len(stations)}")
    print(f"Rectangle des stations: {tight_box}")
    print(f"Marge appliquée       : {margin:.0f} m")
    print(f"Emprise de référence  : {reference_box}")
    print(
        f"Dimensions            : {reference_box.width_kilometres:.1f} km "
        f"× {reference_box.height_kilometres:.1f} km "
        f"= {reference_box.area_square_kilometres:.0f} km²"
    )

    if arguments.dry_run:
        print("\n--dry-run : configuration inchangée.")
        return 0

    config.document["boundingBox"]["marginMeters"] = margin
    config.update_bounding_box(
        reference_box,
        station_count=len(stations),
        generated_at=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
    config.save()
    print(f"\nÉcrit dans {config.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
