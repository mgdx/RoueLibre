package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of what the map's filters take away, and of what they must never take. */
class StationFilterTest {

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.633, 3.053),
        capacity = 20,
        postalCode = "59000",
    )

    private fun entry(
        id: String,
        bikes: Int = 4,
        docks: Int = 4,
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
        byVehicleType: Map<String, Int> = emptyMap(),
    ) = StationWithAvailability(
        station = station(id),
        availability = StationAvailability(
            stationId = id,
            bikesAvailable = bikes,
            docksAvailable = docks,
            bikesByVehicleType = byVehicleType,
            isInstalled = installed,
            isRenting = renting,
            isReturning = returning,
            reportedAt = null,
        ),
    )

    /** A station the real-time feed says nothing about: the delicate case. */
    private fun unknown(id: String) = StationWithAvailability(station(id), availability = null)

    private fun idsShown(
        stations: List<StationWithAvailability>,
        filter: StationFilter,
        mode: AvailabilityMode = AvailabilityMode.Bikes,
        kind: BikeKindFilter? = null,
    ) = stationsShownOnMap(stations, filter, mode, kind).map { it.station.id }

    @Test
    fun `at rest the filter takes nothing away`() {
        val stations = listOf(
            entry("full"),
            entry("empty", bikes = 0),
            entry("closed", installed = false),
            unknown("silent"),
        )

        assertTrue(StationFilter().hidesNothing)
        assertEquals(
            listOf("full", "empty", "closed", "silent"),
            idsShown(stations, StationFilter()),
        )
    }

    @Test
    fun `hiding the out of service leaves the stations the feed says nothing about`() {
        val stations = listOf(entry("open"), entry("closed", installed = false), unknown("silent"))

        assertEquals(
            listOf("open", "silent"),
            idsShown(stations, StationFilter(hideOutOfService = true)),
        )
    }

    @Test
    fun `a station that neither lends nor takes back is out of service in both modes`() {
        val stations = listOf(entry("dead", renting = false, returning = false))
        val filter = StationFilter(hideOutOfService = true)

        assertEquals(emptyList<String>(), idsShown(stations, filter, AvailabilityMode.Bikes))
        assertEquals(emptyList<String>(), idsShown(stations, filter, AvailabilityMode.Docks))
    }

    @Test
    fun `hiding the empty ones leaves a station whose count was never read`() {
        val stations = listOf(entry("some"), entry("none", bikes = 0), unknown("silent"))

        assertEquals(
            listOf("some", "silent"),
            idsShown(stations, StationFilter(hideEmpty = true)),
        )
    }

    @Test
    fun `empty turns round with the mode`() {
        // Nothing to borrow, everything to give back: the same station is out of
        // the way of somebody looking for a bike and exactly what somebody
        // carrying one is after.
        val stations = listOf(entry("drained", bikes = 0, docks = 12))
        val filter = StationFilter(hideEmpty = true)

        assertEquals(emptyList<String>(), idsShown(stations, filter, AvailabilityMode.Bikes))
        assertEquals(listOf("drained"), idsShown(stations, filter, AvailabilityMode.Docks))

        val full = listOf(entry("packed", bikes = 12, docks = 0))
        assertEquals(listOf("packed"), idsShown(full, filter, AvailabilityMode.Bikes))
        assertEquals(emptyList<String>(), idsShown(full, filter, AvailabilityMode.Docks))
    }

    @Test
    fun `the two filters compose without contradicting each other`() {
        val stations = listOf(
            entry("good"),
            entry("empty", bikes = 0),
            entry("closed", installed = false),
            entry("closed and empty", bikes = 0, renting = false, returning = false),
            unknown("silent"),
        )

        assertEquals(
            listOf("good", "silent"),
            idsShown(stations, StationFilter(hideOutOfService = true, hideEmpty = true)),
        )
    }

    @Test
    fun `a breakdown that cannot be read is not a station without bikes of that kind`() {
        val vehicleTypes = mapOf("mech" to VehicleKind.Mechanical, "elec" to VehicleKind.Electric)
        val kind = BikeKindFilter(WantedBikeKind.Electric, vehicleTypes)
        val stations = listOf(
            entry("has electric", bikes = 3, byVehicleType = mapOf("mech" to 1, "elec" to 2)),
            entry("no electric", bikes = 3, byVehicleType = mapOf("mech" to 3, "elec" to 0)),
            // Published under a type the network never declared: unreadable, so
            // the station promises nothing and is accused of nothing.
            entry("unreadable", bikes = 3, byVehicleType = mapOf("scooter" to 3)),
            // No breakdown at all, which is most feeds.
            entry("no breakdown", bikes = 3),
        )

        assertEquals(
            listOf("has electric", "unreadable", "no breakdown"),
            idsShown(stations, StationFilter(hideEmpty = true), kind = kind),
        )
    }

    @Test
    fun `the order the stations arrived in is kept`() {
        val stations = listOf(entry("c"), entry("b"), entry("a"))

        assertEquals(
            listOf("c", "b", "a"),
            idsShown(stations, StationFilter(hideOutOfService = true, hideEmpty = true)),
        )
    }
}
