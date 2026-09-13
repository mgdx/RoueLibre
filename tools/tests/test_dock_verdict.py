"""What makes a station a dock the journey can be sent to (SPEC.md §6).

The figures below are not invented: each case is one network as its feed
published it on 12 September 2026, reduced to the three numbers the rule reads.
They are kept as a test because the two thresholds were drawn from them, and a
threshold nobody can re-derive is a threshold nobody can move.
"""

from __future__ import annotations

import unittest

from discover_networks import (
    EMPTY_DOCK_SHARE,
    FROZEN_FREE_DOCK_SHARE,
    PLACEHOLDER_DOCK_SHARE,
    VIRTUAL_STATION_SHARE,
    dock_verdict,
)


def survey(
    stations: int,
    *,
    capacity: int = 0,
    empty: float = 0.0,
    commonest: float = 0.2,
    virtual: float = 0.0,
    frozen: float = 0.2,
) -> dict:
    """The part of a survey the dock rule reads, and nothing else."""
    return {
        "capacityTotal": capacity,
        "dockedStations": stations,
        "emptyDockShare": empty,
        "commonestDockShare": commonest,
        "virtualStationShare": virtual,
        "commonestFreeDockShare": frozen,
    }


class DeclaredCapacity(unittest.TestCase):
    """A producer publishing its capacity is believed, and still has to count."""

    def test_a_declared_capacity_needs_no_reading_of_the_live_count(self) -> None:
        # Vélib' and the other 300-odd networks that publish `capacity`: the
        # figure is the producer's own, so no threshold applies to it.
        self.assertIsNone(dock_verdict(survey(1519, capacity=49308, commonest=1.0)))

    def test_donkey_republic_keeps_its_virtual_stations(self) -> None:
        # Every one of its stations is declared virtual and every one carries a
        # capacity: the networks served must not move because of this rule.
        self.assertIsNone(dock_verdict(survey(199, capacity=1108, virtual=1.0)))

    def test_a_capacity_without_a_live_count_promises_nothing(self) -> None:
        # LaRa to go, in Waiblingen: it declares docks and never says how many
        # are free, so §6 cannot tell anybody the bike can be returned.
        self.assertEqual("no-docks", dock_verdict({"capacityTotal": 240}))


class CapacityAbsentButDocksReal(unittest.TestCase):
    """The networks the old rule refused for a field they do not publish."""

    def test_philadelphia(self) -> None:
        self.assertIsNone(dock_verdict(survey(319, empty=0.0, commonest=0.122)))

    def test_los_angeles(self) -> None:
        self.assertIsNone(dock_verdict(survey(223, empty=0.0, commonest=0.175)))

    def test_the_dutch_ov_fiets(self) -> None:
        self.assertIsNone(dock_verdict(survey(284, empty=0.0, commonest=0.095)))

    def test_torun_the_smallest_of_them(self) -> None:
        # 3.2% of its stations imply no slot, the highest of any network whose
        # docks are real, and it still passes with room to spare.
        self.assertIsNone(dock_verdict(survey(62, empty=0.032, commonest=0.194)))


class CountWrittenOnceForTheWholeNetwork(unittest.TestCase):
    """MobiData BW fills the field in, and the same forty comes back everywhere."""

    def test_call_a_bike(self) -> None:
        self.assertEqual(
            "placeholder-docks", dock_verdict(survey(1621, empty=0.0, commonest=0.999))
        )

    def test_regiorad_stuttgart(self) -> None:
        self.assertEqual(
            "placeholder-docks", dock_verdict(survey(233, empty=0.0, commonest=1.0))
        )


class FleetLeftAnywhereInAZone(unittest.TestCase):
    """No bike standing there means no slot, which a dock would have all the same."""

    def test_veloleo_braunschweig(self) -> None:
        self.assertEqual("no-docks", dock_verdict(survey(189, empty=0.196, commonest=0.196)))

    def test_sprintrad_hanover(self) -> None:
        self.assertEqual("no-docks", dock_verdict(survey(122, empty=0.344, commonest=0.344)))

    def test_a_feed_naming_no_free_dock_at_all(self) -> None:
        self.assertEqual("no-docks", dock_verdict(survey(0)))


class FreeDocksThatNeverMove(unittest.TestCase):
    """The one figure §6 reads, and some feeds publish it frozen."""

    def test_ov_fiets_answers_one_free_dock_everywhere(self) -> None:
        # Its bikes move, so the bikes and free docks added together move with
        # them and the placeholder line never sees anything wrong.
        self.assertEqual(
            "frozen-free-docks",
            dock_verdict(survey(284, frozen=1.0, empty=0.0, commonest=0.095)),
        )

    def test_gothenburg_answers_nought_everywhere(self) -> None:
        # Frozen at nought no journey can ever end in the network, `canAcceptBike`
        # asking for a free dock.
        self.assertEqual(
            "frozen-free-docks",
            dock_verdict(survey(143, frozen=1.0, empty=0.0, commonest=0.1)),
        )

    def test_mlawa_the_most_uniform_network_that_counts(self) -> None:
        self.assertIsNone(dock_verdict(survey(10, frozen=0.6, empty=0.0, commonest=0.3)))

    def test_the_line_sits_between_the_two_families(self) -> None:
        # 60% is Mława, 93.8% the least frozen of the frozen feeds.
        self.assertLess(0.6, FROZEN_FREE_DOCK_SHARE)
        self.assertLess(FROZEN_FREE_DOCK_SHARE, 0.938)


class StationsTheProducerCallsPaintedZones(unittest.TestCase):
    """`is_virtual_station` is the standard's own word for a drop zone."""

    def test_bird_lisbon(self) -> None:
        # 1,756 stations, every one of them declared virtual, and a free-dock
        # count varied enough to pass both thresholds without this rule.
        self.assertEqual(
            "no-docks", dock_verdict(survey(1756, virtual=1.0, empty=0.0011, commonest=0.261))
        )

    def test_flamingo_auckland(self) -> None:
        self.assertEqual(
            "no-docks", dock_verdict(survey(128, virtual=1.0, empty=0.0, commonest=0.797))
        )

    def test_a_few_virtual_stations_among_racks_change_nothing(self) -> None:
        self.assertIsNone(dock_verdict(survey(319, virtual=0.05, empty=0.0, commonest=0.122)))

    def test_the_line_is_a_majority(self) -> None:
        self.assertEqual(0.5, VIRTUAL_STATION_SHARE)


class ThresholdsStayApartFromWhatTheyJudge(unittest.TestCase):
    """Neither side of either line is decided by where exactly it was drawn."""

    def test_the_empty_line_sits_between_the_two_families(self) -> None:
        # 4.5% is AW-bike, the emptiest network whose docks are real; 19.6% is
        # Veloleo, the fullest free-floating one.
        self.assertLess(0.045, EMPTY_DOCK_SHARE)
        self.assertLess(EMPTY_DOCK_SHARE, 0.196)

    def test_the_placeholder_line_sits_between_the_two_families(self) -> None:
        # 32.2% is Buffalo, the most uniform network that counts its own docks;
        # 99.9% is Call a Bike, the least uniform of the two placeholders.
        self.assertLess(0.322, PLACEHOLDER_DOCK_SHARE)
        self.assertLess(PLACEHOLDER_DOCK_SHARE, 0.999)


if __name__ == "__main__":
    unittest.main()
