package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

/**
 * Tests of the one door every figure the resources do not write goes through
 * (SPEC §9).
 *
 * What is pinned here is the rule the station row broke: "59260 · ٢٠ docks"
 * held two numeration systems on one line, the postcode and the counterpart
 * having reached the screen through `toString()` while the plural beside them
 * was written by Android in the digits of the locale served.
 */
class FiguresTest {

    /** The numeration a device set to Arabic is served. */
    private val arabicDigits = zeroDigitOf(Locale.forLanguageTag("en-u-nu-arab"))

    /** What English and French are served, and what the interface had. */
    private val latinDigits = zeroDigitOf(Locale.FRENCH)

    @Test
    fun `moves a count to the numeration served`() {
        assertEquals("٢٠", inDigitsOf(20.toString(), arabicDigits))
        assertEquals("٠", inDigitsOf(0.toString(), arabicDigits))
        assertEquals("١٤٣", inDigitsOf(143.toString(), arabicDigits))
    }

    /**
     * A postcode is transliterated rather than formatted, which is the whole
     * reason this door takes a string as readily as a count.
     */
    @Test
    fun `moves a postcode digit for digit`() {
        assertEquals("٥٩٢٦٠", inDigitsOf("59260", arabicDigits))
        // Grouped, "59 260" would be what a number format writes and what a
        // postcode never is.
        assertEquals(5, inDigitsOf("59260", arabicDigits).length)
        // A code beginning with a nought keeps it: it is a label, not a number.
        assertEquals("٠٦٠٠٠", inDigitsOf("06000", arabicDigits))
        // And a code that is not made of figures alone comes back whole.
        assertEquals("SW١A ٢AA", inDigitsOf("SW1A 2AA", arabicDigits))
    }

    /** Everything that is not a digit is left exactly where it was. */
    @Test
    fun `leaves the words and the separators alone`() {
        assertEquals("٥٩٢٦٠ · ٢٠ docks", inDigitsOf("59260 · 20 docks", arabicDigits))
    }

    /**
     * The guard on this repair: English and French must be written exactly as
     * they were, character for character — and, Latin digits asking for no work
     * at all, they come back as the very string handed in.
     */
    @Test
    fun `English and French are handed back untouched`() {
        assertEquals('0', latinDigits)
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH, Locale.UK)) {
            val zero = zeroDigitOf(locale)
            assertEquals("Latin digits in $locale", '0', zero)
            val row = "59260 · 20 docks"
            assertSame("nothing rewritten in $locale", row, inDigitsOf(row, zero))
            assertEquals("12", inDigitsOf(12.toString(), zero))
        }
    }
}
