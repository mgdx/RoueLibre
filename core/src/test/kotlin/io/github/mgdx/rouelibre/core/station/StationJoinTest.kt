package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests of bringing static data together with real-time state.
 *
 * The edge case is not theoretical: on 9 August 2026 the Lille network's feed
 * published 268 stations in `station_information` and 267 in `station_status`.
 */
class StationJoinTest {

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.63, 3.06),
        capacity = 20,
        postalCode = "59000",
    )

    private fun availability(
        id: String,
        bikes: Int = 5,
        docks: Int = 15,
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
    ) = StationAvailability(
        stationId = id,
        bikesAvailable = bikes,
        docksAvailable = docks,
        isInstalled = installed,
        isRenting = renting,
        isReturning = returning,
        reportedAt = null,
    )

    @Test
    fun `a station without a state stays present and its state stays unknown`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("1"), station("2")),
            availabilities = listOf(availability("2")),
        )

        assertEquals(2, joined.size)
        val orphan = joined.first { it.station.id == "1" }
        assertNull(orphan.availability)
        assertEquals(ServiceState.Unknown, orphan.serviceState)
    }

    @Test
    fun `an orphan state is ignored for want of knowing where to place it`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("1")),
            availabilities = listOf(availability("1"), availability("fantome")),
        )

        assertEquals(1, joined.size)
        assertEquals("1", joined.single().station.id)
    }

    @Test
    fun `the stations' order is preserved`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("c"), station("a"), station("b")),
            availabilities = emptyList(),
        )

        assertEquals(listOf("c", "a", "b"), joined.map { it.station.id })
    }

    @Test
    fun `a station not deployed is out of service`() {
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", installed = false)),
        )

        assertEquals(ServiceState.OutOfService, joined.single().serviceState)
    }

    @Test
    fun `a station that neither rents nor receives is out of service`() {
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", renting = false, returning = false)),
        )

        assertEquals(ServiceState.OutOfService, joined.single().serviceState)
    }

    @Test
    fun `a station that only receives stays in service`() {
        // It still provides a real service: returning a bike to it.
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", renting = false)),
        )

        assertEquals(ServiceState.InService, joined.single().serviceState)
    }

    @Test
    fun `an empty station stays in service`() {
        // Zero bikes is not a breakdown: it is information to display.
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", bikes = 0)),
        )

        assertEquals(ServiceState.InService, joined.single().serviceState)
        assertEquals(false, joined.single().availability?.canLendBike)
    }
}
