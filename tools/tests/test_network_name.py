"""The name a network is shown under, and what happens when it publishes none.

Every case below is a network of the survey, with the spellings its own
producer publishes. Twenty-one of the three hundred and thirty-seven catalogued
networks used to be listed under an identifier — "bogota-bikebogota — Bogotá"
in the city list, and the same string again in the settings and at the head of
the storage screen.
"""

from __future__ import annotations

import unittest

from discover_networks import display_name_of, name_from_identifier, territory_words


def survey(**fields: object) -> dict:
    """A surveyed network, with only the fields naming it filled in."""
    return {"catalogueTitle": None, "systemName": "", "catalogueName": "", **fields}


class DisplayNameOf(unittest.TestCase):
    """A published name wins; an identifier is only ever a last resort."""

    def test_identifier_gives_way_to_a_name_another_source_holds(self) -> None:
        """"fortworth" is the feed's own id; the registry knows the brand."""
        self.assertEqual(
            display_name_of(survey(
                systemName="fortworth",
                catalogueName="Trinity Metro Bikes",
                mainCity="Fort Worth",
                location="Fort Worth, TX",
            )),
            "Trinity Metro Bikes",
        )

    def test_hyphenated_identifier_gives_way_the_same_way(self) -> None:
        """Sibiu's feed calls itself "sibiu-bikecity"; the registry names it."""
        self.assertEqual(
            display_name_of(survey(
                systemName="sibiu-bikecity",
                catalogueName="Sibiu BikeCity",
                mainCity="Sibiu",
                location="Sibiu",
            )),
            "Sibiu BikeCity",
        )

    def test_identifier_is_spelled_out_when_no_source_names_the_network(self) -> None:
        """Bogotá's three spellings are an identifier and the city itself.

        The city is refused as a name — the row beside it already says Bogotá —
        so nothing is left but the identifier, and it is re-punctuated rather
        than shown as it stands.
        """
        self.assertEqual(
            display_name_of(survey(
                systemName="bogota-bikebogota",
                catalogueName="Bogotá",
                mainCity="Bogotá",
                location="Bogotá",
            )),
            "Bikebogota",
        )

    def test_a_bare_word_stands_as_its_producer_published_it(self) -> None:
        """An opaque name is not an identifier to be tidied up.

        "welo" is how the operator writes it, in all three sources at once, and
        "nextbike" is a brand of fifty networks written lowercase throughout.
        Neither is capitalised on our authority.
        """
        for word, city in (("welo", "Köln"), ("nextbike", "Prague")):
            with self.subTest(word=word):
                self.assertEqual(
                    display_name_of(survey(
                        systemName=word, catalogueName=word,
                        mainCity=city, location=city,
                    )),
                    word,
                )

    def test_an_underscore_is_an_identifier_whatever_its_case(self) -> None:
        """"VAG_Rad" is a file name, not a name; "BIXI" is a name."""
        self.assertEqual(
            display_name_of(survey(
                systemName="VAG_Rad", catalogueName="VAG_Rad",
                mainCity="Nürnberg", location="Nürnberg",
            )),
            "VAG Rad",
        )
        self.assertEqual(
            display_name_of(survey(
                systemName="Bixi_MTL", catalogueName="BIXI",
                mainCity="Montréal", location="Montreal, QC",
            )),
            "BIXI",
        )

    def test_a_published_name_is_left_exactly_as_it_is(self) -> None:
        """The common case: nothing about this changes."""
        self.assertEqual(
            display_name_of(survey(
                catalogueTitle="VLS Vélo'v Métropole de Lyon",
                systemName="Vélo'v", catalogueName="Velo'v",
                mainCity="Lyon", location="Lyon",
            )),
            "Vélo'v",
        )


class NameFromIdentifier(unittest.TestCase):
    """Re-punctuating an identifier: it invents nothing and drops the city."""

    def test_drops_the_word_that_only_repeats_the_conurbation(self) -> None:
        forgettable = territory_words(
            {"mainCity": "Québec", "location": "Ville de Québec"}
        )
        self.assertEqual(name_from_identifier("avelo-quebec", forgettable), "Avelo")

    def test_keeps_an_internal_capital_the_producer_wrote(self) -> None:
        forgettable = territory_words({"mainCity": "Dej"})
        self.assertEqual(name_from_identifier("dej-bikeCity", forgettable), "BikeCity")

    def test_keeps_the_identifier_whole_when_all_of_it_is_territory(self) -> None:
        """Nothing would be left, and a nameless row reads as a defect."""
        forgettable = territory_words({"mainCity": "Fort Worth"})
        self.assertEqual(name_from_identifier("fort-worth", forgettable), "Fort Worth")


if __name__ == "__main__":
    unittest.main()
