package io.github.mgdx.rouelibre.ui.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the quotation the "no address found" message carries.
 *
 * What a user types is quoted back to them, and what a user types has no
 * length: the message has to stay a message rather than become the paste.
 */
class AddressLabelsTest {

    @Test
    fun `quotes an ordinary query back word for word`() {
        val typed = "12 rue Nationale, Lille"
        assertEquals(typed, boundedQuery(typed))
    }

    @Test
    fun `quotes the longest real address back word for word`() {
        // Longer than the addresses the index holds, and still quoted whole:
        // the bound must never trim a search somebody actually made.
        val typed = "246 boulevard de la Liberte, Villeneuve-d'Ascq"
        assertEquals(typed, boundedQuery(typed))
    }

    @Test
    fun `cuts a pasted query short`() {
        val bounded = boundedQuery("q".repeat(400))
        assertTrue("stays short: $bounded", bounded.length < 70)
        assertTrue("says it was cut: $bounded", bounded.endsWith(Typography.ellipsis))
    }

    @Test
    fun `closes the cut on the last word kept`() {
        // "rue    …" reads as a fault in the message; the trailing space goes
        // with what the cut dropped.
        val bounded = boundedQuery("rue " + " ".repeat(80))
        assertEquals("rue" + Typography.ellipsis, bounded)
    }
}
