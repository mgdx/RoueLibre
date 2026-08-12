package io.github.mgdx.rouelibre.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the confinement that keeps the served area covering the screen.
 *
 * The box used here is Nantes', rounded: a little over a tenth of a degree each
 * way, which is what a conurbation's reference box actually looks like.
 */
class ViewportTest {

    private val area = BoundingBox(south = 47.15, west = -1.63, north = 47.27, east = -1.48)

    @Test
    fun `the centre is held a viewport's reach away from the edge`() {
        val centres = area.centresKeepingViewportInside(
            northwardReach = 0.02,
            southwardReach = 0.02,
            eastwardReach = 0.03,
            westwardReach = 0.03,
        )

        assertEquals(47.17, centres.south, 1e-9)
        assertEquals(47.25, centres.north, 1e-9)
        assertEquals(-1.60, centres.west, 1e-9)
        assertEquals(-1.51, centres.east, 1e-9)
    }

    @Test
    fun `a viewport reaching further north than south is held further from the north`() {
        // What Mercator does to a viewport: the same pixels above the centre
        // cover more degrees than those below it.
        val centres = area.centresKeepingViewportInside(
            northwardReach = 0.03,
            southwardReach = 0.02,
            eastwardReach = 0.0,
            westwardReach = 0.0,
        )

        assertEquals(47.17, centres.south, 1e-9)
        assertEquals(47.24, centres.north, 1e-9)
    }

    @Test
    fun `a viewport wider than the area leaves the centre no choice`() {
        val centres = area.centresKeepingViewportInside(
            northwardReach = 0.5,
            southwardReach = 0.5,
            eastwardReach = 0.5,
            westwardReach = 0.5,
        )

        assertEquals(area.centre.latitude, centres.south, 1e-9)
        assertEquals(area.centre.latitude, centres.north, 1e-9)
        assertEquals(area.centre.longitude, centres.west, 1e-9)
        assertEquals(area.centre.longitude, centres.east, 1e-9)
    }

    @Test
    fun `a viewport twice the area calls for one zoom step in`() {
        val zoom = area.zoomKeepingViewportInside(
            zoom = 11.0,
            visibleLatitudeSpan = 0.24,
            visibleLongitudeSpan = 0.06,
        )

        assertEquals(12.0, zoom, 1e-9)
    }

    @Test
    fun `a viewport already inside says how far out one may still go`() {
        val zoom = area.zoomKeepingViewportInside(
            zoom = 14.0,
            visibleLatitudeSpan = 0.03,
            visibleLongitudeSpan = 0.0375,
        )

        assertEquals(12.0, zoom, 1e-9)
    }

    @Test
    fun `the widest zoom is the one where the viewport fills the area exactly`() {
        val widest = area.zoomKeepingViewportInside(
            zoom = 13.0,
            visibleLatitudeSpan = 0.015,
            visibleLongitudeSpan = 0.02,
        )
        // At that zoom the shortfall is gone: measuring again from there asks
        // for no further step, which is what makes the limit stable while the
        // camera moves.
        val spread = Math.pow(2.0, 13.0 - widest)
        val settled = area.zoomKeepingViewportInside(
            zoom = widest,
            visibleLatitudeSpan = 0.015 * spread,
            visibleLongitudeSpan = 0.02 * spread,
        )

        assertEquals(widest, settled, 1e-9)
        assertTrue("the area must cover the screen", 0.02 * spread <= area.east - area.west + 1e-9)
    }

    @Test
    fun `an area without extent is left alone`() {
        val flat = BoundingBox(south = 47.15, west = -1.63, north = 47.15, east = -1.48)

        assertEquals(12.0, flat.zoomKeepingViewportInside(12.0, 0.01, 0.01), 1e-9)
    }
}
