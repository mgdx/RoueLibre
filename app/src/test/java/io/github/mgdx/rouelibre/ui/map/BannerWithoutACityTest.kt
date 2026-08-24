package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.DataError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the map says when no city is chosen (SPEC §7.1, §7.8).
 *
 * The rule this pins is the one the screen broke: with no city chosen it raised
 * the panel that asks for one — the sentence and "Choose my city" in the middle
 * of the screen — and then laid a banner under it saying the same thing with
 * "Try again" on it. That action asks nothing of any network and can only fail
 * the same way, ten seconds later and every ten seconds after that.
 *
 * The map answers with silence because it has the panel; the station list
 * answers by offering the chooser on its banner because it has no panel. Both
 * refuse "Try again", which is the one rule the two screens share. No Android
 * runtime decides it, so it is checked here (SPEC §14).
 */
class BannerWithoutACityTest {

    @Test
    fun `no city chosen raises no banner on the map`() {
        assertFalse(
            "the panel already says it, and offers the gesture with it",
            worthSayingOnTheMap(DataError.NoCityChosen),
        )
    }

    @Test
    fun `a failed feed still speaks`() {
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
            // No panel covers the map for these: the stations are there, only
            // their counters are old, and "Try again" is a real answer.
            assertTrue("$error held back", worthSayingOnTheMap(error))
        }
    }
}
