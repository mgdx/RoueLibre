package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compass button's state, read from how the map is held (SPEC §7.1, §7.4).
 *
 * Three things are pinned here. That the button appears only when there is
 * something for it to undo — SPEC §7 asks the map screen to stay calm, and a
 * control that undoes nothing is one more thing on it — and that **either**
 * departure from the opening attitude is something to undo, one button giving
 * back both. That north is approached from both sides: MapLibre reports a
 * bearing within `[0, 360[`, so a map three tenths of a degree short of north
 * reports 359.7, and read literally that is a map turned nearly the whole way
 * round. And that the screen believes its own order rather than a reading of
 * the map that the library has not refreshed. No Android runtime decides any
 * of it, so it is checked here (SPEC §14).
 */
class CompassNeedleTest {

    @Test
    fun `a map held the way it opened carries no compass`() {
        assertFalse(compassNeedle(NORTH_AND_FLAT).isShown)
    }

    @Test
    fun `a map turned enough to see carries one`() {
        assertTrue(compassNeedle(turned(12.0)).isShown)
        assertTrue(compassNeedle(turned(180.0)).isShown)
        assertTrue("turned the other way, and just as turned", compassNeedle(turned(348.0)).isShown)
    }

    @Test
    fun `a map tilted enough to see carries one too, facing north or not`() {
        val tilted = compassNeedle(MapAttitude(bearingDegrees = NORTH_BEARING, pitchDegrees = 30.0))
        assertTrue(
            "a tilted map with nothing to straighten it is the state to avoid",
            tilted.isShown,
        )
        assertTrue(tilted.isTilted)
        assertFalse("and it is not turned, which the sentence must not claim", tilted.isTurned)

        val both = compassNeedle(MapAttitude(bearingDegrees = 40.0, pitchDegrees = 30.0))
        assertTrue(both.isTurned)
        assertTrue(both.isTilted)
    }

    @Test
    fun `a shiver either side of north is not a turn`() {
        for (bearing in listOf(0.4, 1.9, 358.1, 359.7)) {
            assertFalse("bearing=$bearing", compassNeedle(turned(bearing)).isShown)
        }
    }

    @Test
    fun `a shiver off flat is not a tilt`() {
        for (pitch in listOf(0.0, 0.4, 1.9)) {
            assertFalse("pitch=$pitch", compassNeedle(tilted(pitch)).isShown)
        }
        assertTrue(compassNeedle(tilted(2.0)).isShown)
    }

    @Test
    fun `the needle turns back by as much as the map turned, and the tilt does not enter it`() {
        assertEquals(-90f, compassNeedle(turned(90.0)).iconRotationDegrees, 0.001f)
        assertEquals(-348f, compassNeedle(turned(348.0)).iconRotationDegrees, 0.001f)
        assertEquals(
            "the button is drawn flat on the screen whatever the map is doing",
            compassNeedle(turned(90.0)).iconRotationDegrees,
            compassNeedle(MapAttitude(90.0, 45.0)).iconRotationDegrees,
            0.001f,
        )
    }

    @Test
    fun `the spoken bearing is a whole number of degrees`() {
        assertEquals(43, compassNeedle(turned(42.6)).degreesFromNorth)
        assertEquals(348, compassNeedle(turned(347.5)).degreesFromNorth)
    }

    @Test
    fun `a bearing rounding up to a full turn is read as north`() {
        assertEquals(0, compassNeedle(turned(359.7)).degreesFromNorth)
    }

    /**
     * The defect this pins was found on a phone: the map came back to north on
     * a press and the button stayed, going only when a finger touched the map
     * again. `MapLibreMap.cameraPosition` is cached in the library and is
     * refreshed by a gesture, not by the frames of a move the library is
     * animating — so everything the screen read at the end of its own turn
     * still described the map as it stood before the press. The trap holds for
     * the tilt exactly as it did for the bearing.
     *
     * The answer is not to read later but not to read at all: the screen
     * ordered north and flat and knows it. What is checked here is that rule
     * composed with the needle, on the very reading that misled it.
     */
    @Test
    fun `the compass goes as soon as north and flat are ordered, whatever the map still says`() {
        val stale = MapAttitude(bearingDegrees = 137.0, pitchDegrees = 40.0)
        val needle = compassNeedle(
            attitudeAfterOrderingNorthAndFlat(theOrderStands = true, mapAttitude = stale),
        )
        assertFalse("the screen believes its own order and not that reading", needle.isShown)
        assertFalse("the tilt is given back by the same press", needle.isTilted)
        assertEquals(0, needle.degreesFromNorth)
    }

    @Test
    fun `a move cut short leaves the needle where the map stopped`() {
        val stopped = MapAttitude(bearingDegrees = 137.0, pitchDegrees = 40.0)
        val needle = compassNeedle(
            attitudeAfterOrderingNorthAndFlat(theOrderStands = false, mapAttitude = stopped),
        )
        assertTrue("the map is the only one who knows where the finger stopped it", needle.isShown)
        assertTrue(needle.isTilted)
        assertEquals(137, needle.degreesFromNorth)
    }

    @Test
    fun `the attitude ordered and the attitude reached say the same thing`() {
        assertEquals(
            compassNeedle(NORTH_AND_FLAT),
            compassNeedle(
                attitudeAfterOrderingNorthAndFlat(
                    theOrderStands = true,
                    mapAttitude = NORTH_AND_FLAT,
                ),
            ),
        )
    }

    @Test
    fun `a bearing from outside the reported range is folded back into it`() {
        // Nothing in the application produces one, but the reading must not
        // depend on that: a bearing is an angle, and 370 degrees is 10.
        assertEquals(compassNeedle(turned(10.0)), compassNeedle(turned(370.0)))
        assertEquals(compassNeedle(turned(350.0)), compassNeedle(turned(-10.0)))
    }

    private fun turned(bearingDegrees: Double) =
        MapAttitude(bearingDegrees = bearingDegrees, pitchDegrees = FLAT_PITCH)

    private fun tilted(pitchDegrees: Double) =
        MapAttitude(bearingDegrees = NORTH_BEARING, pitchDegrees = pitchDegrees)
}
