package io.github.mgdx.rouelibre

import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.CityFleet
import io.github.mgdx.rouelibre.data.FleetRepository
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import io.github.mgdx.rouelibre.data.addresses.AddressNormalizers
import io.github.mgdx.rouelibre.data.cities.CityCatalogueSource
import io.github.mgdx.rouelibre.data.datasets.DatasetDownloader
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.local.StationDatabase
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.data.network.ConnectionCost
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import io.github.mgdx.rouelibre.data.network.HttpsOnlyInterceptor
import io.github.mgdx.rouelibre.data.network.SystemConnectionCost
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
        // One conurbation's fleet says nothing about another's: leaving a mixed
        // city for a mechanical one must not carry the cog over.
        fleetRepository.forget()
    }

    /**
     * Whether the base map is on the device, city and tiles both (SPEC §4.4).
     *
     * Asked before the first screen is built, so that the application never
     * lands on a map it cannot draw — see `landingScreen`. It reads the tiles
     * file and not the city's whole configuration: the question is whether
     * there is something to draw, and parsing the configuration under the
     * opening screen would be a file read nobody is waiting for.
     *
     * Without a chosen city there is no base map, whatever sits in the
     * directories: the map screen says exactly that, and answers it with the
     * city chooser rather than with tiles.
     */
    suspend fun hasBaseMap(): Boolean {
        val identifier = preferences.activeCityId() ?: return false
        // The store serves one city at a time, and nothing else has necessarily
        // put this one into service yet: this read happens before any screen.
        datasetStore.useCity(identifier)
        return datasetStore.fileOf(DatasetKind.Tiles) != null
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
        val position = deviceLocation.lastKnown()?.coordinates ?: return null
        val box = activeCity()?.boundingBox ?: return null
        return position.takeIf { it in box }
    }

    /**
     * Says whether the city [id] may still be proposed, and notes that it was.
     *
     * The application offers the network of the conurbation one happens to be
     * in (SPEC §15.1). Offering it again at every screen, once refused, would
     * turn an offer into insistence — and it did: the dialogue came back at
     * every launch and at every press of "locate me", which left that button
     * unusable to anyone who wanted to keep the city they had chosen.
     *
     * Two memories answer that, because they answer two different questions.
     * The one held here, in memory, says "this city has been offered on this
     * run" and stops a second offer a minute later. The one in the settings
     * says "this city has been declined" and survives a restart, because a
     * refusal that only lasted until the application was closed was no refusal
     * at all — see [rememberCityRefusal].
     *
     * A refusal is dropped as soon as the user is somewhere the declined
     * network does not serve: having left the area that prompted the offer,
     * they may want it if they come back. Nothing about the cities passed
     * through is written down beyond that single identifier (SPEC §2, C3).
     *
     * @param id the network serving where the user stands, or `null` where none
     *   does — which proposes nothing, and forgets any refusal.
     * @return true if this city may be proposed now.
     */
    suspend fun rememberCityProposal(id: String?): Boolean {
        val declined = preferences.declinedCityProposalId()
        if (declined != null && declined != id) {
            preferences.setDeclinedCityProposalId(null)
            proposedCityIds.remove(declined)
        }
        if (id == null || declined == id) return false
        return proposedCityIds.add(id)
    }

    /**
     * Notes that the user declined the city offered.
     *
     * Kept in the settings, so that three launches in a row do not put the same
     * question three times.
     */
    suspend fun rememberCityRefusal(id: String) {
        preferences.setDeclinedCityProposalId(id)
    }

    private val proposedCityIds = mutableSetOf<String>()

    /**
     * Says whether location may still be asked for, and notes that it was.
     *
     * The map asks for the permission when it opens (SPEC §7.1): the point
     * that follows the device is the screen's own subject, and reaching it
     * through a button first is a detour. Asking again at every return to the
     * map would turn a request into insistence, which SPEC §10 forbids — hence
     * once, after which the "locate me" button is what remains.
     *
     * In memory and for the session alone, like the city proposal above.
     *
     * @return true the first time, false afterwards.
     */
    fun rememberLocationRequest(): Boolean {
        if (locationRequested) return false
        locationRequested = true
        return true
    }

    private var locationRequested = false

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
            // Every address the application calls goes out in TLS, whatever
            // scheme it was published with (SPEC §4.1).
            .addInterceptor(HttpsOnlyInterceptor())
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
        ).addMigrations(StationDatabase.MIGRATION_1_2)
            .build()
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
     * What the connection in use bills, which decides whether a dataset may
     * travel on it (SPEC §4.4).
     */
    val connectionCost: ConnectionCost by lazy {
        SystemConnectionCost(
            checkNotNull(context.getSystemService(ConnectivityManager::class.java)) {
                "no connectivity service on this device"
            },
        )
    }

    /**
     * The manifest's address, read from the active city's configuration
     * (SPEC §4.4). `null` if there is no city yet.
     */
    suspend fun dataManifestUrl(): String? = activeCity()?.dataRelease?.manifestUrl

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
                // Read at each fetch and not captured here: the label must be
                // in the language in force when the feed is read.
                unnamedStationLabel = { context.getString(R.string.station_unnamed) },
                ioDispatcher = Dispatchers.IO,
            ),
            dao = database.stationDao(),
            refreshTimestamps = preferences,
            // Read on every call rather than captured: changing the city must
            // take effect without a restart (SPEC §4.1).
            discoveryUrlProvider = { activeCity()?.gbfs?.discoveryUrl },
            recordFleet = { fleetRepository.record(it) },
        )
    }

    /**
     * What the city in service lends, as the bikes at its stations say it
     * (SPEC §4.1, §7).
     *
     * Seeded by the configuration so a first launch has an answer, then
     * refined by every refresh of the stations.
     */
    val fleetRepository: FleetRepository by lazy {
        FleetRepository(
            store = preferences,
            activeCityFleet = {
                activeCity()?.let { CityFleet(id = it.network.id, configured = it.fleet) }
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
