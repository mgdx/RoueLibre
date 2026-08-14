#!/usr/bin/env python3
"""Count what a network really lends, from its own feeds (SPEC.md §15).

A network lending pedal-assist bikes is a fact about the city, not about the
application: the interface marks its bike glyph with a bolt (§7), and a network
lending both kinds side by side lets the station sheet split its count into
mechanical and electric (§7.2). Both belong in the city configuration like
every other city-specific value, and neither is ever typed by hand.

What this script writes is the **seed** of that answer. The application counts
again from the live feeds on every refresh (§4.1), so a network that changes
between two releases needs no new survey; the seed is what answers on a first
launch, on a launch with no connection, and where the feeds let nothing be
counted. The two countings must agree — `core/station/FleetReading.kt` is the
same rules in Kotlin, and a change made to one belongs in the other.

Neither is read from the `vehicle_types` declaration alone either, and that is
the whole point of this script. A survey of the three hundred and thirty-three
configured networks found that **a third of those declaring a mixed fleet have
not one bike of one of the two kinds in circulation**: Madrid declares a
mechanical type and puts out 5857 electric bikes and no mechanical one, Berlin
declares an electric type and puts out 1971 mechanical bikes and no electric
one. The declaration says what the operator may lend one day; the status feed
says what is at the stations now, and that is what the user walks to.

So the bikes are counted, station by station, from `station_status`:

- `vehicle_types_available`, the standard breakdown since GBFS 2.1, whose
  identifiers `vehicle_types` gives the propulsion of;
- `num_bikes_available_types`, the extension Vélib' Métropole publishes for
  want of a `vehicle_types` feed — it is on GBFS 1.0, and its 7854 electric
  bikes would otherwise be invisible.

A network whose feeds let nothing be counted — no breakdown published, or every
station empty at that moment — keeps whatever its declaration says and is never
called mixed: saying less is better than splitting a count nobody verified.

Usage:
    python3 tools/read_fleet.py --all
    python3 tools/read_fleet.py --config config/cities/lille.json
    python3 tools/read_fleet.py --all --dry-run
"""

from __future__ import annotations

import argparse
import sys
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from city_config import DEFAULT_CITY_CONFIG, CityConfig
from compute_bbox import fetch_json, resolve_feed_url

REPO_ROOT = Path(__file__).resolve().parent.parent
CITIES_DIRECTORY = REPO_ROOT / "config" / "cities"

# Enough to read three hundred feeds in a couple of minutes without any one
# producer seeing a burst: the same figure discover_networks.py settled on.
CONCURRENT_REQUESTS = 24

# The vehicle forms this application is about, the same set the survey filters
# networks on. A network's electric SCOOTERS say nothing about its bikes, and
# they are counted in the status feed alongside them.
BICYCLE_FORM_FACTORS = frozenset({"bicycle", "cargo_bicycle"})

# GBFS propulsion values that mean a motor helps the rider. "electric_assist"
# is the pedal-assist bike this is about; "electric" is a throttle vehicle,
# which on a bicycle form factor is still a bike one does not pedal alone.
# Everything else — "human", "combustion" — is not.
ELECTRIC_PROPULSIONS = frozenset({"electric_assist", "electric"})

# The three kinds an identifier can be sorted into, as written in the
# configuration. "other" is a scooter or a moped: known, and deliberately left
# out of the bike counts rather than mistaken for an unreadable identifier.
MECHANICAL = "mechanical"
ELECTRIC = "electric"
OTHER = "other"

# The keys of the Vélib' extension, which carries no vehicle type identifier.
# Naming them here is what lets the application read that feed through the very
# same table as every other network, with no special case in the code.
VELIB_KINDS = {"mechanical": MECHANICAL, "ebike": ELECTRIC}

# Below this share of the bikes counted, a kind is a residue rather than an
# offer, and announcing a mixed fleet would promise something the user will
# never find: Barcelona puts out 1922 electric bikes and 2 mechanical ones,
# Mannheim 2258 mechanical and 10 electric. Two percent still keeps the
# smallest genuine mixed fleets — one electric bike out of twenty counts.
MINORITY_SHARE = 0.02


@dataclass
class FleetReading:
    """What one network's feeds say, once counted."""

    # The kind of every identifier the status feed may count by.
    vehicle_types: dict[str, str] = field(default_factory=dict)
    # Whether the vehicle_types feed declares a pedal-assist bicycle, which is
    # all there is to go on when nothing could be counted.
    declares_electric: bool = False
    bikes_seen: dict[str, int] = field(default_factory=dict)

    @property
    def counted(self) -> int:
        return sum(self.bikes_seen.values())

    @property
    def has_electric_bikes(self) -> bool:
        """Whether pedal-assist bikes are in circulation.

        Falls back on the declaration when nothing could be counted: a feed
        publishing no breakdown, or a network whose every station is empty at
        that moment, must not turn an electric city into a mechanical one.
        """
        if self.counted == 0:
            return self.declares_electric
        return self.bikes_seen.get(ELECTRIC, 0) > 0

    @property
    def is_mixed(self) -> bool:
        """Whether both kinds are out, in numbers that make an offer.

        Never true on a declaration alone: a split shown to the user is a
        promise about what stands at the station, and only a count can make it.
        """
        electric = self.bikes_seen.get(ELECTRIC, 0)
        mechanical = self.bikes_seen.get(MECHANICAL, 0)
        if electric == 0 or mechanical == 0:
            return False
        return min(electric, mechanical) / (electric + mechanical) >= MINORITY_SHARE


def read_vehicle_types(discovery: dict) -> tuple[dict[str, str], bool]:
    """Sort the declared vehicle types into the three kinds.

    Returns:
        the kind of every declared identifier, and whether a pedal-assist
        bicycle is among them.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
    """
    try:
        types_url = resolve_feed_url(discovery, "vehicle_types")
    except KeyError:
        # GBFS 1.0 has no such feed. Vélib' Métropole is there, and its own
        # extension is read from the status feed instead.
        return {}, False
    document = fetch_json(types_url)
    kinds: dict[str, str] = {}
    for declared in document.get("data", {}).get("vehicle_types") or []:
        identifier = str(declared.get("vehicle_type_id"))
        if declared.get("form_factor") not in BICYCLE_FORM_FACTORS:
            kinds[identifier] = OTHER
            continue
        electric = declared.get("propulsion_type") in ELECTRIC_PROPULSIONS
        kinds[identifier] = ELECTRIC if electric else MECHANICAL
    return kinds, ELECTRIC in kinds.values()


def count_bikes(discovery: dict, kinds: dict[str, str]) -> dict[str, int]:
    """Count the bikes actually available, by kind, over the whole network.

    Identifiers absent from the declaration are ignored rather than guessed:
    five networks publish at their stations a type they never declared, and a
    bike of unknown propulsion belongs in neither column.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
        KeyError: if the auto-discovery document publishes no station_status.
    """
    document = fetch_json(resolve_feed_url(discovery, "station_status"))
    seen = {MECHANICAL: 0, ELECTRIC: 0}
    for station in document.get("data", {}).get("stations") or []:
        for entry in station.get("vehicle_types_available") or []:
            kind = kinds.get(str(entry.get("vehicle_type_id")))
            if kind in seen:
                seen[kind] += entry.get("count") or 0
        for entry in station.get("num_bikes_available_types") or []:
            for name, count in entry.items():
                kind = VELIB_KINDS.get(name)
                if kind in seen:
                    seen[kind] += count or 0
    return {kind: count for kind, count in seen.items() if count > 0}


def read_fleet(discovery_url: str) -> FleetReading:
    """Ask a network what it lends, and count what it has out.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
        KeyError: if the auto-discovery document cannot be read.
    """
    discovery = fetch_json(discovery_url)
    kinds, declares_electric = read_vehicle_types(discovery)
    bikes = count_bikes(discovery, kinds)
    # The Vélib' keys earn their place in the table only once seen in the
    # feed: writing them everywhere would suggest a breakdown that no other
    # network publishes under those names.
    if any(name in bikes for name in (MECHANICAL, ELECTRIC)) and not kinds:
        kinds = dict(VELIB_KINDS)
    return FleetReading(
        vehicle_types=kinds,
        declares_electric=declares_electric,
        bikes_seen=bikes,
    )


def survey(path: Path) -> tuple[Path, FleetReading | None, str | None]:
    """Read one city's fleet, reporting a failure rather than raising it.

    One unreachable feed out of three hundred must not stop the sweep: the
    others are read, and what could not be is named at the end.
    """
    try:
        config = CityConfig.load(path)
        return path, read_fleet(config.gbfs_discovery_url), None
    except Exception as error:  # noqa: BLE001 — every failure is reportable
        return path, None, f"{type(error).__name__}: {error}"


def describe(reading: FleetReading) -> str:
    """One line saying what was counted, for the run's log."""
    if reading.counted == 0:
        return "nothing out to count, declaration kept"
    parts = [f"{count} {kind}" for kind, count in sorted(reading.bikes_seen.items())]
    return " + ".join(parts)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CITY_CONFIG,
        help="city configuration file to update",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="update every configuration in config/cities/",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="show what the feeds hold without writing anything",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    paths = (
        sorted(CITIES_DIRECTORY.glob("*.json")) if arguments.all else [arguments.config]
    )
    if not paths:
        print(f"No configuration in {CITIES_DIRECTORY}.", file=sys.stderr)
        return 1

    print(f"Calling {len(paths)} networks…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        results = list(pool.map(survey, paths))

    surveyed_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    electric = mechanical = mixed = uncounted = 0
    changed = 0
    failures: list[tuple[Path, str]] = []

    for path, reading, error in results:
        if reading is None:
            failures.append((path, error or "unknown failure"))
            continue
        if reading.counted == 0:
            uncounted += 1
        if reading.is_mixed:
            mixed += 1
        elif reading.has_electric_bikes:
            electric += 1
        else:
            mechanical += 1
        config = CityConfig.load(path)
        if not config.update_fleet(
            has_electric_bikes=reading.has_electric_bikes,
            is_mixed=reading.is_mixed,
            vehicle_types=reading.vehicle_types,
            bikes_seen=reading.bikes_seen,
            surveyed_at=surveyed_at,
        ):
            continue
        changed += 1
        mark = "⚡" if reading.has_electric_bikes else "·"
        if reading.is_mixed:
            mark = "⚡·"
        print(f"  {mark:<3} {path.stem:<28} {describe(reading)}")
        if not arguments.dry_run:
            config.save()

    print()
    print(f"Mixed fleets          : {mixed}")
    print(f"Pedal-assist only     : {electric}")
    print(f"Mechanical only       : {mechanical}")
    print(f"Nothing countable     : {uncounted}")
    print(f"Configurations changed: {changed}")
    if failures:
        print(f"Unreachable feeds     : {len(failures)}")
        for path, error in failures:
            print(f"  ! {path.stem:<28} {error}")
    if arguments.dry_run:
        print("\n--dry-run: nothing written.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
