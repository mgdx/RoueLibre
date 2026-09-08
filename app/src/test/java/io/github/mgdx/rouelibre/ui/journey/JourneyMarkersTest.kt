package io.github.mgdx.rouelibre.ui.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.DeparturePoint
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Point
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests the points a journey lays on the map (SPEC §7.4).
 *
 * What is checked is how many there are and what each one is, which is the
 * whole of what this object decides. A journey through two stations has four;
 * one that sets off on a bike outside them has **three**, and that difference
 * is not a nicety: the bike is the origin on that journey — the search screen
 * locked the field on it — so a walking disc emitted beside it falls on the
 * very same coordinates, and overlap being allowed on this layer, both are
 * drawn and the walker hides the bike. The map then says the journey begins on
 * foot, which is the one thing it must not say.
 */
class JourneyMarkersTest {

    private val origin = Coordinates(43.2965, 5.3698)
    private val destination = Coordinates(43.2951, 5.3746)
    private val arrival = Coordinates(43.2949, 5.3740)
    private val departureStation = Coordinates(43.2970, 5.3700)

    private fun leg(mode: TravelMode) = RouteLeg(
        mode = mode,
        distanceMetres = 500,
        duration = 5.minutes,
        ascentMetres = 0,
        geometry = emptyList(),
    )

    private fun station(id: String, position: Coordinates) = Station(
        id = id,
        name = "Station $id",
        position = position,
        capacity = 20,
        postalCode = "13001",
    )

    private fun option(departure: DeparturePoint, accessWalk: RouteLeg?) = JourneyOption(
        departure = departure,
        arrivalStation = station("arrivee", arrival),
        bikesAtDeparture = 4,
        docksAtArrival = 6,
        walkToStation = accessWalk,
        ride = leg(TravelMode.Cycling),
        walkToDestination = leg(TravelMode.Walking),
        riskPenalty = 30.seconds,
    )

    private fun bike(position: Coordinates) = StreetBike(
        id = "velo-de-rue",
        position = position,
        vehicleTypeId = "ebike",
        chargeRatio = null,
        rangeMetres = null,
    )

    private fun kindsOf(collection: org.maplibre.geojson.FeatureCollection) =
        checkNotNull(collection.features())
            .map { it.getStringProperty(JourneyMarkers.KIND_PROPERTY) }

    @Test
    fun `an ordinary journey lays four points, two ends and two stations`() {
        val features = JourneyMarkers.featuresFor(
            origin = origin,
            destination = destination,
            option = option(
                DeparturePoint.AtStation(station("depart", departureStation)),
                accessWalk = leg(TravelMode.Walking),
            ),
        )

        assertEquals(
            listOf(
                JourneyMarkers.KIND_ENDPOINT,
                JourneyMarkers.KIND_STATION,
                JourneyMarkers.KIND_STATION,
                JourneyMarkers.KIND_ENDPOINT,
            ),
            kindsOf(features),
        )
    }

    @Test
    fun `a journey from a street bike lays three points, the bike among them`() {
        // The regression this test exists for: the walking disc used to be
        // emitted on the bike's own coordinates and drawn over it.
        val features = JourneyMarkers.featuresFor(
            origin = origin,
            destination = destination,
            option = option(
                DeparturePoint.AtStreetBike(bike(origin), VehicleKind.Electric),
                accessWalk = null,
            ),
        )

        assertEquals(
            listOf(
                JourneyMarkers.KIND_STREET_BIKE_ELECTRIC,
                JourneyMarkers.KIND_STATION,
                JourneyMarkers.KIND_ENDPOINT,
            ),
            kindsOf(features),
        )
        // The bike stands where the feed put it, and the destination is still
        // drawn: what went is the disc that duplicated the first of them.
        val points = checkNotNull(features.features()).map { it.geometry() as Point }
        assertEquals(origin.longitude, points.first().longitude(), 1e-9)
        assertEquals(origin.latitude, points.first().latitude(), 1e-9)
        assertEquals(destination.longitude, points.last().longitude(), 1e-9)
        assertEquals(destination.latitude, points.last().latitude(), 1e-9)
    }

    @Test
    fun `a mechanical street bike takes the plain glyph`() {
        val features = JourneyMarkers.featuresFor(
            origin = origin,
            destination = destination,
            option = option(
                DeparturePoint.AtStreetBike(bike(origin), VehicleKind.Mechanical),
                accessWalk = null,
            ),
        )

        assertEquals(JourneyMarkers.KIND_STREET_BIKE, kindsOf(features).first())
    }

    @Test
    fun `a journey with no option still draws its two ends`() {
        // A walk from end to end, or one that could not be composed: the two
        // points say what was asked for, which is worth showing when the answer
        // is empty.
        val features = JourneyMarkers.featuresFor(
            origin = origin,
            destination = destination,
            option = null,
        )

        assertEquals(
            listOf(JourneyMarkers.KIND_ENDPOINT, JourneyMarkers.KIND_ENDPOINT),
            kindsOf(features),
        )
    }
}
