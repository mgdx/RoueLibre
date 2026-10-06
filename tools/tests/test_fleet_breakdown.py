"""How the fleet survey reads a breakdown published without identifiers (SPEC.md §15).

`num_bikes_available_types` is an extension, and it is published in two
shapes. The application reads both; the survey read Vélib's alone and stopped
on BCycle's, which left Philadelphia, Las Vegas and Bentonville with no fleet
block at all. The station entries below are the two producers' own, as their
feeds published them on 6 October 2026.
"""

from __future__ import annotations

import unittest
from unittest import mock

import read_fleet
from read_fleet import ELECTRIC, MECHANICAL, count_bikes, inline_counts

VELIB_STATION = {
    "station_id": "213688169",
    "num_bikes_available": 4,
    "num_bikes_available_types": [{"mechanical": 3}, {"ebike": 1}],
}

BCYCLE_STATION = {
    "station_id": "bcycle_madison_1874",
    "num_bikes_available": 4,
    "num_bikes_available_types": {"electric": 4, "smart": 0, "classic": 0},
}


def counted(*stations: dict) -> tuple[dict[str, int], set[str]]:
    """Run count_bikes over a status feed holding these stations, offline."""
    document = {"data": {"stations": list(stations)}}
    with mock.patch.object(read_fleet, "resolve_feed_url", return_value="status"), \
            mock.patch.object(read_fleet, "fetch_json", return_value=document):
        return count_bikes({}, {})


class BothShapesAreRead(unittest.TestCase):
    def test_velib_sends_a_list_of_single_key_objects(self) -> None:
        self.assertEqual(
            inline_counts(VELIB_STATION["num_bikes_available_types"]),
            [("mechanical", 3), ("ebike", 1)],
        )

    def test_bcycle_sends_one_object_naming_every_kind(self) -> None:
        self.assertEqual(
            sorted(inline_counts(BCYCLE_STATION["num_bikes_available_types"])),
            [("classic", 0), ("electric", 4), ("smart", 0)],
        )

    def test_anything_else_counts_nothing_rather_than_failing(self) -> None:
        self.assertEqual(inline_counts(None), [])
        self.assertEqual(inline_counts("electric"), [])


class KindsAreSortedAndNamed(unittest.TestCase):
    def test_velib(self) -> None:
        bikes, names = counted(VELIB_STATION)
        self.assertEqual(bikes, {MECHANICAL: 3, ELECTRIC: 1})
        self.assertEqual(names, {"mechanical", "ebike"})

    def test_bcycle_smart_and_classic_bikes_are_pedalled(self) -> None:
        bikes, names = counted(BCYCLE_STATION)
        self.assertEqual(bikes, {ELECTRIC: 4})
        # The names enter the table even at nought: the application needs
        # them to sort the next reading, when a classic bike is back.
        self.assertEqual(names, {"classic", "smart", "electric"})

    def test_a_feed_with_no_breakdown_names_nothing(self) -> None:
        bikes, names = counted({"station_id": "1", "num_bikes_available": 2})
        self.assertEqual(bikes, {})
        self.assertEqual(names, set())


if __name__ == "__main__":
    unittest.main()
