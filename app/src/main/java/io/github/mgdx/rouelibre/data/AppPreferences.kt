package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/**
 * The journey's two fixed handling times, in seconds (SPEC §6).
 *
 * @property pickupSeconds time to take the bike at the departure station.
 * @property dropoffSeconds time to return it at the arrival station.
 */
data class HandlingTimes(val pickupSeconds: Int, val dropoffSeconds: Int)

/** No version seen: the application has never been launched. */
const val NEVER_LAUNCHED: Int = 0

/**
 * The application's settings and persistent state (SPEC §8).
 *
 * DataStore rather than Room, because these are only a few isolated values.
 * Nothing written here describes a journey: no history, no position, no
 * destination (SPEC §2, C3).
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) : RefreshTimestampStore {

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
     * The GBFS auto-discovery URL chosen by the user.
     *
     * `null` until it has been changed: the city configuration's own applies
     * then. This setting is what makes the application usable with any GBFS
     * network at all (SPEC §4.1).
     */
    suspend fun gbfsDiscoveryUrlOverride(): String? =
        dataStore.data.first()[GBFS_DISCOVERY_URL]?.takeIf { it.isNotBlank() }

    /** Replaces the feed URL, or restores the configuration's own if `null`. */
    suspend fun setGbfsDiscoveryUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(GBFS_DISCOVERY_URL)
            } else {
                preferences[GBFS_DISCOVERY_URL] = url
            }
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
     * Fixed times for taking and returning the bike (SPEC §6).
     *
     * Configurable because they depend on the person and on the station: two
     * minutes for someone who knows the motion, more with a stubborn lock or a
     * temperamental dock. They weigh on the choice of stations as much as on
     * the time announced.
     *
     * The two sides do not default to the same value: returning a bike is one
     * gesture, taking one is several.
     */
    val handlingTimes: Flow<HandlingTimes> = dataStore.data.map { preferences ->
        HandlingTimes(
            pickupSeconds = preferences[PICKUP_SECONDS] ?: DEFAULT_PICKUP_SECONDS,
            dropoffSeconds = preferences[DROPOFF_SECONDS] ?: DEFAULT_DROPOFF_SECONDS,
        )
    }

    /** Stores the fixed times, clamped to plausible values. */
    suspend fun setHandlingTimes(times: HandlingTimes) {
        dataStore.edit { preferences ->
            preferences[PICKUP_SECONDS] = times.pickupSeconds.coerceIn(0, MAX_HANDLING_SECONDS)
            preferences[DROPOFF_SECONDS] = times.dropoffSeconds.coerceIn(0, MAX_HANDLING_SECONDS)
        }
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

    /**
     * Changes the active city.
     *
     * The settings that designated the previous one — the feed and manifest
     * URLs — are cleared in the same movement: kept, they would show one city's
     * stations on another's map, and nothing in the interface would explain
     * why.
     */
    suspend fun setActiveCityId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(ACTIVE_CITY_ID)
            } else {
                preferences[ACTIVE_CITY_ID] = id
            }
            preferences.remove(GBFS_DISCOVERY_URL)
            preferences.remove(DATA_MANIFEST_URL)
        }
    }

    /**
     * The dataset manifest URL chosen by the user.
     *
     * `null` until it has been changed. This setting exists so that the default
     * host is never a single point of failure (SPEC §4.4).
     */
    suspend fun dataManifestUrlOverride(): String? =
        dataStore.data.first()[DATA_MANIFEST_URL]?.takeIf { it.isNotBlank() }

    /** Replaces the manifest URL, or restores the configuration's own. */
    suspend fun setDataManifestUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(DATA_MANIFEST_URL)
            } else {
                preferences[DATA_MANIFEST_URL] = url
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
        val GBFS_DISCOVERY_URL = stringPreferencesKey("gbfs_discovery_url")

        /** The favourites from before the ordered version, picked up on first read. */
        val FAVOURITE_STATION_IDS = stringSetPreferencesKey("favourite_station_ids")
        val FAVOURITE_STATION_IDS_ORDERED = stringPreferencesKey("favourite_station_ids_ordered")

        /** No GBFS identifier contains a newline. */
        const val SEPARATOR = "\n"
        val THEME = stringPreferencesKey("theme")
        val PICKUP_SECONDS = intPreferencesKey("pickup_seconds")
        val DROPOFF_SECONDS = intPreferencesKey("dropoff_seconds")
        val DATA_MANIFEST_URL = stringPreferencesKey("data_manifest_url")
        val ACTIVE_CITY_ID = stringPreferencesKey("active_city_id")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")

        /**
         * Two minutes to take a bike (SPEC §6): finding one that works,
         * unlocking it, adjusting the saddle.
         */
        const val DEFAULT_PICKUP_SECONDS = 120

        /**
         * One minute to return it (SPEC §6): rolling up to a free dock and
         * pushing the bike in. Nothing to choose, nothing to adjust.
         */
        const val DEFAULT_DROPOFF_SECONDS = 60

        /** A quarter of an hour to take a bike is no longer a fixed time. */
        const val MAX_HANDLING_SECONDS = 900
    }
}
