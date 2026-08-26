"""The per-architecture copies F-Droid reads its release notes from.

F-Droid looks up ``changelogs/<versionCode>.txt`` under the exact code of the
APK it serves — 61 to 64 for base 6 — and falls back on nothing, while the
what's-new screen keys its notes on the base (SPEC.md §7.10). The base file is
therefore the source and the four copies are derived from it, which is what
these tests hold ``expand_changelogs.py`` to — including over the repository's
own fastlane tree, so a release whose notes were written without running the
script fails here rather than shipping blank on F-Droid.
"""

from __future__ import annotations

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from expand_changelogs import (
    FASTLANE,
    GRADLE_BUILD_FILE,
    architecture_ranks,
    stale_copies,
)

RANKS = (1, 2, 3, 4)


def write_all(changelogs: Path) -> None:
    """Bring one folder up to date, the way the script's main loop does."""
    for derived, text in stale_copies(changelogs, RANKS):
        derived.write_text(text, encoding="utf-8")


class ArchitectureRanksTest(unittest.TestCase):
    """The ranks come from where they are defined, not from a second copy."""

    def test_the_real_build_file_yields_the_four_ranks(self) -> None:
        self.assertEqual(sorted(architecture_ranks(GRADLE_BUILD_FILE)), list(RANKS))

    def test_a_build_file_without_the_map_is_refused(self) -> None:
        with TemporaryDirectory() as temporary:
            build_file = Path(temporary) / "build.gradle.kts"
            build_file.write_text("android {}\n", encoding="utf-8")
            with self.assertRaises(SystemExit):
                architecture_ranks(build_file)


class StaleCopiesTest(unittest.TestCase):
    """What one language's folder is missing, and only that."""

    def folder(self, *notes: tuple[str, str]) -> Path:
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        changelogs = Path(self.temporary.name) / "changelogs"
        changelogs.mkdir()
        for name, text in notes:
            (changelogs / name).write_text(text, encoding="utf-8")
        return changelogs

    def test_a_base_file_expands_to_the_four_published_codes(self) -> None:
        changelogs = self.folder(("6.txt", "Notes for 1.2.0\n"))
        self.assertEqual(
            [(derived.name, text) for derived, text in stale_copies(changelogs, RANKS)],
            [(f"{code}.txt", "Notes for 1.2.0\n") for code in (61, 62, 63, 64)],
        )

    def test_the_alpha_bases_are_left_alone(self) -> None:
        """Bases 1 to 3 shipped one universal APK: no derived code ever existed."""
        changelogs = self.folder(
            ("1.txt", "alpha\n"), ("2.txt", "alpha\n"), ("3.txt", "alpha\n"),
        )
        self.assertEqual(stale_copies(changelogs, RANKS), [])

    def test_a_derived_copy_is_not_expanded_in_its_turn(self) -> None:
        """61.txt must not beget 611.txt on the next run."""
        changelogs = self.folder(("6.txt", "notes\n"))
        write_all(changelogs)
        self.assertEqual(stale_copies(changelogs, RANKS), [])
        self.assertEqual(
            sorted(path.name for path in changelogs.iterdir()),
            ["6.txt", "61.txt", "62.txt", "63.txt", "64.txt"],
        )

    def test_a_base_ending_in_a_rank_is_still_a_base(self) -> None:
        """14 beside the alpha 1.txt is base 14, not a copy of 1.

        Nothing ever derives from the bases below the split, so a file whose
        code ends in a rank is only a copy when its putative base is itself
        expandable — otherwise release 14 would silently lose its notes,
        ``--check`` included.
        """
        changelogs = self.folder(("1.txt", "alpha\n"), ("14.txt", "notes\n"))
        self.assertEqual(
            [derived.name for derived, _ in stale_copies(changelogs, RANKS)],
            ["141.txt", "142.txt", "143.txt", "144.txt"],
        )

    def test_a_copy_whose_source_moved_is_reported_again(self) -> None:
        changelogs = self.folder(("5.txt", "first wording\n"))
        write_all(changelogs)
        (changelogs / "5.txt").write_text("corrected wording\n", encoding="utf-8")
        self.assertEqual(
            [derived.name for derived, _ in stale_copies(changelogs, RANKS)],
            ["51.txt", "52.txt", "53.txt", "54.txt"],
        )


class RepositoryIsCurrentTest(unittest.TestCase):
    """The tree as committed carries every copy F-Droid will ask for."""

    def test_every_language_folder_is_expanded(self) -> None:
        ranks = architecture_ranks(GRADLE_BUILD_FILE)
        stale = [
            str(derived.relative_to(FASTLANE))
            for changelogs in sorted(FASTLANE.glob("*/changelogs"))
            for derived, _ in stale_copies(changelogs, ranks)
        ]
        self.assertEqual(
            stale, [],
            "derived release notes missing or stale — "
            "run python3 tools/expand_changelogs.py",
        )


if __name__ == "__main__":
    unittest.main()
