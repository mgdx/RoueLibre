package io.github.mgdx.rouelibre.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [StationDao.replaceStations] against the real SQLite of the device.
 *
 * The fake DAO of the JVM tests binds no variable at all, so it cannot see the
 * limit SQLite puts on them: 999 up to Android 11, 32,766 from Android 12. A
 * statement binding one variable per received station crashed on Vélib's
 * ~1,500 stations on the older systems; the largest case here exceeds even the
 * newer limit, so that the regression shows on whatever device runs the test.
 */
@RunWith(AndroidJUnit4::class)
class StationDaoOnDeviceTest {

    private lateinit var database: StationDatabase
    private lateinit var dao: StationDao

    @Before
    fun openDatabase() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(target, StationDatabase::class.java).build()
        dao = database.stationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aNetworkAsLargeAsVelibIsReplacedWithoutCrashing() = runBlocking {
        dao.replaceStations(stations(0 until 1_500))
        dao.replaceStations(stations(500 until 2_000))

        assertEquals(1_500, dao.stationCount())
    }

    @Test
    fun aNetworkPastEveryVariableLimitIsReplacedWithoutCrashing() = runBlocking {
        dao.replaceStations(stations(0 until 40_000))
        dao.replaceStations(stations(1_000 until 41_000))

        assertEquals(40_000, dao.stationCount())
    }

    @Test
    fun moreStaleStationsThanOneChunkAreAllDeleted() = runBlocking {
        dao.replaceStations(stations(0 until 3_000))
        dao.replaceStations(stations(2_900 until 3_000))

        assertEquals(100, dao.stationCount())
    }

    private fun stations(indices: IntRange): List<StationEntity> = indices.map { index ->
        StationEntity(
            id = "station-$index",
            name = "Station $index",
            latitude = 48.85,
            longitude = 2.35,
            capacity = null,
            postalCode = null,
        )
    }
}
