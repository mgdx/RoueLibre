package io.github.mgdx.rouelibre.core.routing

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** The elevation profile of a leg (SPEC §7.4.1). */
class ElevationProfileTest {

    /** A hundred metres east, twice, from a point in Lille. */
    private val track = listOf(
        Coordinates(50.6300, 3.0600),
        Coordinates(50.6300, 3.0614),
        Coordinates(50.6300, 3.0628),
    )

    private fun legOf(elevations: List<Double?>) = RouteLeg(
        mode = TravelMode.Cycling,
        distanceMetres = 200,
        duration = 1.minutes,
        ascentMetres = 10,
        geometry = track,
        elevationsMetres = elevations,
    )

    @Test
    fun `the distance of a reading is the ground covered to reach it`() {
        val profile = legOf(listOf(20.0, 25.0, 30.0)).elevationProfile()

        assertEquals(listOf(20.0, 25.0, 30.0), profile.map { it.elevationMetres })
        assertEquals(0.0, profile.first().distanceMetres, 0.001)
        // Two legs of about a hundred metres each: the second reading stands at
        // roughly half of the last one's distance.
        assertTrue("${profile[1].distanceMetres}", profile[1].distanceMetres in 90.0..110.0)
        assertTrue("${profile[2].distanceMetres}", profile[2].distanceMetres in 180.0..220.0)
    }

    @Test
    fun `a point the graph knows no elevation for is left out, not guessed`() {
        val profile = legOf(listOf(20.0, null, 30.0)).elevationProfile()

        assertEquals(listOf(20.0, 30.0), profile.map { it.elevationMetres })
        // The point dropped still counted towards the ground covered: the last
        // reading did not move up the leg because of it.
        assertTrue(
            "${profile.last().distanceMetres}",
            profile.last().distanceMetres in 180.0..220.0,
        )
    }

    @Test
    fun `a graph carrying no elevation profiles nothing`() {
        assertEquals(emptyList<ElevationPoint>(), legOf(emptyList()).elevationProfile())
        assertEquals(
            emptyList<ElevationPoint>(),
            legOf(listOf(null, null, null)).elevationProfile(),
        )
    }

    @Test
    fun `smoothing flattens the sampling's saw and leaves the hill standing`() {
        // A metre of saw every ten metres, over a hundred metres of ground,
        // then a rise of twenty metres over the next four hundred.
        val saw = (0..10).map { ElevationPoint(it * 10.0, if (it % 2 == 0) 20.0 else 21.0) }
        val hill = (1..8).map { ElevationPoint(100.0 + it * 50.0, 20.0 + it * 2.5) }

        val smoothed = (saw + hill).smoothedOver(windowMetres = 150.0)

        val flat = smoothed.take(saw.size).map { it.elevationMetres }
        assertTrue("$flat", flat.max() - flat.min() < 1.0)
        // The hill is still climbed: its top has not been averaged away.
        assertTrue("$smoothed", smoothed.last().elevationMetres > 35.0)
        assertEquals(saw.size + hill.size, smoothed.size)
    }

    @Test
    fun `a profile too short to have a shape is left alone`() {
        val profile = listOf(ElevationPoint(0.0, 20.0), ElevationPoint(100.0, 40.0))

        assertEquals(profile, profile.smoothedOver(windowMetres = 150.0))
    }
}
