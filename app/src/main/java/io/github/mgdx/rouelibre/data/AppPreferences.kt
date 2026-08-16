package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.VehicleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The theme the user wants the application dressed in (SPEC §7.6).
 *
 * @property id the value written to disk, stable from one release to the next.
 */
enum class AppTheme(val id: String) {
    /** The system's own, and that is the default. */
    System("systeme"),

    /** Always light. */
    Light("clair"),

    /** Always dark. */
    Dark("sombre"),
    ;

    companion object {
        /** Reads a stored theme back; an unknown value returns [System]. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: System
    }
}

/** No version seen: the application has never been launched. */
const val NEVER_LAUNCHED: Int = 0

/**
 * The application's settings and persistent state (SPEC §8).
 *
 * DataStore rather than Room, because these are only a few isolated values.
 * Nothing written here describes a journey: no history, no position, no
 * destination (SPEC §2, C3).
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) :
    RefreshTimestampStore,
    MeasuredFleetStore {

    /**
     * What the network was last counted to lend (SPEC §4.1).
     *
     * One slot for the city in service, holding the identifier it was counted
     * for: a reading found under another city's name is a reading about another
     * network, and is read as none at all.
     */
    override suspend fun measuredFleet(cityId: String): FleetDescription? {
        val stored = dataStore.data.first()[MEASURED_FLEET] ?: return null
        val reading = try {
            json.decodeFromString<StoredFleet>(stored)
        } catch (_: SerializationException) {
            // Written by a version that wrote it differently, or truncated by a
            // device out of space. Counting again costs one refresh; refusing
            // to launch would cost the user the application.
            return null
        }
        if (reading.cityId != cityId) return null
        return FleetDescription(
            hasElectricBikes = reading.electricBikes,
            isMixed = reading.mixed,
            vehicleTypes = reading.vehicleTypes.mapValues { (_, kind) ->
                VehicleKind.ofWireName(kind)
            },
        )
    }

    /** Stores a reading, replacing whatever the slot held. */
    override suspend fun setMeasuredFleet(cityId: String, fleet: FleetDescription) {
        val reading = StoredFleet(
            cityId = cityId,
            electricBikes = fleet.hasElectricBikes,
            mixed = fleet.isMixed,
            vehicleTypes = fleet.vehicleTypes.mapValues { (_, kind) -> kind.wireName },
        )
        dataStore.edit { it[MEASURED_FLEET] = json.encodeToString(reading) }
    }

    /** Empties the slot, the city served having changed. */
    override suspend fun clearMeasuredFleet() {
        dataStore.edit { it.remove(MEASURED_FLEET) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * When the stations' static data was last refreshed.
     *
     * Persisted because the "at most once a day" rule (SPEC §4.1) must survive
     * a restart of the application: otherwise every launch would download the
     * complete station list again.
     */
    override suspend fun stationInformationFetchedAt(): Instant? =
        dataStore.data.first()[STATION_INFORMATION_FETCHED_AT]
            ?.let(Instant::ofEpochSecond)

    /** Stores when the static data was last refreshed. */
    override suspend fun setStationInformationFetchedAt(instant: Instant) {
        dataStore.edit { preferences ->
            preferences[STATION_INFORMATION_FETCHED_AT] = instant.epochSecond
        }
    }

    /**
     * The stations marked as favourites, **in the chosen order** (SPEC §7.5).
     *
     * Station identifiers, and nothing else: these are not the user's own
     * places but public points of the network, and constraint C3 forbids
     * recording anything about a journey.
     *
     * An ordered list rather than a set: §7.5 wants the list to be reorderable,
     * and a set has no order to rearrange. The identifiers are joined by a
     * newline, a character no GBFS identifier contains.
     *
     * A flow rather than a read: a station's star must update everywhere it is
     * shown, without the screens having to tell one another.
     */
    val favouriteStationIds: Flow<List<String>> = dataStore.data.map(::readFavourites)

    /**
     * Adds a station to the favourites, or takes it out.
     *
     * A station added goes to the end of the list: that is where one expects to
     * find what one has just done.
     *
     * @return true if the station is now a favourite.
     */
    suspend fun toggleFavourite(stationId: String): Boolean {
        var isFavourite = false
        dataStore.edit { preferences ->
            val current = readFavourites(preferences)
            isFavourite = stationId !in current
            val updated = if (isFavourite) current + stationId else current - stationId
            preferences[FAVOURITE_STATION_IDS_ORDERED] = updated.joinToString(SEPARATOR)
        }
        return isFavourite
    }

    /** Stores a new order for the favourites (SPEC §7.5). */
    suspend fun setFavouriteOrder(stationIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[FAVOURITE_STATION_IDS_ORDERED] = stationIds.joinToString(SEPARATOR)
        }
    }

    /**
     * Reads the favourites back, picking up those of an earlier version.
     *
     * The first versions kept them in a set, without order. Losing them on an
     * update would be a small betrayal of a user who had filed twenty of them.
     */
    private fun readFavourites(preferences: Preferences): List<String> {
        preferences[FAVOURITE_STATION_IDS_ORDERED]?.let { stored ->
            return stored.split(SEPARATOR).filter { it.isNotBlank() }
        }
        return preferences[FAVOURITE_STATION_IDS].orEmpty().sorted()
    }

    /**
     * The chosen theme: light, dark, or the system's (SPEC §7.6).
     *
     * The default follows the system, the only choice that respects a
     * preference the user has already expressed elsewhere.
     */
    val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromId(preferences[THEME])
    }

    /** Stores the chosen theme. */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.id }
    }

    /**
     * The city the application is serving right now.
     *
     * `null` until one has been chosen: the application assumes no default
     * conurbation, and it is the welcome screen that proposes one (SPEC §15).
     * Only the identifier is kept — not a position, not a history of cities
     * visited (SPEC §2, C3).
     */
    suspend fun activeCityId(): String? =
        dataStore.data.first()[ACTIVE_CITY_ID]?.takeIf { it.isNotBlank() }

    /** Follows the active city, so the screens bring themselves up to date. */
    val activeCityIdFlow: Flow<String?> =
        dataStore.data.map { it[ACTIVE_CITY_ID]?.takeIf { id -> id.isNotBlank() } }

    /** Changes the active city. */
    suspend fun setActiveCityId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(ACTIVE_CITY_ID)
            } else {
                preferences[ACTIVE_CITY_ID] = id
            }
        }
    }

    /**
     * The network whose proposal the user turned down (SPEC §15.1).
     *
     * The application offers the network of the conurbation one happens to be
     * in. Offered again at the next launch, and at every press of "locate me",
     * an offer becomes insistence — and the button that answers "where am I"
     * becomes unusable to anyone who wants to keep the city they chose.
     *
     * **A network identifier, and nothing more.** Not a position, not a date,
     * not a list of the cities one has passed through: constraint C3 forbids
     * recording where somebody has been, and the name of a public network one
     * declined says nothing of the sort. It is dropped as soon as the user is
     * somewhere that network does not serve — see `AppContainer`.
     */
    suspend fun declinedCityProposalId(): String? =
        dataStore.data.first()[DECLINED_CITY_PROPOSAL_ID]?.takeIf { it.isNotBlank() }

    /** Records a declined proposal, or forgets the one recorded. */
    suspend fun setDeclinedCityProposalId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(DECLINED_CITY_PROPOSAL_ID)
            } else {
                preferences[DECLINED_CITY_PROPOSAL_ID] = id
            }
        }
    }

    /**
     * The last version code the user has seen (SPEC §7.9, §7.10).
     *
     * Zero means "never launched": the welcome screen applies then, never the
     * what's-new one. A value below the installed version means "updated
     * since": the notes of the intermediate versions are shown, once.
     */
    suspend fun lastSeenVersionCode(): Int =
        dataStore.data.first()[LAST_SEEN_VERSION_CODE] ?: NEVER_LAUNCHED

    /** Remembers the version the user has just seen. */
    suspend fun setLastSeenVersionCode(versionCode: Int) {
        dataStore.edit { it[LAST_SEEN_VERSION_CODE] = versionCode }
    }

    private companion object {
        val STATION_INFORMATION_FETCHED_AT =
            longPreferencesKey("station_information_fetched_at")

        /** The favourites from before the ordered version, picked up on first read. */
        val FAVOURITE_STATION_IDS = stringSetPreferencesKey("favourite_station_ids")
        val FAVOURITE_STATION_IDS_ORDERED = stringPreferencesKey("favourite_station_ids_ordered")

        /** No GBFS identifier contains a newline. */
        const val SEPARATOR = "\n"
        val THEME = stringPreferencesKey("theme")
        val ACTIVE_CITY_ID = stringPreferencesKey("active_city_id")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        val DECLINED_CITY_PROPOSAL_ID = stringPreferencesKey("declined_city_proposal_id")
        val MEASURED_FLEET = stringPreferencesKey("measured_fleet")
    }
}

/**
 * A counted fleet as it is written to disk.
 *
 * Its own shape rather than the domain one, so that renaming a property of
 * [FleetDescription] cannot silently make every stored reading unreadable. The
 * kinds are written by their `wireName`, the same words the city configuration
 * uses.
 */
@Serializable
private data class StoredFleet(
    val cityId: String,
    val electricBikes: Boolean,
    val mixed: Boolean,
    val vehicleTypes: Map<String, String>,
)
