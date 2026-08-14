package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The splitting of a station's bikes into mechanical and electric.
 *
 * Every case here comes from a live survey of the three hundred and thirty-three
 * networks served: the shapes refused are shapes really published, not
 * hypotheses.
 */
class BikeSplitTest {

    private fun availability(bikes: Int, byType: Map<String, Int>) = StationAvailability(
        stationId = "1",
        bikesAvailable = bikes,
        bikesByVehicleType = byType,
        docksAvailable = 5,
        isInstalled = true,
        isRenting = true,
        isReturning = true,
        reportedAt = null,
    )

    // Munich's table: nextbike numbers its types and declares them.
    private val munich = mapOf(
        "346" to VehicleKind.Mechanical,
        "348" to VehicleKind.Electric,
    )

    @Test
    fun `splits a station between the two kinds`() {
        val split = availability(6, mapOf("346" to 5, "348" to 1)).splitByKind(munich)

        assertEquals(BikeSplit(mechanical = 5, electric = 1), split)
    }

    @Test
    fun `a kind absent from the station is counted as zero, not as unknown`() {
        val split = availability(3, mapOf("346" to 3)).splitByKind(munich)

        assertEquals(BikeSplit(mechanical = 3, electric = 0), split)
    }

    @Test
    fun `sets scooters aside instead of counting them as bikes`() {
        // Chicago lends both, and its status feed counts them side by side;
        // num_bikes_available, however, counts only the bikes.
        val table = munich + mapOf("scooter" to VehicleKind.Other)

        val split = availability(6, mapOf("346" to 5, "348" to 1, "scooter" to 4))
            .splitByKind(table)

        assertEquals(BikeSplit(mechanical = 5, electric = 1), split)
    }

    @Test
    fun `gives up on a vehicle type the network never declared`() {
        // Five networks — Potsdam and Erkner among them — publish at their
        // stations a type absent from their own declaration. A bike of unknown
        // propulsion belongs in neither column.
        val split = availability(6, mapOf("346" to 5, "431" to 1)).splitByKind(munich)

        assertNull(split)
    }

    @Test
    fun `gives up when the breakdown does not add up to the count displayed`() {
        // The Beryl networks count their scooters in num_bikes_available but
        // not in the breakdown: at Norwich it accounts for a fraction of the
        // number shown. Splitting six bikes into "two and one" would be false.
        val split = availability(6, mapOf("346" to 2, "348" to 1)).splitByKind(munich)

        assertNull(split)
    }

    @Test
    fun `gives up when the feed publishes no breakdown`() {
        assertNull(availability(6, emptyMap()).splitByKind(munich))
    }

    @Test
    fun `gives up when the network has no table to read the identifiers with`() {
        assertNull(availability(6, mapOf("346" to 6)).splitByKind(emptyMap()))
    }
}
