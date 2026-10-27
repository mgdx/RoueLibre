package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.ui.map.mapControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journey button of the station list, before and after the download
 * (SPEC §7.6).
 *
 * The list is what the application is reduced to before a city's datasets are
 * installed (SPEC §4.4), and its journey button was offered there all the
 * same, leading to a screen that can only say no journey can be worked out.
 * It now follows the map's own rule for the same button.
 */
class ListOffersTheJourneyTest {

    @Test
    fun `no journey offered before the base map is installed`() {
        assertFalse(listOffersTheJourney(hasBaseMap = false))
    }

    @Test
    fun `the journey offered once it is`() {
        assertTrue(listOffersTheJourney(hasBaseMap = true))
    }

    @Test
    fun `the list answers exactly as the map does`() {
        listOf(false, true).forEach { hasBaseMap ->
            assertEquals(
                mapControls(hasBaseMap = hasBaseMap, isPicking = false).browsing,
                listOffersTheJourney(hasBaseMap),
            )
        }
    }
}
