package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compass button's state, read from the map's bearing (SPEC §7.1, §7.4).
 *
 * Two things are pinned here. That the button appears only when there is
 * something for it to undo — SPEC §7 asks the map screen to stay calm, and a
 * control that undoes nothing is one more thing on it. And that north is
 * approached from both sides: MapLibre reports a bearing within `[0, 360[`, so
 * a map three tenths of a degree short of north reports 359.7, and read
 * literally that is a map turned nearly the whole way round. No Android
 * runtime decides any of it, so it is checked here (SPEC §14).
 */
class CompassNeedleTest {

    @Test
    fun `a map facing north carries no compass`() {
        assertFalse(compassNeedle(0.0).isShown)
    }

    @Test
    fun `a map turned enough to see carries one`() {
        assertTrue(compassNeedle(12.0).isShown)
        assertTrue(compassNeedle(180.0).isShown)
        assertTrue("turned the other way, and just as turned", compassNeedle(348.0).isShown)
    }

    @Test
    fun `a shiver either side of north is not a turn`() {
        for (bearing in listOf(0.4, 1.9, 358.1, 359.7)) {
            assertFalse("bearing=$bearing", compassNeedle(bearing).isShown)
        }
    }

    @Test
    fun `the needle turns back by as much as the map turned`() {
        assertEquals(-90f, compassNeedle(90.0).iconRotationDegrees, 0.001f)
        assertEquals(-348f, compassNeedle(348.0).iconRotationDegrees, 0.001f)
    }

    @Test
    fun `the spoken bearing is a whole number of degrees`() {
        assertEquals(43, compassNeedle(42.6).degreesFromNorth)
        assertEquals(348, compassNeedle(347.5).degreesFromNorth)
    }

    @Test
    fun `a bearing rounding up to a full turn is read as north`() {
        assertEquals(0, compassNeedle(359.7).degreesFromNorth)
    }

    /**
     * The defect this pins was found on a phone: the map came back to north on
     * a press and the button stayed, going only when a finger touched the map
     * again. `MapLibreMap.cameraPosition` is cached in the library and is
     * refreshed by a gesture, not by the frames of a move the library is
     * animating — so everything the screen read at the end of its own turn
     * still described the map as it stood before the press.
     *
     * The answer is not to read later but not to read at all: the screen
     * ordered the bearing to nought and knows it. What is checked here is that
     * rule composed with the needle, on the very reading that misled it.
     */
    @Test
    fun `the compass goes as soon as north is ordered, whatever the map still says`() {
        val stale = 137.0
        val needle =
            compassNeedle(bearingAfterOrderingNorth(theOrderStands = true, mapBearing = stale))
        assertFalse("the screen believes its own order and not that reading", needle.isShown)
        assertEquals(0, needle.degreesFromNorth)
    }

    @Test
    fun `a turn cut short leaves the needle where the map stopped`() {
        val needle =
            compassNeedle(bearingAfterOrderingNorth(theOrderStands = false, mapBearing = 137.0))
        assertTrue("the map is the only one who knows where the finger stopped it", needle.isShown)
        assertEquals(137, needle.degreesFromNorth)
    }

    @Test
    fun `north ordered and reached says the same thing twice`() {
        assertEquals(
            compassNeedle(NORTH_BEARING),
            compassNeedle(
                bearingAfterOrderingNorth(theOrderStands = true, mapBearing = NORTH_BEARING),
            ),
        )
    }

    @Test
    fun `a bearing from outside the reported range is folded back into it`() {
        // Nothing in the application produces one, but the reading must not
        // depend on that: a bearing is an angle, and 370 degrees is 10.
        assertEquals(compassNeedle(10.0), compassNeedle(370.0))
        assertEquals(compassNeedle(350.0), compassNeedle(-10.0))
    }
}
