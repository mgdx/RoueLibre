package io.github.mgdx.rouelibre.core.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the arbitration between the fixes of two providers (SPEC §10).
 *
 * What is verified here is above all that the displayed point **moves**: the
 * bug this rule answers was a point that stayed where it was while the device
 * went on, the coarse network fix overwriting the satellite one every two
 * seconds.
 */
class PositionFixTest {

    private val lille = Coordinates(50.6371, 3.0630)

    private fun fix(atMillis: Long, accuracyMetres: Double?) =
        PositionFix(lille, accuracyMetres, atMillis)

    @Test
    fun `the first fix of all is always worth showing`() {
        assertTrue(fix(1_000, 800.0).improvesOn(null))
    }

    @Test
    fun `a fix no newer than the displayed one is not shown`() {
        val shown = fix(10_000, 12.0)
        assertFalse(fix(10_000, 5.0).improvesOn(shown))
        assertFalse(fix(9_000, 5.0).improvesOn(shown))
    }

    @Test
    fun `a satellite fix follows the walker from one metre to the next`() {
        // The very case the point used to freeze on: accuracy wanders around
        // ten metres, and every one of those fixes must be displayed.
        var shown = fix(0, 8.0)
        listOf(11.0, 9.0, 14.0, 10.0).forEachIndexed { step, accuracy ->
            val next = fix((step + 1) * 2_000L, accuracy)
            assertTrue("the point stopped following at step $step", next.improvesOn(shown))
            shown = next
        }
    }

    @Test
    fun `a coarse fix does not land on top of a precise one`() {
        val satellites = fix(10_000, 9.0)
        assertFalse(fix(12_000, 1_200.0).improvesOn(satellites))
    }

    @Test
    fun `a coarse fix takes over once the precise one has aged`() {
        // Under a roof the satellites go silent: after half a minute, a
        // position to within a kilometre is worth more than a point that is
        // certainly no longer where it says.
        val satellites = fix(10_000, 9.0)
        assertFalse(fix(39_000, 1_200.0).improvesOn(satellites))
        assertTrue(fix(40_000, 1_200.0).improvesOn(satellites))
    }

    @Test
    fun `a fix carrying no accuracy is shown rather than dropped`() {
        // Nothing to arbitrate on: turning it down would leave the point
        // frozen on a device whose provider says nothing about its accuracy.
        assertTrue(fix(12_000, null).improvesOn(fix(10_000, 9.0)))
        assertTrue(fix(12_000, 1_200.0).improvesOn(fix(10_000, null)))
    }

    @Test
    fun `a fix is precise enough at the width of a boulevard`() {
        assertTrue(fix(0, 25.0).isPreciseEnough)
        assertFalse(fix(0, 25.1).isPreciseEnough)
        assertFalse(fix(0, null).isPreciseEnough)
    }
}
