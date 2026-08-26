#!/usr/bin/env python3
"""Survey the bike-share networks of the world that publish a GBFS feed (SPEC.md §4.1).

`SPEC.md` forbids guessing a `gbfs.json` URL: it must come from a public
catalogue — MobilityData's `systems.csv`, the registry the GBFS standard keeps
of itself, or a country's own national access point — and be verified by a real
request before being written into a configuration. This script does exactly
that, for every system of every country at once, and keeps the evidence: it
reads the catalogues, calls every feed it finds, and records what came back.

It then applies the eligibility rules below. They matter more than the survey
itself, because most of what publishes GBFS is **not** what this application
serves: free-floating scooters, car-sharing, and fleets whose "stations" are
painted parking areas with no docks at all.

Three public datasets place what is found, none of them tied to one country:

* MobilityData's registry says which country a system runs in;
* Geofabrik's extract index says which OpenStreetMap extract covers its box —
  the box is tested against the extracts' own geometry, so the answer holds as
  well for Auckland as for Amiens;
* the GeoNames gazetteer names the municipalities the stations stand in. A
  network is named after its brand, or after the largest town it serves, and
  neither says what a *conurbation* is: the list of municipalities does, and it
  is what says out loud that Lille's network is also Roubaix's.

France keeps one enrichment of its own, because its address base is
departmental: the state's geographic API says which Base Adresse Nationale
extracts a box reaches (§4.3). Every other country's address index comes from
OpenStreetMap, as §15 foresaw.

Output:

* ``docs/networks.md`` — the readable list, country by country, eligible
  networks first, then the rejected ones grouped by reason. Regenerating it is
  how the list is kept honest;
* ``data/networks.json`` — the same survey, machine-readable, consumed by
  ``tools/add_city.py`` to write the city configurations.

Usage:
    python3 tools/discover_networks.py [--report PATH] [--survey PATH]
                                       [--country FR] [--offline]
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
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# Shared with the script that writes the box into a configuration: the survey
# and the generation must agree on which stations make the box, or a network
# would be judged on one rectangle and served on another.
from compute_bbox import outlying_positions

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_REPORT = REPO_ROOT / "docs" / "networks.md"
DEFAULT_SURVEY = REPO_ROOT / "data" / "networks.json"
DEFAULT_EXTRA_FEEDS = REPO_ROOT / "config" / "extra-feeds.json"

# Downloads worth keeping between runs: the gazetteer and the extract index
# weigh tens of megabytes and change by the month, not by the hour. Under
# data/, which the repository ignores — every one of them is reproducible.
CACHE_DIR = REPO_ROOT / "data" / "cache"

# The registry the GBFS standard keeps of itself: the one catalogue that lists
# every country. Neither it nor any other is trusted on its own — each is only
# a list of addresses to go and check.
MOBILITYDATA_SYSTEMS_CSV = (
    "https://raw.githubusercontent.com/MobilityData/gbfs/master/systems.csv"
)
MOBILITYDATA_SYSTEMS_PAGE = "https://github.com/MobilityData/gbfs/blob/master/systems.csv"

# France's national access point. Kept beside the registry, and no other
# country's equivalent is: it is the only one that publishes, in one call and
# without a key, both the address of a feed and the authority behind it — which
# is what the "about" screen has to credit (§4.5).
TRANSPORT_DATA_GOUV_DATASETS = "https://transport.data.gouv.fr/api/datasets"

# Where Geofabrik describes every extract it publishes, geometry included. Used
# to answer "which extract covers this box" by testing the box against the
# extracts themselves, rather than by keeping a table of regions that would be
# wrong for the next country added.
GEOFABRIK_INDEX = "https://download.geofabrik.de/index-v1.json"
GEOFABRIK_DOWNLOAD_PREFIX = "https://download.geofabrik.de/"

# The GeoNames gazetteer, populated places of 500 inhabitants and over, in the
# public form its authors publish (CC BY 4.0). It is what names the
# municipalities a network covers, in any country.
GEONAMES_CITIES_ZIP = "https://download.geonames.org/export/dump/cities500.zip"
GEONAMES_CITIES_FILE = "cities500.txt"

# Identifies the tooling without carrying anything specific to a user (§4.4).
USER_AGENT = "RoueLibre-tools/1.0 (+https://github.com/mgdx/RoueLibre)"
NETWORK_TIMEOUT_SECONDS = 20
CONCURRENT_REQUESTS = 24

# The margin §4 puts around the stations to make the reference box. Repeated
# from tools/add_city.py, which writes it into each configuration: here it only
# decides which extracts a box of that size reaches.
DEFAULT_MARGIN_METRES = 3000.0

# Resolves a station's position to its municipality and department, which is
# what tells the generation scripts which Base Adresse Nationale extracts to
# download. Public, keyless, and run by the same administration as the BAN.
# France only: it is the French address base that is departmental.
GEO_API_COMMUNES = "https://geo.api.gouv.fr/communes"

# Below this, the journey algorithm has nothing to optimise: §6 picks the best
# pair among five candidate departure stations and five arrival ones, and a
# network of three stations offers a single itinerary the user already knows.
# Such systems are listed as rejected, never hidden — the reason is printed.
MINIMUM_STATIONS = 10

# There is NO ceiling on the area a network covers, and that is a decision
# rather than an oversight. One was tried — 2,500 km², then 2,700 — and it
# refused Kiel by seven per cent while letting a network of the same shape
# through at 2,499: a line drawn there says more about the line than about the
# network. What the datasets cost is governed where it belongs, by the tile
# ceiling of §4.2, which is measured on the files produced rather than guessed
# from a rectangle. The area is still recorded and printed for every network,
# so whoever generates one knows what they are cutting.

# One line is drawn all the same, and it is not the one above: it separates a
# conurbation, however wide, from a country. Docomo publishes one feed for the
# whole of Japan — 5,758 stations, a box of 1,600 km by 2,100 that takes in the
# two Koreas — and §4 cuts every dataset to a single rectangle: its base map
# alone would run to tens of gigabytes, downloaded whole by someone who rides
# in one city of it. The widest network actually served covers 160,000 km²
# (Careem, along the Gulf), so this line sits three times above what it must
# let through and forty below what it must stop. A feed caught here is not too
# big to serve: it is waiting to be split into the conurbations it covers, each
# of which is an ordinary city of this catalogue.
COUNTRY_WIDE_AREA = 500_000

# Every attempt is retried once: a name resolution that fails for a second is
# not a network that does not exist, and a single miss would drop a real
# conurbation from the list.
ATTEMPTS_PER_URL = 2

# Station positions kept in the survey, spread through the list. Enough to name
# every municipality a network reaches without storing the whole feed.
STATION_SAMPLES = 25

# The reference box is sampled on a grid as well, to find the extracts it
# reaches. The stations alone would not do: the box carries a 3 km margin
# around them, and what is downloaded is cut to the box, not to the stations.
BOX_GRID_SIDE = 5

# Past this, a title has stopped being a name and started describing an offer.
MAXIMUM_NAME_WORDS = 4

# Municipalities named in the report beside the main one. A network covering
# ninety of them is described by its largest, not by a list nobody reads.
MUNICIPALITIES_LISTED = 6

# What the licence codes of the catalogues mean, spelled out for the
# attribution the "about" screen shows (§4.5). A code absent from this table is
# reproduced as it stands rather than guessed at.
LICENCE_NAMES = {
    "lov2": "Licence Ouverte 2.0",
    "fr-lo": "Licence Ouverte",
    "odc-odbl": "ODbL",
    "ODbL-1.0": "ODbL",
    "odbl": "ODbL",
    "CC0-1.0": "CC0 1.0",
    "CC-BY-4.0": "CC BY 4.0",
    "CC-BY-SA-4.0": "CC BY-SA 4.0",
    "cc-by": "CC BY",
    "notspecified": "",
    "other-open": "",
}

# Licences named by their address alone. GBFS lets a feed publish a
# "license_url" and no "license_id", and a third of the networks that name a
# licence at all do it that way: the address is then the only statement there
# is, and dropping it left a hundred configurations crediting an operator for
# data under no licence anybody could read (§4.5).
#
# Each entry below was read off the document the address serves, not deduced
# from the address itself — cdla.dev/permissive-2-0 is titled "Community Data
# License Agreement - Permissive, Version 2.0", and JCDecaux's PDF is Etalab's
# "LICENCE OUVERTE / OPEN LICENCE", whose version it does not print. A licence
# guessed wrong is worse than one left unnamed, so an address absent from this
# table names nothing and the attribution says as much.
LICENCE_URL_NAMES = (
    ("cdla.dev/permissive-2-0", "CDLA-Permissive-2.0"),
    ("developer.jcdecaux.com/files/open-licence", "Licence Ouverte"),
    ("etalab.gouv.fr/licence-ouverte", "Licence Ouverte"),
    ("creativecommons.org/licenses/by-sa/4.0", "CC BY-SA 4.0"),
    ("creativecommons.org/licenses/by/4.0", "CC BY 4.0"),
    ("creativecommons.org/publicdomain/zero/1.0", "CC0 1.0"),
    ("opendatacommons.org/licenses/odbl", "ODbL"),
)

# What the attribution says when nothing names a licence — neither the feed nor
# the catalogue. The clause is written rather than left out: an attribution
# stopping after the operator reads as an unfinished sentence, and says nothing
# about whether anybody looked. This one says somebody did (§4.5).
UNSTATED_LICENCE_FR = "licence non précisée par l'opérateur"
UNSTATED_LICENCE = "licence not stated by the operator"

# Vehicle forms this application is about. A network whose fleet is cars is not
# a bike-share network, whatever its stations look like; a network mixing bikes
# with standing scooters at the same docks is one, and is kept.
BICYCLE_FORM_FACTORS = frozenset({"bicycle", "cargo_bicycle"})
DISQUALIFYING_FORM_FACTORS = frozenset({"car", "moped", "other"})

# GBFS propulsion values that mean a motor helps the rider. A bicycle declaring
# one of them is a pedal-assist bike, which the interface draws with a bolt
# (§7): the answer is read here, from the feed itself, and never guessed.
ELECTRIC_PROPULSIONS = frozenset({"electric_assist", "electric"})

# The languages a country's STREETS are named in, the majority one first
# (§15.1). This is what a city configuration announces, and what decides which
# street-name normalisation rules its address index is built with — not the
# language of the interface, which follows the device (§9).
#
# A country with several is listed with all of them, because the choice is then
# the producer's to state: Barcelona's feed says Catalan and Bilbao's Basque,
# and their streets are indeed carrers and kaleak. What is NOT taken at its
# word is a feed declaring English in a country that has no English street: a
# GBFS producer writes "en" when it has not thought about the question, and
# twenty-five French networks do exactly that.
#
# Kosovo is listed as Albanian alone, though Serbian is co-official there: its
# address base is written in Albanian, and one network's feed says otherwise
# only because its operator's software defaults to it.
OFFICIAL_LANGUAGES = {
    "AE": ("ar",), "AR": ("es",), "AT": ("de",), "AU": ("en",),
    "BA": ("bs", "hr", "sr"), "BE": ("nl", "fr", "de"), "BG": ("bg",),
    "BR": ("pt",), "CA": ("en", "fr"), "CH": ("de", "fr", "it"),
    "CL": ("es",), "CO": ("es",), "CR": ("es",), "CY": ("el", "tr"),
    "CZ": ("cs",), "DE": ("de",), "DK": ("da",), "EE": ("et",),
    "EG": ("ar",), "ES": ("es", "ca", "eu", "gl"), "FI": ("fi", "sv"),
    "FR": ("fr",), "GB": ("en",), "GR": ("el",), "HR": ("hr",),
    "HU": ("hu",), "IE": ("en",), "IL": ("he", "ar"), "IS": ("is",),
    "IT": ("it", "de"), "JP": ("ja",), "KR": ("ko",), "LI": ("de",),
    "LT": ("lt",), "LU": ("fr", "de"), "LV": ("lv",), "MA": ("ar", "fr"),
    "MC": ("fr",), "MT": ("en",), "MX": ("es",), "MY": ("ms",),
    "NL": ("nl",), "NO": ("nb",), "NZ": ("en",), "PA": ("es",),
    "PE": ("es",), "PL": ("pl",), "PT": ("pt",), "QA": ("ar",),
    "RO": ("ro",), "RS": ("sr",), "SA": ("ar",), "SE": ("sv",),
    "SG": ("en", "ms"), "SI": ("sl",), "SK": ("sk",), "TR": ("tr",),
    "TW": ("zh",), "UA": ("uk",), "US": ("en",), "UY": ("es",),
    "XK": ("sq",),
}

# Country names, for a report that reads as prose rather than as a list of
# codes. The catalogue itself carries the code alone (§15.1).
COUNTRY_NAMES = {
    "AE": "United Arab Emirates", "AR": "Argentina", "AT": "Austria",
    "AU": "Australia", "BA": "Bosnia and Herzegovina", "BE": "Belgium",
    "BG": "Bulgaria", "BR": "Brazil", "CA": "Canada", "CH": "Switzerland",
    "CL": "Chile", "CO": "Colombia", "CR": "Costa Rica", "CY": "Cyprus",
    "CZ": "Czechia", "DE": "Germany", "DK": "Denmark", "EE": "Estonia",
    "EG": "Egypt", "ES": "Spain", "FI": "Finland", "FR": "France",
    "GB": "United Kingdom", "GR": "Greece", "HR": "Croatia", "HU": "Hungary",
    "IE": "Ireland", "IL": "Israel", "IS": "Iceland", "IT": "Italy",
    "JP": "Japan", "KR": "South Korea", "LI": "Liechtenstein",
    "LT": "Lithuania", "LU": "Luxembourg", "LV": "Latvia", "MA": "Morocco",
    "MC": "Monaco", "MT": "Malta", "MX": "Mexico", "MY": "Malaysia",
    "NL": "Netherlands", "NO": "Norway", "NZ": "New Zealand", "PA": "Panama",
    "PE": "Peru", "PL": "Poland", "PT": "Portugal", "QA": "Qatar",
    "RO": "Romania", "RS": "Serbia", "SA": "Saudi Arabia", "SE": "Sweden",
    "SG": "Singapore", "SI": "Slovenia", "SK": "Slovakia", "TR": "Türkiye",
    "TW": "Taiwan", "UA": "Ukraine", "US": "United States", "UY": "Uruguay",
    "XK": "Kosovo",
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


def cached_download(url: str, name: str) -> Path:
    """Download a reference dataset once, and reuse it on the next run.

    The gazetteer and the extract index weigh tens of megabytes and describe a
    world that changes by the month. Re-downloading them on every survey would
    only be discourteous to the two projects that publish them for free.
    """
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    path = CACHE_DIR / name
    if path.is_file() and path.stat().st_size > 0:
        return path
    print(f"Downloading {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response:
        path.write_bytes(response.read())
    return path


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
    """Call one network's feeds, and never let a malformed one stop the survey."""
    try:
        return probe_feeds(candidate)
    except Exception as error:  # noqa: BLE001 — one broken feed, not a broken run
        survey = dict(candidate)
        survey["verdict"] = "unreachable"
        survey["error"] = f"{type(error).__name__}: {error}"
        return survey


def probe_feeds(candidate: dict) -> dict:
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
            # The language the producer writes in, which is the one the
            # configuration announces (§15) when the country speaks several.
            declared = information.get("language") or information.get("languages")
            if isinstance(declared, list):
                declared = declared[0] if declared else None
            if isinstance(declared, str) and declared:
                survey["feedLanguage"] = declared.split("-")[0].lower()
        except Exception:  # noqa: BLE001 — optional enrichment
            pass

    if "vehicle_types" in feeds:
        try:
            types = fetch_json(feeds["vehicle_types"])["data"]["vehicle_types"]
            survey["formFactors"] = sorted(
                {kind.get("form_factor", "unknown") for kind in types}
            )
            # Only the bicycles are looked at: a network's electric SCOOTERS
            # say nothing about the bikes one borrows at its docks. Absent
            # from the survey when no vehicle type is declared, which is how a
            # city ends up with no fleet block and the plain bike drawn.
            survey["electricBikes"] = any(
                kind.get("form_factor") in BICYCLE_FORM_FACTORS
                and kind.get("propulsion_type") in ELECTRIC_PROPULSIONS
                for kind in types
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
        position for position in (station_position(station) for station in stations)
        if position is not None
    ]
    # A station — or a small cluster of them — standing hundreds of kilometres
    # from the rest is a mistake in the feed, not an outpost of the network: it
    # would stretch the reference box until the network no longer looked like a
    # conurbation, and §4's three datasets are cut to that box.
    strays = outlying_positions(positioned)
    if strays:
        survey["strayStations"] = len(strays)
        positioned = [
            position for index, position in enumerate(positioned) if index not in strays
        ]
    survey["stationCount"] = len(positioned)
    survey["unpositionedStations"] = len(stations) - len(positioned)
    survey["capacityTotal"] = sum(whole_number(station.get("capacity")) for station in stations)
    if positioned:
        box = {
            "south": min(latitude for latitude, _ in positioned),
            "west": min(longitude for _, longitude in positioned),
            "north": max(latitude for latitude, _ in positioned),
            "east": max(longitude for _, longitude in positioned),
        }
        survey["boundingBox"] = box
        record_reference_area(survey)
        # A handful of positions, spread through the list, is enough to name
        # the conurbation later on without keeping every station in the survey.
        step = max(1, len(positioned) // STATION_SAMPLES)
        survey["stationSamples"] = [
            [latitude, longitude] for latitude, longitude in positioned[::step]
        ][:STATION_SAMPLES]

    if "station_status" in feeds:
        try:
            live = fetch_json(feeds["station_status"])["data"]["stations"]
            survey["reportsDocks"] = any(
                "num_docks_available" in station for station in live
            )
            survey["bikesAvailable"] = sum(
                whole_number(
                    station.get("num_bikes_available")
                    or station.get("num_vehicles_available")
                )
                for station in live
            )
        except Exception:  # noqa: BLE001 — optional enrichment
            pass

    survey["verdict"] = verdict_of(survey)
    return survey


def station_position(station: dict) -> tuple[float, float] | None:
    """The position of a station, if it carries one that can be believed.

    Feeds do publish stations at latitude zero — a field left empty rather than
    omitted. One of them is enough to stretch the reference box from the
    conurbation down to the Gulf of Guinea, and with it the three datasets §4
    derives from that box.

    The numbers are read rather than trusted: the standard asks for a decimal,
    and a producer here and there publishes the string of one. Refusing those
    feeds would drop real networks over a matter of quotation marks.
    """
    try:
        latitude = float(station.get("lat"))
        longitude = float(station.get("lon"))
    except (TypeError, ValueError):
        return None
    if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
        return None
    if abs(latitude) <= 0.0001 and abs(longitude) <= 0.0001:
        return None
    return latitude, longitude


def whole_number(value) -> int:
    """A count as the feed published it, whatever type it chose for it."""
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return 0


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

    if (survey.get("areaSquareKilometres") or 0) > COUNTRY_WIDE_AREA:
        return "country-wide-feed"

    return "eligible"


def read_mobilitydata(source: str) -> list[dict]:
    """Read every system of MobilityData's `systems.csv`, whatever its country."""
    rows = csv.DictReader(io.StringIO(source))
    return [
        {
            "source": "MobilityData",
            "country": row["Country Code"].strip().upper(),
            "systemId": row["System ID"].strip(),
            "catalogueName": row["Name"].strip(),
            "location": row["Location"].strip(),
            "homepageUrl": row["URL"].strip(),
            "declaredVersions": row["Supported Versions"].strip(),
            "authenticationType": row["Authentication Type"].strip(),
            "discoveryUrls": [row["Auto-Discovery URL"].strip()],
        }
        for row in rows
        if row["Auto-Discovery URL"].strip()
    ]


def read_transport_data_gouv(datasets: list) -> list[dict]:
    """Read the station-based bike datasets of France's national access point.

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
            "country": "FR",
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


def read_extra_feeds(path: Path) -> list[dict]:
    """Read the addresses found outside the two catalogues above.

    A national registry lags behind the networks it describes, and a country
    without a national access point has none at all. What that leaves out is
    listed by hand in `config/extra-feeds.json`, each entry carrying the public
    page it was read from — an operator's developer page, a city's open-data
    portal — so that the claim can be checked. Nothing is trusted for having
    been written there: the address is called and judged like any other.
    """
    if not path.is_file():
        return []
    document = json.loads(path.read_text(encoding="utf-8"))
    return [
        {
            "source": feed.get("source") or "hand-checked",
            "country": feed["country"].upper(),
            "systemId": feed["id"],
            "catalogueName": feed.get("name", ""),
            "location": feed.get("location", ""),
            "homepageUrl": feed.get("homepageUrl", ""),
            "publisher": feed.get("publisher", ""),
            "licence": feed.get("licence"),
            "datasetPageUrl": feed.get("datasetPageUrl"),
            "declaredVersions": "",
            "authenticationType": "",
            "discoveryUrls": [feed["discoveryUrl"]],
        }
        for feed in document.get("feeds", [])
    ]


def merge(surveys: list[dict]) -> list[dict]:
    """Fuse the catalogues' views of the same network into one entry.

    The same network is published under different addresses on either side —
    `api.gbfs.ecovelo.mobi` here, `api.gbfs.v3.0.ecovelo.mobi` there — so the
    join cannot be on the URL. It is on the stations themselves: two feeds
    serving the same rectangle with the same number of stations are the same
    network, and no two conurbations are ambiguous at that resolution.
    """
    merged: list[dict] = []
    by_country: dict[str, list[dict]] = {}
    for survey in surveys:
        # Comparing every survey against every other is quadratic and, over a
        # thousand systems, slow for nothing: two feeds sharing a rectangle
        # share a country.
        neighbours = by_country.setdefault(survey.get("country", ""), [])
        twin = next((other for other in neighbours if same_network(other, survey)), None)
        if twin is None:
            copy = dict(survey)
            merged.append(copy)
            neighbours.append(copy)
            continue
        # A national access point knows the authority and the licence; the
        # registry knows the declared versions. Keep both, and keep the address
        # that answered with the most recent GBFS version.
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

    The join cannot be on the address: the catalogues publish the same network
    under different hosts, and a conurbation that has changed operator keeps
    both its feeds listed. It is on the stations — two feeds covering the same
    rectangle with a comparable number of stations describe the same network.
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

    A national access point often publishes an address that no longer answers
    beside one that does — the producer moved its feed and only one of the two
    catalogues followed. The geometric join cannot see it, since a feed that
    did not answer has no stations to compare.

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
            if twin.get("country") == survey.get("country")
            and place_words(twin.get("mainCity") or "") & area
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

    Two networks may legitimately share a brand across a country — "Vélo
    Modalis" runs in Angoulême, Royan and Saintes, "nextbike" in half of
    Germany — so the name is not enough: the boxes must overlap too.
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


class Extracts:
    """Geofabrik's published extracts, and which of them covers a box.

    A table of regions per country would be wrong for the next country added,
    and there are a hundred and eighty of them. Geofabrik describes each of its
    extracts with the geometry it was cut to, so the question can be asked of
    the data instead: the box is tested against the extracts themselves.

    The smallest extract that covers a point wins. Downloading Bavaria to serve
    Munich is a gigabyte; downloading Europe would be sixty.
    """

    def __init__(self, features: list[dict]) -> None:
        self.regions = []
        for feature in features:
            pbf = (feature.get("properties", {}).get("urls") or {}).get("pbf")
            geometry = feature.get("geometry")
            if not pbf or not geometry or not pbf.startswith(GEOFABRIK_DOWNLOAD_PREFIX):
                continue
            path = pbf[len(GEOFABRIK_DOWNLOAD_PREFIX):].removesuffix("-latest.osm.pbf")
            polygons = (
                geometry["coordinates"] if geometry["type"] == "MultiPolygon"
                else [geometry["coordinates"]]
            )
            envelope = self._envelope(polygons)
            self.regions.append({
                "path": path,
                "name": feature["properties"].get("name", path),
                "polygons": polygons,
                "envelope": envelope,
                "area": (envelope[2] - envelope[0]) * (envelope[3] - envelope[1]),
            })

    @classmethod
    def download(cls) -> "Extracts":
        index = json.loads(
            cached_download(GEOFABRIK_INDEX, "geofabrik-index.json").read_text("utf-8")
        )
        return cls(index["features"])

    @staticmethod
    def _envelope(polygons: list) -> tuple[float, float, float, float]:
        longitudes = [point[0] for polygon in polygons for ring in polygon for point in ring]
        latitudes = [point[1] for polygon in polygons for ring in polygon for point in ring]
        return (min(longitudes), min(latitudes), max(longitudes), max(latitudes))

    @staticmethod
    def _inside_ring(longitude: float, latitude: float, ring: list) -> bool:
        """Ray casting, the plain form: a point is inside if a ray crosses oddly."""
        inside = False
        previous_longitude, previous_latitude = ring[-1][0], ring[-1][1]
        for point in ring:
            longitude_here, latitude_here = point[0], point[1]
            if (latitude_here > latitude) != (previous_latitude > latitude):
                crossing = (
                    previous_longitude - longitude_here
                ) * (latitude - latitude_here) / (
                    previous_latitude - latitude_here
                ) + longitude_here
                if longitude < crossing:
                    inside = not inside
            previous_longitude, previous_latitude = longitude_here, latitude_here
        return inside

    def _covers(self, region: dict, latitude: float, longitude: float) -> bool:
        west, south, east, north = region["envelope"]
        if not (west <= longitude <= east and south <= latitude <= north):
            return False
        for polygon in region["polygons"]:
            # A polygon's first ring is its outline, the others its holes: an
            # extract cut around an enclave has them.
            if not self._inside_ring(longitude, latitude, polygon[0]):
                continue
            if any(self._inside_ring(longitude, latitude, hole) for hole in polygon[1:]):
                continue
            return True
        return False

    def covering(self, latitude: float, longitude: float) -> str | None:
        """The smallest extract holding this point, as a download path."""
        candidates = [
            region for region in self.regions
            if self._covers(region, latitude, longitude)
        ]
        if not candidates:
            return None
        return min(candidates, key=lambda region: region["area"])["path"]

    def for_box(self, box: dict) -> list[str]:
        """The extracts a reference box reaches, sampled on a grid.

        A box straddling two of them needs both, merged: Avignon's reaches into
        Languedoc-Roussillon, Basel's into Germany. Sampling the box rather
        than the stations is deliberate — what gets cut is the box, margin
        included.
        """
        found: list[str] = []
        for row in range(BOX_GRID_SIDE):
            for column in range(BOX_GRID_SIDE):
                latitude = box["south"] + (box["north"] - box["south"]) * row / (
                    BOX_GRID_SIDE - 1)
                longitude = box["west"] + (box["east"] - box["west"]) * column / (
                    BOX_GRID_SIDE - 1)
                path = self.covering(latitude, longitude)
                if path and path not in found:
                    found.append(path)
        # A point out at sea is inside no county but inside the country that
        # county belongs to, and the country would then be downloaded beside
        # it — England beside Hampshire, for a box reaching into the Solent.
        # An extract that is an ancestor of another already chosen adds
        # nothing but its own gigabyte.
        return sorted(
            path for path in found
            if not any(other != path and other.startswith(f"{path}/") for other in found)
        )


class Gazetteer:
    """The world's populated places, and which of them a network stands in.

    Answers the question a network's name never does: not "what is this network
    called" but "which municipalities does it serve". A bike-share network is a
    conurbation's, not a town's — Lille's covers ninety-five municipalities —
    and §4 derives the reference box from the stations precisely so that the
    neighbouring towns are inside it. Naming them is how the report shows that
    they were not forgotten.
    """

    # A degree of latitude to a cell: coarse, but it turns a scan of two
    # hundred thousand places into a look at three or four cells.
    CELL_DEGREES = 1.0

    # GeoNames codes a district of a town "PPLX". Those are neighbourhoods —
    # Manhattan, Kreuzberg — and one of them would name a conurbation after a
    # quarter of it. Kept in the list of places covered, never as the main one.
    SECTION_CODE = "PPLX"

    def __init__(self, places: list[tuple]) -> None:
        self.cells: dict[tuple[int, int], list[tuple]] = {}
        for place in places:
            key = (int(place[0] // self.CELL_DEGREES), int(place[1] // self.CELL_DEGREES))
            self.cells.setdefault(key, []).append(place)

    @classmethod
    def download(cls) -> "Gazetteer":
        archive = cached_download(GEONAMES_CITIES_ZIP, "cities500.zip")
        places = []
        with zipfile.ZipFile(archive) as bundle:
            with bundle.open(GEONAMES_CITIES_FILE) as stream:
                for line in io.TextIOWrapper(stream, encoding="utf-8"):
                    columns = line.split("\t")
                    if len(columns) < 15:
                        continue
                    try:
                        places.append((
                            float(columns[4]),          # latitude
                            float(columns[5]),          # longitude
                            columns[1],                 # name
                            columns[8],                 # country code
                            int(columns[14] or 0),      # population
                            columns[7],                 # feature code
                        ))
                    except ValueError:
                        continue
        return cls(places)

    def inside(self, box: dict, country: str | None = None) -> list[tuple]:
        """Every place the box holds, most populous first.

        The country is a filter and not a requirement: a registry and a
        gazetteer do not always agree on where a border runs — Belfast is
        Ireland to the one and the United Kingdom to the other — and a network
        left nameless over a disagreement of that kind serves nobody. Where the
        filter empties the list, the places are taken as they come.
        """
        found = []
        for row in range(int(box["south"] // self.CELL_DEGREES),
                         int(box["north"] // self.CELL_DEGREES) + 1):
            for column in range(int(box["west"] // self.CELL_DEGREES),
                                int(box["east"] // self.CELL_DEGREES) + 1):
                for place in self.cells.get((row, column), ()):
                    if not (box["south"] <= place[0] <= box["north"]
                            and box["west"] <= place[1] <= box["east"]):
                        continue
                    found.append(place)
        of_country = [place for place in found if place[3] == country] if country else found
        return sorted(of_country or found, key=lambda place: -place[4])

    def main_place(self, places: list[tuple]) -> str:
        """The place that names a conurbation: its largest true municipality."""
        towns = [place for place in places if place[5] != self.SECTION_CODE] or places
        return towns[0][2] if towns else ""


def french_departments(survey: dict) -> None:
    """Fill in the Base Adresse Nationale extracts a French box reaches (§4.3).

    France's address base is published department by department, so a French
    city configuration has to name them. The state's own geographic API
    answers, for a position, which department it falls in.

    The reference box is what is sampled, not the stations: the box carries the
    3 km margin, and it routinely crosses into a neighbouring department the
    intercommunality does not include — Lyon's reaches into the Ain,
    Avignon's into the Gard.
    """
    box = survey.get("boundingBox")
    if not box:
        return
    widened = widen(box, DEFAULT_MARGIN_METRES)
    departments: set[str] = set()
    for row in range(BOX_GRID_SIDE):
        for column in range(BOX_GRID_SIDE):
            latitude = widened["south"] + (widened["north"] - widened["south"]) * row / (
                BOX_GRID_SIDE - 1)
            longitude = widened["west"] + (widened["east"] - widened["west"]) * column / (
                BOX_GRID_SIDE - 1)
            try:
                found = fetch_json(
                    f"{GEO_API_COMMUNES}?lat={latitude}&lon={longitude}"
                    "&fields=nom,code,codeDepartement"
                )
            except Exception:  # noqa: BLE001 — a point at sea returns nothing
                continue
            departments.update(
                municipality["codeDepartement"] for municipality in found
            )
    if departments:
        survey["banDepartments"] = sorted(departments)


def locate(survey: dict, extracts: Extracts, gazetteer: Gazetteer) -> None:
    """Say where a network is: its municipalities, and the extract to cut.

    Both are read from the stations rather than from an administrative
    boundary, as §4 requires: a network spills over the edge of the authority
    that runs it, and the box is what the datasets are cut to.
    """
    box = survey.get("boundingBox")
    if not box:
        return

    # The reference box, margin included: it is the ground the datasets cover,
    # and a conurbation's largest town can stand just outside the rectangle its
    # own stations enclose — Mexico City is three hundred metres outside
    # Ecobici's, which would have named the network after a borough of it.
    widened = widen(box, DEFAULT_MARGIN_METRES)
    places = gazetteer.inside(widened, survey.get("country") or None)
    if places:
        survey["mainCity"] = gazetteer.main_place(places)
        # Municipalities, not neighbourhoods: what is worth saying is that the
        # box holds Roubaix and Tourcoing beside Lille, not that it holds the
        # eleventh district of Budapest beside Budapest.
        survey["municipalities"] = [
            place[2] for place in places if place[5] != Gazetteer.SECTION_CODE
        ]

    survey["osmRegions"] = extracts.for_box(widened)

    # Re-rendering a stored survey must not call the state's API again for
    # departments it already holds.
    if survey.get("country") == "FR" and not survey.get("banDepartments"):
        french_departments(survey)


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
# authority, and the vocabulary its name is built from. Stripped from the tail
# of a title along with the territory's own words.
# "vélo", "bike" and their cousins are deliberately absent: half the brands are
# built on them, and popping one would turn "Ti Vélo" into "Ti".
ADMINISTRATIVE_WORDS = frozenset("""
    ca cc cu ce communaute communautes commune communes limitrophe limitrophes
    agglomeration agglo metropole eurometropole syndicat mixte territoire pays
    grand grande grands grandes petit petite region departement ville villes
    de du des d la le les l en et au aux sur sous a
    city ciudad citta cidade stadt stad by kommune gemeinde comune municipio
    municipality metropolitan metropolitana county province provincia
    prefecture district area urban public gmbh ag as sa spa bv nv oy ab
""".split())


def territory_words(survey: dict) -> frozenset[str]:
    """The words a network's title may trail without saying anything new."""
    words = set(ADMINISTRATIVE_WORDS)
    for source in ("coveredAreaName", "mainCity", "location"):
        words.update(normalised(survey.get(source) or "").split())
    # The region as well: an overseas or provincial network trails it —
    # "Altervélo Saint-Pierre La Réunion" — where a metropolitan one never does.
    for region in survey.get("osmRegions") or []:
        words.update(region.rsplit("/", 1)[-1].split("-"))
    return frozenset(words)


def strip_territory(name: str, forgettable: frozenset[str]) -> str:
    """Remove the territory a network's published title trails behind it.

    A national access point titles its datasets "VLS *brand* *territory*" —
    "VLS Naolib Nantes Métropole" — and a registry does the same in its own
    language. The territory belongs in the configuration's own fields, not in
    the network's name: the interface already shows the two side by side, and
    "Naolib — Nantes" must not read "Naolib Nantes Métropole — Nantes".

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

    Three spellings reach us and none is reliably the best: the title of a
    national access point's dataset, the `name` the feed publishes for itself,
    and the label of the registry. They are tried in that order, each stripped
    of the boilerplate its producer adds — "VLS " in front, the territory
    behind, the city in brackets.

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
        # A feed naming itself "bike_share_toronto" has given its identifier,
        # not its name. The catalogue holds the name in that case.
        if "_" in raw and " " not in raw:
            continue
        # "Regensburg, Augsburg, Straubing, Tuttlingen" is the list of towns an
        # operator serves under one feed, not a name. The next source gives the
        # brand — "Donkey Republic Regensburg", which the territory stripping
        # then shortens to "Donkey Republic".
        if "," in raw:
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


def language_of(survey: dict) -> str:
    """The language this city's streets are named in (§15.1).

    The producer's own declaration is followed wherever it names one of the
    country's languages: Barcelona's feed says Catalan, Bilbao's says Basque,
    and their streets are carrers and kaleak. Anything else is the country's
    majority language — a feed declaring English in Nantes has declared a
    default, not a fact about its street names.
    """
    officials = OFFICIAL_LANGUAGES.get(survey.get("country", ""), ("en",))
    declared = survey.get("feedLanguage")
    return declared if declared in officials else officials[0]


def licence_from_url(url: str) -> str:
    """Name the licence a "license_url" points at, or nothing (§4.5).

    Matched on the address stripped of its scheme and of anything a producer
    appends without changing the document — a trailing slash, Creative Commons'
    "deed.ja" translation suffix. An address this table does not hold names
    nothing: the caller then says the licence is unstated, which is true, where
    a guess from the address would be a claim nobody checked.
    """
    address = url.strip().lower()
    address = address.split("://", 1)[-1].removeprefix("www.")
    for fragment, name in LICENCE_URL_NAMES:
        if fragment in address:
            return name
    return ""


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
    if not licence:
        licence = licence_from_url(survey.get("licenceUrl") or "")
    survey["licenceName"] = licence

    # The attribution is written the way the country publishing the data writes
    # it, as an address is (§15.1): a French network credits "Données", and one
    # translated into the language of whoever happens to be reading would credit
    # nobody by the name the producer uses.
    french = survey.get("country") == "FR"
    credited = operator or survey.get("displayName", "")
    lead = "Données" if french else "Data"
    attribution = f"{lead} {survey.get('displayName', '')} — {credited}"
    if licence:
        # "licence Licence Ouverte 2.0" reads as a stammer; some licence names
        # already carry the word.
        article = "" if normalised(licence).startswith("licence") else "licence "
        attribution += f", {article}{licence}"
    else:
        attribution += f", {UNSTATED_LICENCE_FR if french else UNSTATED_LICENCE}"
    survey["attribution"] = attribution
    survey["attributionUrl"] = (
        survey.get("datasetPageUrl")
        or survey.get("licenceUrl")
        or survey.get("homepageUrl")
        or MOBILITYDATA_SYSTEMS_PAGE
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
    "country-wide-feed": (
        f"One feed for a whole country (over {COUNTRY_WIDE_AREA:,} km²)",
        "The datasets are cut to one rectangle (§4), and a country-sized "
        "rectangle produces a base map of tens of gigabytes for someone who "
        "rides in one city of it. Such a feed is served as soon as it is split "
        "into the conurbations it covers.",
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
            survey["language"] = language_of(survey)
            attribution_of(survey)
    drop_duplicate_feeds(surveys)


def country_name(code: str) -> str:
    return COUNTRY_NAMES.get(code, code or "—")


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
    """Write the readable list of the networks of the world."""
    eligible = [survey for survey in surveys if survey["verdict"] == "eligible"]
    by_country: dict[str, list[dict]] = {}
    for survey in eligible:
        by_country.setdefault(survey.get("country", ""), []).append(survey)

    lines = [
        "# Bike-share networks that publish their stations",
        "",
        "> Generated by `tools/discover_networks.py` on "
        f"{generated_at}. Do not edit by hand: regenerate it.",
        "",
        "`SPEC.md` §4.1 forbids guessing a `gbfs.json` address. Every one below was",
        "read from a public catalogue — [MobilityData's `systems.csv`]"
        "(https://github.com/MobilityData/gbfs), the registry the GBFS standard",
        "keeps of itself, France's [national access point](https://transport.data.gouv.fr),",
        "or an operator's own developer page — then **called for real**. The station",
        "counts, the GBFS versions and the rejections all come from what the feeds",
        "answered, not from what their catalogues claim.",
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
        "The area a network covers is NOT one of the rules: a feed serving a whole",
        "region is served like any other if it passes them, and what its data weighs",
        "is settled by the tile ceiling of `SPEC.md` §4.2, on the files produced.",
        "",
        "Most of what publishes GBFS fails the second or the third rule: free-floating",
        "fleets outnumber docked networks, and they publish their parking areas as",
        "stations.",
        "",
        "A conurbation absent from this page altogether — neither served nor set",
        "aside — publishes no station feed in any catalogue read here. Several",
        "sizeable networks are in that case: their availability lives behind a key or",
        "a proprietary interface, and `SPEC.md` §4.1 rules those out. Re-running the",
        "script is what settles whether that is still true.",
        "",
        f"## Served — {len(eligible)} networks in {len(by_country)} "
        + ("country" if len(by_country) == 1 else "countries"),
        "",
        "The bounding box of each is derived from its **own stations** widened by",
        "3 km (§4), which is why it covers the conurbation rather than the",
        "municipality the network is named after. \"Also covers\" names the other",
        "municipalities standing inside that box: a bike-share network belongs to an",
        "agglomeration, and the map must not be centred on its largest town alone.",
        "",
    ]

    for code in sorted(by_country, key=lambda code: (-len(by_country[code]), code)):
        networks = sorted(by_country[code], key=lambda survey: -survey.get("stationCount", 0))
        language = "/".join(OFFICIAL_LANGUAGES.get(code, ("en",)))
        lines += [
            f"### {country_name(code)} ({code}) — {len(networks)} "
            + ("network" if len(networks) == 1 else "networks")
            + f", street names in `{language}`",
            "",
            "| Network | Conurbation | Also covers | Stations | Docks | GBFS | Box | "
            "OSM extract |",
            "|---|---|---|---:|---:|:--:|---:|---|",
        ]
        for survey in networks:
            others = [
                place for place in (survey.get("municipalities") or [])
                if place != survey.get("mainCity")
            ]
            covered = ", ".join(others[:MUNICIPALITIES_LISTED])
            if len(others) > MUNICIPALITIES_LISTED:
                covered += f", +{len(others) - MUNICIPALITIES_LISTED}"
            regions = ", ".join(
                region.rsplit("/", 1)[-1] for region in survey.get("osmRegions", [])
            )
            lines.append(
                f"| {survey.get('displayName') or survey['catalogueName']} "
                f"| {survey.get('mainCity') or survey.get('location') or '—'} "
                f"| {covered or '—'} "
                f"| {survey.get('stationCount', 0)} "
                f"| {survey.get('capacityTotal', 0)} "
                f"| {survey.get('gbfsVersion', '?')} "
                f"| {survey.get('areaSquareKilometres', 0)} km² "
                f"| {regions or '—'} |"
            )
        lines.append("")

    lines += [
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
            key=lambda survey: ((survey.get("country") or ""),
                                (survey.get("catalogueName") or "").lower()),
        )
        if not rejected:
            continue
        lines += [f"### {title} — {len(rejected)}", "", why, ""]
        names = ", ".join(sorted({
            f"{survey.get('systemName') or survey['catalogueName']}"
            + (f" ({survey['location']}, {survey.get('country', '')})"
               if survey.get("location") else f" ({survey.get('country', '')})")
            for survey in rejected
        }, key=str.lower))
        lines += [names, ""]

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--survey", type=Path, default=DEFAULT_SURVEY)
    parser.add_argument("--extra-feeds", type=Path, default=DEFAULT_EXTRA_FEEDS)
    parser.add_argument("--country", action="append", default=[],
                        help="restrict the survey to these ISO country codes")
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
        # The municipalities and the extracts are placed again: both come from
        # two files on disk, and re-reading them costs nothing where calling
        # sixteen hundred feeds again would cost an hour.
        extracts = Extracts.download()
        gazetteer = Gazetteer.download()
        for survey in surveys:
            if survey["verdict"] in ("eligible", "duplicate-feed"):
                locate(survey, extracts, gazetteer)
        describe(surveys)
        write_survey(stored.get("generatedAt", generated_at), surveys, arguments.survey)
        write_report(surveys, arguments.report, generated_at)
        print(f"Rewritten from {arguments.survey}: {arguments.report}")
        return 0

    wanted = {code.upper() for code in arguments.country}

    print(f"Reading {MOBILITYDATA_SYSTEMS_CSV}")
    candidates = read_mobilitydata(fetch_text(MOBILITYDATA_SYSTEMS_CSV))
    print(f"  {len(candidates)} systems in "
          f"{len({candidate['country'] for candidate in candidates})} countries")

    extra = read_extra_feeds(arguments.extra_feeds)
    if extra:
        print(f"Reading {arguments.extra_feeds}")
        print(f"  {len(extra)} addresses found outside the registry")
        candidates += extra

    if not wanted or "FR" in wanted:
        print(f"Reading {TRANSPORT_DATA_GOUV_DATASETS}")
        from_state = read_transport_data_gouv(fetch_json(TRANSPORT_DATA_GOUV_DATASETS))
        print(f"  {len(from_state)} station-based bike datasets")
        candidates += from_state

    if wanted:
        candidates = [
            candidate for candidate in candidates if candidate["country"] in wanted
        ]

    print(f"Calling {len(candidates)} feeds…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        surveys = list(pool.map(probe, candidates))

    surveys = merge(surveys)
    eligible = [survey for survey in surveys if survey["verdict"] == "eligible"]
    print(f"  {len(eligible)} eligible networks out of {len(surveys)} distinct systems")

    print("Reading the extract index and the gazetteer…")
    extracts = Extracts.download()
    gazetteer = Gazetteer.download()

    print("Naming the municipalities and choosing the extracts…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        list(pool.map(lambda survey: locate(survey, extracts, gazetteer), eligible))

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
