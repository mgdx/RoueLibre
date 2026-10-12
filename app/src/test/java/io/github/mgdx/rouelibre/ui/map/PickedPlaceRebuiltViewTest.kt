package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The point a map view shows when it is built again.
 *
 * The anomaly these hold shut: an address searched, then a trip to the station
 * list and back, and the marker stood on the map while its pill was gone —
 * the view had come back without a state bundle, and the point the fragment
 * still held was not shown on it.
 */
class PickedPlaceRebuiltViewTest {

    /** Back from the list or the search: no bundle, the point kept in memory. */
    @Test
    fun `the point held survives a view rebuilt without a bundle`() {
        assertEquals(RIVOLI, placeOnRebuiltView(held = RIVOLI, saved = null))
    }

    /** A rotation or the process dying: the fragment is new and holds nothing. */
    @Test
    fun `the point saved comes back on a fragment rebuilt from scratch`() {
        assertEquals(RIVOLI, placeOnRebuiltView(held = null, saved = RIVOLI))
    }

    /** The bundle is the more recent word when both are there. */
    @Test
    fun `the point saved wins over the point held`() {
        val other = DesignatedPlace(Coordinates(48.8584, 2.2945), "Champ de Mars", "velib")
        assertEquals(other, placeOnRebuiltView(held = RIVOLI, saved = other))
    }

    @Test
    fun `nothing designated shows nothing`() {
        assertNull(placeOnRebuiltView(held = null, saved = null))
    }

    private companion object {
        val RIVOLI = DesignatedPlace(Coordinates(48.8606, 2.3376), "10 Rue de Rivoli", "velib")
    }
}
