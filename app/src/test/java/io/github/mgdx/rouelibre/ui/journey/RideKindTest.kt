package io.github.mgdx.rouelibre.ui.journey

import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.DeparturePoint
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.ui.BikeFleet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests which kind of bike the journey's ride is said to be made on (SPEC §7.4).
 *
 * The rule the drawing and the screen reader share: a kind is named only where
 * it is settled — asked for and applied, or read off the one bike chosen — and
 * only where the network lends both, a one-kind network's bike already saying
 * which it is.
 */
class RideKindTest {

    private val position = Coordinates(43.2965, 5.3698)

    private val atStation = DeparturePoint.AtStation(
        Station(
            id = "depart",
            name = "Station",
            position = position,
            capacity = 20,
            postalCode = null,
        ),
    )

    private fun atStreetBike(kind: VehicleKind) = DeparturePoint.AtStreetBike(
        StreetBike(
            id = "velo-de-rue",
            position = position,
            vehicleTypeId = "type",
            chargeRatio = null,
            rangeMetres = null,
        ),
        kind,
    )

    @Test
    fun `a journey through the stations rides the kind it was worked out for`() {
        assertEquals(WantedBikeKind.Electric, rideKindOf(atStation, WantedBikeKind.Electric))
        assertEquals(WantedBikeKind.Mechanical, rideKindOf(atStation, WantedBikeKind.Mechanical))
    }

    @Test
    fun `a journey worked out for any bike names none`() {
        assertNull(rideKindOf(atStation, computedFor = null))
    }

    @Test
    fun `a street bike rides its own kind, whatever was asked for`() {
        assertEquals(
            WantedBikeKind.Electric,
            rideKindOf(atStreetBike(VehicleKind.Electric), WantedBikeKind.Mechanical),
        )
        assertEquals(
            WantedBikeKind.Mechanical,
            rideKindOf(atStreetBike(VehicleKind.Mechanical), computedFor = null),
        )
    }

    @Test
    fun `a street vehicle that is no bike names none`() {
        assertNull(rideKindOf(atStreetBike(VehicleKind.Other), WantedBikeKind.Electric))
    }

    @Test
    fun `a network lending both kinds says the kind ridden`() {
        assertEquals(
            WantedBikeKind.Electric,
            shownRideKind(BikeFleet.Mixed, WantedBikeKind.Electric),
        )
        assertEquals(
            WantedBikeKind.Mechanical,
            shownRideKind(BikeFleet.Mixed, WantedBikeKind.Mechanical),
        )
        assertNull(shownRideKind(BikeFleet.Mixed, rideKind = null))
    }

    @Test
    fun `a network lending one kind keeps its own bike`() {
        // Its bike already says which, and a street bike there is that kind.
        assertNull(shownRideKind(BikeFleet.Electric, WantedBikeKind.Electric))
        assertNull(shownRideKind(BikeFleet.Mechanical, WantedBikeKind.Mechanical))
    }

    @Test
    fun `the station discs bear the bike taken in a network lending both`() {
        assertEquals(
            R.drawable.marker_journey_station_electric,
            stationMarkerOf(BikeFleet.Mixed, WantedBikeKind.Electric),
        )
        assertEquals(
            R.drawable.marker_journey_station,
            stationMarkerOf(BikeFleet.Mixed, WantedBikeKind.Mechanical),
        )
    }

    @Test
    fun `the station discs keep the network's badge where the bike is not named`() {
        assertEquals(
            R.drawable.marker_journey_station_mixed,
            stationMarkerOf(BikeFleet.Mixed, rideKind = null),
        )
        assertEquals(
            R.drawable.marker_journey_station_electric,
            stationMarkerOf(BikeFleet.Electric, WantedBikeKind.Mechanical),
        )
    }
}
