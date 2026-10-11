package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.ActiveCity
import io.github.mgdx.rouelibre.core.config.CityConfigurationReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the panel covering a map without tiles says, in both of the map's
 * roles (SPEC §4.4, §7.3, §15.1).
 *
 * None of these rules reads a served city's configuration, only that it is
 * served: the served cases go through [served], which stands for any of them.
 */
class MissingTilesPanelTest {

    @Test
    fun `a city gone is said to be gone, not asked for again`() {
        val panel = missingTilesPanel(GONE, transferRunning = false, isPicking = false)
        assertEquals(MissingTilesReason.CityGone, panel.reason)
    }

    @Test
    fun `no city chosen asks for one, download or not`() {
        assertEquals(
            MissingTilesReason.NoCity,
            missingTilesPanel(ActiveCity.None, transferRunning = true, isPicking = false).reason,
        )
    }

    @Test
    fun `a download under way is said as one`() {
        assertEquals(MissingTilesReason.Downloading, served(transferRunning = true).reason)
    }

    @Test
    fun `a city without tiles and nothing coming asks for them`() {
        assertEquals(MissingTilesReason.NoTiles, served(transferRunning = false).reason)
    }

    /** The message about tiles sends the reader to the list; the picker has its own way. */
    @Test
    fun `the picker drops the message about tiles but keeps the one about the city`() {
        assertTrue(served(transferRunning = false, isPicking = false).showsMessage)
        assertFalse(served(transferRunning = false, isPicking = true).showsMessage)
        assertTrue(missingTilesPanel(ActiveCity.None, false, isPicking = true).showsMessage)
        assertTrue(missingTilesPanel(GONE, false, isPicking = true).showsMessage)
    }

    @Test
    fun `the way to the list goes with the network, and stays as the picker's way back`() {
        assertFalse(missingTilesPanel(GONE, false, isPicking = false).showsWayOut)
        assertTrue(missingTilesPanel(GONE, false, isPicking = true).showsWayOut)
        assertTrue(missingTilesPanel(ActiveCity.None, false, isPicking = false).showsWayOut)
    }

    private fun served(transferRunning: Boolean, isPicking: Boolean = false) =
        missingTilesPanel(SERVED, transferRunning, isPicking)

    private companion object {
        val GONE = ActiveCity.NoLongerServed(id = "vlille", withdrawal = null)

        /** Read through the core's own reader, as the application reads it. */
        val SERVED: ActiveCity = ActiveCity.Served(
            (
                CityConfigurationReader.read(
                    """
                    {
                      "configVersion": 1,
                      "network": {
                        "id": "vlille", "displayName": "V'lille",
                        "operator": "Example", "defaultLanguage": "fr"
                      },
                      "gbfs": { "discoveryUrl": "https://example.org/gbfs.json" },
                      "map": {
                        "defaultCenterLatitude": 50.6, "defaultCenterLongitude": 3.0,
                        "defaultZoom": 12.0, "minZoom": 10, "maxZoom": 16
                      },
                      "dataRelease": { "manifestUrl": "https://example.org/manifest.json" }
                    }
                    """.trimIndent(),
                ) as Outcome.Success
                ).value,
        )
    }
}
