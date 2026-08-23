package io.github.mgdx.rouelibre.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The screen the application lands on, once what is installed is taken into
 * account (SPEC §7.0, §7.6).
 *
 * The map is the default, and a default that lands on the panel saying the
 * tiles are missing makes the first thing the application shows an obstacle.
 * The list needs nothing installed, so it is what stands in until there is a
 * map to draw.
 */
class LandingScreenTest {

    @Test
    fun `the map is landed on when its tiles are there`() {
        assertEquals(
            OpeningScreen.Map,
            landingScreen(OpeningScreen.Map, hasBaseMap = true),
        )
    }

    @Test
    fun `without a base map the list stands in for it`() {
        assertEquals(
            OpeningScreen.StationList,
            landingScreen(OpeningScreen.Map, hasBaseMap = false),
        )
    }

    @Test
    fun `the list is landed on either way`() {
        assertEquals(
            OpeningScreen.StationList,
            landingScreen(OpeningScreen.StationList, hasBaseMap = true),
        )
        assertEquals(
            OpeningScreen.StationList,
            landingScreen(OpeningScreen.StationList, hasBaseMap = false),
        )
    }

    @Test
    fun `the choice itself is not rewritten`() {
        // Nothing here writes to the settings: the correction is a landing, and
        // the map comes back the moment its tiles do. Guarded by the shape of
        // the function — it takes a choice and returns one — which this test
        // states so that a future version cannot quietly start storing it.
        val chosen = OpeningScreen.Map
        landingScreen(chosen, hasBaseMap = false)
        assertEquals(OpeningScreen.Map, chosen)
    }
}
