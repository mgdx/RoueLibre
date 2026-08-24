package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Below two favourites there is no order to change, so nothing to say about it. */
private const val REORDERABLE_FROM = 2

/**
 * Whether the reordering hint has anything to talk about (SPEC §7.5).
 *
 * A single favourite cannot be moved anywhere, and an empty list contradicts
 * the invitation to add one that stands in its place. Held here rather than in
 * the fragment so that the rule can be read, and tested, without an Android
 * runtime (SPEC §14).
 *
 * @param favouriteCount how many favourites the screen is showing.
 */
internal fun canReorderFavourites(favouriteCount: Int): Boolean = favouriteCount >= REORDERABLE_FROM

/**
 * The favourite stations, with their live availability (SPEC §7.5).
 *
 * The order is the one the user chose, not the network's: that is what the
 * reordering of §7.5 means. A favourite station the feed no longer publishes
 * drops out of the list quietly — the network withdraws one now and then.
 */
class FavouriteStationsViewModel(
    repository: StationRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val mutableFavourites = MutableStateFlow<List<StationWithAvailability>>(emptyList())

    /** The known favourite stations, in display order. */
    val favourites: StateFlow<List<StationWithAvailability>> = mutableFavourites.asStateFlow()

    private val mutableHasLoaded = MutableStateFlow(false)

    /** True once the cache has been read, telling "empty" from "still loading". */
    val hasLoaded: StateFlow<Boolean> = mutableHasLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeStations(),
                preferences.favouriteStationIds,
            ) { snapshot, favouriteIds ->
                val known = snapshot.stations.associateBy { it.station.id }
                favouriteIds.mapNotNull(known::get)
            }.collect { stations ->
                mutableFavourites.value = stations
                mutableHasLoaded.value = true
            }
        }
    }

    /**
     * Saves the order after a manual move (SPEC §7.5).
     *
     * The order is over identifiers and not over the stations displayed: a
     * station absent from the feed at the moment of the drag must not be
     * dropped from the favourites because of it.
     */
    fun reorder(stationIds: List<String>) {
        viewModelScope.launch { preferences.setFavouriteOrder(stationIds) }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val repository: StationRepository,
        private val preferences: AppPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FavouriteStationsViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return FavouriteStationsViewModel(repository, preferences) as T
        }
    }
}
