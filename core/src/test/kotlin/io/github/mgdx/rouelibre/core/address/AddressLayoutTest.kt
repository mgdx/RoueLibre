package io.github.mgdx.rouelibre.core.address

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that an address is written the way its own country writes it.
 *
 * The defect these were written against: the layout lived in
 * `address_with_number`, a string resource, so it followed whoever was reading.
 * One Lyon address came out six different ways across the translations —
 * "Avenue du 11 Novembre 1918 6" for a Polish reader, "…, 6" for a Portuguese
 * one — and none of the six was France's. The rule is the other way round
 * (SPEC §4.3), so the telling test is the one that calls **the same code** on
 * two bases and gets two layouts back.
 */
class AddressLayoutTest {

    @Test
    fun `the base decides the layout, not the reader`() {
        val french = addressLayoutOf("fr").write("Rue Nationale", "12")
        val polish = addressLayoutOf("pl").write("Marszałkowska", "12")
        assertEquals("12 Rue Nationale", french)
        assertEquals("Marszałkowska 12", polish)
    }

    @Test
    fun `a French address is untouched by the language reading it`() {
        // There is nothing to parameterise on: the call carries no interface
        // language at all, which is the point. The French base answers the
        // French layout to every reader there is.
        assertEquals("12 Rue Nationale", addressLayoutOf("fr").write("Rue Nationale", "12"))
    }

    @Test
    fun `Spanish sets the number off with a comma`() {
        assertEquals("Gran Vía, 12", addressLayoutOf("es").write("Gran Vía", "12"))
    }

    @Test
    fun `Portuguese sets it off the same way`() {
        assertEquals("Rua Augusta, 12", addressLayoutOf("pt").write("Rua Augusta", "12"))
    }

    @Test
    fun `German, Italian, Dutch and Czech close with the number`() {
        assertEquals("Bahnhofstraße 12", addressLayoutOf("de").write("Bahnhofstraße", "12"))
        assertEquals("Via Roma 12", addressLayoutOf("it").write("Via Roma", "12"))
        assertEquals("Kalverstraat 12", addressLayoutOf("nl").write("Kalverstraat", "12"))
        assertEquals("Národní 12", addressLayoutOf("cs").write("Národní", "12"))
    }

    @Test
    fun `a German letter is closed up against the number`() {
        // The space this used to carry was chosen for the 539 spelled-out
        // marks in the German index and paid for by its 755 188 letters:
        // "Hauptstraße 12 a" is not how the country writes it.
        assertEquals(
            "Hauptstraße 12a",
            addressLayoutOf("de").write("Hauptstraße", "12", "a"),
        )
    }

    @Test
    fun `an Italian letter is closed up too`() {
        assertEquals("Via Roma 12A", addressLayoutOf("it").write("Via Roma", "12", "A"))
    }

    @Test
    fun `a Czech address joins its two numbers with a slash`() {
        // What follows a Czech number is a second number, not a mark: the
        // parcel's 185 and the street's 38 are one address, "185/38". Closed
        // up they would read "18538", which exists nowhere — the defect this
        // entry was written against.
        assertEquals(
            "Gen. Štefánika 185/38",
            addressLayoutOf("cs").write("Gen. Štefánika", "185", "38"),
        )
    }

    @Test
    fun `Slovak addresses are numbered the Czech way`() {
        // Slovakia is served and its translation has not landed yet: without
        // an entry it would fall on the English fallback and print
        // "12 Hlavná", wrong in both order and punctuation.
        assertEquals("Hlavná 185/38", addressLayoutOf("sk").write("Hlavná", "185", "38"))
    }

    @Test
    fun `a French repetition mark is a word and takes a space`() {
        assertEquals(
            "12 bis Rue Nationale",
            addressLayoutOf("fr").write("Rue Nationale", "12", "bis"),
        )
    }

    @Test
    fun `a Polish repetition mark is closed up against the number`() {
        assertEquals(
            "Marszałkowska 12A",
            addressLayoutOf("pl").write("Marszałkowska", "12", "A"),
        )
    }

    @Test
    fun `the suffix separator does not follow the street separator`() {
        // Dutch, French and Czech write three different things after the
        // number — a letter closed up, a word spaced, a second number slashed
        // — while nothing else about their layouts disagrees. That is why the
        // two separators are two fields rather than one.
        assertEquals("Kalverstraat 12A", addressLayoutOf("nl").write("Kalverstraat", "12", "A"))
        assertEquals(
            "12 bis Rue Nationale",
            addressLayoutOf("fr").write("Rue Nationale", "12", "bis"),
        )
        assertEquals("Národní 25/17", addressLayoutOf("cs").write("Národní", "25", "17"))
    }

    @Test
    fun `a base the table does not name falls back on English`() {
        // Slovene, Finnish, Japanese: no entry, and the address is still
        // written rather than dropped. Slovene is the reminder that a language
        // absent here is a language nobody has counted yet, not a language
        // that writes English's way.
        assertEquals("12 Trubarjeva cesta", addressLayoutOf("sl").write("Trubarjeva cesta", "12"))
        assertEquals("12 Aleksanterinkatu", addressLayoutOf("fi").write("Aleksanterinkatu", "12"))
        assertEquals("12 Ginza", addressLayoutOf("ja").write("Ginza", "12", ""))
    }

    @Test
    fun `the fallback spaces the repetition mark`() {
        assertEquals("12 bis Main Street", addressLayoutOf("xx").write("Main Street", "12", "bis"))
    }

    @Test
    fun `the digits are taken as given`() {
        // The reader's numbering system is applied before this module sees the
        // number (SPEC §9): the layout moves it, and never rewrites it.
        assertEquals("١٢ Rue Nationale", addressLayoutOf("fr").write("Rue Nationale", "١٢"))
    }
}
