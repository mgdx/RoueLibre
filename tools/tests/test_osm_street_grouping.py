"""Which municipality an OpenStreetMap street belongs to (SPEC.md §4.3, §15).

The bug these cases stand for was reported from Cambridge, Massachusetts:
none of its addresses could be found. OpenStreetMap tags ``addr:city`` on the
house numbers and almost never on the ways, and the index keyed a way on its
name alone — so the Massachusetts Avenues of Boston, Cambridge, Arlington and
Lexington became a single street labelled "Lexington", and "77 Massachusetts
Avenue" was placed in Arlington rather than at MIT. Every network whose
addresses come from OpenStreetMap had the same defect: the Bluebikes index
held 18 043 streets for 17 969 distinct names.

The coordinates below are real enough to be read on a map: Cambridge around
MIT, Arlington Center, Medford Square.
"""

from __future__ import annotations

import unittest

from address_normalization import normalizer_for
from build_address_index import KIND_PLACE, OsmPiece, Street, group_osm_streets

CAMBRIDGE_MIT = (42.3593, -71.0935)
ARLINGTON_CENTER = (42.4154, -71.1565)
MEDFORD_SQUARE = (42.4184, -71.1062)


def offset(point: tuple[float, float], north_meters: float) -> tuple[float, float]:
    """A point that many metres north of another."""
    return point[0] + north_meters / 111_320.0, point[1]


def number(name: str, city: str, value: int, at: tuple[float, float]) -> OsmPiece:
    """A house number, as OpenStreetMap maps one."""
    return OsmPiece(name=name, city=city, postcode="", positions=[at],
                    number=(value, ""))


def way(name: str, *points: tuple[float, float]) -> OsmPiece:
    """A stretch of street, with no municipality, as OpenStreetMap maps one."""
    return OsmPiece(name=name, city="", postcode="", positions=list(points))


def place(name: str, at: tuple[float, float]) -> Street:
    """An inhabited place, as `read_osm_municipalities` returns one."""
    street = Street(display_name=name, city=name, postcode="", kind=KIND_PLACE)
    street.latitudes.append(at[0])
    street.longitudes.append(at[1])
    return street


def group(pieces: list[OsmPiece], places: list[Street] = ()) -> dict[str, Street]:
    streets, _ = group_osm_streets(pieces, list(places), normalizer_for("en"))
    return streets


class HomonymousStreets(unittest.TestCase):
    """Two streets of one name in two municipalities are two streets."""

    def test_each_municipality_keeps_its_own_street(self) -> None:
        streets = group([
            way("Massachusetts Avenue", CAMBRIDGE_MIT, offset(CAMBRIDGE_MIT, 200)),
            number("Massachusetts Avenue", "Cambridge", 77, CAMBRIDGE_MIT),
            way("Massachusetts Avenue", ARLINGTON_CENTER),
            number("Massachusetts Avenue", "Arlington", 77, ARLINGTON_CENTER),
        ])
        self.assertEqual(
            {"Cambridge", "Arlington"},
            {street.city for street in streets.values()},
        )

    def test_a_repeated_number_stays_in_its_own_municipality(self) -> None:
        """The number the merge placed in Arlington, when it is at MIT."""
        streets = group([
            number("Massachusetts Avenue", "Cambridge", 77, CAMBRIDGE_MIT),
            number("Massachusetts Avenue", "Arlington", 77, ARLINGTON_CENTER),
        ])
        by_city = {street.city: street for street in streets.values()}
        self.assertEqual([CAMBRIDGE_MIT], by_city["Cambridge"].numbers[(77, "")])
        self.assertEqual([ARLINGTON_CENTER], by_city["Arlington"].numbers[(77, "")])


class PiecesWithoutMunicipality(unittest.TestCase):
    """What carries no ``addr:city`` takes it from what lies around it."""

    def test_a_way_joins_the_numbers_mapped_along_it(self) -> None:
        streets = group([
            number("Ames Street", "Cambridge", 1, CAMBRIDGE_MIT),
            way("Ames Street", offset(CAMBRIDGE_MIT, 150), offset(CAMBRIDGE_MIT, 300)),
        ], [place("Medford", MEDFORD_SQUARE)])
        self.assertEqual(["Cambridge"], [street.city for street in streets.values()])
        (street,) = streets.values()
        self.assertEqual(3, len(street.latitudes))

    def test_a_way_far_from_the_numbers_does_not_join_them(self) -> None:
        """Beyond the radius, a homonym is another street, however named."""
        streets = group([
            number("Main Street", "Cambridge", 1, CAMBRIDGE_MIT),
            way("Main Street", MEDFORD_SQUARE),
        ], [place("Medford", MEDFORD_SQUARE)])
        self.assertEqual(
            {"Cambridge", "Medford"},
            {street.city for street in streets.values()},
        )

    def test_a_number_without_municipality_joins_its_street(self) -> None:
        streets = group([
            number("Ames Street", "Cambridge", 1, CAMBRIDGE_MIT),
            number("Ames Street", "", 3, offset(CAMBRIDGE_MIT, 40)),
        ])
        (street,) = streets.values()
        self.assertEqual({(1, ""), (3, "")}, set(street.numbers))

    def test_an_unnumbered_street_is_not_cut_at_each_suburb(self) -> None:
        """Its stretches join one another before the places are asked again."""
        start = CAMBRIDGE_MIT
        streets = group([
            way("Memorial Drive", start, offset(start, 300)),
            way("Memorial Drive", offset(start, 300), offset(start, 600)),
        ], [place("Cambridgeport", offset(start, -100)),
            place("Riverside", offset(start, 700))])
        self.assertEqual(["Cambridgeport"], [street.city for street in streets.values()])

    def test_without_any_place_the_municipality_stays_blank(self) -> None:
        streets = group([way("Memorial Drive", CAMBRIDGE_MIT)])
        self.assertEqual([""], [street.city for street in streets.values()])


if __name__ == "__main__":
    unittest.main()
