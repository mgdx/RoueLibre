"""What the "about" screen credits for a feed, and in whose words (SPEC.md §4.5).

The cases are the ones the surveyed networks actually publish. A third of those
naming a licence name it by address alone, with no "license_id" beside it, and
two thirds name none at all — which is a fact about the data worth stating,
not a gap to leave blank.
"""

from __future__ import annotations

import unittest

from discover_networks import attribution_of, licence_from_url


class LicenceFromUrl(unittest.TestCase):
    """A "license_url" is a statement; it is read, never guessed at."""

    def test_names_the_licence_the_address_serves(self) -> None:
        self.assertEqual(
            licence_from_url("https://cdla.dev/permissive-2-0/"),
            "CDLA-Permissive-2.0",
        )
        self.assertEqual(
            licence_from_url(
                "https://developer.jcdecaux.com/files/Open-Licence-fr.pdf"
            ),
            "Licence Ouverte",
        )

    def test_ignores_what_leaves_the_document_unchanged(self) -> None:
        """A trailing slash, a scheme, a translated deed: same licence."""
        for address in (
            "https://creativecommons.org/licenses/by/4.0/deed.ja",
            "http://creativecommons.org/licenses/by/4.0/",
            "https://www.creativecommons.org/licenses/by/4.0",
        ):
            with self.subTest(address=address):
                self.assertEqual(licence_from_url(address), "CC BY 4.0")

    def test_an_unknown_address_names_nothing(self) -> None:
        """Silence beats a guess: a wrong licence is worse than an unnamed one."""
        self.assertEqual(licence_from_url("https://example.com/terms"), "")
        self.assertEqual(licence_from_url(""), "")


class Attribution(unittest.TestCase):
    """What the screen shows, in the words the producer's country writes in."""

    def test_a_licence_named_by_address_alone_still_reaches_the_screen(self) -> None:
        survey = {
            "country": "GB",
            "displayName": "Beryl",
            "systemOperator": "205",
            "licenceUrl": "https://cdla.dev/permissive-2-0/",
        }
        attribution_of(survey)
        self.assertEqual(survey["attribution"], "Data Beryl — 205, licence CDLA-Permissive-2.0")

    def test_an_unlicensed_feed_says_so(self) -> None:
        survey = {"country": "GB", "displayName": "kitchen", "systemOperator": "kitchen"}
        attribution_of(survey)
        self.assertTrue(survey["attribution"].endswith("licence not stated by the operator"))

    def test_a_french_network_is_credited_in_french(self) -> None:
        """As an address is written the way its own country writes it (§15.1)."""
        survey = {"country": "FR", "displayName": "pony", "systemOperator": "pony"}
        attribution_of(survey)
        self.assertEqual(
            survey["attribution"],
            "Données pony — pony, licence non précisée par l'opérateur",
        )

    def test_a_licence_already_carrying_the_word_does_not_stammer(self) -> None:
        survey = {
            "country": "FR",
            "displayName": "Vélib'",
            "systemOperator": "Smovengo",
            "licenceUrl": "https://developer.jcdecaux.com/files/Open-Licence-fr.pdf",
        }
        attribution_of(survey)
        self.assertEqual(survey["attribution"], "Données Vélib' — Smovengo, Licence Ouverte")


if __name__ == "__main__":
    unittest.main()
