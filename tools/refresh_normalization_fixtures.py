#!/usr/bin/env python3
"""Recompute the normalisation reference cases the Kotlin test replays.

`tools/build_address_index.py` writes one file of reference cases per network
generated: raw street names, and what the Python normaliser made of them. A
unit test replays them against the Kotlin normaliser, which is what proves the
two implementations read `config/address-normalization/<language>.json` the
same way.

When those rules change, the expected results change with them. Rebuilding
every index to refresh a few hundred lines would mean downloading the address
base of every conurbation served, so this script recomputes them in place
instead: the raw names are kept, only the results are recomputed.

It also keeps one file per language written down — `reference-<language>.json`
— built from the `referenceNames` of the rules themselves. Without it, a
language would go untested until somebody generated the data of a city that
speaks it, which is to say for a long time: the vocabulary of Polish or Greek
streets deserves to be checked from the day it is written.

Names written out by hand are added to every file that lacks them, so that a
rule written for one region is covered from the moment it is written rather
than from the next generation run. Sampled real names it never invents: only
`build_address_index.py` can add those, since only it has the address base.

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

from address_normalization import available_languages, normalizer_for

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
FIXTURES_DIR = (
    REPO_ROOT / "core" / "src" / "test" / "resources" / "normalization_fixtures"
)


def refresh(path: Path, check_only: bool) -> int:
    """Recompute one file of reference cases; return the number that moved."""
    document = json.loads(path.read_text(encoding="utf-8"))
    # A file states the language it was generated in; the ones written before
    # there was more than one language are French, which is what those three
    # conurbations are.
    language = document.get("language", "fr")
    normalizer = normalizer_for(language)
    stored = {case["input"]: case for case in document["cases"]}

    # The written-out names first, in their own order, then everything the
    # generation run sampled — the order `build_address_index.py` writes.
    written_out = normalizer.reference_names
    inputs = written_out + [
        case["input"] for case in document["cases"]
        if case["input"] not in set(written_out)
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

    if (moved or document.get("language") != language) and not check_only:
        path.write_text(
            json.dumps({"language": language, "cases": cases},
                       ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return moved


def refresh_language_references(fixtures_dir: Path, check_only: bool) -> int:
    """Keep one file of written-out cases per language whose rules exist.

    A language with no city generated yet would otherwise be tested by nothing
    at all: its file of rules could contradict the Kotlin reader for months
    without a single test failing.
    """
    moved = 0
    for language in available_languages():
        normalizer = normalizer_for(language)
        if not normalizer.reference_names:
            continue
        path = fixtures_dir / f"reference-{language}.json"
        if not path.is_file():
            if check_only:
                print(f"{path.name}: absent")
                moved += 1
                continue
            path.write_text(
                json.dumps({"language": language, "cases": []},
                           ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
        print(f"{path.name}:")
        moved += refresh(path, check_only)
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
    if not arguments.fixtures_dir.is_dir():
        print(f"No reference cases in {arguments.fixtures_dir}", file=sys.stderr)
        return 1

    total = refresh_language_references(arguments.fixtures_dir, arguments.check)
    for path in sorted(arguments.fixtures_dir.glob("*.json")):
        if path.name.startswith("reference-"):
            continue
        print(f"{path.name}:")
        total += refresh(path, arguments.check)

    files = sorted(arguments.fixtures_dir.glob("*.json"))
    verb = "would change" if arguments.check else "recomputed"
    print(f"\n{total} case(s) {verb}, across {len(files)} files")
    return 1 if (arguments.check and total) else 0


if __name__ == "__main__":
    sys.exit(main())
