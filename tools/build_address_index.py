#!/usr/bin/env python3
"""Build the offline address index, as a SQLite database (SPEC.md §4.3).

Address search runs entirely on the device. It is the most sensitive data the
application handles — it reveals where the user is going — so no geocoding
service is ever contacted, not even while typing.

Granularity is the house number, not the street. Some Lille thoroughfares run
for over a kilometre; a single point per street would be several hundred
metres off, enough to pick the wrong station and therefore compute a wrong
route.

Sources:
  · Base Adresse Nationale, departmental CSV extracts — house numbers
  · OpenStreetMap — landmarks worth searching for, treated like streets

Storage, and why it fits in a dozen megabytes for half a million addresses:
  · a street row carries the text; a house-number row carries none
  · house-number coordinates are stored as deltas from their street's
    representative point, in units of 1e-5 degree, which keeps almost every
    delta inside one or two bytes of SQLite varint
  · the house-number table is WITHOUT ROWID and keyed on (street, number), so
    the table *is* its own lookup index — there is no second copy of the keys
  · full-text search covers street names only; numbers are resolved by key

Usage:
    /usr/bin/python3 tools/build_address_index.py \
        --ban-csv data/ban/adresses-59.csv.gz [--ban-csv ...] \
        [--osm-extract data/osm/<region>.osm.pbf] \
        [--config PATH] [--output PATH]
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import shutil
import sqlite3
import statistics
import subprocess
import sys
import tempfile
import time
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from address_normalization import AddressNormalizer, normalizer_for
from city_config import DEFAULT_CITY_CONFIG, BoundingBox, CityConfig

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
DEFAULT_OUTPUT = REPO_ROOT / "data" / "out" / "addresses.sqlite"

# Coordinate deltas are stored in hundred-thousandths of a degree: about 1.1 m
# north-south and 0.7 m east-west at this latitude. That is an order of
# magnitude finer than the 50 m accuracy the acceptance criteria ask for, and
# coarse enough that a delta almost always fits in one or two bytes.
DELTA_SCALE = 100_000.0

# Size of a cell of the commune-search grid, in degrees: about a kilometre,
# which is the neighbourhood a landmark and the street serving it are bound to
# share.
GRID_DEGREES = 0.01

# Real street names kept in the reference cases the Kotlin test replays. Enough
# to cover a city's own spellings without turning a fixture into a corpus.
SAMPLED_STREET_NAMES = 120

# Below this many house numbers per street, an index stops being an address
# index and becomes a street directory: nearly every search then falls back on
# the street's representative point, with the error of several hundred metres
# §4.3 calls unacceptable — "enough to designate the wrong station and
# therefore a wrong journey".
#
# The median over the 332 networks served is 16.4 numbers per street, so this
# floor sits far under the ordinary case and only catches what is degenerate.
# It is a threshold on the data and never on a country: nothing here names one,
# and a place whose numbers get mapped rises above it without a release. What
# it catches today is Japan, where OpenStreetMap carries almost no
# "addr:housenumber" because an address there is not built on the street —
# Tokyo at 0.01, Toyama at 0.04.
#
# It warns and does not refuse. §4.3 already accepts that the coverage of
# OpenStreetMap varies from one city to the next, and refusing here would leave
# Tokyo — the catalogue's largest network, 1891 stations — with no address
# search at all, which serves its rider worse than a street-level one that says
# it is approximate.
HOUSE_NUMBERS_PER_STREET_FLOOR = 1.0

# Entry kinds, mirrored by the Kotlin side.
KIND_STREET = 0
KIND_PLACE = 1

# Landmarks worth typing into a search box (§4.3). Deliberately far narrower
# than the map's point-of-interest list: a search result must be somewhere a
# person would actually say they are going.
SEARCHABLE_PLACE_FILTERS = (
    "n/railway=station,halt", "a/railway=station",
    "n/station=subway,light_rail", "n/public_transport=station",
    "n/amenity=university,college,hospital,townhall",
    "a/amenity=university,college,hospital,townhall",
    "n/amenity=theatre,library,museum", "a/amenity=theatre,library,museum",
    "n/tourism=museum,attraction", "a/tourism=museum,attraction",
    "n/place=square", "a/place=square",
    "a/leisure=park,stadium", "n/leisure=stadium",
    "a/landuse=retail",
)


class GenerationError(RuntimeError):
    """A step of the pipeline failed; the message says which one and why."""


@dataclass
class Street:
    """One street or landmark, and the house numbers attached to it."""

    display_name: str
    city: str
    postcode: str
    kind: int
    # Name of the absorbed municipality, when it differs from the current one:
    # "Lomme", "Hellemmes" for addresses the BAN attaches to Lille. That is the
    # name an inhabitant uses, and types into a search.
    former_city: str = ""
    latitudes: list[float] = field(default_factory=list)
    longitudes: list[float] = field(default_factory=list)
    # (number, suffix) -> every position BAN gives for it. Usually exactly one;
    # see `pick_best_position` for why the others have to be kept until the
    # street's representative point is known.
    numbers: dict[tuple[int, str], list[tuple[float, float]]] = field(
        default_factory=dict
    )


def require_fts4() -> None:
    """Refuse to run on a SQLite build that cannot create the index.

    Android guarantees FTS4 from API 11 onwards, whereas FTS5 is not dependable
    on the API 26 devices this application targets, so FTS4 is what we build.
    Some Python distributions — conda's in particular — ship a SQLite compiled
    without it.
    """
    connection = sqlite3.connect(":memory:")
    try:
        connection.execute("CREATE VIRTUAL TABLE probe USING fts4(x)")
    except sqlite3.OperationalError as error:
        raise GenerationError(
            "This Python is linked against an SQLite without FTS4 "
            f"(version {sqlite3.sqlite_version}): {error}.\n"
            "Run the script again with the system Python, for example:\n"
            "  /usr/bin/python3 tools/build_address_index.py …"
        ) from error
    finally:
        connection.close()


def parse_house_number(raw_number: str, raw_suffix: str) -> tuple[int, str] | None:
    """Turn the BAN number and repetition columns into a sortable key.

    Returns ``None`` for rows without a usable number: those carry no more
    information than the street itself.
    """
    raw_number = raw_number.strip()
    # isdecimal, not isdigit: the latter answers true for characters int()
    # refuses — the superscripts ² and ³ among them, which Karlsruhe writes in
    # its addresses. Everything isdecimal accepts, int() parses, including the
    # Arabic-Indic digits an address base outside Europe may hold.
    if not raw_number.isdecimal():
        return None
    number = int(raw_number)
    if number <= 0:
        return None
    return number, raw_suffix.strip().lower()


def read_ban_files(
    csv_paths: list[Path], box: BoundingBox, normalizer: AddressNormalizer
) -> tuple[dict[str, Street], int, int]:
    """Read the BAN extracts, keeping only what falls inside the bounding box.

    Addresses are grouped by (INSEE commune code, normalised street name)
    rather than by ``id_fantoir``. The national street identifier looks like
    the natural key, but BAN leaves it empty on a noticeable share of rows —
    24 363 of the 286 338 inside this bounding box — and a street holding both
    kinds of row would then be indexed twice, its numbers split between two
    entries sitting at two different representative points. Measured on this
    extract, keying on the identifier severed 69 streets in half.

    Returns:
        The streets by key, the number of rows read and the number kept.
    """
    streets: dict[str, Street] = {}
    rows_read = 0
    rows_kept = 0

    for csv_path in csv_paths:
        print(f"  · {csv_path.name}")
        opener = gzip.open if csv_path.suffix == ".gz" else open
        with opener(csv_path, "rt", encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream, delimiter=";"):
                rows_read += 1
                try:
                    latitude = float(row["lat"])
                    longitude = float(row["lon"])
                except (TypeError, ValueError):
                    continue
                if not box.contains(latitude, longitude):
                    continue

                street_name = (row["nom_voie"] or "").strip()
                if not street_name:
                    continue

                # The former-commune code is part of the key: communes that
                # merged often ended up with two streets of the same name in
                # what is now one INSEE code, kilometres apart. Merging those
                # would place half the numbers of each at the other's location.
                former_commune = (row.get("code_insee_ancienne_commune") or "").strip()
                key = (f"{row['code_insee']}|{former_commune}|"
                       f"{normalizer.normalize(street_name)}")

                street = streets.get(key)
                if street is None:
                    city = (row["nom_commune"] or "").strip()
                    former_city = (row.get("nom_ancienne_commune") or "").strip()
                    street = Street(
                        display_name=street_name,
                        city=city,
                        postcode=(row["code_postal"] or "").strip(),
                        kind=KIND_STREET,
                        # The column often repeats the current municipality:
                        # keep only what it genuinely tells us.
                        former_city=former_city if former_city != city else "",
                    )
                    streets[key] = street

                street.latitudes.append(latitude)
                street.longitudes.append(longitude)
                rows_kept += 1

                parsed = parse_house_number(row["numero"], row.get("rep") or "")
                if parsed is not None:
                    street.numbers.setdefault(parsed, []).append(
                        (latitude, longitude)
                    )

    return streets, rows_read, rows_kept


def read_osm_places(
    osm_extract: Path, box: BoundingBox, work_dir: Path
) -> list[Street]:
    """Extract named landmarks from OpenStreetMap, to be searched like streets."""
    if shutil.which("osmium") is None:
        raise GenerationError(
            "osmium is missing. Install it: sudo apt install osmium-tool"
        )

    clipped = work_dir / "places-area.osm.pbf"
    subprocess.run(
        ["osmium", "extract", "--bbox", box.as_osmium_extract_argument(),
         "--strategy", "complete_ways", "--overwrite",
         "-o", str(clipped), str(osm_extract)],
        check=True, capture_output=True,
    )
    filtered = work_dir / "places.osm.pbf"
    subprocess.run(
        ["osmium", "tags-filter", "--overwrite", "-o", str(filtered),
         str(clipped), *SEARCHABLE_PLACE_FILTERS],
        check=True, capture_output=True,
    )
    export_config = work_dir / "places.export.json"
    export_config.write_text(json.dumps({
        "attributes": {"type": False, "id": False},
        "include_tags": ["name", "amenity", "railway", "tourism", "leisure",
                         "place", "public_transport", "addr:city",
                         "addr:postcode"],
        "linear_tags": True, "area_tags": True,
    }), encoding="utf-8")
    exported = work_dir / "places.geojsonseq"
    subprocess.run(
        ["osmium", "export", "--overwrite", "-f", "geojsonseq",
         "--geometry-types", "point", "--config", str(export_config),
         "-o", str(exported), str(filtered)],
        check=True, capture_output=True,
    )

    places: list[Street] = []
    seen: set[tuple[str, int, int]] = set()
    with exported.open(encoding="utf-8") as stream:
        for line in stream:
            line = line.strip().lstrip("\x1e")
            if not line:
                continue
            record = json.loads(line)
            properties = record.get("properties") or {}
            name = (properties.get("name") or "").strip()
            if not name:
                continue
            longitude, latitude = record["geometry"]["coordinates"]
            if not box.contains(latitude, longitude):
                continue
            # The same landmark is often mapped as both a node and an area.
            fingerprint = (name, round(latitude, 3), round(longitude, 3))
            if fingerprint in seen:
                continue
            seen.add(fingerprint)
            place = Street(
                display_name=name,
                city=(properties.get("addr:city") or "").strip(),
                postcode=(properties.get("addr:postcode") or "").strip(),
                kind=KIND_PLACE,
            )
            place.latitudes.append(latitude)
            place.longitudes.append(longitude)
            places.append(place)
    return places


# What OpenStreetMap calls a way one can be addressed on. Service roads and
# tracks are in: a house number hangs off them as readily as off a street, and
# a search that ignored them would miss the addresses attached to them.
ADDRESSABLE_HIGHWAYS = (
    "w/highway=residential,living_street,unclassified,tertiary,secondary,"
    "primary,trunk,pedestrian,footway,cycleway,service,track,road",
)

# Objects carrying a house number: a node in the garden, the building itself,
# or the parcel. All three are in use, often in the same street.
ADDRESSED_OBJECTS = ("n/addr:housenumber", "w/addr:housenumber", "a/addr:housenumber")

# Places whose name is worth attaching to a street that carries no municipality
# of its own — which, in OpenStreetMap, is almost every street.
MUNICIPALITY_PLACES = ("n/place=city,town,village,borough,suburb,quarter,hamlet",)


def osmium_export(
    source: Path, filters: tuple[str, ...], tags: list[str],
    geometry_types: str, work_dir: Path, name: str,
) -> Path:
    """Filter an extract on tags and export what is left as GeoJSON lines."""
    filtered = work_dir / f"{name}.osm.pbf"
    subprocess.run(
        ["osmium", "tags-filter", "--overwrite", "-o", str(filtered),
         str(source), *filters],
        check=True, capture_output=True,
    )
    export_config = work_dir / f"{name}.export.json"
    export_config.write_text(json.dumps({
        "attributes": {"type": False, "id": False},
        "include_tags": tags,
        "linear_tags": True, "area_tags": True,
    }), encoding="utf-8")
    exported = work_dir / f"{name}.geojsonseq"
    subprocess.run(
        ["osmium", "export", "--overwrite", "-f", "geojsonseq",
         "--geometry-types", geometry_types, "--config", str(export_config),
         "-o", str(exported), str(filtered)],
        check=True, capture_output=True,
    )
    return exported


def geojson_records(path: Path):
    """Read a GeoJSON-sequence file, one record at a time."""
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            line = line.strip().lstrip("\x1e")
            if line:
                yield json.loads(line)


def positions_of(geometry: dict) -> list[tuple[float, float]]:
    """Every point of a geometry, whatever its shape.

    A house number is a node here, a building outline there, and a street is a
    line: the index only ever needs points, and the median of them is what
    becomes a street's representative position.
    """
    kind = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if kind == "Point":
        return [(coordinates[1], coordinates[0])]
    if kind == "LineString":
        return [(point[1], point[0]) for point in coordinates]
    if kind == "MultiLineString":
        return [(point[1], point[0]) for line in coordinates for point in line]
    if kind == "Polygon":
        return [(point[1], point[0]) for point in coordinates[0]]
    if kind == "MultiPolygon":
        return [(point[1], point[0]) for polygon in coordinates for point in polygon[0]]
    return []


def read_osm_addresses(
    osm_extract: Path, box: BoundingBox, work_dir: Path,
    normalizer: AddressNormalizer,
) -> tuple[dict[str, Street], int, int]:
    """Read streets and house numbers from OpenStreetMap (SPEC.md §4.3, §15).

    France publishes a national address base and the script reads that one
    there. Everywhere else there is no such base, or it is neither free nor
    downloadable, and §15 said what to do about it: rebuild the index from
    OpenStreetMap. The extract is already on disk — the map and the routing
    graph are cut from it — so a city costs one download rather than two.

    What is read, and why in this order:

    1. the named ways, which give the streets themselves. A street with no
       house number mapped on it is still a street somebody types into a
       search box, and dropping it would leave whole neighbourhoods silent;
    2. the objects carrying ``addr:housenumber``, attached to the street named
       by their ``addr:street``. Where that tag is absent the ``addr:place``
       one takes over, which is how villages without street names are
       addressed in the Nordic countries and in much of central Europe.

    The coverage is not the BAN's, and it varies from one city to the next —
    that is the honest cost of the substitution, and the figures printed at the
    end of a run say what it came to for this conurbation.

    Returns:
        The streets by key, the number of objects read and the number kept.
    """
    if shutil.which("osmium") is None:
        raise GenerationError(
            "osmium is missing. Install it: sudo apt install osmium-tool"
        )

    clipped = work_dir / "addresses-area.osm.pbf"
    subprocess.run(
        ["osmium", "extract", "--bbox", box.as_osmium_extract_argument(),
         "--strategy", "complete_ways", "--overwrite",
         "-o", str(clipped), str(osm_extract)],
        check=True, capture_output=True,
    )

    streets: dict[str, Street] = {}
    read = kept = 0

    def key_of(name: str, city: str) -> str:
        return f"{normalizer.normalize(city)}|{normalizer.normalize(name)}"

    ways = osmium_export(
        clipped, ADDRESSABLE_HIGHWAYS,
        ["name", "addr:city", "addr:postcode"], "linestring", work_dir, "ways",
    )
    for record in geojson_records(ways):
        read += 1
        properties = record.get("properties") or {}
        name = (properties.get("name") or "").strip()
        if not name:
            continue
        positions = [
            position for position in positions_of(record.get("geometry") or {})
            if box.contains(*position)
        ]
        if not positions:
            continue
        city = (properties.get("addr:city") or "").strip()
        street = streets.setdefault(key_of(name, city), Street(
            display_name=name,
            city=city,
            postcode=(properties.get("addr:postcode") or "").strip(),
            kind=KIND_STREET,
        ))
        # A street is mapped in several pieces — one per junction, or per
        # change of surface. Every piece's points go in: the median of the
        # whole is the point that ends up representing the street.
        street.latitudes.extend(latitude for latitude, _ in positions)
        street.longitudes.extend(longitude for _, longitude in positions)
        kept += 1

    addresses = osmium_export(
        clipped, ADDRESSED_OBJECTS,
        ["addr:housenumber", "addr:street", "addr:place", "addr:city",
         "addr:postcode"],
        "point,polygon", work_dir, "addresses",
    )
    for record in geojson_records(addresses):
        read += 1
        properties = record.get("properties") or {}
        # A street name first, a place name failing that: whole regions are
        # addressed "Storgatan 4" in one country and "Bergsäter 12" in the next.
        name = (properties.get("addr:street") or properties.get("addr:place") or "").strip()
        if not name:
            continue
        positions = positions_of(record.get("geometry") or {})
        if not positions:
            continue
        # A building is a polygon: its address is one point, not its outline.
        latitude = sum(position[0] for position in positions) / len(positions)
        longitude = sum(position[1] for position in positions) / len(positions)
        if not box.contains(latitude, longitude):
            continue

        city = (properties.get("addr:city") or "").strip()
        street = streets.get(key_of(name, city))
        if street is None and city:
            # The number names its municipality and the street does not, which
            # is the common case: the street is looked up without it rather
            # than a second, empty-municipality street being created beside it.
            street = streets.get(key_of(name, ""))
        if street is None:
            street = Street(
                display_name=name,
                city=city,
                postcode=(properties.get("addr:postcode") or "").strip(),
                kind=KIND_STREET,
            )
            streets[key_of(name, city)] = street
        if not street.city and city:
            street.city = city
        if not street.postcode:
            street.postcode = (properties.get("addr:postcode") or "").strip()

        street.latitudes.append(latitude)
        street.longitudes.append(longitude)
        kept += 1

        parsed = parse_house_number(
            *split_house_number(properties.get("addr:housenumber") or "")
        )
        if parsed is not None:
            street.numbers.setdefault(parsed, []).append((latitude, longitude))

    return streets, read, kept


def split_house_number(raw: str) -> tuple[str, str]:
    """Separate a house number from what trails it.

    OpenStreetMap holds the number as one string, and the world writes it in
    every way there is: "12", "12A", "12 bis", "12-14" for a building spanning
    two numbers. The leading digits are the number, the rest is the repetition
    mark the BAN would have put in its own column.
    """
    raw = raw.strip()
    digits = ""
    for character in raw:
        # isdecimal rather than isdigit, or "23²" — a real Karlsruhe address —
        # has its superscript counted as part of the number and int() then
        # refuses the whole thing, taking the city's index down with it. Read
        # this way the ² becomes what it is, a repetition mark.
        if not character.isdecimal():
            break
        digits += character
    return digits, raw[len(digits):].strip(" -/").lower()


def attach_municipalities(streets: dict[str, Street], places: list[Street]) -> int:
    """Name the municipality of the streets that carry none.

    OpenStreetMap tags ``addr:city`` on the house numbers, rarely on the street
    itself, and in some countries on neither. A street with no municipality is
    still findable, but the results list would show it against a blank, and two
    streets of the same name in two towns would be indistinguishable.

    The nearest inhabited place gives its name. It is an approximation — a
    boundary is not a distance — and it is the same one `fill_missing_places_communes`
    makes for landmarks, for the same reason: the alternative is a blank.
    """
    if not places:
        return 0
    named = 0
    for street in streets.values():
        if street.city or not street.latitudes:
            continue
        latitude = statistics.median(street.latitudes)
        longitude = statistics.median(street.longitudes)
        nearest = min(places, key=lambda place: (
            (place.latitudes[0] - latitude) ** 2
            + (place.longitudes[0] - longitude) ** 2
        ))
        street.city = nearest.display_name
        named += 1
    return named


def read_osm_municipalities(
    osm_extract: Path, box: BoundingBox, work_dir: Path
) -> list[Street]:
    """The inhabited places of the box, as points carrying a name."""
    exported = osmium_export(
        osm_extract, MUNICIPALITY_PLACES, ["name", "place"], "point",
        work_dir, "municipalities",
    )
    places = []
    for record in geojson_records(exported):
        name = ((record.get("properties") or {}).get("name") or "").strip()
        longitude, latitude = record["geometry"]["coordinates"]
        if not name or not box.contains(latitude, longitude):
            continue
        place = Street(display_name=name, city=name, postcode="", kind=KIND_PLACE)
        place.latitudes.append(latitude)
        place.longitudes.append(longitude)
        places.append(place)
    return places


def fill_missing_places_communes(
    streets: dict[str, Street], places: list[Street]
) -> int:
    """Give each landmark the commune of the street nearest to it.

    OpenStreetMap rarely tags ``addr:city`` on a metro station or a library:
    2 011 of the 2 436 landmarks inside the Paris bounding box carry none. The
    application would then show "Châtelet - Les Halles" with an empty town,
    or worse, a postcode with nothing after it.

    A landmark sits in the commune of the streets around it, so the nearest
    street answers the question. The search goes through a coarse grid rather
    than comparing every pair: forty thousand streets against two thousand
    landmarks would be eighty million comparisons for a fact that a
    hundred-metre neighbourhood settles.

    Returns:
        The number of landmarks that received a commune.
    """
    grid: dict[tuple[int, int], list[Street]] = defaultdict(list)
    for street in streets.values():
        if street.kind != KIND_STREET or not street.latitudes:
            continue
        cell = (int(statistics.median(street.latitudes) / GRID_DEGREES),
                int(statistics.median(street.longitudes) / GRID_DEGREES))
        grid[cell].append(street)

    filled = 0
    for place in places:
        if place.city or not place.latitudes:
            continue
        latitude, longitude = place.latitudes[0], place.longitudes[0]
        cell = (int(latitude / GRID_DEGREES), int(longitude / GRID_DEGREES))
        nearest, best = None, None
        # The neighbouring cells are enough: beyond them, the municipality
        # found would be too far away to say anything about the place.
        for delta_lat in (-1, 0, 1):
            for delta_lon in (-1, 0, 1):
                for street in grid.get((cell[0] + delta_lat, cell[1] + delta_lon), ()):
                    distance = (
                        (statistics.median(street.latitudes) - latitude) ** 2
                        + (statistics.median(street.longitudes) - longitude) ** 2
                    )
                    if best is None or distance < best:
                        nearest, best = street, distance
        if nearest is not None:
            place.city = nearest.city
            place.postcode = place.postcode or nearest.postcode
            filled += 1
    return filled


def pick_best_position(
    positions: list[tuple[float, float]],
    street_latitude: float,
    street_longitude: float,
) -> tuple[float, float]:
    """Choose one position for a house number BAN reports more than once.

    Roughly half a percent of numbers come with several positions, and some of
    those sit kilometres apart — a stray row geocoded onto the wrong street, or
    two sources disagreeing. Taking whichever arrived first would scatter those
    addresses across the map, so the one nearest the street's own centre wins.
    Comparing squared degrees is enough to rank candidates a few hundred metres
    apart; no projection is needed.
    """
    if len(positions) == 1:
        return positions[0]
    return min(
        positions,
        key=lambda point: (point[0] - street_latitude) ** 2
        + (point[1] - street_longitude) ** 2,
    )


SCHEMA = """
PRAGMA page_size = 4096;

-- One row per street or landmark. This is the only table holding text.
CREATE TABLE street(
    id               INTEGER PRIMARY KEY,
    display_name     TEXT    NOT NULL,  -- shown to the user, accents intact
    normalized_name  TEXT    NOT NULL,  -- proper name, folded (§4.3)
    normalized_type  TEXT,              -- "rue", "boulevard"… or NULL
    city             TEXT    NOT NULL,
    normalized_city  TEXT    NOT NULL,
    -- Absorbed municipality, when it differs from the current one. The BAN
    -- attaches Lomme and Hellemmes to Lille; without this field, nobody there
    -- would find their street by typing the name of their municipality.
    former_city      TEXT,
    normalized_former_city TEXT,
    postcode         TEXT,
    latitude         REAL    NOT NULL,  -- representative point
    longitude        REAL    NOT NULL,
    kind             INTEGER NOT NULL   -- 0 street, 1 landmark
);

-- Full-text index over street names ONLY, as required by §4.3.
--
-- FTS4 rather than FTS5: FTS4 has been part of Android's bundled SQLite since
-- API 11, while FTS5 is not dependable on the API 26 devices this application
-- still supports.
--
-- The `simple` tokenizer rather than `unicode61`: the indexed text has already
-- been folded to unaccented lowercase by the shared normalisation rules, so
-- the tokenizer has nothing left to fold, and `simple` exists in every build
-- ever shipped. The trigram tokenizer is deliberately not used — it is absent
-- from the older Android SQLite versions, which is why fuzzy matching happens
-- in Kotlin instead.
--
-- Contentless (`content=""`): the text already lives in `street`, and the
-- search only ever needs the matching rowid.
CREATE VIRTUAL TABLE street_search USING fts4(
    terms,
    content="",
    tokenize=simple,
    prefix="2,3"
);

-- One row per house number. No text, no rowid, no secondary index: the
-- primary key doubles as the lookup path, which halves what this table costs.
CREATE TABLE house_number(
    street_id   INTEGER NOT NULL,
    number      INTEGER NOT NULL,
    suffix      TEXT    NOT NULL,  -- "bis", "ter", "a"… empty when absent
    delta_lat   INTEGER NOT NULL,  -- from the street point, 1e-5 degree units
    delta_lon   INTEGER NOT NULL,
    PRIMARY KEY (street_id, number, suffix)
) WITHOUT ROWID;

-- Written by the build, read by the application to check it can use the file.
CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""


def build_database(
    output: Path,
    streets: list[tuple[str, Street]],
    normalizer: AddressNormalizer,
    config: CityConfig,
    generated_at: str,
) -> dict[str, int]:
    """Write the SQLite index and return a few counts for the report."""
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    connection = sqlite3.connect(output)
    # Durability is irrelevant for a file rebuilt from scratch on every run.
    connection.execute("PRAGMA journal_mode = OFF")
    connection.execute("PRAGMA synchronous = OFF")
    connection.executescript(SCHEMA)

    street_rows = []
    search_rows = []
    number_rows = []
    oversized_deltas = 0

    for street_id, (_, street) in enumerate(streets, start=1):
        # The median resists the outliers that a mis-geocoded address at the
        # far end of the commune would otherwise introduce; a mean would drag
        # the whole street towards it.
        latitude = statistics.median(street.latitudes)
        longitude = statistics.median(street.longitudes)
        split = normalizer.analyse(street.display_name)
        normalized_city = normalizer.normalize(street.city)
        normalized_former_city = (
            normalizer.normalize(street.former_city) if street.former_city else ""
        )

        street_rows.append((
            street_id, street.display_name, split.proper_name, split.street_type,
            street.city, normalized_city,
            street.former_city or None, normalized_former_city or None,
            street.postcode or None, latitude, longitude, street.kind,
        ))
        search_rows.append((
            street_id,
            " ".join(part for part in (split.street_type, split.proper_name,
                                       normalized_city, normalized_former_city)
                     if part),
        ))

        for (number, suffix), positions in street.numbers.items():
            number_lat, number_lon = pick_best_position(
                positions, latitude, longitude
            )
            delta_lat = round((number_lat - latitude) * DELTA_SCALE)
            delta_lon = round((number_lon - longitude) * DELTA_SCALE)
            if abs(delta_lat) > 32767 or abs(delta_lon) > 32767:
                oversized_deltas += 1
            number_rows.append((street_id, number, suffix, delta_lat, delta_lon))

    connection.executemany(
        "INSERT INTO street VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", street_rows
    )
    connection.executemany(
        "INSERT INTO street_search(docid, terms) VALUES (?,?)", search_rows
    )
    connection.executemany(
        "INSERT INTO house_number VALUES (?,?,?,?,?)", number_rows
    )
    connection.executemany(
        "INSERT INTO metadata VALUES (?,?)",
        [
            ("formatVersion", str(config.format_version)),
            ("generatedAt", generated_at),
            ("deltaScale", str(int(DELTA_SCALE))),
            # Which rules this index was built with, and in which language.
            # The application reads both back rather than deciding for itself:
            # an index searched with another language's street types would
            # answer nothing to "ulica D\u0142uga" (SPEC \u00a715.1).
            ("normalizationLanguage", normalizer.language),
            ("normalizationRulesVersion", str(normalizer.rules_version)),
            ("boundingBox", json.dumps({
                "south": config.bounding_box.south,
                "west": config.bounding_box.west,
                "north": config.bounding_box.north,
                "east": config.bounding_box.east,
            })),
            ("streetCount", str(len(street_rows))),
            ("houseNumberCount", str(len(number_rows))),
        ],
    )
    connection.commit()
    connection.executescript("INSERT INTO street_search(street_search) "
                             "VALUES('optimize')")
    connection.commit()
    connection.execute("VACUUM")
    connection.close()

    return {
        "streets": len(street_rows),
        "numbers": len(number_rows),
        "oversized_deltas": oversized_deltas,
    }


def report_house_number_coverage(streets: int, numbers: int) -> None:
    """State how well the index locates an address, and say so when it barely does.

    The figure the report was missing. A count of streets and a count of
    numbers side by side say nothing on their own; their ratio says whether a
    search will land on a doorstep or on the middle of a thoroughfare, which is
    the whole difference §4.3 draws between an address index and a street list.

    Written to stderr below the floor so that it survives a run over 332 cities,
    where a line of standard output scrolls past unread.
    """
    if streets <= 0:
        return
    ratio = numbers / streets
    print(f"Numbers/street  : {ratio:.2f}")
    if ratio >= HOUSE_NUMBERS_PER_STREET_FLOOR:
        return
    print(
        f"\nWarning: {ratio:.2f} house numbers per street, under the floor of "
        f"{HOUSE_NUMBERS_PER_STREET_FLOOR:.2f}.\n"
        f"         The address base carries almost no numbers over this box, so "
        f"nearly every\n"
        f"         search will land on the street's representative point rather "
        f"than on a\n"
        f"         doorstep. The application drops a number it cannot resolve "
        f"rather than\n"
        f"         placing it wrongly, so nothing is broken here and the index "
        f"is worth\n"
        f"         publishing as it is (SPEC.md §4.3).",
        file=sys.stderr,
    )


def write_normalization_fixtures(normalizer: AddressNormalizer,
                                 streets: list[tuple[str, Street]],
                                 network_id: str) -> Path:
    """Record normalisation results for the Kotlin test to reproduce.

    The Python script and the Android application apply the same rule file, but
    nothing would catch a difference in how each one *applies* it. These cases,
    replayed by a unit test, do.

    One file per network: generating a city must add coverage, never replace
    another city's. Producers spell street names differently, and that variety
    is exactly what makes the check worth running.
    """
    # A slice of real names as well, so the fixture covers what the data
    # actually contains rather than only what we thought to imagine. The stride
    # follows the corpus: a fixed one was cut for the half-million rows of a
    # French department and left a city read from OpenStreetMap — a twentieth
    # of that — with two samples.
    stride = max(1, len(streets) // SAMPLED_STREET_NAMES)
    sampled = [street.display_name for _, street in streets[::stride]][:SAMPLED_STREET_NAMES]

    cases = []
    for raw in normalizer.reference_names + sampled:
        split = normalizer.analyse(raw)
        cases.append({
            "input": raw,
            "normalized": normalizer.normalize(raw),
            "type": split.street_type,
            "name": split.proper_name,
        })

    # In :core, where the Kotlin normaliser lives: the search logic is pure
    # Kotlin, testable on the JVM without an emulator (SPEC §14).
    destination = (REPO_ROOT / "core" / "src" / "test" / "resources"
                   / "normalization_fixtures" / f"{network_id}.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps({"language": normalizer.language, "cases": cases},
                   ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return destination


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ban-csv", type=Path, action="append", default=[],
                        help="departmental BAN extract (.csv or .csv.gz), "
                             "repeatable. France only: elsewhere the addresses "
                             "come from the OSM extract (§15)")
    parser.add_argument("--osm-extract", type=Path, default=None,
                        help="OSM extract, for the landmarks and — where no "
                             "national address base is given — for the "
                             "streets and house numbers themselves (§4.3)")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        require_fts4()
        config = CityConfig.load(arguments.config)
        box = config.bounding_box
        # The rules of the language the city's address base is written in
        # (\u00a715.1). The configuration names it; what is retained goes into the
        # index, so the search can never apply another language's.
        normalizer = normalizer_for(config.default_language)
        started = time.monotonic()
        print(f"Emprise : {box}\n")

        for path in arguments.ban_csv:
            if not path.exists():
                raise GenerationError(f"BAN extract not found: {path}")
        if not arguments.ban_csv and arguments.osm_extract is None:
            raise GenerationError(
                "Nothing to read addresses from: pass --ban-csv for a French "
                "conurbation, or --osm-extract for any other (§15)."
            )

        municipalities: list[Street] = []
        if arguments.ban_csv:
            print("[1/3] Reading the BAN extracts…")
            streets, rows_read, rows_kept = read_ban_files(
                arguments.ban_csv, box, normalizer
            )
            print(f"      {rows_read} rows read, {rows_kept} inside the box, "
                  f"{len(streets)} streets")
        else:
            # No national address base for this country: the addresses are read
            # from the extract the map is already cut from (§15).
            print("[1/3] Reading the addresses of the OpenStreetMap extract…")
            with tempfile.TemporaryDirectory() as work:
                streets, rows_read, rows_kept = read_osm_addresses(
                    arguments.osm_extract, box, Path(work), normalizer
                )
                municipalities = read_osm_municipalities(
                    arguments.osm_extract, box, Path(work)
                )
            named = attach_municipalities(streets, municipalities)
            numbered = sum(len(street.numbers) for street in streets.values())
            print(f"      {rows_read} objects read, {rows_kept} inside the box, "
                  f"{len(streets)} streets, {numbered} house numbers")
            print(f"      {named} streets named after the nearest of "
                  f"{len(municipalities)} municipalities")

        place_count = 0
        if arguments.osm_extract is not None:
            print("[2/3] Extracting the OpenStreetMap landmarks…")
            with tempfile.TemporaryDirectory() as work:
                places = read_osm_places(arguments.osm_extract, box, Path(work))
            # The attachment happens before the merge: the grid must hold
            # streets only, not the landmarks we are trying to place.
            filled = fill_missing_places_communes(streets, places)
            for index, place in enumerate(places):
                streets[f"osm|{index}"] = place
            place_count = len(places)
            print(f"      {place_count} landmarks, "
                  f"{filled} attached to a neighbouring municipality")
        else:
            print("[2/3] Landmarks skipped (--osm-extract not given)")

        print("[3/3] Writing the database…")
        ordered = sorted(streets.items())
        generated_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        counts = build_database(
            arguments.output, ordered, normalizer, config, generated_at
        )
        fixtures = write_normalization_fixtures(normalizer, ordered, config.network_id)

        size = arguments.output.stat().st_size
        elapsed = time.monotonic() - started
        print(f"\n{'':=<60}")
        print(f"Index produced  : {arguments.output}")
        print(f"Size            : {size / 1e6:.1f} MB")
        print(f"Streets         : {counts['streets'] - place_count}")
        print(f"Landmarks       : {place_count}")
        print(f"House numbers   : {counts['numbers']}")
        # Streets only: a landmark carries no number by construction, and
        # counting it in the denominator would blame the wrong thing.
        report_house_number_coverage(counts["streets"] - place_count, counts["numbers"])
        print(f"Duration        : {elapsed / 60:.1f} min")
        if counts["oversized_deltas"]:
            print(f"Deltas beyond 16 bits: {counts['oversized_deltas']} "
                  f"(stored as they are, at a cost of a few bytes)")
        print(f"{'':=<60}")
        print(f"Normalisation reference cases: {fixtures}")
        return 0

    except GenerationError as error:
        print(f"\nError: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
