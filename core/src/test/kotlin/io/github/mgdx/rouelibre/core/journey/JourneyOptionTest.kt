package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What a chosen journey totals up, once its legs are known. */
class JourneyOptionTest {

    private fun leg(mode: TravelMode, metres: Int, climb: Int) = RouteLeg(
        mode = mode,
        distanceMetres = metres,
        duration = metres.seconds,
        ascentMetres = climb,
        geometry = emptyList(),
    )

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.63, 3.06),
        capacity = 20,
        postalCode = "59000",
    )

    private fun option(walkTo: Int, ride: Int, walkFrom: Int) = JourneyOption(
        departure = DeparturePoint.AtStation(station("departure")),
        arrivalStation = station("arrival"),
        bikesAtDeparture = 5,
        docksAtArrival = 5,
        walkToStation = leg(TravelMode.Walking, metres = 400, climb = walkTo),
        ride = leg(TravelMode.Cycling, metres = 3_000, climb = ride),
        walkToDestination = leg(TravelMode.Walking, metres = 200, climb = walkFrom),
        riskPenalty = 2.minutes,
    )

    @Test
    fun `the climb is the three legs added up, walks included`() {
        // A hill walked up is climbed as surely as one ridden up: what the
        // summary announces is the whole journey's relief.
        assertEquals(62, option(walkTo = 12, ride = 45, walkFrom = 5).climbMetres)
    }

    @Test
    fun `flat ground climbs nothing`() {
        // Which is what lets the interface say nothing about it: a city with no
        // relief, or a graph built without elevation data, both land here.
        assertEquals(0, option(walkTo = 0, ride = 0, walkFrom = 0).climbMetres)
    }

    /** The same journey, begun on a bike outside the stations (SPEC §7.2.1). */
    private fun fromAStreetBike(ride: Int, walkFrom: Int) = JourneyOption(
        departure = DeparturePoint.AtStreetBike(
            StreetBike(
                id = "velo-de-rue",
                position = Coordinates(50.63, 3.06),
                vehicleTypeId = null,
                chargeRatio = null,
                rangeMetres = null,
            ),
            VehicleKind.Mechanical,
        ),
        arrivalStation = station("arrival"),
        bikesAtDeparture = 1,
        docksAtArrival = 5,
        walkToStation = null,
        ride = leg(TravelMode.Cycling, metres = 3_000, climb = ride),
        walkToDestination = leg(TravelMode.Walking, metres = 200, climb = walkFrom),
        riskPenalty = 2.minutes,
    )

    @Test
    fun `a journey begun on a street bike adds up its two legs and no more`() {
        // The access walk does not exist there and nothing stands in for it: a
        // zero would be a leg the reader could look for on the drawing, and the
        // totals must be the journey's own (SPEC §6).
        val option = fromAStreetBike(ride = 45, walkFrom = 5)

        assertEquals(50, option.climbMetres)
        assertEquals(3_200, option.distanceMetres)
        assertEquals(3_200.seconds, option.travelTime)
    }
}
