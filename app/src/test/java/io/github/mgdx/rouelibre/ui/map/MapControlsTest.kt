package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the map screen lays over its map, in each of its two roles (SPEC §7.1, §7.3).
 *
 * The rule this pins is the one the screen broke: opened to designate a point
 * with no base map on the device, it raised the crosshair, "Choose this point"
 * and "Locate me" all the same, and then covered them with the full-screen
 * panel that asks for a city. The three stayed clickable, and were pressed
 * without being seen. No Android runtime is involved in the decision, so it is
 * checked here (SPEC §14).
 */
class MapControlsTest {

    @Test
    fun `nothing is laid over a base map that is not there`() {
        for (isPicking in listOf(false, true)) {
            val controls = mapControls(hasBaseMap = false, isPicking = isPicking)
            assertFalse("picking=$isPicking", controls.browsing)
            assertFalse("picking=$isPicking", controls.picking)
            assertFalse("picking=$isPicking", controls.locateMe)
        }
    }

    @Test
    fun `the picker aims and does not browse`() {
        val controls = mapControls(hasBaseMap = true, isPicking = true)
        assertTrue(controls.picking)
        assertFalse("one came to aim, not to browse availability", controls.browsing)
        assertTrue("aiming at where one stands is the point of it", controls.locateMe)
    }

    @Test
    fun `the main screen browses and does not aim`() {
        val controls = mapControls(hasBaseMap = true, isPicking = false)
        assertTrue(controls.browsing)
        assertFalse("no point is being designated", controls.picking)
        assertTrue(controls.locateMe)
    }
}
