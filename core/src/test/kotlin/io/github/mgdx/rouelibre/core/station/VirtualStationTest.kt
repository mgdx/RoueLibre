package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the rule that `is_installed` closes no virtual station.
 *
 * The documents are trimmed from Pony's Limoges feed as it stood on 6 October
 * 2026: every station virtual, every one "not installed", all renting and
 * returning, 269 bikes between them. The application showed the whole city
 * out of service. Cergy and Mulhouse, closed for real, publish the same
 * `is_installed: false` on stations that are not virtual, and must stay shut.
 */
class VirtualStationTest {

    private val parser = GbfsParser()

    private val information = """
        {"last_updated":"2026-10-06T08:00:00+02:00","ttl":60,"version":"3.0",
         "data":{"stations":[
          {"station_id":"pony-limoges-1","name":[{"text":"Place Jourdan","language":"fr"}],
           "lat":45.8302,"lon":1.2643,"is_virtual_station":true},
          {"station_id":"pony-limoges-2","name":[{"text":"Gare","language":"fr"}],
           "lat":45.8361,"lon":1.2676,"is_virtual_station":1},
          {"station_id":"dock-3","name":[{"text":"Rack","language":"fr"}],
           "lat":45.8300,"lon":1.2600}
        ]}}
    """.trimIndent()

    private val status = """
        {"last_updated":"2026-10-06T08:00:00+02:00","ttl":60,"version":"3.0",
         "data":{"stations":[
          {"station_id":"pony-limoges-1","num_vehicles_available":4,
           "num_docks_available":0,"is_installed":false,"is_renting":true,
           "is_returning":true,"last_reported":"2026-10-06T07:59:30+02:00"},
          {"station_id":"pony-limoges-2","num_vehicles_available":2,
           "num_docks_available":3,"is_installed":false,"is_renting":false,
           "is_returning":true,"last_reported":"2026-10-06T07:59:30+02:00"},
          {"station_id":"dock-3","num_vehicles_available":0,
           "num_docks_available":0,"is_installed":false,"is_renting":false,
           "is_returning":false,"last_reported":"2026-10-06T07:59:30+02:00"}
        ]}}
    """.trimIndent()

    private fun <T> success(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw AssertionError("parse failed: ${outcome.error}")
    }

    private fun joined(): Map<String, StationWithAvailability> = joinStationsWithAvailability(
        success(parser.parseStationInformation(information)).stations,
        success(parser.parseStationStatus(status)).availabilities,
    ).associateBy { it.station.id }

    @Test
    fun `reads is_virtual_station as a boolean, as an integer, or absent`() {
        val stations = success(parser.parseStationInformation(information)).stations
            .associateBy { it.id }

        assertTrue(stations.getValue("pony-limoges-1").isVirtual)
        assertTrue(stations.getValue("pony-limoges-2").isVirtual)
        assertFalse(stations.getValue("dock-3").isVirtual)
    }

    @Test
    fun `a virtual station not installed but renting is in service and lends its bikes`() {
        val station = joined().getValue("pony-limoges-1")

        assertEquals(ServiceState.InService, station.serviceState)
        assertTrue(station.availability!!.canLendBike)
        val display = station.displayFor(AvailabilityMode.Bikes)
        assertFalse(display.isOutOfService)
        assertEquals(4, display.count)
    }

    @Test
    fun `a virtual station that does not rent lends no bike`() {
        val station = joined().getValue("pony-limoges-2")

        assertFalse(station.availability!!.canLendBike)
        assertTrue(station.displayFor(AvailabilityMode.Bikes).isOutOfService)
        // Returning is still open, and still decides on its own side.
        assertTrue(station.availability.canAcceptBike)
        assertFalse(station.displayFor(AvailabilityMode.Docks).isOutOfService)
    }

    @Test
    fun `a physical station not installed stays out of service`() {
        val station = joined().getValue("dock-3")

        assertEquals(ServiceState.OutOfService, station.serviceState)
        assertTrue(station.displayFor(AvailabilityMode.Bikes).isOutOfService)
    }

    @Test
    fun `a physical station not installed but renting stays out of service`() {
        // The rule rests on the station being virtual, not on is_renting
        // overriding is_installed everywhere.
        val station = Station(
            id = "dock",
            name = "Rack",
            position = Coordinates(45.83, 1.26),
            capacity = 10,
            postalCode = null,
        )
        val state = StationAvailability(
            stationId = "dock",
            bikesAvailable = 3,
            docksAvailable = 7,
            isInstalled = false,
            isRenting = true,
            isReturning = true,
            reportedAt = null,
        )

        val joined = joinStationsWithAvailability(listOf(station), listOf(state)).single()

        assertEquals(ServiceState.OutOfService, joined.serviceState)
        assertFalse(joined.availability!!.canLendBike)
    }

    @Test
    fun `the state of a virtual station keeps the feed's own is_installed`() {
        // What is stored must be what the feed sent: the rule is applied when
        // the state meets its station, never written over the flag.
        val state = joined().getValue("pony-limoges-1").availability!!

        assertFalse(state.isInstalled)
        assertTrue(state.isDeployed)
    }
}
