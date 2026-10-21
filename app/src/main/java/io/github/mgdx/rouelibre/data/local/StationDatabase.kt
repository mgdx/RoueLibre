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
    /**
     * The station is virtual — a parking zone, not a rack (GBFS
     * `is_virtual_station`).
     *
     * Kept with the static data because that is the only feed saying it, and
     * it is read once a day while the state comes every minute: without it in
     * the cache, a restart would close every station of a network such as
     * Pony's in Limoges until the next daily read (`Station.isVirtual`).
     *
     * `null` on a row carried over from before the flag was kept: nobody has
     * read it yet, and that is what makes the static data read again at the
     * next refresh rather than up to a day later ([StationDao.unreadVirtualFlagCount]).
     */
    val isVirtual: Boolean?,
    /**
     * The operator the station belongs to (`Station.providerId`), kept so that
     * a cache written before the city named its operators — or under another
     * list of them — is noticed and read again at the next refresh rather
     * than shown for up to a day ([StationDao.stationsOutsideProviderCount]).
     */
    val providerId: String? = null,
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
    /**
     * The free docks, and `null` where the feed published no count — which
     * GBFS reserves for a station with unlimited docking (SPEC §4.1). Stored
     * as the feed said it, the nought nextbike derives for its zones included:
     * whether a count limits anything is decided when the state meets its
     * station, never written here.
     */
    val docksAvailable: Int?,
    val isInstalled: Boolean,
    val isRenting: Boolean,
    val isReturning: Boolean,
    val reportedAtEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
)

/** Kept under the oldest SQLite limit of 999 bound variables, with margin. */
private const val DELETE_CHUNK_SIZE = 500

/** Read and write access to the station cache. */
@Dao
interface StationDao {

    /**
     * The known stations, re-emitted whenever the cache changes.
     *
     * In no particular order, and deliberately so: this used to end on
     * `ORDER BY name`, which sorts the bytes. "ÉPINETTES" then came after
     * "TINQUEUX" because U+00C9 is past "Z", and `COLLATE NOCASE` knows only
     * ASCII, so it would have changed nothing. The alphabet is the reader's
     * and not the database's — `orderStations` applies it with a collator.
     */
    @Query("SELECT * FROM station")
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

    /**
     * How many cached stations predate the virtual flag, whose static data
     * must therefore be read again without waiting for the day to run out.
     */
    @Query("SELECT COUNT(*) FROM station WHERE isVirtual IS NULL")
    suspend fun unreadVirtualFlagCount(): Int

    /**
     * How many cached stations belong to none of [providerIds], which a
     * city keeping some operators alone must read again without waiting for
     * the day to run out. A row without an operator counts: it was written
     * before the operator was kept, or by a feed that names none.
     */
    @Query(
        "SELECT COUNT(*) FROM station WHERE providerId IS NULL OR providerId NOT IN (:providerIds)",
    )
    suspend fun stationsOutsideProviderCount(providerIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Query("SELECT id FROM station")
    suspend fun stationIds(): List<String>

    @Query("DELETE FROM station WHERE id IN (:ids)")
    suspend fun deleteStationsByIds(ids: List<String>)

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
     *
     * Stale stations are found by a difference computed here, then deleted in
     * chunks: a `NOT IN` over every received id binds one variable per station,
     * and a large network exceeds SQLite's limit on bound variables (999 up to
     * Android 11). Chunking is only sound for `IN`, which is additive.
     */
    @Transaction
    suspend fun replaceStations(stations: List<StationEntity>) {
        insertStations(stations)
        if (stations.isNotEmpty()) {
            val receivedIds = stations.mapTo(HashSet()) { it.id }
            val staleIds = stationIds().filter { it !in receivedIds }
            staleIds.chunked(DELETE_CHUNK_SIZE).forEach { deleteStationsByIds(it) }
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
    version = 5,
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

        /**
         * Adds the virtual flag to the cached stations.
         *
         * Migrated rather than rebuilt, for the same reason as [MIGRATION_1_2].
         * The existing rows are left without a value rather than marked
         * physical: marked physical, a network such as Pony's in Limoges would
         * stay closed after the update until the next daily read of the static
         * data, up to a day. Left unread, they make that read happen at the
         * very next refresh, and meanwhile they are read as physical — how they
         * were read before.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE station ADD COLUMN isVirtual INTEGER",
                )
            }
        }

        /**
         * Lets the cached free docks be absent.
         *
         * A station publishing no count was stored as nought and read as full,
         * when GBFS leaves the count out precisely for a station that cannot
         * be full (SPEC §4.1). SQLite cannot drop a `NOT NULL` from a column, so
         * the table is rebuilt and its rows copied across — migrated rather
         * than emptied, for the same reason as [MIGRATION_1_2]. The rows carried
         * over keep the nought they were stored with: what the feed really
         * said is not known any more, and the next refresh, a minute away
         * online, writes it again.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE `station_availability_new` (" +
                        "`stationId` TEXT NOT NULL, " +
                        "`bikesAvailable` INTEGER NOT NULL, " +
                        "`bikesByVehicleType` TEXT NOT NULL, " +
                        "`docksAvailable` INTEGER, " +
                        "`isInstalled` INTEGER NOT NULL, " +
                        "`isRenting` INTEGER NOT NULL, " +
                        "`isReturning` INTEGER NOT NULL, " +
                        "`reportedAtEpochSeconds` INTEGER, " +
                        "`fetchedAtEpochSeconds` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`stationId`))",
                )
                connection.execSQL(
                    "INSERT INTO `station_availability_new` SELECT " +
                        "`stationId`, `bikesAvailable`, `bikesByVehicleType`, " +
                        "`docksAvailable`, `isInstalled`, `isRenting`, `isReturning`, " +
                        "`reportedAtEpochSeconds`, `fetchedAtEpochSeconds` " +
                        "FROM `station_availability`",
                )
                connection.execSQL("DROP TABLE `station_availability`")
                connection.execSQL(
                    "ALTER TABLE `station_availability_new` RENAME TO `station_availability`",
                )
            }
        }

        /**
         * Adds the operator to the cached stations.
         *
         * Migrated rather than rebuilt, for the same reason as [MIGRATION_1_2].
         * The rows carried over have none, which only matters to a city that
         * keeps some operators alone: its stations are then read again at the
         * next refresh.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE station ADD COLUMN providerId TEXT")
            }
        }
    }
}
