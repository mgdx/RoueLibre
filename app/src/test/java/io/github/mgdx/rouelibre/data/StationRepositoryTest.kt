package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.data.local.StationAvailabilityEntity
import io.github.mgdx.rouelibre.data.local.StationDao
import io.github.mgdx.rouelibre.data.local.StationEntity
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tests de la politique de rafraîchissement (SPEC §4.1).
 *
 * Un vrai serveur HTTP local plutôt qu'une source simulée : cela couvre aussi
 * le client OkHttp et le décodage des réponses, c'est-à-dire tout le chemin
 * qu'emprunte réellement une actualisation.
 */
class StationRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeStationDao
    private lateinit var timestamps: FakeRefreshTimestampStore
    private var now: Instant = Instant.parse("2026-08-09T12:00:00Z")

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

    private fun repository(): StationRepository = StationRepository(
        remote = GbfsRemoteSource(
            client = OkHttpClient(),
            parser = GbfsParser(),
            userAgent = "RoueLibre-test/1.0",
            ioDispatcher = Dispatchers.IO,
        ),
        dao = dao,
        refreshTimestamps = timestamps,
        discoveryUrlProvider = { server.url("/gbfs.json").toString() },
        clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        },
    )

    private fun enqueueDiscovery() {
        val body = """
            {"last_updated":1786264920,"ttl":0,"version":"2.3","data":{"en":{"feeds":[
              {"name":"station_information","url":"${server.url("/information.json")}"},
              {"name":"station_status","url":"${server.url("/status.json")}"}
            ]}}}
        """.trimIndent()
        server.enqueue(MockResponse(body = body))
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
    fun `un premier rafraichissement recupere decouverte, stations et etat`() = runTest {
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
    fun `l'entete User-Agent nomme l'application sans identifiant d'appareil`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 1)

        repository().refresh()

        val userAgent = server.takeRequest().headers["User-Agent"]
        assertEquals("RoueLibre-test/1.0", userAgent)
    }

    @Test
    fun `un second rafraichissement dans la minute ne part pas sur le reseau`() = runTest {
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
    fun `tirer pour rafraichir passe outre le delai minimal`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        enqueueStatus(bikesAtFirstStation = 7)
        val repository = repository()
        repository.refresh()

        now += Duration.ofSeconds(5)
        enqueueStatus(bikesAtFirstStation = 2)
        repository.refresh(force = true)

        // La découverte et les données stables ne sont pas redemandées, seul
        // l'état l'est : une seule requête de plus.
        assertEquals(4, server.requestCount)
        assertEquals(2, dao.availabilities.value.single().bikesAvailable)
    }

    @Test
    fun `les donnees stables ne sont pas redemandees avant un jour`() = runTest {
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
    fun `les donnees stables sont redemandees passe un jour`() = runTest {
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
    fun `un echec sur les donnees stables n'empeche pas d'actualiser l'etat`() = runTest {
        // Le cache contient déjà les stations : leur indisponibilité passagère
        // ne doit pas priver l'utilisateur d'une disponibilité fraîche.
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
    fun `un echec sur les donnees stables est fatal si le cache est vide`() = runTest {
        enqueueDiscovery()
        server.enqueue(MockResponse(code = 503))

        val outcome = repository().refresh()

        assertEquals(Outcome.Failure(DataError.ServerRefused(503)), outcome)
    }

    @Test
    fun `un serveur en erreur sur l'etat remonte le code recu`() = runTest {
        enqueueDiscovery()
        enqueueInformation()
        server.enqueue(MockResponse(code = 500))

        val outcome = repository().refresh()

        assertEquals(Outcome.Failure(DataError.ServerRefused(500)), outcome)
    }

    @Test
    fun `une reponse illisible est signalee comme telle`() = runTest {
        server.enqueue(MockResponse(body = "<html>maintenance</html>"))

        val outcome = repository().refresh()

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }
}

/** Cache en mémoire, pour éprouver la politique sans base de données. */
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
}

private class FakeRefreshTimestampStore : RefreshTimestampStore {
    var fetchedAt: Instant? = null
    override suspend fun stationInformationFetchedAt(): Instant? = fetchedAt
    override suspend fun setStationInformationFetchedAt(instant: Instant) {
        fetchedAt = instant
    }
}
