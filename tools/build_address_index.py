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

from address_normalization import AddressNormalizer
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
    if not raw_number.isdigit():
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
            ("normalizationRulesVersion", str(
                json.loads(
                    (REPO_ROOT / "config" / "address_normalization.json")
                    .read_text(encoding="utf-8")
                )["rulesVersion"]
            )),
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
    handpicked = [
        "Rue Gambetta", "Boulevard de la Liberté", "Av. des Flandres",
        "Bd Victor Hugo", "R. Nationale", "St-André", "Rue de l'Hôpital Militaire",
        "Place du Général de Gaulle", "Chemin des Écoliers", "Grand Place",
        "Rond-Point de l'Europe", "Impasse Sainte-Cécile", "Drève du Château",
        "rue jean-baptiste lebas", "FAUBOURG DE ROUBAIX", "Allée Père Damien",
    ]
    # A slice of real names as well, so the fixture covers what the data
    # actually contains rather than only what we thought to imagine.
    sampled = [street.display_name for _, street in streets[::997]][:120]

    cases = []
    for raw in handpicked + sampled:
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
        json.dumps({"cases": cases}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return destination


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ban-csv", type=Path, action="append", required=True,
                        help="departmental BAN extract (.csv or .csv.gz), "
                             "repeatable")
    parser.add_argument("--osm-extract", type=Path, default=None,
                        help="OSM extract for the landmarks (§4.3)")
    parser.add_argument("--config", type=Path, default=DEFAULT_CITY_CONFIG)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        require_fts4()
        config = CityConfig.load(arguments.config)
        box = config.bounding_box
        normalizer = AddressNormalizer.load()
        started = time.monotonic()
        print(f"Emprise : {box}\n")

        for path in arguments.ban_csv:
            if not path.exists():
                raise GenerationError(f"BAN extract not found: {path}")

        print("[1/3] Reading the BAN extracts…")
        streets, rows_read, rows_kept = read_ban_files(
            arguments.ban_csv, box, normalizer
        )
        print(f"      {rows_read} rows read, {rows_kept} inside the box, "
              f"{len(streets)} streets")

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
