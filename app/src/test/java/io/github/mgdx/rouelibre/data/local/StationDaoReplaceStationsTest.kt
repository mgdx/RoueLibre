package io.github.mgdx.rouelibre.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the policy of [StationDao.replaceStations] on the JVM.
 *
 * The cache under test refuses, as SQLite does up to Android 11, any statement
 * binding more than 999 variables: a network the size of Vélib's must be
 * replaced without a single query going past it.
 */
class StationDaoReplaceStationsTest {

    private val dao = VariableLimitedStationDao()

    @Test
    fun `a network as large as Velib never binds more variables than SQLite allows`() = runTest {
        dao.replaceStations(stations(0 until 1_500))
        dao.replaceStations(stations(500 until 2_000))

        assertTrue(dao.boundVariableCounts.all { it <= OLDEST_SQLITE_VARIABLE_LIMIT })
    }

    @Test
    fun `the stations missing from the new list are all deleted, whatever their number`() =
        runTest {
            dao.replaceStations(stations(0 until 3_000))
            dao.replaceStations(stations(2_900 until 3_100))

            assertEquals(ids(2_900 until 3_100), dao.stationIds().toSet())
        }

    @Test
    fun `nothing is deleted when every cached station is received again`() = runTest {
        dao.replaceStations(stations(0 until 1_500))
        dao.boundVariableCounts.clear()

        dao.replaceStations(stations(0 until 1_500))

        assertEquals(1_500, dao.stationCount())
        assertTrue(dao.boundVariableCounts.isEmpty())
    }

    @Test
    fun `an empty list leaves the cache as it was`() = runTest {
        // An empty feed is far more likely a failed producer than a network
        // that closed every station overnight.
        dao.replaceStations(stations(0 until 10))

        dao.replaceStations(emptyList())

        assertEquals(10, dao.stationCount())
    }

    private fun stations(indices: IntRange): List<StationEntity> = indices.map { index ->
        StationEntity(
            id = "station-$index",
            name = "Station $index",
            latitude = 48.85,
            longitude = 2.35,
            capacity = null,
            postalCode = null,
            isVirtual = false,
        )
    }

    private fun ids(indices: IntRange): Set<String> = indices.mapTo(HashSet()) { "station-$it" }
}

/** SQLite's limit on bound variables before version 3.32, shipped with Android 12. */
private const val OLDEST_SQLITE_VARIABLE_LIMIT = 999

/** An in-memory cache that fails like SQLite on too many bound variables. */
private class VariableLimitedStationDao : StationDao {
    private val stations = MutableStateFlow<Map<String, StationEntity>>(emptyMap())

    /** How many variables each statement taking a list of ids has bound. */
    val boundVariableCounts = mutableListOf<Int>()

    override fun observeStations(): Flow<List<StationEntity>> =
        throw UnsupportedOperationException()
    override fun observeAvailabilities(): Flow<List<StationAvailabilityEntity>> =
        throw UnsupportedOperationException()
    override suspend fun mostRecentFetchTime(): Long? = null
    override suspend fun stationCount(): Int = stations.value.size

    override suspend fun insertStations(stations: List<StationEntity>) {
        // Room inserts row by row with one prepared statement: a handful of
        // variables per row, never one per station.
        this.stations.value += stations.associateBy { it.id }
    }

    override suspend fun stationIds(): List<String> = stations.value.keys.toList()

    override suspend fun deleteStationsByIds(ids: List<String>) {
        bind(ids.size)
        stations.value -= ids.toSet()
    }

    override suspend fun insertAvailabilities(availabilities: List<StationAvailabilityEntity>) =
        Unit
    override suspend fun clearAvailabilities() = Unit
    override suspend fun clearStations() {
        stations.value = emptyMap()
    }

    private fun bind(variableCount: Int) {
        boundVariableCounts += variableCount
        check(variableCount <= OLDEST_SQLITE_VARIABLE_LIMIT) { "too many SQL variables" }
    }
}
