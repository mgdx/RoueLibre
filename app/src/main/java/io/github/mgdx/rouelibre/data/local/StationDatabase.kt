package io.github.mgdx.rouelibre.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A station's static data, cached (SPEC §8).
 *
 * This cache is what lets the application show anything offline. It holds only
 * what the producer publishes: nothing coming from the user, no journey, no
 * position.
 */
@Entity(tableName = "station")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val capacity: Int?,
    val postalCode: String?,
)

/**
 * A station's last known state (SPEC §8).
 *
 * Kept to be shown offline, clearly marked as stale (SPEC §4.1).
 * [fetchedAtEpochSeconds] is when the application fetched it, distinct from
 * [reportedAtEpochSeconds] which is the date the producer declared: it is the
 * former that tells the user how long they have been looking at frozen data.
 */
@Entity(tableName = "station_availability")
data class StationAvailabilityEntity(
    @PrimaryKey val stationId: String,
    val bikesAvailable: Int,
    /**
     * The breakdown by vehicle type, as the feed published it.
     *
     * Kept raw rather than already split into mechanical and electric: the
     * table that translates the producer's identifiers lives in the city
     * configuration and can be surveyed again, and a cache holding the feed's
     * own words is then re-read correctly instead of carrying yesterday's
     * reading. Empty for the feeds that publish no breakdown.
     */
    val bikesByVehicleType: Map<String, Int>,
    val docksAvailable: Int,
    val isInstalled: Boolean,
    val isRenting: Boolean,
    val isReturning: Boolean,
    val reportedAtEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
)

/** Read and write access to the station cache. */
@Dao
interface StationDao {

    /** The known stations, re-emitted whenever the cache changes. */
    @Query("SELECT * FROM station ORDER BY name")
    fun observeStations(): Flow<List<StationEntity>>

    /** The last known state of each station. */
    @Query("SELECT * FROM station_availability")
    fun observeAvailabilities(): Flow<List<StationAvailabilityEntity>>

    /** The most recent fetch date, or `null` if the cache is empty. */
    @Query("SELECT MAX(fetchedAtEpochSeconds) FROM station_availability")
    suspend fun mostRecentFetchTime(): Long?

    /** How many stations are cached, to know whether to download them. */
    @Query("SELECT COUNT(*) FROM station")
    suspend fun stationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Query("DELETE FROM station WHERE id NOT IN (:keptIds)")
    suspend fun deleteStationsMissingFrom(keptIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvailabilities(availabilities: List<StationAvailabilityEntity>)

    @Query("DELETE FROM station_availability")
    suspend fun clearAvailabilities()

    @Query("DELETE FROM station")
    suspend fun clearStations()

    /**
     * Replaces the station list with the one received.
     *
     * In a single transaction, so that an interruption never leaves a
     * half-written cache — the list must be consistent at every instant.
     */
    @Transaction
    suspend fun replaceStations(stations: List<StationEntity>) {
        insertStations(stations)
        if (stations.isNotEmpty()) {
            deleteStationsMissingFrom(stations.map { it.id })
        }
    }

    /**
     * Replaces the state of every station.
     *
     * The old state is cleared rather than merged: a station absent from the
     * new feed no longer has a known state, and presenting an hour-old state as
     * though it were fresh would mislead.
     */
    @Transaction
    suspend fun replaceAvailabilities(availabilities: List<StationAvailabilityEntity>) {
        clearAvailabilities()
        insertAvailabilities(availabilities)
    }
}

/**
 * Stores a breakdown by vehicle type as JSON.
 *
 * A map of a producer's own identifiers has no fixed set of columns, and it is
 * read as a whole or not at all: one text field carries it without a second
 * table for a handful of numbers per station.
 */
object VehicleTypeCountsConverter {

    private val json = Json

    @TypeConverter
    fun fromCounts(counts: Map<String, Int>): String =
        if (counts.isEmpty()) "" else json.encodeToString(counts)

    @TypeConverter
    fun toCounts(stored: String): Map<String, Int> {
        if (stored.isEmpty()) return emptyMap()
        // A cache written by another version, or truncated by a device running
        // out of space, must cost the count and nothing more.
        return try {
            json.decodeFromString<Map<String, Int>>(stored)
        } catch (_: SerializationException) {
            emptyMap()
        }
    }
}

/** The local database of stations and their last known state. */
@Database(
    entities = [StationEntity::class, StationAvailabilityEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(VehicleTypeCountsConverter::class)
abstract class StationDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

    companion object {
        /** The database file name, single for the whole application. */
        const val FILE_NAME: String = "stations.db"

        /**
         * Adds the breakdown by vehicle type to the cached states.
         *
         * Migrated rather than rebuilt: the cache is what the application shows
         * offline, and dropping it would leave a user with no network staring
         * at an empty list until they find one. The existing rows keep an empty
         * breakdown, which shows the single figure they were already showing.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE station_availability " +
                        "ADD COLUMN bikesByVehicleType TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }
}
