"""How a house number is taken apart, and whose spelling survives it (SPEC.md §4.3).

The index holds a house number as a number and a repetition mark, because the
BAN holds them in two columns and OpenStreetMap holds them in one string. The
splitting is this script's business. **The spelling is not.**

The case is what these cases are about, and the bug they stand for is real: the
mark was lowercased on the way in, which reads as harmless while France is the
only country served — "12 BIS" and "12 bis" are the same mark, and the BAN
writes it either way. It is not harmless anywhere else. Poland writes "12A",
the Netherlands writes "12A", Romania and Spain write "12A"; Germany writes
"12a". Which of the two a country writes is a fact about that country, the
source carries it, and a lowercased index cannot give it back.

The size of what it reached: 2 902 667 house numbers over 307 of the 332 cities
served carry a letter in this field.
"""

from __future__ import annotations

import unittest

from build_address_index import parse_house_number, split_house_number


class ParseHouseNumber(unittest.TestCase):
    """What both sources funnel through, and where the spelling is decided.

    The BAN arrives here as two columns and OpenStreetMap as the two halves of
    `split_house_number`. **This is the function the bug lived in**, and the
    reason it is worth saying: the first repair was written into the splitting
    instead, which reads as the obvious place and changed nothing at all,
    because everything is lowercased again one call later. A test that only
    exercised the splitting would have passed over the defect twice.
    """

    def test_a_capital_letter_stays_capital(self) -> None:
        """The case the bug destroyed, on the path that destroyed it."""
        self.assertEqual((12, "A"), parse_house_number("12", "A"))

    def test_a_small_letter_stays_small(self) -> None:
        self.assertEqual((12, "a"), parse_house_number("12", "a"))

    def test_a_written_mark_keeps_the_source_spelling(self) -> None:
        self.assertEqual((12, "BIS"), parse_house_number("12", "BIS"))
        self.assertEqual((12, "bis"), parse_house_number("12", "bis"))

    def test_the_two_sources_agree_through_the_splitting(self) -> None:
        """OpenStreetMap's one string reaches the same place as the BAN's two."""
        self.assertEqual((12, "A"), parse_house_number(*split_house_number("12A")))

    def test_a_row_with_no_usable_number_is_dropped(self) -> None:
        """It carries no more information than the street it sits on."""
        self.assertIsNone(parse_house_number("", ""))
        self.assertIsNone(parse_house_number("0", ""))
        self.assertIsNone(parse_house_number("sans numéro", ""))

    def test_a_superscript_is_not_a_digit(self) -> None:
        """`isdecimal` is what stops "23²" taking a whole city's index down."""
        self.assertEqual((23, "²"), parse_house_number(*split_house_number("23²")))


class SplitHouseNumber(unittest.TestCase):
    """The number, the mark, and the spelling the source gave them."""

    def test_a_bare_number_has_no_mark(self) -> None:
        self.assertEqual(("12", ""), split_house_number("12"))

    def test_the_french_repetition_marks_are_words(self) -> None:
        """"12 bis" and "12 ter": the BAN's own marks, spelled as it spells them."""
        self.assertEqual(("12", "bis"), split_house_number("12 bis"))
        self.assertEqual(("12", "ter"), split_house_number("12 ter"))

    def test_a_capital_letter_stays_capital(self) -> None:
        """The case the bug destroyed.

        Poland, the Netherlands, Romania and Spain write the letter capitalised
        and glue it to the number. Reading "12A" back as "12a" is that reader's
        address written the way another country writes it.
        """
        self.assertEqual(("12", "A"), split_house_number("12A"))
        self.assertEqual(("12", "A"), split_house_number("12 A"))
        self.assertEqual(("5", "B"), split_house_number("5-B"))

    def test_a_small_letter_stays_small(self) -> None:
        """Germany writes "12a", and that is not to be capitalised either."""
        self.assertEqual(("12", "a"), split_house_number("12a"))

    def test_a_written_mark_keeps_the_source_spelling(self) -> None:
        """Neither case is imposed on a word: the source decides.

        A BAN row saying "BIS" and one saying "bis" are the same mark and this
        script says so about neither. Folding them together belongs to the
        search, which normalises the query and the index alike (SPEC §4.3),
        not to the file the reader is shown.
        """
        self.assertEqual(("12", "BIS"), split_house_number("12 BIS"))

    def test_a_separator_is_not_part_of_the_mark(self) -> None:
        self.assertEqual(("12", "14"), split_house_number("12-14"))
        self.assertEqual(("7", "9"), split_house_number("7/9"))

    def test_a_superscript_is_a_mark_and_not_a_digit(self) -> None:
        """"23²" is a real Karlsruhe address.

        `isdecimal` rather than `isdigit` is what keeps the superscript out of
        the number: read as a digit, `int()` refuses the whole string and takes
        the city's index down with it.
        """
        self.assertEqual(("23", "²"), split_house_number("23²"))

    def test_a_number_with_no_digits_yields_none(self) -> None:
        self.assertEqual(("", "sans numéro"), split_house_number("sans numéro"))


if __name__ == "__main__":
    unittest.main()
