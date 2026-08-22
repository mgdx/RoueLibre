"""What a city's output directory keeps when its box shrinks (SPEC.md §4).

The case is Careem BIKE's, reduced to its file names. Its box spanned Dubai and
Medina because the feed publishes six stations in Saudi Arabia; corrected, it
went from 1,612 km wide to 45, and the run that followed produced the two
squares the new box needs — and left the eight of the desert in place, which
the manifest then went on announcing.
"""

from __future__ import annotations

import contextlib
import io
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from build_manifest import DATASETS, describe
from build_routing import prune_stale_segments

# The two squares the corrected box asks for, and the eight the old one left.
DEMANDED = ("E55_N20.rd5", "E55_N25.rd5")
DESERT = (
    "E35_N20.rd5", "E35_N25.rd5", "E40_N20.rd5", "E40_N25.rd5",
    "E45_N20.rd5", "E45_N25.rd5", "E50_N20.rd5", "E50_N25.rd5",
)


def city_output(directory: Path) -> Path:
    """An output directory as a run leaves it: the three datasets, side by side.

    The two heavy single files are written too, because they are exactly what
    the cleanup must not touch.
    """
    (directory / "tiles.mbtiles").write_bytes(b"mbtiles")
    (directory / "addresses.sqlite").write_bytes(b"sqlite")
    routing = directory / "routing"
    routing.mkdir()
    for name in DEMANDED:
        (routing / name).write_bytes(b"x" * 2_000)
    for name in DESERT:
        (routing / name).write_bytes(b"x" * 1_000)
    return routing


def prune(routing: Path) -> str:
    """Run the cleanup over ``routing`` and hand back what it printed."""
    log = io.StringIO()
    with contextlib.redirect_stdout(log):
        prune_stale_segments(routing, set(DEMANDED))
    return log.getvalue()


class PruneStaleSegmentsTest(unittest.TestCase):
    """A square the current box no longer asks for does not survive a run."""

    def test_the_squares_of_a_wider_box_are_removed(self) -> None:
        with TemporaryDirectory() as temporary:
            routing = city_output(Path(temporary))
            prune(routing)
            self.assertEqual(
                sorted(path.name for path in routing.iterdir()), sorted(DEMANDED)
            )

    def test_what_the_box_asks_for_is_kept_whole(self) -> None:
        """A cleanup that took the graph with it would be worse than the leak."""
        with TemporaryDirectory() as temporary:
            routing = city_output(Path(temporary))
            prune(routing)
            for name in DEMANDED:
                self.assertEqual((routing / name).stat().st_size, 2_000)

    def test_removals_are_named_with_their_size(self) -> None:
        """Nothing disappears in silence, as with the clusters set aside (§4)."""
        with TemporaryDirectory() as temporary:
            routing = city_output(Path(temporary))
            log = prune(routing)
            for name in DESERT:
                self.assertIn(name, log)
            self.assertIn("0.00 MB", log)

    def test_the_other_datasets_are_left_alone(self) -> None:
        """The two single files cost hours; a resumed run must find them there."""
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            routing = city_output(directory)
            prune(routing)
            self.assertTrue((directory / "tiles.mbtiles").is_file())
            self.assertTrue((directory / "addresses.sqlite").is_file())

    def test_a_file_this_script_did_not_write_is_not_removed(self) -> None:
        """Only rd5 files are considered: what is not ours, we do not delete."""
        with TemporaryDirectory() as temporary:
            routing = city_output(Path(temporary))
            (routing / "notes.txt").write_text("kept", encoding="utf-8")
            prune(routing)
            self.assertTrue((routing / "notes.txt").is_file())

    def test_a_run_that_changed_nothing_removes_nothing(self) -> None:
        with TemporaryDirectory() as temporary:
            routing = Path(temporary) / "routing"
            routing.mkdir()
            for name in DEMANDED:
                (routing / name).write_bytes(b"x" * 2_000)
            self.assertEqual(prune_stale_segments(routing, set(DEMANDED)), 0)


class ManifestAfterPruningTest(unittest.TestCase):
    """The manifest lists the directory, so the leak is only fixed there."""

    def entries(self, directory: Path) -> dict[str, list[dict]]:
        return {
            dataset.identifier: describe(
                dataset, directory, "https://example.invalid/download",
                "data-2026-08", "careem-bike",
            )["files"]
            for dataset in DATASETS
        }

    def test_a_stale_square_is_announced_until_it_is_removed(self) -> None:
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            routing = city_output(directory)

            before = self.entries(directory)["routing"]
            self.assertEqual(len(before), len(DEMANDED) + len(DESERT))

            prune(routing)

            after = self.entries(directory)["routing"]
            self.assertEqual(
                sorted(file["name"] for file in after), sorted(DEMANDED)
            )
            self.assertEqual(sum(file["sizeBytes"] for file in after), 4_000)

    def test_the_two_other_datasets_are_still_described(self) -> None:
        with TemporaryDirectory() as temporary:
            directory = Path(temporary)
            prune(city_output(directory))
            entries = self.entries(directory)
            self.assertEqual(len(entries["tiles"]), 1)
            self.assertEqual(len(entries["addresses"]), 1)


if __name__ == "__main__":
    unittest.main()
