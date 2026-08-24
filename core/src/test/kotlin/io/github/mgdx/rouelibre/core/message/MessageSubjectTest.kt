package io.github.mgdx.rouelibre.core.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of which of two messages gets the screen's single banner (SPEC §7.8).
 *
 * The case that brought this rule about: a place received from another
 * application, outside the covered area, on a phone in flight mode. The
 * coverage message went up and the failed refresh took it down before it could
 * be read, leaving "no connection" as the only thing said about a gesture it
 * had nothing to do with.
 */
class MessageSubjectTest {

    private val answer = MessageSubject.Answer
    private val refresh = MessageSubject.Refresh

    @Test
    fun `an answer takes the banner from a failed refresh`() {
        assertTrue(takesTheBanner(showing = refresh, incoming = answer))
    }

    @Test
    fun `a failed refresh leaves an answer where it is`() {
        assertFalse(takesTheBanner(showing = answer, incoming = refresh))
    }

    @Test
    fun `a free banner takes whatever comes`() {
        assertTrue(takesTheBanner(showing = null, incoming = refresh))
        assertTrue(takesTheBanner(showing = null, incoming = answer))
    }

    @Test
    fun `two messages on the same subject replace one another`() {
        assertTrue(takesTheBanner(showing = answer, incoming = answer))
        assertTrue(takesTheBanner(showing = refresh, incoming = refresh))
    }

    /** The reproduction of the defect, played as the sequence it is. */
    @Test
    fun `the coverage message outlives the refresh that fails just after it`() {
        var onShow: MessageSubject? = null

        // The place received lies outside the covered area: the map opens on
        // it and the application says no journey can reach it.
        if (takesTheBanner(onShow, answer)) onShow = answer
        // A few hundred milliseconds later, the map's first refresh comes back
        // empty-handed because the phone is in flight mode.
        if (takesTheBanner(onShow, refresh)) onShow = refresh

        assertEquals(answer, onShow)
    }
}
