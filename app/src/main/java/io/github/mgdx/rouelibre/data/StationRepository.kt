package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.joinStationsWithAvailability
import io.github.mgdx.rouelibre.data.local.StationAvailabilityEntity
import io.github.mgdx.rouelibre.data.local.StationDao
import io.github.mgdx.rouelibre.data.local.StationEntity
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The single source of the stations and their availability.
 *
 * It applies the refresh policy of SPEC §4.1, which fits in three rules: static
 * data at most once a day, real-time state at most once a minute, and **never
 * anything in the background**. No periodic task is scheduled: every call comes
 * from a screen on display or from a user's gesture.
 *
 * @property remote access to the GBFS feeds.
 * @property dao the local cache.
 * @property refreshTimestamps remembers when the static data was last
 *   refreshed, which must survive a restart of the application.
 * @property discoveryUrlProvider gives the auto-discovery document's URL, or
 *   `null` if no city is chosen. It is a function and not a value because the
 *   setting is user-editable (SPEC §4.1) and changes with the active city.
 * @property clock the clock, injected to keep the policy testable.
 */
class StationRepository(
    private val remote: GbfsRemoteSource,
    private val dao: StationDao,
    private val refreshTimestamps: RefreshTimestampStore,
    private val discoveryUrlProvider: suspend () -> String?,
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

    /** Serialises refreshes: two screens can ask for one. */
    private val refreshLock = Mutex()

    private var lastStatusRefresh: Instant? = null

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
            }
        }

        Outcome.Success(Unit)
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
        lastStatusRefresh = null
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

    private suspend fun stationInformationRefreshIsDue(now: Instant): Boolean {
        if (dao.stationCount() == 0) return true
        val last = refreshTimestamps.stationInformationFetchedAt() ?: return true
        return Duration.between(last, now) >= STATION_INFORMATION_MAXIMUM_AGE
    }

    private companion object {
        /**
         * The feed is produced every minute; asking more often would bring back
         * no new data and would only load the producer's server (SPEC §4.1).
         */
        val STATUS_MINIMUM_INTERVAL: Duration = Duration.ofSeconds(60)

        /**
         * The static data only changes when a station opens or closes, which
         * happens a few times a year.
         */
        val STATION_INFORMATION_MAXIMUM_AGE: Duration = Duration.ofDays(1)
    }
}

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
