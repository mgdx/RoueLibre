package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *   position, or `null` if they have not shared it.
 * @property isFavourite the station is among the favourites.
 * @property fetchedAt when the last successful fetch happened.
 */
data class StationDetailUiState(
    val entry: StationWithAvailability? = null,
    val address: AddressResult? = null,
    val distanceInMetres: Double? = null,
    val isFavourite: Boolean = false,
    val fetchedAt: Instant? = null,
)

/**
 * Feeds a station's detail sheet (SPEC §7.2).
 *
 * The sheet stays alive while it is open: the availability it shows follows the
 * repository's stream, it is not frozen at opening time. That is what avoids
 * offering a station that emptied while one was looking at it.
 *
 * @property stationId the station described.
 */
class StationDetailViewModel(
    private val repository: StationRepository,
    private val preferences: AppPreferences,
    private val addressIndex: AddressIndex,
    private val deviceLocation: DeviceLocation,
    private val stationId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StationDetailUiState())

    /** The sheet's current state. */
    val state: StateFlow<StationDetailUiState> = mutableState.asStateFlow()

    private var addressResolved = false
    private var distanceResolved = false

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                val entry = snapshot.stations.firstOrNull { it.station.id == stationId }
                mutableState.update { it.copy(entry = entry, fetchedAt = snapshot.fetchedAt) }
                if (entry != null) {
                    resolveAddressOnce(entry)
                    showDistanceOnce(entry)
                }
            }
        }
        viewModelScope.launch {
            preferences.favouriteStationIds.collect { favourites ->
                mutableState.update { it.copy(isFavourite = stationId in favourites) }
            }
        }
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
        val address = addressIndex.nearestAddress(entry.station.position)
        mutableState.update { it.copy(address = address) }
    }

    /**
     * Computes the distance from the position, if it is already known.
     *
     * **No permission is requested here**: opening a station's detail is not
     * the moment to demand location, and a missing distance deprives the user
     * of nothing (SPEC §10). Only the last known position is read, which turns
     * on no sensor.
     */
    private fun showDistanceOnce(entry: StationWithAvailability) {
        if (distanceResolved) return
        distanceResolved = true
        val here = deviceLocation.lastKnown() ?: return
        val distance = here.distanceInMetresTo(entry.station.position)
        mutableState.update { it.copy(distanceInMetres = distance) }
    }

    /** Marks the station as a favourite, or takes it out (SPEC §7.2). */
    fun toggleFavourite() {
        viewModelScope.launch { preferences.toggleFavourite(stationId) }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val repository: StationRepository,
        private val preferences: AppPreferences,
        private val addressIndex: AddressIndex,
        private val deviceLocation: DeviceLocation,
        private val stationId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationDetailViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StationDetailViewModel(
                repository,
                preferences,
                addressIndex,
                deviceLocation,
                stationId,
            ) as T
        }
    }
}
