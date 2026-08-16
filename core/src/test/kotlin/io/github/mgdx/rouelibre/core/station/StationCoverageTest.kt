package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the rule that tells a station the data reaches from one it does not. */
class StationCoverageTest {

    private fun station(latitude: Double, longitude: Double) = Station(
        id = "s",
        name = "s",
        position = Coordinates(latitude, longitude),
        capacity = null,
        postalCode = null,
    )

    /** Hunedoara's box, as `compute_bbox.py` derives it. */
    private val hunedoara = BoundingBox(south = 45.70, west = 22.85, north = 45.79, east = 22.95)

    @Test
    fun `a station of the city is covered`() {
        assertFalse(station(45.75, 22.90).isBeyondCoveredArea(hunedoara))
    }

    @Test
    fun `a station on the very edge is covered`() {
        // The edges belong to the box: a station on the boundary is served by
        // the tiles and the graph that stop there.
        assertTrue(station(45.70, 22.85).position in hunedoara)
        assertFalse(station(45.70, 22.85).isBeyondCoveredArea(hunedoara))
    }

    @Test
    fun `the test entry Hunedoara publishes in Bucharest is beyond it`() {
        // "SUMS", 290 km away, which the station list used to offer a journey
        // to before the computation could tell the user it was impossible.
        assertTrue(station(44.43, 26.10).isBeyondCoveredArea(hunedoara))
    }

    @Test
    fun `nothing is beyond a box that is not known`() {
        // With no box the application has no ground to call a station
        // unreachable, and saying so would be worse than saying nothing.
        assertFalse(station(44.43, 26.10).isBeyondCoveredArea(null))
        assertFalse(
            station(44.43, 26.10).isBeyondCoveredArea(BoundingBox(0.0, 0.0, 0.0, 0.0)),
        )
    }
}
