package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.data.NEVER_LAUNCHED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the station list says on a launch that has not seen the welcome yet.
 *
 * Without a base map installed one lands on this list whatever screen was
 * chosen, so the very first launch builds it, asks it for the stations of a
 * city nobody has chosen, and covers it a frame later with the welcome
 * sequence. Its "no city is selected · choose one" then stood over that
 * sequence — the banner belongs to the activity and outlives the screen that
 * raised it — hiding the button that carries the sequence forward and offering
 * an action that could do nothing, the chooser being what the sequence is about
 * to offer properly.
 *
 * The two halves are tested together on purpose: holding the message back for
 * good would leave somebody who has deleted their city's data with a list that
 * says nothing, and holding back anything else would silence a feed that failed.
 */
class ErrorsUnderTheWelcomeTest {

    @Test
    fun `no city chosen says nothing while the welcome is still due`() {
        assertFalse(
            "the banner would cover the welcome's own way forward",
            worthSaying(DataError.NoCityChosen, NEVER_LAUNCHED),
        )
    }

    @Test
    fun `no city chosen speaks once the welcome has been seen`() {
        // Somebody who has deleted their city's data: the message and its
        // "choose my city" are the whole of what this screen has to offer them.
        assertTrue(worthSaying(DataError.NoCityChosen, SEEN))
    }

    @Test
    fun `a failed feed speaks, welcome or no welcome`() {
        listOf(
            DataError.Offline,
            DataError.Timeout,
            DataError.ServerRefused(503),
            DataError.FeedUnavailable("station_status"),
            DataError.MalformedResponse("station_information"),
            DataError.UnsupportedFeedVersion("1.0"),
            DataError.UntrustedServer("certificate"),
            DataError.LocalStorageFailure("disk full"),
        ).forEach { error ->
            assertTrue("$error held back under the welcome", worthSaying(error, NEVER_LAUNCHED))
            assertTrue("$error held back on an ordinary launch", worthSaying(error, SEEN))
        }
    }

    private companion object {
        /**
         * A version code that has been seen. Any but [NEVER_LAUNCHED] means the
         * sequence is over: it is written down when the welcome **finishes**,
         * so that leaving in the middle of it brings it back.
         */
        const val SEEN = 1
    }
}
