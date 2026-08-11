package io.github.mgdx.rouelibre

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import io.github.mgdx.rouelibre.data.addresses.AddressNormalizers
import io.github.mgdx.rouelibre.data.cities.CityCatalogueSource
import io.github.mgdx.rouelibre.data.datasets.DatasetDownloader
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.local.StationDatabase
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import io.github.mgdx.rouelibre.data.routing.OfflineRouter
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import java.time.Duration

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/**
 * Assembles the application's dependencies, by hand.
 *
 * No Hilt and no Koin (SPEC §3): the dependency tree fits on one screen, and an
 * explicit container reads without knowing any framework — which counts for a
 * project meant to be audited and taken over.
 *
 * Everything is built lazily: nothing is initialised until a screen needs it.
 */
class AppContainer(private val context: Context) {

    /** The catalogue of cities served and their configurations (SPEC §15). */
    val cityCatalogueSource: CityCatalogueSource by lazy {
        CityCatalogueSource(context, httpClient, userAgent(), Dispatchers.IO)
    }

    /**
     * The active city's configuration, or `null` if none is chosen.
     *
     * The application assumes no default conurbation: until the welcome screen
     * has proposed one and it has been accepted, there is neither a map to
     * frame nor a feed to query.
     *
     * The result is held in memory, keyed by the identifier read from the
     * settings: changing city therefore invalidates the cache by itself,
     * without any screen having to remember to clear it.
     */
    suspend fun activeCity(): CityConfiguration? {
        val identifier = preferences.activeCityId()
        // Put into service here rather than only by watching the setting: a
        // screen that asks for the city and then reads its files in the same
        // breath must not depend on the order two coroutines happen to run in.
        datasetStore.useCity(identifier)
        if (identifier == null) return null
        cachedCity?.let { (cachedId, configuration) ->
            if (cachedId == identifier) return configuration
        }
        val configuration = cityCatalogueSource.configuration(identifier) ?: return null
        cachedCity = identifier to configuration
        return configuration
    }

    /**
     * Changes the city served.
     *
     * The station cache is emptied in the same movement: one conurbation's
     * stations have no business on another's map, and offline nothing would
     * come to replace them. The datasets, for their part, stay where they are —
     * every city has its own directory, and coming back must not make anything
     * download again.
     *
     * @param id the network's identifier, or `null` to serve none.
     */
    suspend fun switchToCity(id: String?) {
        if (preferences.activeCityId() == id) return
        preferences.setActiveCityId(id)
        cachedCity = null
        datasetStore.useCity(id)
        stationRepository.forget()
    }

    /**
     * The user's position, but only if it falls inside the city served.
     *
     * Used to order the station list by proximity. It reads what the system
     * already holds and never asks for a fix: ordering a list is not worth
     * waking the GPS, and a screen that opened faster than a fix arrives would
     * reorder itself under the finger.
     *
     * @return the position, or `null` if it is unknown, too old, not permitted,
     *   or outside the served conurbation — cases where the alphabet serves
     *   better than a distance to somewhere else.
     */
    suspend fun positionInsideActiveCity(): Coordinates? {
        val position = deviceLocation.lastKnown() ?: return null
        val box = activeCity()?.boundingBox ?: return null
        return position.takeIf { it in box }
    }

    /**
     * Says whether the city [id] may still be proposed, and notes that it was.
     *
     * The application offers the network of the conurbation one happens to be
     * in (SPEC §15.1). Offering it again at every screen, once refused, would
     * turn an offer into insistence.
     *
     * In memory and for the session alone: nothing about the cities one passes
     * through is written to disk (SPEC §2, C3).
     *
     * @return true the first time this city is proposed, false afterwards.
     */
    fun rememberCityProposal(id: String): Boolean = proposedCityIds.add(id)

    private val proposedCityIds = mutableSetOf<String>()

    /**
     * The active city as of the last call to [activeCity].
     *
     * `@Volatile` because the read comes from the main thread and the write
     * from the IO dispatcher.
     */
    @Volatile
    private var cachedCity: Pair<String, CityConfiguration>? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            // No disk cache: the freshness policy is the repository's
            // (SPEC §4.1), and an HTTP cache on top would make it impossible to
            // reason about.
            .cache(null)
            .build()
    }

    /** Settings and favourites, shared by the screens that read them. */
    val preferences: AppPreferences by lazy {
        AppPreferences(context.preferencesDataStore)
    }

    private val database: StationDatabase by lazy {
        Room.databaseBuilder(
            context,
            StationDatabase::class.java,
            StationDatabase.FILE_NAME,
        ).build()
    }

    /**
     * The offline datasets installed on the device.
     *
     * Created early and shared: the map, the routing and the address search
     * will all read from it the file they depend on.
     */
    val datasetStore: DatasetStore by lazy {
        DatasetStore(context, Dispatchers.IO)
    }

    /**
     * Route computation on the device.
     *
     * The computation is purely processor-bound: it runs on the dispatcher
     * meant for that, not on the IO one.
     */
    val router: OfflineRouter by lazy {
        OfflineRouter(context, datasetStore, Dispatchers.Default)
    }

    /**
     * The street-name normalisation rules, shared with the script that builds
     * the index (SPEC §4.3), one set per language (§15.1).
     *
     * The files are copied into the APK at build time from
     * `config/address-normalization/`, the single source on both sides: a
     * divergence would make streets impossible to find. Which set applies is
     * the index's business, not this container's — it is the file that was
     * built with them.
     */
    val addressNormalizers: AddressNormalizers by lazy { AddressNormalizers(context) }

    /**
     * Downloading of the published datasets (SPEC §4.4).
     *
     * Never called of its own accord: only the storage screen triggers it, on
     * an explicit action.
     */
    val datasetDownloader: DatasetDownloader by lazy {
        DatasetDownloader(httpClient, userAgent(), Dispatchers.IO)
    }

    /** Where to drop what is being downloaded, before verification. */
    val downloadWorkDirectory: File
        get() = File(context.cacheDir, "downloads")

    /**
     * The manifest's address: the one the user chose, otherwise the active
     * city's (SPEC §4.4). `null` if there is no city yet.
     */
    suspend fun dataManifestUrl(): String? = preferences.dataManifestUrlOverride()
        ?: activeCity()?.dataRelease?.manifestUrl

    /**
     * The device's position, asked for at the moment of use only.
     *
     * Stateless: every call queries the system. Nothing is cached, so nothing
     * survives the session (SPEC §2, C3).
     */
    val deviceLocation: DeviceLocation by lazy { DeviceLocation(context) }

    /**
     * Offline address search.
     *
     * On the IO dispatcher: the first search opens the file and reads its
     * corpus, the following ones walk through it.
     */
    val addressIndex: AddressIndex by lazy {
        AddressIndex(datasetStore, addressNormalizers, Dispatchers.IO)
    }

    /**
     * The device's engine, as the journey algorithm sees it.
     *
     * This adapter exists so the algorithm stays in pure Kotlin: it knows only
     * an interface taking two points and a mode, never BRouter (SPEC §14).
     *
     * The planner itself is not built here: its settings — the fixed handling
     * times of §6 — depend on what the user chose, and therefore change between
     * two computations.
     */
    val journeyRouter: Router by lazy {
        object : Router {
            override suspend fun route(
                from: Coordinates,
                to: Coordinates,
                mode: TravelMode,
            ): RouteResult = router.route(from, to, mode)
        }
    }

    /** The single source of the stations and their availability. */
    val stationRepository: StationRepository by lazy {
        StationRepository(
            remote = GbfsRemoteSource(
                client = httpClient,
                parser = GbfsParser(),
                userAgent = userAgent(),
                ioDispatcher = Dispatchers.IO,
            ),
            dao = database.stationDao(),
            refreshTimestamps = preferences,
            // The user setting wins over the shipped configuration; it is
            // re-read on every call so a change takes effect without a restart
            // (SPEC §4.1).
            discoveryUrlProvider = {
                preferences.gbfsDiscoveryUrlOverride()
                    ?: activeCity()?.gbfs?.discoveryUrl
            },
        )
    }

    /**
     * Identifies the application and its version to the data producers, with no
     * identifier specific to the user or the device (SPEC §4.4).
     */
    private fun userAgent(): String = "RoueLibre/${BuildConfig.VERSION_NAME} (+$REPOSITORY_URL)"

    private companion object {
        const val REPOSITORY_URL = "https://github.com/mgdx/RoueLibre"
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
