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
import kotlinx.coroutines.flow.Flow

/**
 * Données stables d'une station, mises en cache (SPEC §8).
 *
 * Ce cache est ce qui permet à l'application de montrer quelque chose hors
 * ligne. Il ne contient que ce que le producteur publie : rien qui vienne de
 * l'utilisateur, aucun trajet, aucune position.
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
 * Dernier état connu d'une station (SPEC §8).
 *
 * Conservé pour être affiché hors ligne, clairement marqué comme périmé
 * (SPEC §4.1). [fetchedAtEpochSeconds] est la date de récupération par
 * l'application, distincte de [reportedAtEpochSeconds] qui est celle déclarée
 * par le producteur : c'est la première qui dit à l'utilisateur depuis quand
 * il regarde une donnée figée.
 */
@Entity(tableName = "station_availability")
data class StationAvailabilityEntity(
    @PrimaryKey val stationId: String,
    val bikesAvailable: Int,
    val docksAvailable: Int,
    val isInstalled: Boolean,
    val isRenting: Boolean,
    val isReturning: Boolean,
    val reportedAtEpochSeconds: Long?,
    val fetchedAtEpochSeconds: Long,
)

/** Accès en lecture et en écriture au cache des stations. */
@Dao
interface StationDao {

    /** Les stations connues, réémises à chaque modification du cache. */
    @Query("SELECT * FROM station ORDER BY name")
    fun observeStations(): Flow<List<StationEntity>>

    /** Le dernier état connu de chaque station. */
    @Query("SELECT * FROM station_availability")
    fun observeAvailabilities(): Flow<List<StationAvailabilityEntity>>

    /** Date de récupération la plus récente, ou `null` si le cache est vide. */
    @Query("SELECT MAX(fetchedAtEpochSeconds) FROM station_availability")
    suspend fun mostRecentFetchTime(): Long?

    /** Nombre de stations en cache, pour savoir s'il faut les télécharger. */
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

    /**
     * Remplace la liste des stations par celle reçue.
     *
     * En une transaction, pour qu'une interruption ne laisse jamais un cache
     * à moitié écrit — la liste doit être cohérente à tout instant.
     */
    @Transaction
    suspend fun replaceStations(stations: List<StationEntity>) {
        insertStations(stations)
        if (stations.isNotEmpty()) {
            deleteStationsMissingFrom(stations.map { it.id })
        }
    }

    /**
     * Remplace l'état de toutes les stations.
     *
     * L'ancien état est effacé plutôt que fusionné : une station absente du
     * nouveau flux n'a plus d'état connu, et présenter un état vieux d'une
     * heure comme s'il était frais serait trompeur.
     */
    @Transaction
    suspend fun replaceAvailabilities(availabilities: List<StationAvailabilityEntity>) {
        clearAvailabilities()
        insertAvailabilities(availabilities)
    }
}

/** Base locale des stations et de leur dernier état connu. */
@Database(
    entities = [StationEntity::class, StationAvailabilityEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StationDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

    companion object {
        /** Nom du fichier de base, unique pour toute l'application. */
        const val FILE_NAME: String = "stations.db"
    }
}
