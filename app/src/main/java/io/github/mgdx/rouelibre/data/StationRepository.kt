package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.gbfs.VehicleTypesFeed
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.DockedBike
import io.github.mgdx.rouelibre.core.station.FleetReading
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.countFleet
import io.github.mgdx.rouelibre.core.station.joinStationsWithAvailability
import io.github.mgdx.rouelibre.core.station.streetBikesShown
import io.github.mgdx.rouelibre.data.local.StationAvailabilityEntity
import io.github.mgdx.rouelibre.data.local.StationDao
import io.github.mgdx.rouelibre.data.local.StationEntity
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The single source of the stations and their availability.
 *
 * It applies the refresh policy of SPEC §4.1, which fits in four rules: static
 * data at most once a day, real-time state at most once a minute, the vehicle
 * feed on the same minute and only on request, and **never anything in the
 * background**. No periodic task is scheduled: every call comes from a screen
 * on display or from a user's gesture.
 *
 * @property remote access to the GBFS feeds.
 * @property dao the local cache.
 * @property refreshTimestamps remembers when the static data was last
 *   refreshed, which must survive a restart of the application.
 * @property discoveryUrlProvider gives the auto-discovery document's URL, or
 *   `null` if no city is chosen. It is a function and not a value because the
 *   setting is user-editable (SPEC §4.1) and changes with the active city.
 * @property recordFleet takes what the bikes just fetched say the network
 *   lends. Reported rather than stored here: this repository counts, and what
 *   is made of the count — remembered, merged with earlier readings, shown — is
 *   the fleet repository's business.
 * @property clock the clock, injected to keep the policy testable.
 */
class StationRepository(
    private val remote: GbfsRemoteSource,
    private val dao: StationDao,
    private val refreshTimestamps: RefreshTimestampStore,
    private val discoveryUrlProvider: suspend () -> String?,
    private val recordFleet: suspend (FleetReading) -> Unit = {},
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * The session's auto-discovery document.
     *
     * Held in memory so it is not asked for again on every state refresh: it
     * changes only exceptionally, and re-reading it every minute would double
     * the traffic for nothing.
     */
    private var cachedDiscovery: GbfsDiscovery? = null
    private var cachedDiscoveryUrl: String? = null

    /**
     * The session's vehicle type table, once read.
     *
     * What each identifier stands for changes when an operator adds a kind to
     * its fleet, which happens a few times a year — so once per session is
     * plenty, and it is what makes "the application checks the network when it
     * opens" true without asking again every minute.
     */
    private var cachedVehicleTypes: VehicleTypesFeed? = null

    /** Serialises refreshes: two screens can ask for one. */
    private val refreshLock = Mutex()

    private var lastStatusRefresh: Instant? = null

    /**
     * The bikes outside the stations, in memory and nowhere else (SPEC §8).
     *
     * Not Room, not DataStore: the standard rotates a bike's identifier after
     * every rental, a stale bike position is a wrong answer where a stale rack
     * count is a rough one, and nothing needs to survive a restart that the
     * next read does not give better.
     */
    private val streetBikes = MutableStateFlow(NO_STREET_BIKES)

    /**
     * The auto-discovery URL [streetBikes] was read under, which is how this
     * repository knows a city: another URL is another network, whose bikes
     * have no business on this one's map.
     */
    private var streetBikesDiscoveryUrl: String? = null

    private var lastStreetBikesRefresh: Instant? = null

    /**
     * The stations and their last known state, re-emitted on every change.
     *
     * Emits the cache's contents immediately, offline included. An empty cache
     * gives an empty list, which the interface presents as an invitation to
     * refresh rather than as an error.
     */
    fun observeStations(): Flow<StationsSnapshot> = combine(
        dao.observeStations(),
        dao.observeAvailabilities(),
    ) { stations, availabilities ->
        StationsSnapshot(
            stations = joinStationsWithAvailability(
                stations.map(StationEntity::toDomain),
                availabilities.map(StationAvailabilityEntity::toDomain),
            ),
            fetchedAt = availabilities.maxOfOrNull { it.fetchedAtEpochSeconds }
                ?.let(Instant::ofEpochSecond),
        )
    }

    /**
     * Updates the data from the network if the policy allows it.
     *
     * @param force ignores the minimum delay between two states. Reserved for
     *   the pull-to-refresh gesture: a user who asks explicitly must not be
     *   answered with a cache.
     * @return success if the data was updated or was already fresh, otherwise
     *   the cause of the failure.
     */
    suspend fun refresh(force: Boolean = false): Outcome<Unit> = refreshLock.withLock {
        val now = clock.instant()

        if (!force && !statusRefreshIsDue(now)) {
            return@withLock Outcome.Success(Unit)
        }

        val discovery = when (val outcome = discovery()) {
            is Outcome.Failure -> return@withLock outcome
            is Outcome.Success -> outcome.value
        }

        // Static data first: without it, a real-time state has no station to
        // describe.
        if (stationInformationRefreshIsDue(now)) {
            when (val outcome = remote.fetchStationInformation(discovery)) {
                is Outcome.Failure -> {
                    // A failure here is only fatal if the cache is empty:
                    // otherwise the known stations are enough to show a fresh
                    // state.
                    if (dao.stationCount() == 0) return@withLock outcome
                }

                is Outcome.Success -> {
                    dao.replaceStations(outcome.value.stations.map(Station::toEntity))
                    refreshTimestamps.setStationInformationFetchedAt(now)
                }
            }
        }

        when (val outcome = remote.fetchStationStatus(discovery)) {
            is Outcome.Failure -> return@withLock outcome
            is Outcome.Success -> {
                dao.replaceAvailabilities(
                    outcome.value.availabilities.map { it.toEntity(fetchedAt = now) },
                )
                lastStatusRefresh = now
                countFleetFrom(discovery, outcome.value.availabilities)
            }
        }

        Outcome.Success(Unit)
    }

    /**
     * The vehicles of the city served — the bikes outside stations and those
     * standing at one — re-emitted on every read.
     *
     * Empty, and answering nothing about the network, until [refreshStreetBikes]
     * has been asked once: the feed is read only while the setting of SPEC §7.6
     * is on, and this flow says nothing that was not read.
     */
    fun observeStreetBikes(): Flow<StreetBikesSnapshot> = streetBikes

    /**
     * Reads the vehicle feed from the network if the policy allows it
     * (SPEC §4.1).
     *
     * On the station feed's own minute, and only on request: the two feeds
     * describe the same racks, and a sheet whose charge line lagged its count
     * by five minutes was answering two different moments — see
     * [STATUS_MINIMUM_INTERVAL]. A network publishing no such feed is
     * remembered as publishing none for the session and is not asked again,
     * exactly as the vehicle types are; that answer is a success, the absence
     * being an ordinary fact about a docked fleet rather than a failure.
     *
     * The scooters a network lists in the same feed are dropped here, through
     * the session's vehicle type table, so that what the snapshot holds are
     * bikes and nothing else.
     *
     * @param force ignores the minimum delay. Reserved for the pull-to-refresh
     *   gesture, as for [refresh]; it never asks a network already known to
     *   publish none.
     */
    suspend fun refreshStreetBikes(force: Boolean = false): Outcome<Unit> = refreshLock.withLock {
        val now = clock.instant()
        val discovery = when (val outcome = discovery()) {
            is Outcome.Failure -> return@withLock outcome
            is Outcome.Success -> outcome.value
        }
        forgetStreetBikesOfAnotherCity()
        if (streetBikes.value.published == false) return@withLock Outcome.Success(Unit)
        if (!force && !streetBikesRefreshIsDue(now)) return@withLock Outcome.Success(Unit)

        when (val outcome = remote.fetchVehicleStatus(discovery)) {
            is Outcome.Failure -> {
                if (outcome.error !is DataError.FeedUnavailable) return@withLock outcome
                streetBikes.value = NO_STREET_BIKES.copy(published = false)
            }

            is Outcome.Success -> {
                streetBikes.value = StreetBikesSnapshot(
                    bikes = streetBikesShown(
                        outcome.value.bikes,
                        vehicleTypes(discovery).kinds,
                    ),
                    dockedBikes = outcome.value.dockedBikes.groupBy { it.stationId },
                    fetchedAt = now,
                    published = true,
                )
                lastStreetBikesRefresh = now
            }
        }
        Outcome.Success(Unit)
    }

    /**
     * Drops the street bikes if they were read under another city's
     * auto-discovery URL.
     *
     * Called once the URL in force is known, so that a city change that went
     * through [forget] costs nothing, and one that did not still leaves no
     * bike of the previous network — nor its memorised absence — on the map.
     */
    private fun forgetStreetBikesOfAnotherCity() {
        if (streetBikesDiscoveryUrl == cachedDiscoveryUrl) return
        streetBikes.value = NO_STREET_BIKES
        lastStreetBikesRefresh = null
        streetBikesDiscoveryUrl = cachedDiscoveryUrl
    }

    /**
     * Counts what the network lends, from the state just fetched.
     *
     * Done here because this is where both halves are in hand: the bikes
     * standing at the stations, and the table saying what they are. It costs no
     * request beyond the vehicle type table, read once for the session. The
     * bikes last read outside the stations are counted with them, when the
     * setting of SPEC §7.6 has had any read: a network whose electric bikes
     * are all out on the street must not read as a mechanical one.
     *
     * A failure to count is never a failure to refresh: the counts are the
     * screen's subject, and what the bikes *are* is a refinement of them.
     */
    private suspend fun countFleetFrom(
        discovery: GbfsDiscovery,
        availabilities: List<StationAvailability>,
    ) {
        val declared = vehicleTypes(discovery)
        recordFleet(
            countFleet(
                availabilities = availabilities,
                declaredVehicleTypes = declared.kinds,
                declaresElectricBikes = declared.declaresElectricBikes,
                streetBikes = streetBikes.value.bikes,
            ).copy(
                // Carried with the count rather than counted: the sheet of a
                // street bike reads its charge from the same table it reads
                // its kind from (SPEC §7.2.1).
                maxRangeMetresByType = declared.maxRangeMetresByType,
                cargoVehicleTypeIds = declared.cargoVehicleTypeIds,
            ),
        )
    }

    /**
     * The vehicle type table, read once per session.
     *
     * An empty table is a perfectly ordinary answer: a network on GBFS 1.0 has
     * no such feed to publish, and its kinds are read from the names it puts
     * inline in `station_status` instead. That answer is remembered like a real
     * one — there is nothing to retry — whereas a network failure is not, so a
     * connection coming back brings the table with it.
     */
    private suspend fun vehicleTypes(discovery: GbfsDiscovery): VehicleTypesFeed {
        cachedVehicleTypes?.let { return it }
        return when (val outcome = remote.fetchVehicleTypes(discovery)) {
            is Outcome.Success -> outcome.value.also { cachedVehicleTypes = it }
            is Outcome.Failure -> NO_VEHICLE_TYPES.also {
                if (outcome.error is DataError.FeedUnavailable) cachedVehicleTypes = it
            }
        }
    }

    /**
     * Forgets everything known about the stations.
     *
     * Called when the city changes: one conurbation's stations have no business
     * on another's map, and offline nothing would come to replace them. The
     * auto-discovery document goes too, since it describes the feeds of the
     * network being left.
     */
    suspend fun forget(): Unit = refreshLock.withLock {
        dao.clearAvailabilities()
        dao.clearStations()
        cachedDiscovery = null
        cachedDiscoveryUrl = null
        cachedVehicleTypes = null
        lastStatusRefresh = null
        streetBikes.value = NO_STREET_BIKES
        streetBikesDiscoveryUrl = null
        lastStreetBikesRefresh = null
        // The date of the last fetch need not be rewritten: an empty cache
        // makes the refresh due anyway.
    }

    /**
     * The auto-discovery document, re-read only if the URL has changed.
     */
    private suspend fun discovery(): Outcome<GbfsDiscovery> {
        // No URL: no city is chosen. There is nothing to retry, and saying so
        // this way avoids showing a network failure that does not exist.
        val url = discoveryUrlProvider() ?: return Outcome.Failure(DataError.NoCityChosen)
        cachedDiscovery?.let { cached ->
            if (cachedDiscoveryUrl == url) return Outcome.Success(cached)
        }
        return when (val outcome = remote.fetchDiscovery(url)) {
            is Outcome.Failure -> outcome
            is Outcome.Success -> {
                cachedDiscovery = outcome.value
                cachedDiscoveryUrl = url
                outcome
            }
        }
    }

    private fun statusRefreshIsDue(now: Instant): Boolean {
        val last = lastStatusRefresh ?: return true
        return Duration.between(last, now) >= STATUS_MINIMUM_INTERVAL
    }

    private fun streetBikesRefreshIsDue(now: Instant): Boolean {
        val last = lastStreetBikesRefresh ?: return true
        return Duration.between(last, now) >= STATUS_MINIMUM_INTERVAL
    }

    private suspend fun stationInformationRefreshIsDue(now: Instant): Boolean {
        if (dao.stationCount() == 0) return true
        val last = refreshTimestamps.stationInformationFetchedAt() ?: return true
        return Duration.between(last, now) >= STATION_INFORMATION_MAXIMUM_AGE
    }

    private companion object {
        /** What a network declaring no vehicle type at all leaves us with. */
        val NO_VEHICLE_TYPES = VehicleTypesFeed(
            kinds = emptyMap(),
            declaresElectricBikes = false,
            maxRangeMetresByType = emptyMap(),
            cargoVehicleTypeIds = emptySet(),
            lastUpdated = null,
            version = null,
        )

        /**
         * The feed is produced every minute; asking more often would bring back
         * no new data and would only load the producer's server (SPEC §4.1).
         *
         * The vehicle feed keeps the same minute, since 11 September 2026,
         * and it is a cost accepted with open eyes: one read weighs Berlin
         * 1,846 KiB (182 gzipped), Marseille 1,167 (120), where the station
         * feed of the same networks weighs 2 to 9 KiB gzipped — twenty times
         * the station feed, every minute, and gzip is asked for on every
         * request. It was five minutes until then, on the grounds that
         * little moves in it; but the station's sheet now reads both feeds
         * on one line, and a charge five minutes older than the count beside
         * it was two moments passed off as one. The setting that reads the
         * feed stays off by default for that very weight (SPEC §7.6).
         */
        val STATUS_MINIMUM_INTERVAL: Duration = Duration.ofSeconds(60)

        /**
         * The static data only changes when a station opens or closes, which
         * happens a few times a year.
         */
        val STATION_INFORMATION_MAXIMUM_AGE: Duration = Duration.ofDays(1)

        /** What the flow holds before any read, and after the city is forgotten. */
        val NO_STREET_BIKES = StreetBikesSnapshot(
            bikes = emptyList(),
            dockedBikes = emptyMap(),
            fetchedAt = null,
            published = null,
        )
    }
}

/**
 * What the application knows of the vehicle feed of the active city, in
 * memory only (SPEC §8).
 *
 * @property bikes the bikes standing outside the stations, scooters already
 *   dropped, and empty until a read has been made.
 * @property dockedBikes the vehicles standing at a station, by the station's
 *   identifier, for the station's sheet to describe (SPEC §7.2). Not sorted
 *   into bikes and scooters here: the sheet reads them through the type table
 *   as it reads the charge, and a station holding none has no entry.
 * @property fetchedAt when they were read, or `null` if they never were. It is
 *   the one age the bike's sheet shows (SPEC §7.2.1): a bike gets no age of
 *   its own.
 * @property published whether the network publishes the feed at all: `null`
 *   until it has been asked, `false` once it answered that it has no such
 *   feed, in which case it is not asked again this session.
 */
data class StreetBikesSnapshot(
    val bikes: List<StreetBike>,
    val dockedBikes: Map<String, List<DockedBike>>,
    val fetchedAt: Instant?,
    val published: Boolean?,
)

/**
 * A snapshot of what the application knows about the stations.
 *
 * @property stations the known stations and their last state.
 * @property fetchedAt when that state was fetched, or `null` if no state has
 *   ever been received.
 */
data class StationsSnapshot(val stations: List<StationWithAvailability>, val fetchedAt: Instant?)

private fun StationEntity.toDomain() = Station(
    id = id,
    name = name,
    position = Coordinates(latitude, longitude),
    capacity = capacity,
    postalCode = postalCode,
)

private fun Station.toEntity() = StationEntity(
    id = id,
    name = name,
    latitude = position.latitude,
    longitude = position.longitude,
    capacity = capacity,
    postalCode = postalCode,
)

private fun StationAvailabilityEntity.toDomain() = StationAvailability(
    stationId = stationId,
    bikesAvailable = bikesAvailable,
    bikesByVehicleType = bikesByVehicleType,
    docksAvailable = docksAvailable,
    isInstalled = isInstalled,
    isRenting = isRenting,
    isReturning = isReturning,
    reportedAt = reportedAtEpochSeconds?.let(Instant::ofEpochSecond),
)

private fun StationAvailability.toEntity(fetchedAt: Instant) = StationAvailabilityEntity(
    stationId = stationId,
    bikesAvailable = bikesAvailable,
    bikesByVehicleType = bikesByVehicleType,
    docksAvailable = docksAvailable,
    isInstalled = isInstalled,
    isRenting = isRenting,
    isReturning = isReturning,
    reportedAtEpochSeconds = reportedAt?.epochSecond,
    fetchedAtEpochSeconds = fetchedAt.epochSecond,
)
