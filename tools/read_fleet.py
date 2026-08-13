#!/usr/bin/env python3
"""Read what a network lends from its own feed (SPEC.md §15).

A network lending pedal-assist bikes is a fact about the city, not about the
application: the interface marks its bike glyph with a bolt (§7), and the flag
that decides it belongs in the city configuration like every other city-specific
value. It is never guessed and never typed by hand — it is read from the GBFS
`vehicle_types` feed, whose `propulsion_type` says it plainly.

A feed publishing no `vehicle_types` — GBFS 1.0 has no such feed — leaves the
configuration untouched: saying nothing is the honest answer, and the
application then draws the plain bike rather than promising a motor.

Usage:
    python3 tools/read_fleet.py --all
    python3 tools/read_fleet.py --config config/cities/lille.json
    python3 tools/read_fleet.py --all --dry-run
"""

from __future__ import annotations

import argparse
import sys
from concurrent.futures import ThreadPoolExecutor
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
# networks on. A network's electric SCOOTERS say nothing about its bikes.
BICYCLE_FORM_FACTORS = frozenset({"bicycle", "cargo_bicycle"})

# GBFS propulsion values that mean a motor helps the rider. "electric_assist" is
# the pedal-assist bike this is about; "electric" is a throttle vehicle, which
# on a bicycle form factor is still a bike one does not pedal alone. Everything
# else — "human", "combustion" — is not.
ELECTRIC_PROPULSIONS = frozenset({"electric_assist", "electric"})


def declares_electric_bikes(vehicle_types: list[dict]) -> bool:
    """True if the declared fleet holds at least one pedal-assist bike.

    A mixed fleet counts: a city lending both mechanical and electric bikes
    does lend electric bikes, and the bolt says exactly that. The station
    markers keep counting whole stations, which is what the user borrows from.
    """
    return any(
        kind.get("form_factor") in BICYCLE_FORM_FACTORS
        and kind.get("propulsion_type") in ELECTRIC_PROPULSIONS
        for kind in vehicle_types
    )


def read_fleet(discovery_url: str) -> bool | None:
    """Ask a network what it lends.

    Returns:
        whether the fleet holds pedal-assist bikes, or `None` if the feed
        declares no vehicle type — in which case there is nothing to record.

    Raises:
        urllib.error.URLError: on any network or HTTP failure.
        KeyError: if the auto-discovery document cannot be read.
    """
    discovery = fetch_json(discovery_url)
    try:
        types_url = resolve_feed_url(discovery, "vehicle_types")
    except KeyError:
        return None
    document = fetch_json(types_url)
    vehicle_types = document.get("data", {}).get("vehicle_types")
    if not vehicle_types:
        return None
    return declares_electric_bikes(vehicle_types)


def survey(path: Path) -> tuple[Path, bool | None, str | None]:
    """Read one city's fleet, reporting a failure rather than raising it.

    One unreachable feed out of three hundred must not stop the sweep: the
    others are read, and what could not be is named at the end.
    """
    try:
        config = CityConfig.load(path)
        return path, read_fleet(config.gbfs_discovery_url), None
    except Exception as error:  # noqa: BLE001 — every failure is reportable
        return path, None, f"{type(error).__name__}: {error}"


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
        help="show what the feeds declare without writing anything",
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

    print(f"Calling {len(paths)} feeds…")
    with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as pool:
        results = list(pool.map(survey, paths))

    surveyed_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    electric = mechanical = silent = 0
    changed = 0
    failures: list[tuple[Path, str]] = []

    for path, has_electric, error in results:
        if error is not None:
            failures.append((path, error))
            continue
        if has_electric is None:
            silent += 1
            print(f"  ? {path.stem:<28} the feed declares no vehicle type")
            continue
        if has_electric:
            electric += 1
        else:
            mechanical += 1
        config = CityConfig.load(path)
        if not config.update_fleet(
            has_electric_bikes=has_electric,
            surveyed_at=surveyed_at,
        ):
            continue
        changed += 1
        mark = "⚡" if has_electric else "·"
        print(f"  {mark} {path.stem:<28} {'pedal-assist' if has_electric else 'mechanical'}")
        if not arguments.dry_run:
            config.save()

    print()
    print(f"Pedal-assist fleets   : {electric}")
    print(f"Mechanical fleets     : {mechanical}")
    print(f"Feeds declaring none  : {silent}")
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
