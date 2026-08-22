"""Where a city's map opens, and when a recomputation moves it (SPEC.md §4).

The two failures these cases stand for both happened: a centre left behind by a
box that shrank around it, and a centre sitting comfortably inside a box that
spanned two conurbations and showed neither.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from city_config import (
    BoundingBox,
    CityConfig,
    OpeningView,
    default_zoom,
    median_position,
)

# The margin §4 puts around the stations of every city served.
MARGIN_METRES = 3000.0


def configuration_document(
    box: BoundingBox, latitude: float, longitude: float, zoom: float
) -> dict:
    """The two blocks of a city configuration this module ever touches."""
    return {
        "boundingBox": {
            "marginMeters": MARGIN_METRES,
            "generatedAt": "2026-01-01T00:00:00Z",
            "stationCount": 0,
            "south": box.south,
            "west": box.west,
            "north": box.north,
            "east": box.east,
        },
        "map": {
            "defaultCenterLatitude": latitude,
            "defaultCenterLongitude": longitude,
            "defaultZoom": zoom,
            "minZoom": 10,
            "maxZoom": 16,
        },
    }


class MedianPositionTest(unittest.TestCase):
    """Why the median and not the mean."""

    def test_a_secondary_cluster_does_not_pull_the_centre_out(self) -> None:
        """Blue-bike stands at the railway stations of the whole of Belgium.

        The mean of these lands between the two groups, in a field; the median
        stays where the stations are.
        """
        positions = [(50.85 + index * 0.01, 4.35) for index in range(9)]
        positions += [(49.6, 5.9), (49.62, 5.92)]
        latitude, _ = median_position(positions)
        self.assertGreater(latitude, 50.8)

    def test_no_position_at_all_is_an_error(self) -> None:
        with self.assertRaises(ValueError):
            median_position([])


class OpeningViewTest(unittest.TestCase):
    """The framing read from the stations, and what it shows."""

    def test_the_zoom_follows_the_main_cluster_and_not_the_box(self) -> None:
        """A regional network would otherwise open on the region.

        The main cluster is 14 km across and the whole network 210, so framing
        the box costs four zoom steps — a map opened on four departments.
        """
        main_cluster = BoundingBox(south=50.6, west=3.0, north=50.7, east=3.2)
        positions = [(50.65, 3.1), (50.66, 3.11), (50.65, 6.0)]
        opening = OpeningView.from_stations(positions, main_cluster, MARGIN_METRES)
        whole_network = BoundingBox(south=50.6, west=3.0, north=50.7, east=6.0)
        self.assertGreater(opening.zoom, default_zoom(whole_network))

    def test_the_centre_is_the_median_of_the_stations(self) -> None:
        main_cluster = BoundingBox(south=50.6, west=3.0, north=50.7, east=3.2)
        positions = [(50.60, 3.00), (50.65, 3.10), (50.70, 3.20)]
        opening = OpeningView.from_stations(positions, main_cluster, MARGIN_METRES)
        self.assertAlmostEqual(opening.latitude, 50.65, places=6)
        self.assertAlmostEqual(opening.longitude, 3.10, places=6)

    def test_a_station_on_screen_is_seen(self) -> None:
        opening = OpeningView(latitude=50.63, longitude=3.06, zoom=11.4)
        self.assertTrue(opening.shows_a_station([(50.64, 3.07)]))

    def test_a_station_a_thousand_kilometres_away_is_not(self) -> None:
        """Dubai, in one line: the framing and the stations, far apart."""
        opening = OpeningView(latitude=24.883403, longitude=47.523604, zoom=10.5)
        self.assertFalse(opening.shows_a_station([(25.2, 55.3)]))
        self.assertFalse(opening.reaches_a_station([(25.2, 55.3)]))

    def test_a_station_just_off_screen_is_still_within_reach(self) -> None:
        """The two readings of the same screen, and what each is for.

        Twenty-seven kilometres east of the framing sharedmobility.ch is given:
        off the strict square, well inside a screenful, and the map opens on a
        band of Switzerland holding thousands of stations all the same.
        """
        opening = OpeningView(latitude=47.13965, longitude=7.600948, zoom=10.5)
        sample = [(47.13307, 7.24344)]
        self.assertFalse(opening.shows_a_station(sample))
        self.assertTrue(opening.reaches_a_station(sample))


class UpdateBoundingBoxTest(unittest.TestCase):
    """When a recomputation moves the framing, and when it leaves it alone."""

    def setUp(self) -> None:
        self.directory = TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "city.json"

    def write(self, document: dict) -> CityConfig:
        self.path.write_text(json.dumps(document), encoding="utf-8")
        return CityConfig.load(self.path)

    def test_a_centre_that_still_shows_a_station_is_left_alone(self) -> None:
        """The cities settled by hand open on their city centre, not on a middle."""
        box = BoundingBox(south=50.5, west=2.9, north=50.8, east=3.3)
        config = self.write(configuration_document(box, 50.63, 3.06, 11.4))
        moved = config.update_bounding_box(
            box,
            station_count=200,
            generated_at="2026-08-19T00:00:00Z",
            opening=OpeningView(latitude=50.65, longitude=3.10, zoom=11.4),
            station_positions=[(50.64, 3.07)],
        )
        self.assertFalse(moved)
        self.assertEqual(config.document["map"]["defaultCenterLatitude"], 50.63)

    def test_a_centre_that_shows_no_station_is_moved(self) -> None:
        """Dubai: inside its box, 769 km from the nearest bike."""
        box = BoundingBox(south=24.43, west=39.54, north=25.33, east=55.51)
        config = self.write(configuration_document(box, 24.883403, 47.523604, 10.5))
        moved = config.update_bounding_box(
            box,
            station_count=206,
            generated_at="2026-08-19T00:00:00Z",
            opening=OpeningView(latitude=25.186528, longitude=55.257875, zoom=10.5),
            station_positions=[(25.2, 55.3)],
        )
        self.assertTrue(moved)
        self.assertEqual(
            config.document["map"]["defaultCenterLongitude"], 55.257875
        )

    def test_a_centre_left_outside_a_box_that_shrank_is_moved(self) -> None:
        """VélôToulouse: one station shed took the western edge with it."""
        box = BoundingBox(south=43.5, west=1.3, north=43.7, east=1.5)
        config = self.write(configuration_document(box, 43.6, 1.0, 11.4))
        moved = config.update_bounding_box(
            box,
            station_count=280,
            generated_at="2026-08-19T00:00:00Z",
            opening=OpeningView(latitude=43.6, longitude=1.44, zoom=11.4),
            station_positions=[(43.6, 1.44)],
        )
        self.assertTrue(moved)
        self.assertEqual(config.document["map"]["defaultCenterLongitude"], 1.44)

    def test_a_framing_that_cannot_be_improved_on_is_not_rewritten(self) -> None:
        """sharedmobility.ch: no framing shows a point of a country-wide spread.

        The stored centre shows none of the witness, and neither would the
        computed one — it is the very centre a previous run wrote. Moving it
        would churn three hundred files at every regeneration and say "moved"
        about a map nobody moved.
        """
        box = BoundingBox(south=45.8, west=5.95, north=47.77, east=10.41)
        config = self.write(configuration_document(box, 47.139650, 7.600948, 10.5))
        moved = config.update_bounding_box(
            box,
            station_count=12879,
            generated_at="2026-08-19T00:00:00Z",
            opening=OpeningView(latitude=47.139650, longitude=7.600948, zoom=10.5),
            station_positions=[(47.13307, 7.24344), (46.19574, 6.14632)],
        )
        self.assertFalse(moved)

    def test_the_box_is_recorded_whatever_happens_to_the_framing(self) -> None:
        box = BoundingBox(south=50.5, west=2.9, north=50.8, east=3.3)
        config = self.write(configuration_document(box, 50.63, 3.06, 11.4))
        wider = BoundingBox(south=50.4, west=2.8, north=50.9, east=3.4)
        config.update_bounding_box(
            wider,
            station_count=210,
            generated_at="2026-08-19T00:00:00Z",
            opening=OpeningView(latitude=50.65, longitude=3.10, zoom=11.4),
            station_positions=[(50.64, 3.07)],
        )
        stored = config.document["boundingBox"]
        self.assertEqual(stored["south"], 50.4)
        self.assertEqual(stored["stationCount"], 210)


if __name__ == "__main__":
    unittest.main()
