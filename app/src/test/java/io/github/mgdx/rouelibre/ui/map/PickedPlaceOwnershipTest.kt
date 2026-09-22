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
 *
 * The question is put once, where a city is applied to the map, and these are
 * the moments it is put in.
 */
class PickedPlaceOwnershipTest {

    /**
     * First display, nothing designated: the city arrives, and there is
     * nothing for it to throw away.
     */
    @Test
    fun `no designated point is nothing to clear`() {
        assertTrue(designatedPlaceSurvives(null, "vlille"))
        assertTrue(designatedPlaceSurvives(null, null))
    }

    /**
     * A turn of the phone, or a trip to another screen and back: the point and
     * its city come back from the state, the city served is the same, and the
     * point stands. Breaking this would be worse than the anomaly — a point
     * surviving a rotation is what the screen promises.
     */
    @Test
    fun `a point survives a rotation under the same city`() {
        assertTrue(designatedPlaceSurvives("vlille", "vlille"))
    }

    /**
     * The city changed under a map that is up: the point no longer belongs to
     * what is served, and goes.
     */
    @Test
    fun `a point does not survive the city changing under the map`() {
        assertFalse(designatedPlaceSurvives("capital-bikeshare", "vlille"))
    }

    /**
     * The city changed, then the process was killed: the screen is rebuilt,
     * the point and its city come from the state bundle, and the first
     * configuration applied to that fresh screen is enough to recognise the
     * point as another city's. This is the case the identifier travelling in
     * the bundle exists for.
     */
    @Test
    fun `a point restored under another city does not survive`() {
        assertFalse(designatedPlaceSurvives("capital-bikeshare", "velov"))
    }

    /**
     * Giving the city up altogether is a change like any other: there is then
     * no data at all the address could name.
     */
    @Test
    fun `a point does not survive the city being given up`() {
        assertFalse(designatedPlaceSurvives("capital-bikeshare", null))
    }
}
