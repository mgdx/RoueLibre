package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.splitByKind
import io.github.mgdx.rouelibre.data.StationsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The state of a station's detail sheet.
 *
 * @property entry the station and its last known state, or `null` until the
 *   cache has been read.
 * @property address the station's address, derived from the offline index, or
 *   `null` if the index is absent or knows nothing near enough.
 * @property distanceInMetres the straight-line distance from the user's
 *   position, or `null` if they have not shared it or stand outside the
 *   conurbation being consulted.
 * @property isFavourite the station is among the favourites.
 * @property fetchedAt when the last successful fetch happened.
 * @property bikeSplit how the bikes divide between mechanical and electric, or
 *   `null` where the city does not lend both or where the feed's breakdown
 *   cannot be trusted — the total then stands alone.
 */
data class StationDetailUiState(
    val entry: StationWithAvailability? = null,
    val address: AddressResult? = null,
    val distanceInMetres: Double? = null,
    val isFavourite: Boolean = false,
    val fetchedAt: Instant? = null,
    val bikeSplit: BikeSplit? = null,
)

/**
 * Feeds a station's detail sheet (SPEC §7.2).
 *
 * The sheet stays alive while it is open: the availability it shows follows the
 * repository's stream, it is not frozen at opening time. That is what avoids
 * offering a station that emptied while one was looking at it.
 *
 * Its dependencies arrive as streams and as functions rather than as the
 * objects behind them: the rules held here — what the split says, when a
 * distance is owed — are then settled on the JVM, where they can be tested
 * (SPEC §14).
 *
 * @property stationId the station described.
 */
class StationDetailViewModel(
    private val stations: Flow<StationsSnapshot>,
    private val favouriteStationIds: Flow<List<String>>,
    private val setFavourite: suspend (String) -> Unit,
    private val nearestAddress: suspend (Coordinates) -> AddressResult?,
    private val knownPositionInCity: suspend () -> Coordinates?,
    private val fleet: Flow<FleetDescription?>,
    private val stationId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StationDetailUiState())

    /** The sheet's current state. */
    val state: StateFlow<StationDetailUiState> = mutableState.asStateFlow()

    private var addressResolved = false
    private var distanceResolved = false

    init {
        viewModelScope.launch {
            // Followed rather than read once: the first refresh may be what
            // establishes that the network lends both kinds, and the split is
            // then owed to a sheet already open (SPEC §4.1).
            combine(stations, fleet, ::Pair).collect { (snapshot, lent) ->
                val entry = snapshot.stations.firstOrNull { it.station.id == stationId }
                mutableState.update {
                    it.copy(
                        entry = entry,
                        fetchedAt = snapshot.fetchedAt,
                        bikeSplit = splitOf(entry, lent),
                    )
                }
                if (entry != null) {
                    resolveAddressOnce(entry)
                    showDistanceOnce(entry)
                }
            }
        }
        viewModelScope.launch {
            favouriteStationIds.collect { favourites ->
                mutableState.update { it.copy(isFavourite = stationId in favourites) }
            }
        }
    }

    /**
     * Splits the station's bikes, where the split says something.
     *
     * Two conditions, and both are about not misleading. The city must lend
     * both kinds in numbers that make an offer — elsewhere "5 mechanical ·
     * 0 electric" suggests a shortage that does not exist. And the feed's own
     * breakdown must add up to the count displayed, which [splitByKind]
     * checks: a wrong split sends someone to a station for a bike that is not
     * there.
     */
    private fun splitOf(entry: StationWithAvailability?, fleet: FleetDescription?): BikeSplit? {
        if (fleet == null || !fleet.isMixed) return null
        return entry?.availability?.splitByKind(fleet.vehicleTypes)
    }

    /**
     * Looks up the station's address, once only.
     *
     * The repository's stream re-emits on every availability refresh, every
     * minute; a station's position, however, does not move, and the lookup
     * walks through thousands of house numbers.
     */
    private suspend fun resolveAddressOnce(entry: StationWithAvailability) {
        if (addressResolved) return
        addressResolved = true
        val address = nearestAddress(entry.station.position)
        mutableState.update { it.copy(address = address) }
    }

    /**
     * Computes the distance from the position, if it is already known.
     *
     * **No permission is requested here**: opening a station's detail is not
     * the moment to demand location, and a missing distance deprives the user
     * of nothing (SPEC §10). Only the last known position is read, which turns
     * on no sensor.
     *
     * And it is read only where it means something: [knownPositionInCity]
     * hands over a position the conurbation being consulted covers, or nothing
     * at all. A phone in Lille still holding this morning's fix, opened on a
     * Dubai station, was answered "5,236.1 km" — a true figure measuring a
     * journey nobody is making, under the name of a station the list behind it
     * rightly refused any distance to. Both screens now apply the one rule.
     */
    private suspend fun showDistanceOnce(entry: StationWithAvailability) {
        if (distanceResolved) return
        distanceResolved = true
        val here = knownPositionInCity() ?: return
        val distance = here.distanceInMetresTo(entry.station.position)
        mutableState.update { it.copy(distanceInMetres = distance) }
    }

    /** Marks the station as a favourite, or takes it out (SPEC §7.2). */
    fun toggleFavourite() {
        viewModelScope.launch { setFavourite(stationId) }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val stations: Flow<StationsSnapshot>,
        private val favouriteStationIds: Flow<List<String>>,
        private val setFavourite: suspend (String) -> Unit,
        private val nearestAddress: suspend (Coordinates) -> AddressResult?,
        private val knownPositionInCity: suspend () -> Coordinates?,
        private val fleet: Flow<FleetDescription?>,
        private val stationId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationDetailViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return StationDetailViewModel(
                stations,
                favouriteStationIds,
                setFavourite,
                nearestAddress,
                knownPositionInCity,
                fleet,
                stationId,
            ) as T
        }
    }
}
