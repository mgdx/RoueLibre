"""What the reference box is drawn around, and what it leaves out (SPEC.md §4).

The cases are the ones the networks served actually publish, reduced to the
handful of positions that make each of them what it is: a feed carrying two
conurbations, a regional network that only looks like one, and a station at
latitude zero.
"""

from __future__ import annotations

import contextlib
import io
import unittest

from compute_bbox import (
    StationSurvey,
    bounding_box_of_stations,
    has_usable_position,
    outlying_positions,
    rectangle_gap_kilometres,
    station_clusters,
    station_name,
    survey_stations,
)

from city_config import BoundingBox

# A degree of latitude is 111 km, so a tenth of one is 11 km: two positions a
# tenth of a degree apart are neighbours, ten tenths apart are not.
DEGREE_OF_ELEVEN_KILOMETRES = 0.1


def town(latitude: float, longitude: float, count: int, name: str) -> list[dict]:
    """A cluster of ``count`` stations a few hundred metres from one another."""
    return [
        {
            "station_id": f"{name}-{index}",
            "name": f"{name} {index}",
            "lat": latitude + index * 0.002,
            "lon": longitude + index * 0.002,
        }
        for index in range(count)
    ]


class StationClusterTest(unittest.TestCase):
    """The grouping the box and the framing are both read from."""

    def test_neighbours_make_one_cluster(self) -> None:
        positions = [(50.0, 3.0), (50.05, 3.05), (50.1, 3.1)]
        self.assertEqual(station_clusters(positions), [[0, 1, 2]])

    def test_a_chain_of_neighbours_makes_one_cluster(self) -> None:
        """Chaining is the point: a valley network has no centre worth the name.

        Each step is eleven kilometres, so no two ends of the chain are within
        the link distance of one another and the cluster holds all the same.
        """
        positions = [
            (50.0 + step * DEGREE_OF_ELEVEN_KILOMETRES, 3.0) for step in range(6)
        ]
        self.assertEqual(len(station_clusters(positions)), 1)

    def test_a_distant_group_is_a_cluster_of_its_own(self) -> None:
        positions = [(50.0, 3.0), (50.02, 3.02), (25.0, 55.0), (25.02, 55.02)]
        self.assertEqual(station_clusters(positions), [[0, 1], [2, 3]])

    def test_the_most_populous_cluster_comes_first(self) -> None:
        positions = [(25.0, 55.0), (50.0, 3.0), (50.01, 3.01), (50.02, 3.02)]
        self.assertEqual(station_clusters(positions)[0], [1, 2, 3])

    def test_a_cluster_is_found_across_a_grid_cell_boundary(self) -> None:
        """Two stations either side of a cell edge are still neighbours.

        The grid is an optimisation and must change no answer: the cells are
        0.25° wide, so this pair straddles one.
        """
        positions = [(50.249, 3.0), (50.251, 3.0)]
        self.assertEqual(station_clusters(positions), [[0, 1]])


class OutlyingClusterTest(unittest.TestCase):
    """The two conditions that set a cluster aside, and each one alone."""

    def test_far_and_marginal_is_dropped(self) -> None:
        """Careem BIKE: 206 stations in Dubai, 6 in Medina, 1,580 km away."""
        positions = [(25.2 + index * 0.002, 55.3) for index in range(200)]
        positions += [(24.48 + index * 0.002, 39.6) for index in range(6)]
        self.assertEqual(outlying_positions(positions), set(range(200, 206)))

    def test_far_but_substantial_is_kept(self) -> None:
        """Nicosia spreads 14 % of its stations over the far side of Cyprus."""
        positions = [(35.17 + index * 0.002, 33.36) for index in range(86)]
        positions += [(34.7 + index * 0.002, 32.42) for index in range(14)]
        self.assertEqual(outlying_positions(positions), set())

    def test_near_but_marginal_is_kept(self) -> None:
        """A network's outskirt is not a mistake in the feed."""
        positions = [(50.6 + index * 0.002, 3.1) for index in range(100)]
        positions += [(50.0, 3.1), (50.005, 3.1)]
        self.assertEqual(outlying_positions(positions), set())

    def test_a_lone_station_is_dropped_by_the_same_rule(self) -> None:
        """Valenbisi's "LABMAD", three hundred kilometres away, in Madrid."""
        positions = [(39.47 + index * 0.002, -0.37) for index in range(50)]
        positions.append((40.42, -3.7))
        self.assertEqual(outlying_positions(positions), {50})

    def test_two_stations_are_left_alone(self) -> None:
        """With nothing to be far from, the question does not arise."""
        self.assertEqual(outlying_positions([(50.0, 3.0), (25.0, 55.0)]), set())


class RectangleGapTest(unittest.TestCase):
    """The distance the outlying rule reads, edge to edge."""

    def test_overlapping_rectangles_are_zero_apart(self) -> None:
        first = BoundingBox(south=50.0, west=3.0, north=50.5, east=3.5)
        second = BoundingBox(south=50.2, west=3.2, north=50.7, east=3.7)
        self.assertEqual(rectangle_gap_kilometres(first, second), 0.0)

    def test_the_gap_is_measured_between_the_facing_edges(self) -> None:
        """Centre to centre would read three times as far here."""
        first = BoundingBox(south=50.0, west=3.0, north=51.0, east=3.2)
        second = BoundingBox(south=52.0, west=3.0, north=53.0, east=3.2)
        self.assertAlmostEqual(
            rectangle_gap_kilometres(first, second), 111.32, places=2
        )


class SurveyStationsTest(unittest.TestCase):
    """What a feed hands over once read, and what the log says about it."""

    def survey(self, stations: list[dict]) -> StationSurvey:
        """Read a feed with its log put aside.

        ``survey_stations`` names what it drops on standard output, which is
        the point of it — here that would only bury the failures.
        """
        with contextlib.redirect_stdout(io.StringIO()):
            return survey_stations(stations)

    def test_the_box_is_drawn_around_the_retained_stations(self) -> None:
        stations = town(25.2, 55.3, 40, "Dubai") + town(24.48, 39.6, 2, "Medina")
        survey = self.survey(stations)
        self.assertEqual(len(survey.stations), 40)
        box = bounding_box_of_stations(survey.stations)
        self.assertAlmostEqual(box.west, 55.3, places=6)
        self.assertGreater(box.east, 55.3)

    def test_the_main_cluster_box_ignores_a_legitimate_second_cluster(self) -> None:
        """Kept in the box, and still not what the opening zoom is read from."""
        stations = town(50.6, 3.1, 60, "Lille") + town(50.0, 3.1, 40, "Arras")
        survey = self.survey(stations)
        self.assertEqual(len(survey.stations), 100)
        self.assertAlmostEqual(survey.main_cluster_box.south, 50.6, places=6)

    def test_a_station_at_latitude_zero_is_dropped(self) -> None:
        stations = town(47.2, -1.55, 10, "Nantes")
        stations.append({"station_id": "empty", "name": "Null Island",
                         "lat": 0.0, "lon": 0.0})
        self.assertEqual(len(self.survey(stations).stations), 10)

    def test_a_feed_with_no_usable_station_is_refused(self) -> None:
        with self.assertRaises(ValueError):
            self.survey([{"station_id": "empty", "lat": 0.0, "lon": 0.0}])

    def test_a_position_out_of_range_is_not_usable(self) -> None:
        self.assertFalse(has_usable_position({"lat": 95.0, "lon": 3.0}))
        self.assertTrue(has_usable_position({"lat": 50.6, "lon": 3.0}))

    def test_a_gbfs_3_name_is_read_for_the_log(self) -> None:
        """GBFS 3.0 publishes a name as a list of translations."""
        station = {
            "station_id": "42",
            "name": [{"text": "Wadi Aqiq Corniche", "language": "en"}],
        }
        self.assertEqual(station_name(station), "Wadi Aqiq Corniche")

    def test_a_nameless_station_is_called_by_its_identifier(self) -> None:
        self.assertEqual(station_name({"station_id": "42"}), "42")


if __name__ == "__main__":
    unittest.main()
