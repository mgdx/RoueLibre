package io.github.mgdx.rouelibre.ui.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.DeparturePoint
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the journey result decides to show, apart from its views (SPEC §7.4). */
class JourneyResultDecisionsTest {

    private val a = Coordinates(43.2965, 5.3698)
    private val b = Coordinates(43.2970, 5.3700)
    private val c = Coordinates(43.2949, 5.3740)
    private val d = Coordinates(43.2951, 5.3746)

    private fun leg(mode: TravelMode, vararg points: Coordinates) = RouteLeg(
        mode = mode,
        distanceMetres = 500,
        duration = 5.minutes,
        ascentMetres = 0,
        geometry = points.toList(),
    )

    private fun station(id: String, position: Coordinates) = Station(
        id = id,
        name = "Station $id",
        position = position,
        capacity = 20,
        postalCode = "13001",
    )

    private val found = JourneyPlan.Found(
        JourneyOption(
            departure = DeparturePoint.AtStation(station("depart", b)),
            arrivalStation = station("arrivee", c),
            bikesAtDeparture = 4,
            docksAtArrival = 6,
            walkToStation = leg(TravelMode.Walking, a, b),
            ride = leg(TravelMode.Cycling, b, c),
            walkToDestination = leg(TravelMode.Walking, c, d),
            riskPenalty = 30.seconds,
        ),
    )
    private val walkOnly = JourneyPlan.WalkOnly(
        leg(TravelMode.Walking, a, d),
        NoBikeJourney.WalkingIsQuicker,
    )
    private val ownBike = JourneyPlan.OwnBike(leg(TravelMode.Cycling, a, d))
    private val impossible = JourneyPlan.Impossible(NoBikeJourney.WalkingIsQuicker)

    @Test
    fun `the track of a journey through stations runs its three legs end to end`() {
        assertEquals(listOf(a, b, b, c, c, d), trackOf(found))
    }

    @Test
    fun `the track of a journey without stations is its one leg`() {
        assertEquals(listOf(a, d), trackOf(walkOnly))
        assertEquals(listOf(a, d), trackOf(ownBike))
    }

    @Test
    fun `no journey has no track, and nothing to frame`() {
        assertTrue(trackOf(impossible).isEmpty())
        assertTrue(trackOf(null).isEmpty())
        assertFalse(canBeFramed(trackOf(impossible)))
        assertFalse(canBeFramed(listOf(a)))
        assertTrue(canBeFramed(listOf(a, d)))
    }

    @Test
    fun `the sole leg is the walk or the ride on one's own bike`() {
        assertEquals(walkOnly.directWalk, soleLegOf(walkOnly))
        assertEquals(ownBike.ride, soleLegOf(ownBike))
        assertNull(soleLegOf(found))
        assertNull(soleLegOf(impossible))
    }

    @Test
    fun `the ways on are offered for every journey there is, and only then`() {
        assertTrue(offersWaysOn(found))
        assertTrue(offersWaysOn(walkOnly))
        assertTrue(offersWaysOn(ownBike))
        assertFalse(offersWaysOn(impossible))
        assertFalse(offersWaysOn(null))
    }

    @Test
    fun `the margin gives way on a short map, never past a quarter of it`() {
        assertEquals(84, frameMarginPixels(wantedPixels = 84, width = 1080, height = 900))
        assertEquals(50, frameMarginPixels(wantedPixels = 84, width = 1080, height = 200))
    }
}
