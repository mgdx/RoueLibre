package io.github.mgdx.rouelibre.ui.journey

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.VehicleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What the two journey screens say of the bikes waiting at the departure
 * station (SPEC §7.4).
 *
 * The wording itself takes a `Context` and is left to the device; what is
 * checked here is the one decision behind it — whether there is anything to
 * say at all.
 */
class JourneySummariesTest {

    private val lille = mapOf(
        "mecanique" to VehicleKind.Mechanical,
        "electrique" to VehicleKind.Electric,
    )

    private fun leg(mode: TravelMode) = RouteLeg(
        mode = mode,
        distanceMetres = 500,
        duration = 5.minutes,
        ascentMetres = 0,
        geometry = emptyList(),
    )

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.63, 3.06),
        capacity = 20,
        postalCode = "59000",
    )

    private fun option(bikes: Int, byType: Map<String, Int>) = JourneyOption(
        departureStation = station("departure"),
        arrivalStation = station("arrival"),
        bikesAtDeparture = bikes,
        bikesByVehicleTypeAtDeparture = byType,
        docksAtArrival = 5,
        walkToStation = leg(TravelMode.Walking),
        ride = leg(TravelMode.Cycling),
        walkToDestination = leg(TravelMode.Walking),
        riskPenalty = 30.seconds,
    )

    private fun fleet(isMixed: Boolean) = FleetDescription(
        hasElectricBikes = true,
        isMixed = isMixed,
        vehicleTypes = lille,
    )

    @Test
    fun `splits what the departure station held when both kinds are lent`() {
        val split = option(bikes = 4, byType = mapOf("mecanique" to 3, "electrique" to 1))
            .bikeSplitAtDeparture(fleet(isMixed = true))

        assertEquals(BikeSplit(mechanical = 3, electric = 1), split)
    }

    @Test
    fun `says nothing in a city that lends one kind`() {
        // "3 mechanical · 0 electric" would announce a shortage that does not
        // exist (SPEC §7.2), and the count beside it already says everything.
        val split = option(bikes = 3, byType = mapOf("mecanique" to 3))
            .bikeSplitAtDeparture(fleet(isMixed = false))

        assertNull(split)
    }

    @Test
    fun `says nothing while the city served is still being read from disk`() {
        val split = option(bikes = 4, byType = mapOf("mecanique" to 3, "electrique" to 1))
            .bikeSplitAtDeparture(null)

        assertNull(split)
    }

    @Test
    fun `the departure split takes no kind, so a filter cannot reach it`() {
        // A negative requirement, and therefore worth a test of its own. The
        // result screen and its detail show BOTH counts whatever kind was asked
        // for on the search screen (SPEC §7.4): somebody who asked for an
        // electric bike and opens the journey wants to know what is waiting,
        // not to be handed back their own request — that is also what lets them
        // change their mind knowing what they are changing to. The day somebody
        // wires a kind in here, this fails.
        val split = Class.forName("io.github.mgdx.rouelibre.ui.journey.JourneySummariesKt")
            .declaredMethods
            .single { it.name == "bikeSplitAtDeparture" }

        assertEquals(
            listOf(JourneyOption::class.java, FleetDescription::class.java),
            split.parameterTypes.toList(),
        )
    }

    @Test
    fun `says nothing when the breakdown does not add up to the count shown`() {
        // The shape the Beryl networks publish: the total counts scooters the
        // breakdown does not. A wrong split sends somebody to a station for a
        // bike that is not there.
        val split = option(bikes = 9, byType = mapOf("mecanique" to 3, "electrique" to 1))
            .bikeSplitAtDeparture(fleet(isMixed = true))

        assertNull(split)
    }
}
