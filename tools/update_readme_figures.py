#!/usr/bin/env python3
"""Re-derive the figures quoted in README.md and docs/offline-data.md.

Those figures — how many networks are served, in how many countries, how many
stations they hold, what a median city's data weighs, what the release APK
weighs — appear in several places each: a badge, a feature, a sentence in the
build instructions. Written by hand, the first network added leaves some of
those places lying, and nothing says which one is right.

So none of them is written by hand. Each has **one authoritative source**, and
this script copies it into every place that quotes it:

- the counts and the median dataset size come from `config/catalogue.json`,
  which `tools/build_catalogue.py` derives from the configurations and the
  published manifests;
- the APK sizes come from the release APKs themselves, under
  `app/build/outputs/apk/release/`, so the figure is the file's own size and
  not a memory of it. Without a release build on disk those figures are left
  alone, and the script says so rather than inventing them.

Megabytes and gigabytes are counted in powers of ten, as the application
counts them on its storage screen: a user comparing the two must not find two
different numbers for one file.

Usage:
    python3 tools/update_readme_figures.py             # rewrites the files
    python3 tools/update_readme_figures.py --check     # fails if any is stale
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CATALOGUE = REPO_ROOT / "config" / "catalogue.json"
RELEASE_APK_DIRECTORY = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "release"
README = REPO_ROOT / "README.md"
OFFLINE_DATA = REPO_ROOT / "docs" / "offline-data.md"

# The two architectures the README names. The others are built and published
# too; these are the ones a reader is told about, being the two that run on a
# phone bought this decade and the one that still runs on an older one.
NAMED_ARCHITECTURES = ("arm64-v8a", "armeabi-v7a")


class FigureError(RuntimeError):
    """A figure could not be re-derived, or its place could not be found."""


@dataclass(frozen=True)
class Figures:
    """Everything the documentation quotes, read from its own source."""

    networks: int
    countries: int
    stations: int
    median_dataset_bytes: int
    total_dataset_bytes: int
    # Empty when no release build is on disk: see the module docstring.
    apk_bytes: dict[str, int]


def read_catalogue() -> Figures:
    """Counts the catalogue the application ships and downloads."""
    document = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    cities = document["cities"]
    if not cities:
        raise FigureError(f"{CATALOGUE} names no city")
    sizes = [city["dataSizeBytes"] for city in cities if city.get("dataSizeBytes")]
    if not sizes:
        raise FigureError(f"{CATALOGUE} announces no dataset size")
    return Figures(
        networks=len(cities),
        countries=len({city["country"] for city in cities}),
        stations=sum(city["stationCount"] for city in cities),
        median_dataset_bytes=int(statistics.median(sizes)),
        total_dataset_bytes=sum(sizes),
        apk_bytes=read_release_apks(),
    )


def read_release_apks() -> dict[str, int]:
    """The size of each named architecture's release APK, if it is built."""
    sizes: dict[str, int] = {}
    for architecture in NAMED_ARCHITECTURES:
        apk = RELEASE_APK_DIRECTORY / f"app-{architecture}-release.apk"
        if apk.is_file():
            sizes[architecture] = apk.stat().st_size
    # All or nothing: half a set of figures would leave the README quoting one
    # release for one architecture and another for the other.
    return sizes if len(sizes) == len(NAMED_ARCHITECTURES) else {}


def megabytes(count: int, decimals: int = 1) -> str:
    """Bytes as the application writes them: powers of ten, one decimal."""
    return f"{count / 1_000_000:.{decimals}f}"


def gigabytes(count: int) -> str:
    """Bytes as gigabytes, for the corpus taken as a whole."""
    return f"{count / 1_000_000_000:.1f}"


def rewrite(text: str, pattern: str, replacement: str, where: str) -> str:
    """Applies one substitution, refusing to pass over a place it cannot find.

    A silent no-match is the one failure this script must not have: it would
    report success and leave the figure it was run to correct exactly as it
    was.
    """
    rewritten, count = re.subn(pattern, replacement, text)
    if count == 0:
        raise FigureError(f"nothing to rewrite in {where}: /{pattern}/")
    return rewritten


def updated_readme(text: str, figures: Figures) -> str:
    """Puts every figure back into README.md."""
    networks = str(figures.networks)
    # Station totals run into five digits and are grouped the way the
    # README already writes them; the network and country counts are
    # three digits at most and are not.
    stations = f"{figures.stations:,}"

    # The badge, alt text and shields.io address in one go: the two carry the
    # same figures and drift apart the day only one of them is edited.
    text = rewrite(
        text,
        r'<a href="docs/networks\.md"><img alt="[^"]*" src="https://img\.shields\.io/badge/'
        r'networks-[^"]*"></a>',
        f'<a href="docs/networks.md"><img alt="{networks} networks in '
        f'{figures.countries} countries" src="https://img.shields.io/badge/'
        f'networks-{networks}%20in%20{figures.countries}%20countries-'
        f'0F6E56?style=flat-square"></a>',
        "the networks badge",
    )
    text = rewrite(
        text,
        r"\*\*[\d,]+ bike-share networks in \d+ countries\*\*, [\d,]+ stations",
        f"**{networks} bike-share networks in {figures.countries} countries**, "
        f"{stations} stations",
        "the networks feature",
    )
    text = rewrite(
        text,
        # The sentence is wrapped by hand and the break may fall inside it:
        # the whitespace it fell on is put back as it was found.
        r"— [\d.]+ MB for a(\s+)median city —",
        f"— {megabytes(figures.median_dataset_bytes)} MB for a" + r"\1" + "median city —",
        "the median city's weight",
    )

    if not figures.apk_bytes:
        return text

    arm64 = figures.apk_bytes["arm64-v8a"]
    arm32 = figures.apk_bytes["armeabi-v7a"]
    text = rewrite(
        text,
        r'<img alt="APK: [\d.]+ MB" src="https://img\.shields\.io/badge/APK-[^"]*">',
        f'<img alt="APK: {megabytes(arm64)} MB" src="https://img.shields.io/badge/'
        f'APK-{megabytes(arm64)}%20MB-0F6E56?style=flat-square">',
        "the APK badge",
    )
    text = rewrite(
        text,
        r"\*\*Light and frugal\.\*\* [\d.]+ MB of APK",
        f"**Light and frugal.** {megabytes(arm64)} MB of APK",
        "the lightness feature",
    )
    text = rewrite(
        text,
        r"weighs \*\*[\d.]+ MB on arm64-v8a\*\* and [\d.]+ MB on armeabi-v7a",
        f"weighs **{megabytes(arm64, 2)} MB on arm64-v8a** and "
        f"{megabytes(arm32, 2)} MB on armeabi-v7a",
        "the build instructions",
    )
    return text


def updated_offline_data(text: str, figures: Figures) -> str:
    """Puts the corpus figures back into docs/offline-data.md."""
    return rewrite(
        text,
        r"\*\*All [\d,]+ networks served have their data produced: [\d.]+ GB in all\*\*,\n"
        r"median [\d.]+ MB a city\.",
        f"**All {figures.networks} networks served have their data produced: "
        f"{gigabytes(figures.total_dataset_bytes)} GB in all**,\n"
        f"median {megabytes(figures.median_dataset_bytes)} MB a city.",
        "the corpus figures",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="report stale figures without writing, and exit 1 if any is found",
    )
    arguments = parser.parse_args()

    try:
        figures = read_catalogue()
    except (OSError, KeyError, ValueError, FigureError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    if not figures.apk_bytes:
        print(
            "note: no release build under app/build/outputs/apk/release — "
            "the APK figures are left as they are. Run ./gradlew assembleRelease "
            "to refresh them.",
            file=sys.stderr,
        )

    stale = False
    for path, update in ((README, updated_readme), (OFFLINE_DATA, updated_offline_data)):
        before = path.read_text(encoding="utf-8")
        try:
            after = update(before, figures)
        except FigureError as error:
            print(f"error: {error}", file=sys.stderr)
            return 2
        if before == after:
            continue
        stale = True
        relative = path.relative_to(REPO_ROOT)
        if arguments.check:
            print(f"{relative}: figures are out of date")
        else:
            path.write_text(after, encoding="utf-8")
            print(f"{relative}: figures brought up to date")

    if arguments.check and stale:
        return 1
    if not stale:
        print("every figure already agrees with its source")
    return 0


if __name__ == "__main__":
    sys.exit(main())
