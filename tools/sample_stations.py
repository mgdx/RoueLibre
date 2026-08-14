#!/usr/bin/env python3
"""Record where a network's stations are, in a handful of points (SPEC.md §15.1).

The application proposes the network of the conurbation one happens to be in.
Until now it measured that on the reference box, and a rectangle answers badly
for a network that serves a whole region: Vélo Fluo puts one station in each
town of the Grand Est, 261 km by 327, so its box passes 46 km from the middle
of the Morvan while its nearest bike is 130 km away. Someone standing there was
offered 1.4 GB of map for a station they could not walk to.

So the configuration carries a few station positions, spread through the
network, and proximity is measured on those. Eight is enough: they are taken at
regular intervals through the list, so a network stretched over a region is
described along its whole length, and the distance read from them is an upper
bound of the real one — it never flatters a network whose bikes are far away.

The positions come from the network's own feed, like the box they share, and
the strays are dropped by the same rule (§4): a station at latitude zero, or
one standing 25 km from every other, would put a bike where there is none.

Usage:
    python3 tools/sample_stations.py --all
    python3 tools/sample_stations.py --config config/cities/lille.json
    python3 tools/sample_stations.py --all --dry-run
"""

from __future__ import annotations

import argparse
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from city_config import DEFAULT_CITY_CONFIG, CityConfig, sample_positions
from compute_bbox import load_stations, positioned_stations

REPO_ROOT = Path(__file__).resolve().parent.parent
CITIES_DIRECTORY = REPO_ROOT / "config" / "cities"

# The same figure discover_networks.py settled on for its own calls: enough to
# read three hundred feeds in a couple of minutes without any one producer
# seeing a burst.
CONCURRENT_REQUESTS = 24


def survey(path: Path) -> tuple[Path, list[list[float]] | None, str | None]:
    """Read one city's stations, reporting a failure rather than raising it.

    One unreachable feed out of three hundred must not stop the sweep: the
    others are read, and what could not be is named at the end.
    """
    try:
        config = CityConfig.load(path)
        stations = positioned_stations(load_stations(config.gbfs_discovery_url, None))
        return path, sample_positions(stations), None
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
        help="show what the feeds say without writing anything",
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

    changed = unchanged = 0
    failures: list[tuple[Path, str]] = []
    for path, samples, error in results:
        if error is not None:
            failures.append((path, error))
            continue
        config = CityConfig.load(path)
        if not config.update_station_samples(samples):
            unchanged += 1
            continue
        changed += 1
        print(f"  + {path.stem:<30} {len(samples)} positions")
        if not arguments.dry_run:
            config.save()

    print()
    print(f"Configurations updated : {changed}")
    print(f"Already up to date     : {unchanged}")
    if failures:
        print(f"Unreachable feeds      : {len(failures)}")
        for path, error in failures:
            print(f"  ! {path.stem:<30} {error}")
    if arguments.dry_run:
        print("\n--dry-run: nothing written.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
