package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.station.FleetReading
import io.github.mgdx.rouelibre.data.local.StationAvailabilityEntity
import io.github.mgdx.rouelibre.data.local.StationDao
import io.github.mgdx.rouelibre.data.local.StationEntity
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tests of the refresh policy (SPEC §4.1).
 *
 * A real local HTTP server rather than a fake source: that also covers the
 * OkHttp client and the decoding of responses, which is to say the whole path a
 * refresh actually travels.
 */
class StationRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeStationDao
    private lateinit var timestamps: FakeRefreshTimestampStore
    private var now: Instant = Instant.parse("2026-08-09T12:00:00Z")

    /** The auto-discovery document in force, which a change of city moves. */
    private var discoveryPath: String = "/gbfs.json"

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        dao = FakeStationDao()
        timestamps = FakeRefreshTimestampStore()
    }

    @After
    fun stopServer() {
        server.close()
    }

    private fun repository(
        recordFleet: suspend (FleetReading) -> Unit = {},
        streetBikesMinimumInterval: suspend () -> Duration = { Duration.ofMinutes(5) },
    ): StationRepository = StationRepository(
        remote = GbfsRemoteSource(
            client = OkHttpClient(),
            parser = GbfsParser(),
            userAgent = "RoueLibre-test/1.0",
            unnamedStationLabel = { "Unnamed station" },
            ioDispatcher = Dispatchers.IO,
        ),
        dao = dao,
        refreshTimestamps = timestamps,
        discoveryUrlProvider = { server.url(discoveryPath).toString() },
        recordFleet = recordFleet,
        streetBikesMinimumInterval = streetBikesMinimumInterval,
        clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        },
    )

    private fun enqueueDiscovery(streetBikes: Boolean = false, vehicleTypes: Boolean = false) {
        val feeds = buildList {
            add("station_information" to "/information.json")
            add("station_status" to "/status.json")
            if (vehicleTypes) add("vehicle_types" to "/vehicle_types.json")
            if (streetBikes) add("free_bike_status" to "/free_bike_status.json")
        }.joinToString(",") { (name, path) ->
            """{"name":"$name","url":"${server.url(path)}"}"""
        }
        val body = """
            {"last_updated":1786264920,"ttl":0,"version":"2.3","data":{"en":{"feeds":[$feeds]}}}
        """.trimIndent()
        server.enqueue(MockResponse(body = body))
    }

    /** nextbike's table: a mechanical type, an electric one, and a scooter. */
    private fun enqueueVehicleTypes() {
        server.enqueue(
            MockResponse(
                body = """
                    {"version":"2.3","data":{"vehicle_types":[
                      {"vehicle_type_id":"346","form_factor":"bicycle","propulsion_type":"human"},
                      {"vehicle_type_id":"348","form_factor":"bicycle",
                       "propulsion_type":"electric_assist","max_range_meters":60000},
                      {"vehicle_type_id":"360","form_factor":"scooter","propulsion_type":"electric"}
                    ]}}
                """.trimIndent(),
            ),
        )
    }

    /** Two bikes on the street, a scooter beside them, and a bike at a station. */
    private fun enqueueStreetBikes() {
        server.enqueue(
            MockResponse(
                body = """
                    {"version":"2.3","data":{"bikes":[
                      {"bike_id":"e1","lat":50.633,"lon":3.053,"vehicle_type_id":"348",
                       "current_fuel_percent":0.67,"current_range_meters":0},
                      {"bike_id":"m1","lat":50.634,"lon":3.054,"vehicle_type_id":"346"},
                      {"bike_id":"s1","lat":50.635,"lon":3.055,"vehicle_type_id":"360"},
                      {"bike_id":"d1","lat":50.636,"lon":3.071,"vehicle_type_id":"346",
                       "station_id":"2"}
                    ]}}
                """.trimIndent(),
            ),
        )
    }

    private fun enqueueInformation() {
        server.enqueue(
            MockResponse(
                body = """
                    {"version":"2.3","data":{"stations":[
                      {"station_id":"1","name":"Rue Nationale","lat":50.633,"lon":3.053,
                       "capacity":20,"post_code":"59000"},
                      {"station_id":"2","name":"Gare Lille Flandres","lat":50.636,
                       "lon":3.071,"capacity":40,"post_code":"59000"}
                    ]}}
                """.trimIndent(),
            ),
        )
    }

    private fun enqueueStatus(bikesAtFirstStation: Int) {
        server.enqueue(
            MockResponse(
                body = """
                    {"version":"2.3","data":{"stations":[
                      {"station_id":"1","num_bikes_available":$bikesAtFirstStation,
                       "num_docks_available":5,"is_installed":true,"is_renting":true,
                       "is_returning":true,"last_reported":1786264900}
                    ]}}
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a first refresh fetches discovery, stations and state`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)

        val outcome = repository().refresh()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(3, server.requestCount)
        assertEquals(2, dao.stations.value.size)
        assertEquals(7, dao.availabilities.value.single().bikesAvailable)
    }

    @Test
    fun `changing city leaves none of the previous city's stations`() = runTest {
        // They have no business on another conurbation's map, and offline
        // nothing would come to replace them (SPEC §15.1).
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()

        repository.forget()

        assertEquals(0, dao.stations.value.size)
        assertEquals(0, dao.availabilities.value.size)
    }

    @Test
    fun `the User-Agent header names the application without a device identifier`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 1)

        repository().refresh()

        val userAgent = server.takeRequest().headers["User-Agent"]
        assertEquals("RoueLibre-test/1.0", userAgent)
    }

    @Test
    fun `a second refresh within the minute does not go out on the network`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()
        val afterFirst = server.requestCount

        now += Duration.ofSeconds(30)
        val outcome = repository.refresh()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(afterFirst, server.requestCount)
    }

    @Test
    fun `pull to refresh overrides the minimum delay`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()

        now += Duration.ofSeconds(5)
        enqueueStatus(bikesAtFirstStation = 2)
        repository.refresh(force = true)

        // Discovery and static data are not asked for again, only the state
        // is: a single extra request.
        assertEquals(4, server.requestCount)
        assertEquals(2, dao.availabilities.value.single().bikesAvailable)
    }

    @Test
    fun `static data is not asked for again before a day has passed`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()

        now += Duration.ofHours(2)
        enqueueStatus(bikesAtFirstStation = 3)
        repository.refresh()

        assertEquals(4, server.requestCount)
        assertEquals(2, dao.stations.value.size)
    }

    @Test
    fun `static data is asked for again once a day has passed`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()

        now += Duration.ofDays(1).plusMinutes(1)
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 3)
        repository.refresh()

        assertEquals(5, server.requestCount)
    }

    @Test
    fun `a failure on static data does not prevent refreshing the state`() = runTest {
        // The cache already holds the stations: their momentary unavailability
        // must not deprive the user of fresh availability.
        dao.stations.value = listOf(
            StationEntity("1", "Rue Nationale", 50.633, 3.053, 20, "59000"),
        )
        timestamps.fetchedAt = null
        enqueueDiscovery()
        server.enqueue(MockResponse(code = 503))
        enqueueStatus(bikesAtFirstStation = 4)

        val outcome = repository().refresh()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(4, dao.availabilities.value.single().bikesAvailable)
    }

    @Test
    fun `a failure on static data is fatal when the cache is empty`() = runTest {
        enqueueDiscovery()
        server.enqueue(MockResponse(code = 503))

        val outcome = repository().refresh()

        assertEquals(Outcome.Failure(DataError.ServerRefused(503)), outcome)
    }

    @Test
    fun `a server erroring on the state reports the code received`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        server.enqueue(MockResponse(code = 500))

        val outcome = repository().refresh()

        assertEquals(Outcome.Failure(DataError.ServerRefused(500)), outcome)
    }

    @Test
    fun `an unreadable response is reported as such`() = runTest {
        server.enqueue(MockResponse(body = "<html>maintenance</html>"))

        val outcome = repository().refresh()

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    // ------------------------------------------- the bikes outside stations --

    @Test
    fun `the street bikes are read on request and kept in memory alone`() = runTest {
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository()

        val outcome = repository.refreshStreetBikes()

        assertEquals(Outcome.Success(Unit), outcome)
        // Discovery and the feed: the table is unavailable here, and nothing
        // else is asked for.
        assertEquals(2, server.requestCount)
        val snapshot = repository.observeStreetBikes().first()
        assertEquals(listOf("e1", "m1", "s1"), snapshot.bikes.map { it.id })
        assertEquals(now, snapshot.fetchedAt)
        assertEquals(true, snapshot.published)
        assertTrue("nothing of them reaches the database", dao.availabilities.value.isEmpty())
    }

    @Test
    fun `nothing is said of the street bikes before they are asked for`() = runTest {
        val snapshot = repository().observeStreetBikes().first()

        assertTrue(snapshot.bikes.isEmpty())
        assertNull(snapshot.fetchedAt)
        assertNull(snapshot.published)
    }

    @Test
    fun `a second read of the street bikes within five minutes does not go out`() = runTest {
        // The feed weighs twenty times the station feed and little moves in
        // it (SPEC §4.1).
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository()
        repository.refreshStreetBikes()
        val afterFirst = server.requestCount

        now += Duration.ofMinutes(4)
        val outcome = repository.refreshStreetBikes()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(afterFirst, server.requestCount)

        now += Duration.ofMinutes(1)
        enqueueStreetBikes()
        repository.refreshStreetBikes()

        assertEquals(afterFirst + 1, server.requestCount)
    }

    @Test
    fun `the minutes between two reads are the user's, read at each call`() = runTest {
        // The slider in the settings applies to the next read, not to the
        // next launch (SPEC §7.6): the interval is asked for every time.
        var minutes = 1L
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository(streetBikesMinimumInterval = { Duration.ofMinutes(minutes) })
        repository.refreshStreetBikes()
        val afterFirst = server.requestCount

        now += Duration.ofSeconds(90)
        enqueueStreetBikes()
        repository.refreshStreetBikes()
        assertEquals("one minute has passed", afterFirst + 1, server.requestCount)

        minutes = 30
        now += Duration.ofMinutes(20)
        repository.refreshStreetBikes()
        assertEquals("thirty minutes have not", afterFirst + 1, server.requestCount)
    }

    @Test
    fun `the bikes at the stations travel with the street bikes, by station`() = runTest {
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository()

        repository.refreshStreetBikes()

        val docked = repository.observeStreetBikes().first().dockedBikes
        assertEquals(setOf("2"), docked.keys)
        assertEquals("346", docked.getValue("2").single().vehicleTypeId)
        assertTrue("nothing of them reaches the database", dao.availabilities.value.isEmpty())
    }

    @Test
    fun `pull to refresh overrides the five minutes`() = runTest {
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository()
        repository.refreshStreetBikes()

        now += Duration.ofSeconds(5)
        enqueueStreetBikes()
        repository.refreshStreetBikes(force = true)

        assertEquals(3, server.requestCount)
        assertEquals(now, repository.observeStreetBikes().first().fetchedAt)
    }

    @Test
    fun `a network publishing no such feed is remembered as publishing none`() = runTest {
        // An ordinary answer for a docked fleet, not a failure — and not a
        // question to ask again, even on a pull to refresh.
        enqueueDiscovery(streetBikes = false)
        val repository = repository()

        val outcome = repository.refreshStreetBikes()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(false, repository.observeStreetBikes().first().published)
        assertEquals(1, server.requestCount)

        repository.refreshStreetBikes(force = true)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `forgetting the city drops the street bikes`() = runTest {
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        val repository = repository()
        repository.refreshStreetBikes()

        repository.forget()

        val snapshot = repository.observeStreetBikes().first()
        assertTrue(snapshot.bikes.isEmpty())
        assertNull(snapshot.published)
    }

    @Test
    fun `one city's street bikes, and its silence, are not read as another's`() = runTest {
        // Lille publishes no such feed; the traveller then switches to a
        // network that does, and must neither keep Lille's answer nor wait
        // five minutes for the new city's bikes.
        enqueueDiscovery(streetBikes = false)
        val repository = repository()
        repository.refreshStreetBikes()
        assertEquals(false, repository.observeStreetBikes().first().published)

        discoveryPath = "/berlin/gbfs.json"
        enqueueDiscovery(streetBikes = true)
        enqueueStreetBikes()
        repository.refreshStreetBikes()

        val snapshot = repository.observeStreetBikes().first()
        assertEquals(true, snapshot.published)
        assertEquals(3, snapshot.bikes.size)
    }

    @Test
    fun `the street bikes are counted with the stations' bikes, scooters left out`() = runTest {
        // Every electric bike out on the street and none at a station: counted
        // from the stations alone the network reads as mechanical, and the
        // bolt is wrong on every marker (SPEC §4.1).
        val readings = mutableListOf<FleetReading>()
        enqueueDiscovery(streetBikes = true, vehicleTypes = true)
        enqueueStreetBikes()
        enqueueVehicleTypes()
        val repository = repository(recordFleet = { readings += it })
        repository.refreshStreetBikes()

        val shown = repository.observeStreetBikes().first().bikes
        assertEquals("the scooter is not a bike", listOf("e1", "m1"), shown.map { it.id })

        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 0)
        repository.refresh()

        val reading = readings.single()
        assertTrue(reading.hasElectricBikes)
        assertEquals(2, reading.bikesCounted)
        assertEquals(mapOf("348" to 60_000), reading.maxRangeMetresByType)
    }
}

/** An in-memory cache, to exercise the policy without a database. */
private class FakeStationDao : StationDao {
    val stations = MutableStateFlow<List<StationEntity>>(emptyList())
    val availabilities = MutableStateFlow<List<StationAvailabilityEntity>>(emptyList())

    override fun observeStations(): Flow<List<StationEntity>> = stations
    override fun observeAvailabilities(): Flow<List<StationAvailabilityEntity>> = availabilities
    override suspend fun mostRecentFetchTime(): Long? =
        availabilities.value.maxOfOrNull { it.fetchedAtEpochSeconds }

    override suspend fun stationCount(): Int = stations.value.size

    override suspend fun insertStations(stations: List<StationEntity>) {
        val merged = this.stations.value.associateBy { it.id }.toMutableMap()
        stations.forEach { merged[it.id] = it }
        this.stations.value = merged.values.toList()
    }

    override suspend fun deleteStationsMissingFrom(keptIds: List<String>) {
        stations.value = stations.value.filter { it.id in keptIds }
    }

    override suspend fun insertAvailabilities(availabilities: List<StationAvailabilityEntity>) {
        val merged = this.availabilities.value.associateBy { it.stationId }.toMutableMap()
        availabilities.forEach { merged[it.stationId] = it }
        this.availabilities.value = merged.values.toList()
    }

    override suspend fun clearAvailabilities() {
        availabilities.value = emptyList()
    }

    override suspend fun clearStations() {
        stations.value = emptyList()
    }
}

private class FakeRefreshTimestampStore : RefreshTimestampStore {
    var fetchedAt: Instant? = null
    override suspend fun stationInformationFetchedAt(): Instant? = fetchedAt
    override suspend fun setStationInformationFetchedAt(instant: Instant) {
        fetchedAt = instant
    }
}
