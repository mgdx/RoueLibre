#!/usr/bin/env python3
"""Copy each release's notes to the version codes F-Droid actually serves.

F-Droid shows a version's release notes by reading
``changelogs/<versionCode>.txt`` under the **exact** code of the APK it
serves, and falls back on nothing. This project publishes one APK per
architecture, each under its own code — ten times the base plus the
architecture's rank (``app/build.gradle.kts``) — while the notes are written
once, under the base: the what's-new screen keys on
``BuildConfig.VERSION_CODE``, which stays at the base (SPEC.md §7.10), and a
release publishes one set of notes, not four.

Both readers are right, so both get their file. The base copy remains the one
written by hand and the one the application reads; this script derives the
per-architecture copies from it — real files, not symbolic links, because
nothing guarantees ``fdroid update`` follows a link. Run it again after
writing a release's notes: it is idempotent, refreshes a copy whose source
moved, and ``--check`` fails instead of writing, for whoever wants the
verification without the side effect.

    python3 tools/expand_changelogs.py            # writes what is missing or stale
    python3 tools/expand_changelogs.py --check    # exit 1 instead of writing
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Sequence

FASTLANE = Path(__file__).resolve().parent.parent / "fastlane" / "metadata" / "android"
GRADLE_BUILD_FILE = Path(__file__).resolve().parent.parent / "app" / "build.gradle.kts"

# The first release published as one APK per architecture, 1.0.0. The bases
# below it are the alphas, published as a single universal APK whose code was
# the base itself: no derived code ever existed for them, so their notes —
# still present in the twenty-odd started translations — have nothing to
# expand to.
FIRST_SPLIT_BASE = 4

# One entry of the `architectureVersionCodes` map, e.g. `"arm64-v8a" to 4`.
RANK_ENTRY = re.compile(r'"(?P<abi>[A-Za-z0-9_-]+)" to (?P<rank>\d+)')


def architecture_ranks(build_file: Path) -> list[int]:
    """The unit each architecture adds to ten times the base, read at its source.

    ``app/build.gradle.kts`` is where the derivation is defined and justified;
    reading it from there keeps the two in step, so an architecture added to
    the build is a copy gained here without this script being remembered.
    """
    text = build_file.read_text(encoding="utf-8")
    body = re.search(r"val architectureVersionCodes = mapOf\((?P<body>[^)]*)\)", text)
    if body is None:
        raise SystemExit(f"{build_file}: architectureVersionCodes not found")
    ranks = [int(entry.group("rank")) for entry in RANK_ENTRY.finditer(body.group("body"))]
    if not ranks:
        raise SystemExit(f"{build_file}: architectureVersionCodes holds no entry")
    return ranks


def is_derived(changelogs: Path, code: int, ranks: Sequence[int]) -> bool:
    """Whether ``code`` is itself one of the copies this script writes.

    A derived code ends in an architecture's rank and sits next to the base
    file it came from — which is also what keeps a copy from being expanded
    in its turn. The putative base must itself be one this script expands:
    base 14 next to the alpha ``1.txt`` is a base of its own, not a copy of
    1, because nothing ever derives from the bases below the split.
    """
    return (
        code // 10 >= FIRST_SPLIT_BASE
        and code % 10 in ranks
        and (changelogs / f"{code // 10}.txt").is_file()
    )


def stale_copies(changelogs: Path, ranks: Sequence[int]) -> list[tuple[Path, str]]:
    """Every derived file missing or differing, with the text it should hold."""
    found: list[tuple[Path, str]] = []
    for source in sorted(changelogs.glob("*.txt")):
        if not source.stem.isdigit():
            continue
        base = int(source.stem)
        if base < FIRST_SPLIT_BASE or is_derived(changelogs, base, ranks):
            continue
        text = source.read_text(encoding="utf-8")
        for rank in ranks:
            derived = changelogs / f"{base * 10 + rank}.txt"
            if not derived.is_file() or derived.read_text(encoding="utf-8") != text:
                found.append((derived, text))
    return found


def main() -> int:
    check = "--check" in sys.argv[1:]
    ranks = architecture_ranks(GRADLE_BUILD_FILE)
    problems = 0
    for changelogs in sorted(FASTLANE.glob("*/changelogs")):
        for derived, text in stale_copies(changelogs, ranks):
            problems += 1
            relative = derived.relative_to(FASTLANE)
            if check:
                print(f"{relative}: missing or stale")
            else:
                derived.write_text(text, encoding="utf-8")
                print(f"{relative}: written")
    if check and problems:
        print(
            f"{problems} derived note(s) missing or stale — "
            "run python3 tools/expand_changelogs.py",
            file=sys.stderr,
        )
        return 1
    if not problems:
        print("every derived note is current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
