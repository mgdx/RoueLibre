package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What "locate me" answers somebody standing outside the city served, once the
 * screen is known not to be designating a point (SPEC §7.1, §15.1).
 */
class ElsewhereAnswerTest {

    @Test
    fun `a network worth proposing where one stands is proposed`() {
        assertEquals(
            ElsewhereAnswer.OfferTheCityHere,
            elsewhereAnswer(cityHereKnown = true, mayPropose = true),
        )
    }

    /**
     * Reopening the dialogue on every press was what made "locate me" unusable
     * to anybody who meant to keep the city they had chosen.
     */
    @Test
    fun `a network already turned down is not proposed again, but where we are is said`() {
        assertEquals(
            ElsewhereAnswer.SayWhereWeAre,
            elsewhereAnswer(cityHereKnown = true, mayPropose = false),
        )
    }

    @Test
    fun `with no network here, the way out is the list of cities`() {
        assertEquals(
            ElsewhereAnswer.OfferTheCityList,
            elsewhereAnswer(cityHereKnown = false, mayPropose = true),
        )
        assertEquals(
            ElsewhereAnswer.OfferTheCityList,
            elsewhereAnswer(cityHereKnown = false, mayPropose = false),
        )
    }
}
