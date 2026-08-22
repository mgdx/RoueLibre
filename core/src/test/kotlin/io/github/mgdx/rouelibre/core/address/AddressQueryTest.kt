package io.github.mgdx.rouelibre.core.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of taking a query apart into a house number and search words. */
class AddressQueryTest {

    private val normalizer = testNormalizer()

    private fun parse(raw: String) = normalizer.parseQuery(raw)

    @Test
    fun `a leading house number is recognised`() {
        val query = parse("12 rue Nationale")

        assertEquals(12, query.houseNumber)
        assertEquals("", query.houseNumberSuffix)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `a house number at the end of the query is recognised too`() {
        // Both writing orders are in use.
        val query = parse("rue Nationale 12")

        assertEquals(12, query.houseNumber)
        assertEquals(listOf("rue", "nationale"), query.terms)
    }

    @Test
    fun `the repetition mark travels with the number`() {
        assertEquals("bis", parse("12 bis rue Nationale").houseNumberSuffix)
        assertEquals("ter", parse("rue Nationale 12 ter").houseNumberSuffix)
        assertEquals("a", parse("5 A boulevard de la Liberté").houseNumberSuffix)
    }

    @Test
    fun `a bare number does not become an empty search`() {
        // Without words there is no street to search for: better to treat the
        // number as an ordinary word than to search for "nothing".
        val query = parse("12")

        assertNull(query.houseNumber)
        assertEquals(listOf("12"), query.terms)
    }

    @Test
    fun `an implausible number is not a house number`() {
        // 59000 is a postcode, not a house number.
        val query = parse("59000 rue Nationale")

        assertNull(query.houseNumber)
    }

    @Test
    fun `a postcode is removed from the searched words`() {
        // The index does not hold postcodes in full text: keeping it would
        // fail an otherwise sound query.
        val query = parse("rue Nationale 59800 Lille")

        assertEquals(listOf("rue", "nationale", "lille"), query.terms)
    }

    @Test
    fun `an empty query searches for nothing`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
        assertTrue(parse("...").isEmpty)
    }

    @Test
    fun `the query's abbreviations are expanded as they are at indexing time`() {
        assertEquals(listOf("boulevard", "victor", "hugo"), parse("bd victor hugo").terms)
    }

    @Test
    fun `the two orders accepted before still read the same`() {
        val leading = parse("12 bis rue Nationale")
        assertEquals(12, leading.houseNumber)
        assertEquals("bis", leading.houseNumberSuffix)
        assertEquals(listOf("rue", "nationale"), leading.terms)

        val trailing = parse("rue Nationale 12 ter")
        assertEquals(12, trailing.houseNumber)
        assertEquals("ter", trailing.houseNumberSuffix)
        assertEquals(listOf("rue", "nationale"), trailing.terms)

        val letter = parse("5 A boulevard de la Liberté")
        assertEquals(5, letter.houseNumber)
        assertEquals("a", letter.houseNumberSuffix)
        assertEquals(listOf("boulevard", "de", "la", "liberte"), letter.terms)
    }

    @Test
    fun `a house number between the street and the town is recognised`() {
        // "Street, number, town" is the ordinary order of German, Spanish,
        // Italian, Dutch, Polish, Czech and Portuguese, and each translation
        // writes its search prompt in the order of its own language.
        val spanish = TestRules.of("es").parseQuery("Gran Vía 12 Madrid")

        assertEquals(12, spanish.houseNumber)
        assertEquals(listOf("gran", "via", "madrid"), spanish.terms)

        val german = TestRules.of("de").parseQuery("Bahnhofstraße 12 Berlin")

        assertEquals(12, german.houseNumber)
        assertEquals(listOf("bahnhofstrasse", "berlin"), german.terms)
    }

    @Test
    fun `a postcode does not hide the house number standing before it`() {
        // The postcode is removed before the number is looked for: written in
        // full, an address puts it between the two.
        val query = TestRules.of("es").parseQuery("Gran Vía 12 28013 Madrid")

        assertEquals(12, query.houseNumber)
        assertEquals(listOf("gran", "via", "madrid"), query.terms)

        // Without the town, the same address falls back on the trailing case.
        val withoutTown = TestRules.of("es").parseQuery("Gran Vía 12 28013")

        assertEquals(12, withoutTown.houseNumber)
        assertEquals(listOf("gran", "via"), withoutTown.terms)
    }

    @Test
    fun `the repetition mark travels with a number between street and town`() {
        val query = TestRules.of("de").parseQuery("Bahnhofstraße 12 a Berlin")

        assertEquals(12, query.houseNumber)
        assertEquals("a", query.houseNumberSuffix)
        assertEquals(listOf("bahnhofstrasse", "berlin"), query.terms)
    }

    @Test
    fun `a number the street's own name carries is not a house number`() {
        // All four are streets of networks the application serves: taking
        // their number for an address would search a street nobody named.
        val spanish = TestRules.of("es")
        assertNamesTheStreet(
            spanish,
            "Avenida 9 de Julio",
            listOf("avenida", "9", "de", "julio"),
        )
        assertNamesTheStreet(
            spanish,
            "Calle 20 de Noviembre",
            listOf("calle", "20", "de", "noviembre"),
        )
        assertNamesTheStreet(
            normalizer,
            "Rue du 8 Mai 1945",
            listOf("rue", "du", "8", "mai", "1945"),
        )
        assertNamesTheStreet(
            normalizer,
            "Rue du 11 Novembre",
            listOf("rue", "du", "11", "novembre"),
        )
        assertNamesTheStreet(
            TestRules.of("de"),
            "Straße des 17. Juni",
            listOf("strasse", "des", "17", "juni"),
        )
    }

    @Test
    fun `a number opening the query is an address whatever the name that follows`() {
        // Nothing stands before it to make it part of a name, so the second
        // number of the street's own date takes nothing away from it.
        val query = parse("12 rue du 8 Mai 1945")

        assertEquals(12, query.houseNumber)
        assertEquals(listOf("rue", "du", "8", "mai", "1945"), query.terms)
    }

    @Test
    fun `an article after the number is read as an article, not as a mark`() {
        // "a" is a repetition mark in German and a preposition in Italian.
        // Reading "via Roma 12 a Milano" the first way would eat the
        // preposition; the number is given up instead, which costs a doorway
        // where the other would cost the address.
        val query = TestRules.of("it").parseQuery("via Roma 12 a Milano")

        assertNull(query.houseNumber)
        assertEquals(listOf("via", "roma", "12", "a", "milano"), query.terms)
    }

    /** A query naming a street with a number in it keeps that name whole. */
    private fun assertNamesTheStreet(
        rules: AddressNormalizer,
        raw: String,
        expectedTerms: List<String>,
    ) {
        val query = rules.parseQuery(raw)

        assertNull("\"$raw\" names a street, it does not give a house number", query.houseNumber)
        assertEquals(expectedTerms, query.terms)
    }
}
