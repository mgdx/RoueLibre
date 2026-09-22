package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A designated point belongs to the city it was designated in.
 *
 * The anomaly these hold shut: "Pennsylvania Avenue Northwest", found under
 * Capital Bikeshare, stayed named at the bottom of the map and marked on it
 * after V'lille was served — an address of Washington over Lille, pointing at
 * ground absent from the installed data — and came back after the process was
 * killed, the state bundle having kept it.
 */
class PickedPlaceOwnershipTest {

    @Test
    fun `a point designated in another city does not survive`() {
        assertFalse(designatedPlaceSurvives("capital-bikeshare", "vlille"))
    }

    @Test
    fun `a point designated in the city served survives`() {
        assertTrue(designatedPlaceSurvives("vlille", "vlille"))
    }

    /**
     * Serving no city at all is a change like any other: there is then no data
     * the address could name.
     */
    @Test
    fun `a point does not survive the city being given up`() {
        assertFalse(designatedPlaceSurvives("capital-bikeshare", null))
    }

    /**
     * The first time a configuration is applied to a screen, no city has been
     * served yet: nothing has changed, and a point restored from the state
     * must not be destroyed by that first application. This is the case that
     * makes the identifier saved beside the point necessary, rather than
     * optional.
     */
    @Test
    fun `an unknown city of origin leaves the point standing`() {
        assertTrue(designatedPlaceSurvives(null, "vlille"))
        assertTrue(designatedPlaceSurvives(null, null))
    }
}
