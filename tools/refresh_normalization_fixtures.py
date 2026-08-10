#!/usr/bin/env python3
"""Recompute the normalisation reference cases the Kotlin test replays.

`tools/build_address_index.py` writes one file of reference cases per network
generated: raw street names, and what the Python normaliser made of them. A
unit test replays them against the Kotlin normaliser, which is what proves the
two implementations read `config/address_normalization.json` the same way.

When those rules change, the expected results change with them. Rebuilding
every index to refresh a few hundred lines would mean downloading the address
base of every department served, so this script recomputes them in place
instead: the raw names are kept, only the results are recomputed.

Names written out by hand — `REFERENCE_STREET_NAMES` — are added to every file
that lacks them, so that a rule written for one region is covered from the
moment it is written rather than from the next generation run. Sampled real
names it never invents: only `build_address_index.py` can add those, since only
it has the address base.

Run it after editing the rules, and read the diff: a case that moves is a
street whose split has changed, and that is worth a look.

Usage:
    python3 tools/refresh_normalization_fixtures.py [--check]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from address_normalization import REFERENCE_STREET_NAMES, default_normalizer

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
FIXTURES_DIR = (
    REPO_ROOT / "core" / "src" / "test" / "resources" / "normalization_fixtures"
)


def refresh(path: Path, check_only: bool) -> int:
    """Recompute one file of reference cases; return the number that moved."""
    normalizer = default_normalizer()
    document = json.loads(path.read_text(encoding="utf-8"))
    stored = {case["input"]: case for case in document["cases"]}

    # The written-out names first, in their own order, then everything the
    # generation run sampled — the order `build_address_index.py` writes.
    inputs = REFERENCE_STREET_NAMES + [
        case["input"] for case in document["cases"]
        if case["input"] not in set(REFERENCE_STREET_NAMES)
    ]

    moved = 0
    cases = []
    for raw in inputs:
        split = normalizer.analyse(raw)
        recomputed = {
            "input": raw,
            "normalized": normalizer.normalize(raw),
            "type": split.street_type,
            "name": split.proper_name,
        }
        previous = stored.get(raw)
        if previous is None:
            moved += 1
            print(f"  + {raw}  →  type={recomputed['type']!r} "
                  f"name={recomputed['name']!r}")
        elif recomputed != previous:
            moved += 1
            print(f"  {raw}")
            print(f"      was  type={previous['type']!r} name={previous['name']!r}")
            print(f"      now  type={recomputed['type']!r} name={recomputed['name']!r}")
        cases.append(recomputed)

    if moved and not check_only:
        path.write_text(
            json.dumps({"cases": cases}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return moved


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures-dir", type=Path, default=FIXTURES_DIR)
    parser.add_argument(
        "--check",
        action="store_true",
        help="report what would change and write nothing; fails if anything would",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    files = sorted(arguments.fixtures_dir.glob("*.json"))
    if not files:
        print(f"No reference cases in {arguments.fixtures_dir}", file=sys.stderr)
        return 1

    total = 0
    for path in files:
        print(f"{path.name}:")
        total += refresh(path, arguments.check)

    verb = "would change" if arguments.check else "recomputed"
    print(f"\n{total} case(s) {verb}, across {len(files)} networks")
    return 1 if (arguments.check and total) else 0


if __name__ == "__main__":
    sys.exit(main())
