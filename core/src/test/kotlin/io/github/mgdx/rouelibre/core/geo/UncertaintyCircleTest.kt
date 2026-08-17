package io.github.mgdx.rouelibre.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the circle showing how wide a fix's uncertainty is (SPEC §7.1).
 *
 * Two things are verified: that the circle is drawn exactly when the fix is too
 * coarse to be trusted on its own, and that its radius is the announced one on
 * the ground — a circle a hundred metres short would understate the doubt as
 * surely as no circle at all.
 */
class UncertaintyCircleTest {

    private val lille = Coordinates(50.6371, 3.0630)

    private fun fix(accuracyMetres: Double?) = PositionFix(lille, accuracyMetres, 1_000L)

    @Test
    fun `a fix that measured no accuracy is drawn without a circle`() {
        assertNull(fix(null).uncertaintyCircle())
    }

    @Test
    fun `a satellite fix is precise enough to need no circle`() {
        assertNull(fix(8.0).uncertaintyCircle())
    }

    @Test
    fun `the threshold is the one that already decides a fix is precise enough`() {
        assertNull("a fix at the threshold still needs no circle", fix(25.0).uncertaintyCircle())
        assertNotNull("a fix past the threshold needs one", fix(25.1).uncertaintyCircle())
    }

    @Test
    fun `a network fix is circled at the radius it announces`() {
        val circle = circleAround(300.0)

        val radii = circle.map { lille.distanceInMetresTo(it) }
        radii.forEach { assertEquals("vertex off the announced radius", 300.0, it, TOLERANCE) }
    }

    @Test
    fun `the circle closes on itself`() {
        val circle = circleAround(300.0)

        assertTrue("too few vertices to pass for a circle", circle.size > 32)
        assertEquals("the ring is left open", circle.first(), circle.last())
    }

    @Test
    fun `a circle wide enough to cross a meridian keeps usable longitudes`() {
        // A kilometre-wide fix near the antimeridian: its vertices must stay
        // inside the bounds `Coordinates` accepts, which is what building them
        // would throw on.
        val circle = PositionFix(Coordinates(0.0, 179.995), 1_000.0, 1_000L).uncertaintyCircle()

        assertNotNull(circle)
        circle!!.forEach { assertTrue("longitude out of bounds", it.longitude in -180.0..180.0) }
    }

    private fun circleAround(accuracyMetres: Double): List<Coordinates> {
        val circle = fix(accuracyMetres).uncertaintyCircle()
        assertNotNull("no circle drawn", circle)
        return circle!!
    }

    private companion object {
        /**
         * How far a vertex may sit from the announced radius, in metres.
         *
         * A tenth of a metre: the spherical earth the circle is drawn on and
         * the haversine distance it is measured with make the same
         * approximation, so what is left is rounding.
         */
        const val TOLERANCE = 0.1
    }
}
