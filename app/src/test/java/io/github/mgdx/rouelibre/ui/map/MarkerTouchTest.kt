package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.SavedPlace
import io.github.mgdx.rouelibre.data.SavedPlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the main map answers when a marker is touched, and which named places it marks. */
class MarkerTouchTest {

    @Test
    fun `a cluster zooms in, even while a point is being designated`() {
        assertEquals(MarkerTouch.Cluster, markerTouch(true, null, null, isPicking = false))
        assertEquals(MarkerTouch.Cluster, markerTouch(true, null, null, isPicking = true))
    }

    @Test
    fun `a bike or a station opens its sheet`() {
        assertEquals(
            MarkerTouch.StreetBike("b1"),
            markerTouch(false, "b1", null, isPicking = false),
        )
        assertEquals(MarkerTouch.Station("s1"), markerTouch(false, null, "s1", isPicking = false))
    }

    /** A sheet would divert the gesture from what the user came to do. */
    @Test
    fun `no sheet opens while a point is being designated`() {
        assertEquals(MarkerTouch.Nothing, markerTouch(false, "b1", null, isPicking = true))
        assertEquals(MarkerTouch.Nothing, markerTouch(false, null, "s1", isPicking = true))
    }

    @Test
    fun `a marker that is none of them is left to the map`() {
        assertEquals(MarkerTouch.Nothing, markerTouch(false, null, null, isPicking = false))
    }

    @Test
    fun `the named places are marked only where the setting says so`() {
        val home = SavedPlace("Home", Coordinates(50.63, 3.06))
        val settings = SavedPlacesSettings(home = home, work = null, markers = true)
        assertEquals(mapOf(SavedPlaceKind.Home to home), settings.marked())
        assertTrue(settings.copy(markers = false).marked().isEmpty())
    }
}
