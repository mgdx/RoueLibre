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
    return OsmPiece(name=name, city="", postcode="", positions=list(points),
                    is_way=True)


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


class OneStreetTaggedTwoWays(unittest.TestCase):
    """A street whose numbers disagree on their town is still one street."""

    def test_a_district_and_its_city_on_one_street_are_merged(self) -> None:
        """Athol Street, whose houses say "Boston" or "Allston" in turn."""
        streets = group([
            number("Athol Street", "Boston" if value % 4 else "Allston",
                   value, offset(CAMBRIDGE_MIT, value * 10))
            for value in range(1, 20)
        ])
        (street,) = streets.values()
        self.assertEqual("Boston", street.city)
        self.assertEqual(19, len(street.numbers))

    def test_two_towns_meeting_at_one_end_stay_apart(self) -> None:
        """Only the last house of each lies near the other."""
        streets = group(
            [number("Main Street", "Cambridge", value, offset(CAMBRIDGE_MIT, -value * 50))
             for value in range(0, 10)]
            + [number("Main Street", "Somerville", value, offset(CAMBRIDGE_MIT, value * 50 + 60))
               for value in range(0, 10)]
        )
        self.assertEqual({"Cambridge", "Somerville"},
                         {street.city for street in streets.values()})


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

    def test_the_order_of_the_extract_does_not_cut_a_street(self) -> None:
        """The two ends read before the stretch joining them."""
        start = CAMBRIDGE_MIT
        streets = group([
            way("Memorial Drive", start, offset(start, 300)),
            way("Memorial Drive", offset(start, 600), offset(start, 900)),
            way("Memorial Drive", offset(start, 300), offset(start, 600)),
        ], [place("Cambridgeport", offset(start, -100)),
            place("Riverside", offset(start, 1000))])
        self.assertEqual(1, len(streets))

    def test_an_unnumbered_stretch_follows_the_numbered_one_it_continues(self) -> None:
        """Beyond the radius, along the street rather than to the nearest place."""
        start = CAMBRIDGE_MIT
        streets = group([
            number("Massachusetts Avenue", "Cambridge", 77, start),
            way("Massachusetts Avenue", offset(start, 100), offset(start, 400)),
            way("Massachusetts Avenue", offset(start, 400), offset(start, 1200)),
        ], [place("Somerville", offset(start, 1200))])
        self.assertEqual(["Cambridge"], [street.city for street in streets.values()])

    def test_without_any_place_the_municipality_stays_blank(self) -> None:
        streets = group([way("Memorial Drive", CAMBRIDGE_MIT)])
        self.assertEqual([""], [street.city for street in streets.values()])


class DisplayedName(unittest.TestCase):
    """A street is shown as the map writes it, not as its numbers do."""

    def test_the_way_spelling_wins_over_the_numbers(self) -> None:
        streets = group([
            number("Georgernes verft", "Bergen", 6, CAMBRIDGE_MIT),
            way("Georgernes Verft", CAMBRIDGE_MIT, offset(CAMBRIDGE_MIT, 100)),
        ])
        self.assertEqual(["Georgernes Verft"],
                         [street.display_name for street in streets.values()])


if __name__ == "__main__":
    unittest.main()
