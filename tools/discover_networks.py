#!/usr/bin/env python3
"""Survey the French bike-share networks that publish a GBFS feed (SPEC.md §4.1).

`SPEC.md` forbids guessing a `gbfs.json` URL: it must come from the national
access point `transport.data.gouv.fr` or from MobilityData's `systems.csv`
catalogue, and be verified by a real request before being written into a
configuration. This script does exactly that, for every French system at once,
and keeps the evidence: it reads both catalogues, calls every feed it finds,
and records what came back.

It then applies the eligibility rules below. They matter more than the survey
itself, because most of what publishes GBFS in France is **not** what this
application serves: free-floating scooters, car-sharing, and fleets whose
"stations" are painted parking areas with no docks at all.

Output:

* ``docs/networks-france.md`` — the readable list, eligible networks first,
  then the rejected ones grouped by reason. Regenerating it is how the list is
  kept honest;
* ``data/networks-fr.json`` — the same survey, machine-readable, consumed by
  ``tools/add_city.py`` to write the city configurations.

Usage:
    python3 tools/discover_networks.py [--report PATH] [--survey PATH]
                                       [--offline]
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import math
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_REPORT = REPO_ROOT / "docs" / "networks-france.md"
DEFAULT_SURVEY = REPO_ROOT / "data" / "networks-fr.json"

# The two catalogues SPEC.md §4.1 names as acceptable starting points. Neither
# is trusted on its own: each is only a list of addresses to go and check.
MOBILITYDATA_SYSTEMS_CSV = (
    "https://raw.githubusercontent.com/MobilityData/gbfs/master/systems.csv"
)
TRANSPORT_DATA_GOUV_DATASETS = "https://transport.data.gouv.fr/api/datasets"

# Identifies the tooling without carrying anything specific to a user (§4.4).
USER_AGENT = "RoueLibre-tools/1.0 (+https://github.com/mgdx/RoueLibre)"
NETWORK_TIMEOUT_SECONDS = 25
CONCURRENT_REQUESTS = 10

# The margin §4 puts around the stations to make the reference box. Repeated
# from tools/add_city.py, which writes it into each configuration: here it only
# decides which extracts a box of that size reaches.
DEFAULT_MARGIN_METRES = 3000.0

# Resolves a station's position to its municipality and department, which is
# what tells the generation scripts which Base Adresse Nationale extracts to
# download. Public, keyless, and run by the same administration as the BAN.
GEO_API_COMMUNES = "https://geo.api.gouv.fr/communes"
GEO_API_EPCI_COMMUNES = "https://geo.api.gouv.fr/epcis/{code}/communes"

# Below this, the journey algorithm has nothing to optimise: §6 picks the best
# pair among five candidate departure stations and five arrival ones, and a
# network of three stations offers a single itinerary the user already knows.
# Such systems are listed as rejected, never hidden — the reason is printed.
MINIMUM_STATIONS = 10

# Beyond this, the stations no longer describe a conurbation. Some operators
# publish one feed for a whole region, or for the whole country: their
# enclosing rectangle would then cover hundreds of municipalities without a
# station, and §4's three datasets — tiles, routing graph, address index — are
# sized for a conurbation, not for a quarter of France. As a landmark, the
# Paris box measures 33 × 30 km, near 1,000 km².
MAXIMUM_AREA_SQUARE_KILOMETRES = 2_500

# Every attempt is retried once: a name resolution that fails for a second is
# not a network that does not exist, and a single miss would drop a real
# conurbation from the list.
ATTEMPTS_PER_URL = 2

# Station positions kept in the survey, spread through the list. Enough to name
# every municipality a network reaches without storing the whole feed.
STATION_SAMPLES = 25

# The reference box is sampled on a grid as well, to list the departments it
# reaches. The stations alone would not do: the box carries a 3 km margin
# around them, and the Base Adresse Nationale extract to download is chosen for
# the box, not for the stations. A grid of this side leaves no gap wider than a
# fifth of the box — no French department is that small.
BOX_GRID_SIDE = 5

# Past this, a title has stopped being a name and started describing an offer.
MAXIMUM_NAME_WORDS = 4

# What the licence codes of the two catalogues mean, spelled out for the
# attribution the "about" screen shows (§4.5). A code absent from this table is
# reproduced as it stands rather than guessed at.
LICENCE_NAMES = {
    "lov2": "Licence Ouverte 2.0",
    "fr-lo": "Licence Ouverte",
    "odc-odbl": "ODbL",
    "ODbL-1.0": "ODbL",
    "CC0-1.0": "CC0 1.0",
    "CC-BY-4.0": "CC BY 4.0",
    "cc-by": "CC BY",
    "notspecified": "",
    "other-open": "",
}

# Vehicle forms this application is about. A network whose fleet is cars is not
# a bike-share network, whatever its stations look like; a network mixing bikes
# with standing scooters at the same docks is one, and is kept.
BICYCLE_FORM_FACTORS = frozenset({"bicycle", "cargo_bicycle"})
DISQUALIFYING_FORM_FACTORS = frozenset({"car", "moped", "other"})

# Geofabrik publishes its French extracts under the pre-2016 regions. The map
# is needed to tell a generation run which extract to download; there is no
# programmatic source for it, so it is written out once here.
GEOFABRIK_REGION_BY_DEPARTMENT = {
    "alsace": ("67", "68"),
    "aquitaine": ("24", "33", "40", "47", "64"),
    "auvergne": ("03", "15", "43", "63"),
    "basse-normandie": ("14", "50", "61"),
    "bourgogne": ("21", "58", "71", "89"),
    "bretagne": ("22", "29", "35", "56"),
    "centre": ("18", "28", "36", "37", "41", "45"),
    "champagne-ardenne": ("08", "10", "51", "52"),
    "corse": ("2A", "2B"),
    "franche-comte": ("25", "39", "70", "90"),
    "guadeloupe": ("971",),
    "guyane": ("973",),
    "haute-normandie": ("27", "76"),
    "ile-de-france": ("75", "77", "78", "91", "92", "93", "94", "95"),
    "languedoc-roussillon": ("11", "30", "34", "48", "66"),
    "limousin": ("19", "23", "87"),
    "lorraine": ("54", "55", "57", "88"),
    "martinique": ("972",),
    "mayotte": ("976",),
    "midi-pyrenees": ("09", "12", "31", "32", "46", "65", "81", "82"),
    "nord-pas-de-calais": ("59", "62"),
    "pays-de-la-loire": ("44", "49", "53", "72", "85"),
    "picardie": ("02", "60", "80"),
    "poitou-charentes": ("16", "17", "79", "86"),
    "provence-alpes-cote-d-azur": ("04", "05", "06", "13", "83", "84"),
    "reunion": ("974",),
    "rhone-alpes": ("01", "07", "26", "38", "42", "69", "73", "74"),
}
REGION_OF_DEPARTMENT = {
    department: region
    for region, departments in GEOFABRIK_REGION_BY_DEPARTMENT.items()
    for department in departments
}


def fetch_json(url: str) -> dict | list:
    """Fetch and parse a JSON document, retrying once on failure.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
        json.JSONDecodeError: if the response is not JSON.
    """
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(ATTEMPTS_PER_URL):
        try:
            with urllib.request.urlopen(
                request, timeout=NETWORK_TIMEOUT_SECONDS
            ) as response:
                return json.loads(response.read().decode("utf-8", "replace"))
        except Exception:  # noqa: BLE001 — retried, then re-raised below
            if attempt == ATTEMPTS_PER_URL - 1:
                raise
            time.sleep(1.0)
    raise RuntimeError("unreachable")  # pragma: no cover


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=NETWORK_TIMEOUT_SECONDS) as response:
        return response.read().decode("utf-8", "replace")


def feed_urls(discovery_document: dict) -> dict[str, str]:
    """Index a GBFS auto-discovery document by feed name.

    GBFS 3.0 publishes the feed list directly; earlier versions nest it under a
    language key whose name the standard does not fix. Where several languages
    are published — nextbike does — they carry the same feeds, so the first
    one will do.

    Raises:
        KeyError: if the document holds no feed list at all.
    """
    data = discovery_document["data"]
    feeds = data.get("feeds") if isinstance(data, dict) else None
    if feeds is None:
        first_language = next(iter(data))
        feeds = data[first_language]["feeds"]
    return {feed["name"]: feed["url"] for feed in feeds if "name" in feed and "url" in feed}


def localised(value) -> str:
    """Read a GBFS text field, whichever version wrote it.

    Up to 2.3 a name is a plain string; 3.0 turned it into a list of
    ``{language, text}`` objects. Both reach us, sometimes from the same
    operator on two different networks.
    """
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list) and value:
        return str(value[0].get("text", "")).strip()
    return ""


def probe(candidate: dict) -> dict:
    """Call one network's feeds and describe what came back.

    Never raises: a failure is a result like any other, and it is reported
    rather than dropped — a feed that is down today may be the only source for
    a real network, and hiding it would look like the network does not exist.

    A catalogue often publishes several addresses for the same network, an
    older version beside the current one. They are tried in turn: what matters
    is reaching the network, not the address that happened to be listed first.
    """
    survey = dict(candidate)
    if candidate.get("authenticationType"):
        # A feed behind a key cannot be read by an application that keeps no
        # account and hard-codes no secret (§2, C3).
        survey["verdict"] = "authentication-required"
        return survey

    discovery = feeds = None
    for url in candidate["discoveryUrls"]:
        try:
            discovery = fetch_json(url)
            feeds = feed_urls(discovery)
            survey["discoveryUrl"] = url
            break
        except Exception as error:  # noqa: BLE001 — every failure is reportable
            survey["error"] = f"{type(error).__name__}: {error}"
    if feeds is None:
        survey["verdict"] = "unreachable"
        return survey
    survey.pop("error", None)

    survey["gbfsVersion"] = str(discovery.get("version", "1.0"))
    survey["feeds"] = sorted(feeds)

    if "system_information" in feeds:
        try:
            information = fetch_json(feeds["system_information"])["data"]
            survey["systemName"] = localised(information.get("name"))
            survey["systemOperator"] = localised(information.get("operator"))
            survey["licenceId"] = information.get("license_id")
            survey["licenceUrl"] = information.get("license_url")
        except Exception:  # noqa: BLE001 — optional enrichment
            pass

    if "vehicle_types" in feeds:
        try:
            types = fetch_json(feeds["vehicle_types"])["data"]["vehicle_types"]
            survey["formFactors"] = sorted(
                {kind.get("form_factor", "unknown") for kind in types}
            )
        except Exception:  # noqa: BLE001 — optional enrichment
            pass

    if "station_information" not in feeds:
        # A fleet with no station is a free-floating fleet: this application
        # takes a bike at a dock and returns it at another (§6).
        survey["verdict"] = "free-floating"
        return survey

    try:
        stations = fetch_json(feeds["station_information"])["data"]["stations"]
    except Exception as error:  # noqa: BLE001
        survey["verdict"] = "unreachable"
        survey["error"] = f"station_information — {type(error).__name__}: {error}"
        return survey

    positioned = [
        station for station in stations
        if is_usable_position(station.get("lat"), station.get("lon"))
    ]
    survey["stationCount"] = len(positioned)
    survey["unpositionedStations"] = len(stations) - len(positioned)
    survey["capacityTotal"] = sum(station.get("capacity") or 0 for station in stations)
    if positioned:
        box = {
            "south": min(station["lat"] for station in positioned),
            "west": min(station["lon"] for station in positioned),
            "north": max(station["lat"] for station in positioned),
            "east": max(station["lon"] for station in positioned),
        }
        survey["boundingBox"] = box
        record_reference_area(survey)
        # A handful of positions, spread through the list, is enough to name
        # the conurbation later on without keeping every station in the survey.
        step = max(1, len(positioned) // STATION_SAMPLES)
        survey["stationSamples"] = [
            [station["lat"], station["lon"]] for station in positioned[::step]
        ][:STATION_SAMPLES]

    if "station_status" in feeds:
        try:
            live = fetch_json(feeds["station_status"])["data"]["stations"]
            survey["reportsDocks"] = any(
                "num_docks_available" in station for station in live
            )
            survey["bikesAvailable"] = sum(
                station.get("num_bikes_available")
                or station.get("num_vehicles_available")
                or 0
                for station in live
            )
        except Exception:  # noqa: BLE001 — optional enrichment
            pass

    survey["verdict"] = verdict_of(survey)
    return survey


def is_usable_position(latitude, longitude) -> bool:
    """True if a station carries a position that can be believed.

    Feeds do publish stations at latitude zero — a field left empty rather than
    omitted. One of them is enough to stretch the reference box from the
    conurbation down to the Gulf of Guinea, and with it the three datasets §4
    derives from that box.
    """
    if latitude is None or longitude is None:
        return False
    if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
        return False
    return abs(latitude) > 0.0001 or abs(longitude) > 0.0001


def widen(box: dict, margin_metres: float) -> dict:
    """Grow a box by a margin on all four sides, as §4 does around the stations."""
    centre_latitude = math.radians((box["south"] + box["north"]) / 2.0)
    latitude_margin = margin_metres / 111_320.0
    longitude_margin = latitude_margin / max(math.cos(centre_latitude), 0.01)
    return {
        "south": box["south"] - latitude_margin,
        "west": box["west"] - longitude_margin,
        "north": box["north"] + latitude_margin,
        "east": box["east"] + longitude_margin,
    }


def area_square_kilometres(box: dict) -> float:
    """The area of a bounding box, near enough for a size check."""
    centre_latitude = math.radians((box["south"] + box["north"]) / 2.0)
    height = (box["north"] - box["south"]) * 111.32
    width = (box["east"] - box["west"]) * 111.32 * math.cos(centre_latitude)
    return abs(height * width)


def record_reference_area(survey: dict) -> None:
    """Store the area of the box §4 actually cuts the datasets to.

    The stations' own rectangle is not that box: the reference box carries the
    3 km margin, and a small network is mostly margin — Auch's stations enclose
    4 km², the box around them 65. What decides whether a set of data is worth
    producing is the second figure, so that is the one recorded.
    """
    box = survey.get("boundingBox")
    if box:
        survey["areaSquareKilometres"] = round(
            area_square_kilometres(widen(box, DEFAULT_MARGIN_METRES))
        )


def verdict_of(survey: dict) -> str:
    """Decide whether a surveyed network is one this application can serve.

    The order of the checks is the order in which they explain a rejection:
    the first thing wrong is the thing worth telling.
    """
    if not survey.get("stationCount"):
        return "no-positioned-station"

    forms = set(survey.get("formFactors") or [])
    if forms and not forms & BICYCLE_FORM_FACTORS:
        return "no-bicycle"
    if forms & DISQUALIFYING_FORM_FACTORS:
        # Cars and mopeds share the docks with the bikes here: the availability
        # figure shown on a marker would count vehicles nobody can pedal.
        return "mixed-with-motor-vehicles"

    # Free-floating operators publish their parking areas as stations. Two
    # signs give them away, and both must be absent: a fleet declaring no
    # capacity at all, and one whose live feed never mentions a free dock.
    if survey.get("capacityTotal", 0) <= 0:
        return "no-capacity"
    if survey.get("reportsDocks") is False:
        return "no-docks"

    if survey["stationCount"] < MINIMUM_STATIONS:
        return "too-few-stations"

    if survey.get("areaSquareKilometres", 0) > MAXIMUM_AREA_SQUARE_KILOMETRES:
        return "not-a-conurbation"

    return "eligible"


def read_mobilitydata(source: str) -> list[dict]:
    """Read the French entries of MobilityData's `systems.csv`."""
    rows = csv.DictReader(io.StringIO(source))
    return [
        {
            "source": "MobilityData",
            "systemId": row["System ID"].strip(),
            "catalogueName": row["Name"].strip(),
            "location": row["Location"].strip(),
            "declaredVersions": row["Supported Versions"].strip(),
            "authenticationType": row["Authentication Type"].strip(),
            "discoveryUrls": [row["Auto-Discovery URL"].strip()],
        }
        for row in rows
        if row["Country Code"].strip() == "FR" and row["Auto-Discovery URL"].strip()
    ]


def read_transport_data_gouv(datasets: list) -> list[dict]:
    """Read the station-based bike datasets of the national access point.

    The access point classifies its shared-vehicle datasets itself: `bicycle`
    says what the fleet is, `freefloating` says whether it has docks. Trusting
    that classification here only shortens the list to probe — every entry is
    still called and judged on what its feed actually returns.
    """
    candidates = []
    for dataset in datasets:
        if dataset.get("type") != "vehicles-sharing":
            continue
        subtypes = dataset.get("sub_types") or []
        if "bicycle" not in subtypes or "freefloating" in subtypes:
            continue
        urls = [
            resource["original_url"]
            for resource in dataset.get("resources", [])
            if resource.get("format") == "gbfs" and resource.get("original_url")
        ]
        # An auto-discovery file is usually named, but not always: some
        # producers publish a directory. Where nothing looks like one, every
        # address is tried rather than the dataset dropped — calling them is
        # what settles it.
        named = [url for url in urls if url.rstrip("/").endswith(("gbfs.json", "gbfs"))]
        discovery = named or urls
        if not discovery:
            continue
        areas = dataset.get("covered_area") or []
        candidates.append({
            "source": "transport.data.gouv.fr",
            "systemId": dataset.get("slug", ""),
            "catalogueName": dataset.get("title", ""),
            "catalogueTitle": dataset.get("title", ""),
            "location": areas[0].get("nom", "") if areas else "",
            "coveredAreaType": areas[0].get("type") if areas else None,
            "coveredAreaCode": areas[0].get("insee") if areas else None,
            "coveredAreaName": areas[0].get("nom", "") if areas else "",
            "publisher": (dataset.get("publisher") or {}).get("name", ""),
            "licence": dataset.get("licence"),
            "datasetPageUrl": dataset.get("page_url"),
            "declaredVersions": "",
            "authenticationType": "",
            "discoveryUrls": discovery,
        })
    return candidates


def merge(surveys: list[dict]) -> list[dict]:
    """Fuse the two catalogues' views of the same network into one entry.

    The same network is published under different addresses on either side —
    `api.gbfs.ecovelo.mobi` here, `api.gbfs.v3.0.ecovelo.mobi` there — so the
    join cannot be on the URL. It is on the stations themselves: two feeds
    serving the same rectangle with the same number of stations are the same
    network, and no French conurbation is ambiguous at that resolution.
    """
    merged: list[dict] = []
    for survey in surveys:
        twin = next((other for other in merged if same_network(other, survey)), None)
        if twin is None:
            merged.append(dict(survey))
            continue
        # The national access point knows the authority and the licence; the
        # MobilityData catalogue knows the declared versions. Keep both, and
        # keep the address that answered with the most recent GBFS version.
        for key, value in survey.items():
            if value in (None, "", [], {}):
                continue
            if key not in twin or twin[key] in (None, "", [], {}):
                twin[key] = value
        twin.setdefault("alternateUrls", []).extend(survey.get("discoveryUrls", []))
        if newer_version(survey.get("gbfsVersion"), twin.get("gbfsVersion")):
            twin["discoveryUrl"] = survey.get("discoveryUrl", twin["discoveryUrl"])
            twin["gbfsVersion"] = survey["gbfsVersion"]
    return merged


def same_network(left: dict, right: dict) -> bool:
    """True if two surveys describe the same network.

    The join cannot be on the address: the two catalogues publish the same
    network under different hosts, and a conurbation that has changed operator
    keeps both its feeds listed. It is on the stations — two feeds covering the
    same rectangle with a comparable number of stations describe the same
    network, and no two French conurbations overlap at that resolution.
    """
    if set(left.get("discoveryUrls", [])) & set(right.get("discoveryUrls", [])):
        return True
    first, second = left.get("boundingBox"), right.get("boundingBox")
    if not first or not second:
        return False
    counts = (left.get("stationCount", 0), right.get("stationCount", 0))
    # A tenth of the fleet apart: enough for a feed lagging behind an extension
    # of the network, too little to conflate two networks of the same city.
    if abs(counts[0] - counts[1]) > max(3, 0.1 * max(counts)):
        return False
    return all(
        abs(first[corner] - second[corner]) < 0.01
        for corner in ("south", "west", "north", "east")
    )


def attach_unreachable_metadata(surveys: list[dict]) -> None:
    """Lend an unreachable dataset's description to the network it describes.

    The national access point often publishes an address that no longer
    answers beside one that does — the producer moved its feed and only one of
    the two catalogues followed. The geometric join cannot see it, since a
    feed that did not answer has no stations to compare.

    Its description is still worth having: it is where the authority, the
    licence and the dataset page come from, and those go into the attribution
    the "about" screen must show (§4.5). The join is then on the territory,
    which is the only thing both sides state.
    """
    reachable = [survey for survey in surveys if survey["verdict"] == "eligible"]

    def place_words(text: str) -> frozenset[str]:
        """The words of a place name that actually name a place."""
        return frozenset(
            word for word in normalised(text).split()
            if word not in ADMINISTRATIVE_WORDS and len(word) > 2
        )

    for survey in surveys:
        if survey["verdict"] != "unreachable" or survey["source"] != "transport.data.gouv.fr":
            continue
        area = place_words(
            f"{survey.get('coveredAreaName') or ''} {survey.get('location') or ''}"
            f" {survey.get('catalogueTitle') or ''}"
        )
        matches = [
            twin for twin in reachable
            if place_words(twin.get("mainCity") or "") & area
        ]
        # Only an unambiguous match: two networks in the same conurbation would
        # each be as plausible, and a wrong attribution is worse than none.
        if len(matches) != 1:
            continue
        twin = matches[0]
        for key in ("catalogueTitle", "publisher", "licence", "datasetPageUrl",
                    "coveredAreaName", "coveredAreaType", "coveredAreaCode"):
            if survey.get(key) and not twin.get(key):
                twin[key] = survey[key]
        survey["verdict"] = "alternate-address"
        survey["describes"] = twin.get("systemId")


def overlapping(left: dict, right: dict) -> bool:
    """True if two networks cover the same ground."""
    first, second = left.get("boundingBox"), right.get("boundingBox")
    if not first or not second:
        return False
    return not (
        first["north"] < second["south"] or first["south"] > second["north"]
        or first["east"] < second["west"] or first["west"] > second["east"]
    )


def drop_duplicate_feeds(surveys: list[dict]) -> None:
    """Keep one feed per network when a conurbation publishes two.

    A city that changes operator keeps both feeds alive for a while, under the
    same brand — Cergy-Pontoise's VélO2 is published by its former operator and
    by its new one at once. The user chooses a city, not a feed: showing the
    same name twice would read as a defect. The feed speaking the more recent
    GBFS version wins, then the one holding more stations.

    Two networks may legitimately share a brand across the country — "Vélo
    Modalis" runs in Angoulême, Royan and Saintes — so the name is not enough:
    the boxes must overlap too.
    """
    by_name: dict[str, list[dict]] = {}
    for survey in surveys:
        if survey["verdict"] != "eligible":
            continue
        by_name.setdefault(normalised(survey.get("displayName", "")), []).append(survey)

    groups: list[list[dict]] = []
    for namesakes in by_name.values():
        for survey in namesakes:
            twins = next(
                (group for group in groups
                 if group and group[0] in namesakes and overlapping(group[0], survey)),
                None,
            )
            if twins is None:
                groups.append([survey])
            else:
                twins.append(survey)

    for twins in groups:
        if len(twins) < 2:
            continue
        twins.sort(
            key=lambda survey: (
                tuple(int(piece) for piece in survey.get("gbfsVersion", "0").split(".")),
                survey.get("stationCount", 0),
            ),
            reverse=True,
        )
        for loser in twins[1:]:
            loser["verdict"] = "duplicate-feed"
            loser["describes"] = twins[0].get("systemId")


def newer_version(candidate: str | None, current: str | None) -> bool:
    """Compare two GBFS version strings, tolerating anything unparsable."""
    def parts(version: str | None) -> tuple:
        try:
            return tuple(int(piece) for piece in (version or "0").split("."))
        except ValueError:
            return (0,)
    return parts(candidate) > parts(current)


def locate(survey: dict) -> None:
    """Fill in the main municipality and the departments a network spans.

    Both come from the state's own geographic API rather than from the feed: a
    network names itself after its brand, not after the municipalities it
    covers, and the generation scripts need the departments to know which Base
    Adresse Nationale extracts to download.

    The stations are what is looked up, not the intercommunality's boundary.
    SPEC.md §4 derives the reference box from the stations, and that box
    routinely crosses into a neighbouring department the intercommunality does
    not include — Lyon's reaches into the Ain, Avignon's into the Gard.
    """
    stations = survey.get("stationSamples") or []
    box = survey.get("boundingBox")
    grid: list[list[float]] = []
    if box:
        # The reference box, margin included: it is what the three datasets are
        # cut to, so it is what decides which extracts to download.
        widened = widen(box, DEFAULT_MARGIN_METRES)
        for row in range(BOX_GRID_SIDE):
            for column in range(BOX_GRID_SIDE):
                grid.append([
                    widened["south"] + (widened["north"] - widened["south"])
                    * row / (BOX_GRID_SIDE - 1),
                    widened["west"] + (widened["east"] - widened["west"])
                    * column / (BOX_GRID_SIDE - 1),
                ])
    if not stations and not grid:
        return

    departments: set[str] = set()
    municipalities: dict[str, dict] = {}
    for index, (latitude, longitude) in enumerate(stations + grid):
        try:
            found = fetch_json(
                f"{GEO_API_COMMUNES}?lat={latitude}&lon={longitude}"
                "&fields=nom,code,codeDepartement,population"
            )
        except Exception:  # noqa: BLE001 — a point at sea returns nothing
            continue
        for municipality in found:
            departments.add(municipality["codeDepartement"])
            # Only the municipalities actually holding stations may name the
            # conurbation: the grid reaches into the countryside around it.
            if index < len(stations):
                municipalities[municipality["code"]] = municipality

    if municipalities:
        # The most populous municipality holding stations names the
        # conurbation: it is what the interface shows beside the brand, and
        # "Vélo'v — Lyon" is the only form that locates a network for someone
        # who has never been there. Reading it from the stations rather than
        # from the intercommunality keeps it true of where the bikes are.
        largest = max(municipalities.values(), key=lambda item: item.get("population") or 0)
        survey["mainCity"] = largest["nom"]
    if departments:
        survey["departments"] = sorted(departments)
        regions = sorted({
            REGION_OF_DEPARTMENT[department]
            for department in departments
            if department in REGION_OF_DEPARTMENT
        })
        survey["osmRegions"] = [f"europe/france/{region}" for region in regions]


def normalised(text: str) -> str:
    """Fold a place name down to a form two spellings of it can be compared in."""
    decomposed = unicodedata.normalize("NFD", text.lower())
    letters = "".join(
        character if character.isalnum() else " "
        for character in decomposed
        if unicodedata.category(character) != "Mn"
    )
    return " ".join(letters.split())


# Words that never distinguish one network from another: the legal form of an
# intercommunality, and the vocabulary its name is built from. Stripped from
# the tail of a title along with the territory's own words.
# "vélo" is deliberately absent: half the brands are built on it, and popping
# it would turn "Ti Vélo" into "Ti".
ADMINISTRATIVE_WORDS = frozenset("""
    ca cc cu ce communaute communautes commune communes limitrophe limitrophes
    agglomeration agglo metropole eurometropole syndicat mixte territoire pays
    grand grande grands grandes petit petite region departement ville villes
    de du des d la le les l en et au aux sur sous a
""".split())


def territory_words(survey: dict) -> frozenset[str]:
    """The words a network's title may trail without saying anything new."""
    words = set(ADMINISTRATIVE_WORDS)
    for source in ("coveredAreaName", "mainCity", "location"):
        words.update(normalised(survey.get(source) or "").split())
    # The region as well: an overseas network trails it — "Altervélo
    # Saint-Pierre La Réunion" — where a mainland one never does.
    for region in survey.get("osmRegions") or []:
        words.update(region.rsplit("/", 1)[-1].split("-"))
    return frozenset(words)


def strip_territory(name: str, forgettable: frozenset[str]) -> str:
    """Remove the territory a network's published title trails behind it.

    The national access point titles its datasets "VLS *brand* *territory*" —
    "VLS Naolib Nantes Métropole", "VLS AuxR_M le vélo Communauté
    d'agglomération de l'Auxerrois". The territory belongs in the
    configuration's own fields, not in the network's name: the interface
    already shows the two side by side, and "Naolib — Nantes" must not read
    "Naolib Nantes Métropole — Nantes".

    Only a **trailing** territory goes: a brand may legitimately carry a place
    name at its head, as "Vélib' Métropole" does.
    """
    words = name.split()
    while len(words) > 1:
        pieces = normalised(words[-1]).split()
        if not pieces or not all(piece in forgettable for piece in pieces):
            break
        words.pop()
    return " ".join(words)


def display_name_of(survey: dict) -> str:
    """The name to show for a network, as short as it can be while still true.

    Three spellings reach us and none is reliably the best: the title of the
    national access point's dataset, the `name` the feed publishes for itself,
    and the label of the MobilityData catalogue. They are tried in that order,
    each stripped of the boilerplate its producer adds — "VLS " in front, the
    territory behind, the city in brackets.

    A candidate that says nothing but where it runs — a feed calling itself
    "Valenciennes", a title reading "Tarbes Lourdes Pyrénées" — is refused and
    the next source tried: the interface already shows the conurbation, and a
    network named after it twice tells the user nothing.
    """
    forgettable = territory_words(survey)
    for key in ("catalogueTitle", "systemName", "catalogueName"):
        raw = (survey.get(key) or "").strip()
        if not raw:
            continue
        # "VLS Vélomagg Montpellier…", "GraouLib' (Metz)", "Vélhop - Strasbourg"
        candidate = re.sub(r"^VLS\s+", "", raw)
        candidate = re.sub(r"\s*[\(\[][^\)\]]*[\)\]]\s*$", "", candidate)
        candidate = re.sub(r"\s+[-–—]\s+.*$", "", candidate)
        candidate = strip_territory(candidate.strip(), forgettable).strip(" -–—,")
        if len(candidate) < 3:
            continue
        if all(word in forgettable for word in normalised(candidate).split()):
            continue
        # A sentence, not a name: "Vélos et trottinettes Naolib micromob Nantes
        # (avec stations)" describes an offer. The next source states the brand.
        if len(candidate.split()) > MAXIMUM_NAME_WORDS:
            continue
        return candidate
    return survey.get("catalogueName", "").strip()


def attribution_of(survey: dict) -> None:
    """Compose what the "about" screen must credit for this feed (§4.5).

    Two names matter and they are rarely the same: the authority that opened
    the data — the metropolis, the transport authority — and the company that
    runs the service. The catalogues hold one each, so both are used.
    """
    contractor = (survey.get("systemOperator") or "").split(",")[0].strip()
    authority = (survey.get("publisher") or "").strip()
    if contractor and authority and normalised(contractor) != normalised(authority):
        operator = f"{contractor} / {authority}"
    else:
        operator = contractor or authority
    survey["operator"] = operator

    licence = LICENCE_NAMES.get(
        survey.get("licence") or "",
        LICENCE_NAMES.get(survey.get("licenceId") or "", survey.get("licence") or ""),
    )
    survey["licenceName"] = licence

    credited = operator or survey.get("displayName", "")
    attribution = f"Données {survey.get('displayName', '')} — {credited}"
    if licence:
        # "licence Licence Ouverte 2.0" reads as a stammer; some licence names
        # already carry the word.
        article = "" if normalised(licence).startswith("licence") else "licence "
        attribution += f", {article}{licence}"
    survey["attribution"] = attribution
    survey["attributionUrl"] = (
        survey.get("datasetPageUrl")
        or survey.get("licenceUrl")
        or "https://transport.data.gouv.fr/datasets"
    )


VERDICT_EXPLANATIONS = {
    "duplicate-feed": (
        "A second feed for a network already listed",
        "A conurbation that changed operator keeps both feeds alive under the "
        "same brand. The one speaking the more recent GBFS version is the one "
        "served.",
    ),
    "alternate-address": (
        "Another address for a network already listed",
        "Published by one catalogue and no longer answering, while the other "
        "catalogue's address for the same network does. Its description of the "
        "authority and the licence has been kept.",
    ),
    "not-a-conurbation": (
        "One feed for a whole region",
        "The stations are scattered over more than "
        f"{MAXIMUM_AREA_SQUARE_KILOMETRES:,} km²: their enclosing rectangle is "
        "not a conurbation, and §4's three datasets are cut to a conurbation.",
    ),
    "free-floating": (
        "Free-floating fleet",
        "No `station_information` feed: the vehicles are left anywhere. "
        "This application takes a bike at a dock and returns it at another (§6).",
    ),
    "no-docks": (
        "Parking areas, not docks",
        "The stations are drop zones: the live feed never reports a free dock, "
        "so §6 cannot guarantee the arrival station can take the bike.",
    ),
    "no-capacity": (
        "Stations without capacity",
        "Every station declares a capacity of zero — the mark of a free-floating "
        "operator publishing its parking areas as stations.",
    ),
    "no-bicycle": (
        "No bicycle in the fleet",
        "The declared vehicle types hold no bicycle.",
    ),
    "mixed-with-motor-vehicles": (
        "Cars sharing the same stations",
        "The fleet mixes bikes with cars or mopeds at the same stations: the "
        "availability figure shown on a marker would count vehicles nobody pedals.",
    ),
    "too-few-stations": (
        f"Fewer than {MINIMUM_STATIONS} stations",
        "§6 picks the best pair among five candidate departure stations and five "
        "arrival ones; below ten stations there is nothing to optimise.",
    ),
    "no-positioned-station": (
        "Stations without coordinates",
        "The feed answers, but no station carries a position.",
    ),
    "authentication-required": (
        "Feed behind a key",
        "Reading it would mean hard-coding a secret, which §2 rules out.",
    ),
    "unreachable": (
        "Feed unreachable",
        "The address published by the catalogue did not answer when the survey ran.",
    ),
}


def describe(surveys: list[dict]) -> None:
    """Name and credit every eligible network, then set duplicate feeds aside.

    Runs after the calls, and again whenever the report is rebuilt from a
    stored survey: naming needs the conurbation, so as to strip it back off the
    title the producer trails behind its brand.
    """
    for survey in surveys:
        if survey["verdict"] in ("eligible", "duplicate-feed"):
            survey["verdict"] = "eligible"
            record_reference_area(survey)
            survey["displayName"] = display_name_of(survey)
            attribution_of(survey)
    drop_duplicate_feeds(surveys)


def write_survey(generated_at: str, surveys: list[dict], path: Path) -> None:
    """Write the machine-readable survey `tools/add_city.py` reads."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as stream:
        json.dump(
            {"generatedAt": generated_at, "networks": surveys},
            stream, ensure_ascii=False, indent=1,
        )
        stream.write("\n")


def write_report(surveys: list[dict], path: Path, generated_at: str) -> None:
    """Write the readable list of French networks."""
    eligible = sorted(
        (survey for survey in surveys if survey["verdict"] == "eligible"),
        key=lambda survey: -survey.get("stationCount", 0),
    )
    lines = [
        "# Bike-share networks in France that publish their stations",
        "",
        "> Generated by `tools/discover_networks.py` on "
        f"{generated_at}. Do not edit by hand: regenerate it.",
        "",
        "`SPEC.md` §4.1 forbids guessing a `gbfs.json` address. Every one below was",
        "read from [MobilityData's `systems.csv`](https://github.com/MobilityData/gbfs)",
        "or from the [national access point](https://transport.data.gouv.fr), then",
        "**called for real** — the station counts, the GBFS versions and the",
        "rejections all come from what the feeds answered, not from what their",
        "catalogues claim.",
        "",
        "## What counts as a network this application can serve",
        "",
        "1. it publishes `station_information` with positioned stations;",
        "2. its fleet holds bicycles, and no car or moped shares those stations;",
        "3. its stations are real docks — a declared capacity, and a live count of",
        "   free docks, both of which §6 needs to promise the bike can be returned;",
        f"4. it has at least {MINIMUM_STATIONS} stations;",
        "5. its feed needs no key, since the application hard-codes no secret.",
        "",
        "Most of what publishes GBFS in France fails the second or the third rule:",
        "scooter fleets outnumber bike-share networks, and they publish their",
        "parking areas as stations.",
        "",
        "A conurbation absent from this page altogether — neither served nor set",
        "aside — publishes no station feed in either catalogue. Several sizeable",
        "French networks are in that case: their availability lives behind a",
        "proprietary interface, and `SPEC.md` §4.1 rules those out. Re-running the",
        "script is what settles whether that is still true.",
        "",
        f"## Served — {len(eligible)} networks",
        "",
        "The bounding box of each is derived from its **own stations** widened by",
        "3 km (§4), which is why it covers the conurbation rather than the",
        "municipality the network is named after.",
        "",
        "| Network | Conurbation | Stations | Docks | GBFS | Box | "
        "OSM extract | BAN |",
        "|---|---|---:|---:|:--:|---:|---|---|",
    ]
    for survey in eligible:
        regions = ", ".join(
            region.rsplit("/", 1)[-1] for region in survey.get("osmRegions", [])
        )
        lines.append(
            f"| {survey.get('displayName') or survey['catalogueName']} "
            f"| {survey.get('mainCity') or survey.get('location') or '—'} "
            f"| {survey.get('stationCount', 0)} "
            f"| {survey.get('capacityTotal', 0)} "
            f"| {survey.get('gbfsVersion', '?')} "
            f"| {survey.get('areaSquareKilometres', 0)} km² "
            f"| {regions or '—'} "
            f"| {', '.join(survey.get('departments', [])) or '—'} |"
        )

    lines += [
        "",
        "\"Box\" is the reference box, margin included: the area the base map,",
        "the routing graph and the address index are all cut to. A small network",
        "is mostly margin — Auch's ten stations enclose 4 km², the box around",
        "them 65.",
        "",
        "## Set aside, and why",
        "",
    ]
    for verdict, (title, why) in VERDICT_EXPLANATIONS.items():
        rejected = sorted(
            (survey for survey in surveys if survey["verdict"] == verdict),
            key=lambda survey: (survey.get("catalogueName") or "").lower(),
        )
        if not rejected:
            continue
        lines += [f"### {title} — {len(rejected)}", "", why, ""]
        names = ", ".join(sorted({
            f"{survey.get('systemName') or survey['catalogueName']}"
            + (f" ({survey['location']})" if survey.get("location") else "")
            for survey in rejected
        }, key=str.lower))
        lines += [names, ""]

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--survey", type=Path, default=DEFAULT_SURVEY)
    parser.add_argument(
        "--offline",
        action="store_true",
        help="re-render the report from the last survey without calling anything",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    generated_at = time.strftime("%Y-%m-%d", time.gmtime())

    if arguments.offline:
        stored = json.loads(arguments.survey.read_text(encoding="utf-8"))
        surveys = stored["networks"]
        describe(surveys)
        write_survey(stored.get("generatedAt", generated_at), surveys, arguments.survey)
        write_report(surveys, arguments.report, generated_at)
        print(f"Rewritten from {arguments.survey}: {arguments.report}")
        return 0

    print(f"Reading {MOBILITYDATA_SYSTEMS_CSV}")
    candidates = read_mobilitydata(fetch_text(MOBILITYDATA_SYSTEMS_CSV))
    print(f"  {len(candidates)} French systems")

    print(f"Reading {TRANSPORT_DATA_GOUV_DATASETS}")
    from_state = read_transport_data_gouv(fetch_json(TRANSPORT_DATA_GOUV_DATASETS))
    print(f"  {len(from_state)} station-based bike datasets")
    candidates += from_state

    print(f"Calling {len(candidates)} feeds…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        surveys = list(pool.map(probe, candidates))

    surveys = merge(surveys)
    eligible = [survey for survey in surveys if survey["verdict"] == "eligible"]
    print(f"  {len(eligible)} eligible networks out of {len(surveys)} distinct systems")

    print("Resolving municipalities and departments…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        list(pool.map(locate, eligible))

    attach_unreachable_metadata(surveys)
    describe(surveys)
    eligible = [survey for survey in surveys if survey["verdict"] == "eligible"]
    print(f"  {len(eligible)} after setting duplicate feeds aside")

    write_survey(generated_at, surveys, arguments.survey)
    write_report(surveys, arguments.report, generated_at)

    print(f"\nSurvey : {arguments.survey}")
    print(f"Report : {arguments.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
