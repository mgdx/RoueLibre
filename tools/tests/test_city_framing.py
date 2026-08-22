"""Every city served must open on a map showing its own stations (SPEC.md §4).

This is the guard rail Dubai went through. Its configuration opened the map
769 km from the nearest bike, in the Saudi desert, and everything else about
the city was right — the tiles, the routing graph, the addresses and the live
availability were all on the device and all correct. Nothing failed; the map
was simply pointed somewhere else. Only a check on the framing itself catches
that, so it is checked here, on every configuration, at every run.

The check is run against ``stationSamples`` because that is what a
configuration knows about where its stations are without calling a feed
(§15.1), and eight positions spread through a network are a sparse witness: on
a network the size of a country they can all be forty kilometres from a framing
that is perfectly good. So the test asks the question that witness can answer —
is ONE of them within a screenful of where the map opens — and leaves the
strict reading to ``tools/compute_bbox.py``, which has every station in hand
when it writes the framing. What is caught here is a map that has left its
network altogether, which is what Dubai's had done, by a factor of twelve.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from city_config import BoundingBox, OpeningView

CITIES_DIRECTORY = Path(__file__).resolve().parent.parent.parent / "config" / "cities"


def city_configurations() -> list[tuple[str, dict]]:
    """Every city configuration of the repository, by file name."""
    return [
        (path.stem, json.loads(path.read_text(encoding="utf-8")))
        for path in sorted(CITIES_DIRECTORY.glob("*.json"))
    ]


class CityFramingTest(unittest.TestCase):
    """The opening framing of the configurations the application ships with."""

    def setUp(self) -> None:
        self.cities = city_configurations()
        self.assertTrue(self.cities, f"No configuration in {CITIES_DIRECTORY}")

    def test_the_opening_view_shows_a_station(self) -> None:
        """A map that opens on no station at all opens on nothing at all."""
        for name, document in self.cities:
            with self.subTest(city=name):
                opening = OpeningView(
                    latitude=document["map"]["defaultCenterLatitude"],
                    longitude=document["map"]["defaultCenterLongitude"],
                    zoom=document["map"]["defaultZoom"],
                )
                samples = [
                    (latitude, longitude)
                    for latitude, longitude in document.get("stationSamples", [])
                ]
                self.assertTrue(
                    samples,
                    "no stationSamples: run tools/sample_stations.py",
                )
                self.assertTrue(
                    opening.reaches_a_station(samples),
                    f"the map opens at {opening.latitude}, {opening.longitude} "
                    f"at zoom {opening.zoom}, and not one of the "
                    f"{len(samples)} sampled stations is within a screenful "
                    f"of it — re-run tools/compute_bbox.py --config "
                    f"{CITIES_DIRECTORY.name}/{name}.json",
                )

    def test_the_opening_centre_lies_inside_the_box(self) -> None:
        """Outside its box, a centre opens on tiles that were never cut."""
        for name, document in self.cities:
            with self.subTest(city=name):
                stored = document["boundingBox"]
                box = BoundingBox(
                    south=stored["south"],
                    west=stored["west"],
                    north=stored["north"],
                    east=stored["east"],
                )
                self.assertTrue(
                    box.contains(
                        document["map"]["defaultCenterLatitude"],
                        document["map"]["defaultCenterLongitude"],
                    ),
                    "the opening centre falls outside the reference box",
                )


if __name__ == "__main__":
    unittest.main()
