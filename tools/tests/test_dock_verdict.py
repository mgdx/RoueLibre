"""What makes a station a dock the journey can be sent to (SPEC.md §6).

The figures below are not invented: each case is one network as its feed
published it on 12 September 2026, reduced to the three numbers the rule reads.
They are kept as a test because the two thresholds were drawn from them, and a
threshold nobody can re-derive is a threshold nobody can move.
"""

from __future__ import annotations

import unittest
from unittest import mock

import discover_networks
from discover_networks import (
    EMPTY_DOCK_SHARE,
    FROZEN_FREE_DOCK_SHARE,
    MINIMUM_CAPACITY_FOR_HALF,
    MINIMUM_OCCUPIED_STATIONS_TO_JUDGE,
    PLACEHOLDER_DOCK_SHARE,
    VIRTUAL_STATION_SHARE,
    bikes_available,
    capacity_of,
    dock_verdict,
    explained_by_capacity,
    feed_counts_without_capacity,
    feed_writes_docks_from_capacity,
    free_docks,
    lenient_flag,
    reads_as_counted,
    takes_any_bike_back,
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


class FrozenDespiteADeclaredCapacity(unittest.TestCase):
    """A capacity says how many docks there are, never how many are free (#8)."""

    def test_a_declared_capacity_no_longer_hides_a_frozen_nought(self) -> None:
        # A nought at every station the application would believe: no journey
        # could ever end there, whatever capacity the producer declares.
        self.assertEqual(
            "frozen-free-docks", dock_verdict(survey(143, capacity=1200, frozen=1.0))
        )

    def test_myradl_munich_is_kept_once_its_zones_take_any_bike(self) -> None:
        # 21 October 2026: 805 stations, 3 declaring a capacity, 800 zones
        # without one at nought. The zones weigh in the share as stations where
        # a journey can end, and are never the frozen figure: what is left is
        # at most 5 counted stations in 805.
        self.assertIsNone(dock_verdict(survey(805, capacity=19, frozen=5 / 805)))

    def test_donkey_republic_counting_its_zones_stays_served(self) -> None:
        self.assertIsNone(dock_verdict(survey(199, capacity=1108, virtual=1.0, frozen=0.3)))


class UnlimitedReturns(unittest.TestCase):
    """The mirror of `StationAvailability.hasUnlimitedDocks` in `:core`.

    Each case here has its twin in `UnlimitedDocksTest.kt`; the two must agree.
    """

    def test_no_count_at_all_is_unlimited(self) -> None:
        self.assertTrue(takes_any_bike_back({"num_bikes_available": 2}, None, True))

    def test_a_virtual_station_without_capacity_at_nought(self) -> None:
        # nextbike: capacity less bikes, floored at nought, with no capacity.
        # A zone has no dock to fill, even in a feed that counts its racks.
        for flag in (True, 1, "true", "1"):
            self.assertTrue(
                takes_any_bike_back({"num_docks_available": 0}, {"is_virtual_station": flag}, True)
            )

    def test_a_rack_without_capacity_at_nought_where_nothing_is_counted(self) -> None:
        # Düsseldorf: 345 racks at nought and no capacity anywhere among them.
        self.assertTrue(takes_any_bike_back({"num_docks_available": 0}, {}, False))
        self.assertTrue(takes_any_bike_back({"num_docks_available": 0}, None, False))

    def test_a_rack_without_capacity_at_nought_is_full_where_the_feed_counts(self) -> None:
        # Indego, Philadelphia: 317 racks counted above nought, 4 at nought.
        self.assertFalse(takes_any_bike_back({"num_docks_available": 0}, {}, True))

    def test_a_station_declaring_a_capacity_keeps_its_count(self) -> None:
        for information in (
            {"is_virtual_station": True, "capacity": 12},
            {"capacity": 12},
            {"is_virtual_station": True, "vehicle_docks_capacity": [{"count": 4}]},
        ):
            self.assertFalse(takes_any_bike_back({"num_docks_available": 0}, information, False))

    def test_a_count_above_nought_is_kept(self) -> None:
        self.assertFalse(
            takes_any_bike_back({"num_docks_available": 3}, {"is_virtual_station": True}, False)
        )

    def test_a_negative_count_is_nought(self) -> None:
        self.assertEqual(0, free_docks({"num_docks_available": -2}))
        self.assertTrue(takes_any_bike_back({"num_docks_available": -2}, {}, False))
        self.assertFalse(takes_any_bike_back({"num_docks_available": -2}, {"capacity": 10}, False))

    def test_a_negative_capacity_is_no_capacity(self) -> None:
        self.assertIsNone(capacity_of({"capacity": -1}))
        self.assertTrue(takes_any_bike_back({"num_docks_available": 0}, {"capacity": -1}, False))

    def test_an_unreadable_flag_is_no_flag(self) -> None:
        # The application refuses to read the document; here, no flag at all.
        for flag in ("yes", "True", None, [1]):
            self.assertFalse(lenient_flag(flag))
        self.assertFalse(lenient_flag("false"))
        self.assertFalse(lenient_flag("0"))

    def test_only_a_count_above_nought_without_capacity_proves_counting(self) -> None:
        information = {"a": {}, "b": {"capacity": 12}, "c": {}}
        live_derived = [
            {"station_id": "a", "num_docks_available": 0},
            {"station_id": "b", "num_docks_available": 5},
            {"station_id": "c"},
        ]
        self.assertFalse(feed_counts_without_capacity(live_derived, information))
        live_counted = live_derived + [{"station_id": "c", "num_docks_available": 7}]
        self.assertTrue(feed_counts_without_capacity(live_counted, information))


class NoughtsThatAreMostOfTheFeed(unittest.TestCase):
    """A feed where most racks without a capacity say nought measured none of them."""

    def test_sarajevo_four_real_docks_among_derived_noughts(self) -> None:
        information = {str(index): {} for index in range(42)}
        live = [{"station_id": str(index), "num_docks_available": 0} for index in range(38)]
        live += [{"station_id": str(index), "num_docks_available": 6} for index in range(38, 42)]
        self.assertFalse(feed_counts_without_capacity(live, information))

    def test_virtual_stations_say_nothing_of_the_racks(self) -> None:
        # Their nought is never believed, so it cannot outvote the racks'.
        information = {"rack": {}, "zone-1": {"is_virtual_station": True},
                       "zone-2": {"is_virtual_station": True}}
        live = [
            {"station_id": "rack", "num_docks_available": 7},
            {"station_id": "zone-1", "num_docks_available": 0},
            {"station_id": "zone-2", "num_docks_available": 0},
        ]
        self.assertTrue(feed_counts_without_capacity(live, information))

    def test_philadelphia_four_full_racks_among_counted_ones(self) -> None:
        information = {str(index): {} for index in range(321)}
        live = [{"station_id": str(index), "num_docks_available": 0} for index in range(4)]
        live += [{"station_id": str(index), "num_docks_available": 7} for index in range(4, 321)]
        self.assertTrue(feed_counts_without_capacity(live, information))


def stations_of(capacity: int, docks, *bikes: int) -> tuple[dict, list[dict]]:
    """Stations of `capacity` docks, one per count of bikes, reading `docks(bikes)` free."""
    information = {}
    live = []
    for index, held in enumerate(bikes):
        identifier = f"c{capacity}-{index}"
        information[identifier] = {"station_id": identifier, "capacity": capacity}
        live.append(
            {
                "station_id": identifier,
                "num_vehicles_available": held,
                "num_docks_available": docks(held),
            }
        )
    return information, live


def merged(*parts: tuple[dict, list[dict]]) -> tuple[dict, list[dict]]:
    information, live = {}, []
    for part_information, part_live in parts:
        information.update(part_information)
        live += part_live
    return information, live


class FreeDocksRepeatingTheCapacity(unittest.TestCase):
    """The mirror of `isInFeedWritingDocksFromCapacity` in `:core`.

    Each case here has its twin in `UnlimitedDocksTest.kt`; the two must agree.
    """

    def test_auch_writes_the_capacity_at_every_station_holding_bikes(self) -> None:
        # Ecovelo, 22 October 2026: 10 free beside 10 bikes at a ten-dock
        # station, 5 free beside 7 bikes at a five-dock one.
        information, live = merged(
            stations_of(10, lambda _: 10, 10, 2, 8),
            stations_of(5, lambda _: 5, 7, 4, 1, 2, 6, 0),
        )
        self.assertTrue(feed_writes_docks_from_capacity(live, information))
        for station in live:
            self.assertTrue(
                takes_any_bike_back(station, information[station["station_id"]], True, True)
            )

    def test_a_docked_feed_keeps_its_counts_beside_a_few_capacities(self) -> None:
        # Berlin: 2 of 704 stations holding bikes read their whole capacity.
        information, live = merged(
            stations_of(20, lambda held: 20 - held, 3, 7, 12, 15),
            stations_of(10, lambda _: 10, 2, 4),
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))
        self.assertFalse(takes_any_bike_back(live[0], information["c20-0"], True, False))

    def test_half_the_stations_holding_bikes_is_no_majority(self) -> None:
        information, live = merged(
            stations_of(10, lambda _: 10, 2, 4, 6),
            stations_of(20, lambda held: 20 - held, 1, 2, 3),
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))

    def test_empty_stations_reading_their_capacity_are_counted(self) -> None:
        # Agen's empty fleet: five free docks at five-dock racks holding nothing.
        information, live = merged(
            stations_of(5, lambda _: 5, 0, 0, 0, 0, 0, 0, 0),
            stations_of(6, lambda held: 6 - held, 2),
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))

    def test_too_few_stations_holding_bikes_decide_nothing(self) -> None:
        self.assertEqual(5, MINIMUM_OCCUPIED_STATIONS_TO_JUDGE)
        information, live = merged(
            stations_of(5, lambda _: 5, 1, 2, 3, 4), stations_of(6, lambda _: 6, 0, 0, 0)
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))
        information, live = stations_of(5, lambda _: 5, 1, 2, 3, 4, 5)
        self.assertTrue(feed_writes_docks_from_capacity(live, information))

    def test_a_station_without_a_capacity_or_a_count_weighs_nothing(self) -> None:
        information, live = merged(
            stations_of(8, lambda _: 8, 1, 2), stations_of(9, lambda held: 9 - held, 1, 2, 3)
        )
        information.update({"zero": {"capacity": 0}, "none": {}, "uncounted": {"capacity": 5}})
        live += [
            {"station_id": "zero", "num_bikes_available": 3, "num_docks_available": 0},
            {"station_id": "none", "num_bikes_available": 3, "num_docks_available": 4},
            {"station_id": "uncounted", "num_bikes_available": 3},
            {"station_id": "undescribed", "num_bikes_available": 3, "num_docks_available": 5},
        ]
        self.assertFalse(feed_writes_docks_from_capacity(live, information))

    def test_niort_writes_half_the_capacity_beside_the_whole_of_it(self) -> None:
        # 22 October 2026: 10 free at twenty-dock stations and 7 at
        # fourteen-dock ones whatever stands there, 8 at the eight-dock ones.
        information, live = merged(
            stations_of(20, lambda _: 10, 1, 3, 2),
            stations_of(14, lambda _: 7, 1, 4),
            stations_of(8, lambda _: 8, 2, 5),
            stations_of(22, lambda _: 13, 4),
        )
        self.assertTrue(feed_writes_docks_from_capacity(live, information))
        self.assertTrue(takes_any_bike_back(live[-1], information["c22-0"], True, True))

    def test_half_an_odd_capacity_is_read_rounded_either_way(self) -> None:
        # Cherbourg: 8 free at fifteen docks holding 6. Soissons: 3 free at
        # seven docks holding 1.
        information, live = merged(
            stations_of(15, lambda _: 8, 6, 2, 3),
            stations_of(7, lambda _: 3, 1, 2),
            stations_of(30, lambda held: 30 - held, 4, 5, 6),
        )
        self.assertTrue(feed_writes_docks_from_capacity(live, information))

    def test_half_a_small_rack_is_not_read_as_written(self) -> None:
        # LoveSharing, Las Palmas: small racks reading half their docks. Below
        # six docks, half is left alone; the whole capacity is not.
        self.assertEqual(6, MINIMUM_CAPACITY_FOR_HALF)
        information, live = merged(
            stations_of(4, lambda _: 2, 1, 1),
            stations_of(5, lambda _: 2, 1, 2),
            stations_of(10, lambda held: 10 - held, 1, 2),
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))
        information, live = merged(
            stations_of(4, lambda _: 4, 1, 2, 3),
            stations_of(10, lambda held: 10 - held, 1, 2),
        )
        self.assertTrue(feed_writes_docks_from_capacity(live, information))

    def test_half_the_capacity_left_by_as_many_bikes_is_a_count(self) -> None:
        # Twenty docks holding ten bikes do have ten free.
        self.assertTrue(reads_as_counted(20, 10, 10))
        self.assertTrue(reads_as_counted(2, 4, 0))
        self.assertFalse(reads_as_counted(20, 3, 10))
        information, live = merged(
            stations_of(20, lambda held: 20 - held, 10, 11, 9),
            stations_of(14, lambda held: 14 - held, 7, 7),
            stations_of(16, lambda _: 8, 3),
        )
        self.assertFalse(feed_writes_docks_from_capacity(live, information))

    def test_the_bikes_are_read_as_the_application_reads_them(self) -> None:
        self.assertEqual(
            0, bikes_available({"num_bikes_available": 0, "num_vehicles_available": 4})
        )
        self.assertEqual(4, bikes_available({"num_vehicles_available": 4}))
        self.assertEqual(0, bikes_available({"num_bikes_available": -3}))

    def test_auch_is_eligible_again(self) -> None:
        # The whole survey, on a feed shaped like Auch's: rejected as
        # "frozen-free-docks" while its stations' capacity was believed, five
        # free docks being the commonest figure at 8 of its 10 stations.
        information, live = merged(
            stations_of(10, lambda _: 10, 10),
            stations_of(5, lambda _: 5, 7, 4, 1, 2, 6, 3, 5, 2, 0),
        )
        for index, station in enumerate(information.values()):
            station.update({"name": station["station_id"], "lat": 43.64 + index / 1000,
                            "lon": 0.58 + index / 1000})
        documents = {
            "gbfs": {"version": "3.0", "data": {"feeds": [
                {"name": "station_information", "url": "information"},
                {"name": "station_status", "url": "status"},
            ]}},
            "information": {"data": {"stations": list(information.values())}},
            "status": {"data": {"stations": live}},
        }
        with mock.patch.object(discover_networks, "fetch_json", documents.__getitem__):
            survey = discover_networks.probe_feeds({"discoveryUrls": ["gbfs"]})
        self.assertEqual(len(live), survey["unlimitedDockStations"])
        self.assertNotIn("commonestFreeDockShare", survey)
        self.assertEqual("eligible", survey["verdict"])


class CountsTheCapacityExplains(unittest.TestCase):
    """A repeated value is not frozen when every station's capacity accounts for it."""

    def test_an_empty_fleet_reads_its_capacity(self) -> None:
        # Agen: no bike out, five free docks at five-dock stations.
        self.assertTrue(
            explained_by_capacity(
                {"num_bikes_available": 0, "num_docks_available": 5}, {"capacity": 5}
            )
        )

    def test_a_full_station_at_night(self) -> None:
        # Calais: nought free docks, the bikes filling the capacity.
        self.assertTrue(
            explained_by_capacity(
                {"num_bikes_available": 9, "num_docks_available": 0}, {"capacity": 9}
            )
        )

    def test_docks_and_bikes_out_of_service_make_up_the_difference(self) -> None:
        self.assertTrue(
            explained_by_capacity(
                {
                    "num_bikes_available": 6,
                    "num_docks_available": 0,
                    "num_bikes_disabled": 1,
                    "num_docks_disabled": 1,
                },
                {"capacity": 8},
            )
        )

    def test_more_bikes_than_docks_reads_nought(self) -> None:
        # Mława: two-dock stations holding three to five bikes, nought free.
        self.assertTrue(
            explained_by_capacity(
                {"num_bikes_available": 4, "num_docks_available": 0}, {"capacity": 2}
            )
        )

    def test_free_docks_frozen_at_the_capacity(self) -> None:
        # Auch: four bikes at a five-dock station, and five free docks.
        self.assertFalse(
            explained_by_capacity(
                {"num_vehicles_available": 4, "num_docks_available": 5}, {"capacity": 5}
            )
        )

    def test_a_nought_the_capacity_does_not_explain(self) -> None:
        # Gothenburg's frozen nought: two bikes in a twenty-dock rack.
        self.assertFalse(
            explained_by_capacity(
                {"num_bikes_available": 2, "num_docks_available": 0}, {"capacity": 20}
            )
        )

    def test_no_capacity_explains_nothing(self) -> None:
        self.assertFalse(
            explained_by_capacity({"num_bikes_available": 0, "num_docks_available": 1}, {})
        )


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
