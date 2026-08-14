package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counting of what a network lends, from the bikes standing at its stations.
 *
 * Every figure here comes from the live survey of the three hundred and
 * thirty-three networks served, run on 14 August 2026: these are readings really
 * taken, not hypotheses. The same cases hold `tools/read_fleet.py` to account,
 * the two having to agree.
 */
class FleetReadingTest {

    private fun availability(byType: Map<String, Int>) = StationAvailability(
        stationId = "1",
        bikesAvailable = byType.values.sum(),
        bikesByVehicleType = byType,
        docksAvailable = 5,
        isInstalled = true,
        isRenting = true,
        isReturning = true,
        reportedAt = null,
    )

    /** Munich's table: nextbike numbers its types and declares them. */
    private val munich = mapOf(
        "346" to VehicleKind.Mechanical,
        "348" to VehicleKind.Electric,
    )

    @Test
    fun `a network with both kinds out is mixed`() {
        val reading = countFleet(
            availabilities = listOf(
                availability(mapOf("346" to 5, "348" to 1)),
                availability(mapOf("346" to 3, "348" to 4)),
            ),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertTrue(reading.isMixed)
        assertTrue(reading.hasElectricBikes)
        assertEquals(13, reading.bikesCounted)
        assertEquals(munich, reading.vehicleTypes)
    }

    @Test
    fun `Madrid declares a mechanical type and puts out none`() {
        // 5857 electric bikes, not one mechanical: the declaration says what the
        // operator may lend one day, the stations say what one can ride today.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("346" to 0, "348" to 5857))),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertFalse(reading.isMixed)
        assertTrue(reading.hasElectricBikes)
    }

    @Test
    fun `Berlin declares an electric type and puts out none`() {
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("346" to 1971, "348" to 0))),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertFalse(reading.isMixed)
        assertFalse(reading.hasElectricBikes)
    }

    @Test
    fun `a residue of one kind does not make a mixed fleet`() {
        // Barcelona: 1922 electric bikes and 2 mechanical ones, a tenth of a
        // percent. Splitting a count on that promises what nobody will find.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("346" to 2, "348" to 1922))),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertFalse(reading.isMixed)
        assertTrue(reading.hasElectricBikes)
    }

    @Test
    fun `one electric bike out of twenty is still a mixed fleet`() {
        // The floor has to keep the smallest genuine offers: five percent is an
        // offer, and somebody deciding whether to climb wants to know.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("346" to 19, "348" to 1))),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertTrue(reading.isMixed)
    }

    @Test
    fun `nothing out to count keeps the declaration`() {
        // Every station empty at that moment, or a feed publishing no breakdown
        // at all: it must not turn an electric city into a mechanical one.
        val reading = countFleet(
            availabilities = listOf(availability(emptyMap())),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertEquals(0, reading.bikesCounted)
        assertTrue(reading.hasElectricBikes)
        assertFalse("a declaration alone never makes a mixed fleet", reading.isMixed)
    }

    @Test
    fun `reads the kinds Velib names inline, having no vehicle_types feed`() {
        // GBFS 1.0: no declaration to point identifiers at, and 7854 electric
        // bikes that would otherwise be invisible.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("mechanical" to 3, "ebike" to 2))),
            declaredVehicleTypes = emptyMap(),
            declaresElectricBikes = false,
        )

        assertTrue(reading.isMixed)
        assertTrue(reading.hasElectricBikes)
        assertEquals(5, reading.bikesCounted)
        assertEquals(
            "the table has to carry both names, or a station's split is silenced",
            mapOf("mechanical" to VehicleKind.Mechanical, "ebike" to VehicleKind.Electric),
            reading.vehicleTypes,
        )
    }

    @Test
    fun `ignores an identifier the network never declared`() {
        // Five of the networks served publish at their stations a type absent
        // from their declaration: a bike of unknown propulsion belongs in
        // neither column.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("346" to 4, "unknown" to 99))),
            declaredVehicleTypes = munich,
            declaresElectricBikes = false,
        )

        assertEquals(4, reading.bikesCounted)
        assertFalse(reading.hasElectricBikes)
    }

    @Test
    fun `sets scooters aside instead of counting them as bikes`() {
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("348" to 2, "scooter" to 40))),
            declaredVehicleTypes = munich + mapOf("scooter" to VehicleKind.Other),
            declaresElectricBikes = true,
        )

        assertEquals(2, reading.bikesCounted)
        assertFalse("a fleet of scooters is not a mixed bike fleet", reading.isMixed)
    }

    @Test
    fun `a declared type wins over the name Velib uses`() {
        // A network declaring a type of its own called "mechanical" means its
        // own. Here it declares that name electric, and it is counted electric.
        val reading = countFleet(
            availabilities = listOf(availability(mapOf("mechanical" to 10))),
            declaredVehicleTypes = mapOf("mechanical" to VehicleKind.Electric),
            declaresElectricBikes = true,
        )

        assertTrue(reading.hasElectricBikes)
        assertFalse(reading.isMixed)
    }

    @Test
    fun `counts the whole network, not one station`() {
        // A station holding one kind says nothing: it is the network that is
        // mixed or not, and the glyph is drawn for the city.
        val reading = countFleet(
            availabilities = listOf(
                availability(mapOf("346" to 10)),
                availability(mapOf("348" to 10)),
            ),
            declaredVehicleTypes = munich,
            declaresElectricBikes = true,
        )

        assertTrue(reading.isMixed)
    }
}
