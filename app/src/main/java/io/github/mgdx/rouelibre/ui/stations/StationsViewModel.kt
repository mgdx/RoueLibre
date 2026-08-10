package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.filterStations
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The state of the screens that show stations.
 *
 * @property stations the stations the search kept, ready to be displayed.
 * @property query what the user typed into the search field.
 * @property isRefreshing a fetch is under way.
 * @property fetchedAt when the last successful fetch happened, or `null`. The
 *   age that follows from it is recomputed by the view on every tick, otherwise
 *   a state ageing on screen would stay marked as fresh.
 * @property hasLoadedOnce true as soon as the cache has been read, which tells
 *   "still loading" apart from "genuinely empty".
 */
data class StationsUiState(
    val stations: List<StationWithAvailability> = emptyList(),
    val query: String = "",
    val isRefreshing: Boolean = false,
    val fetchedAt: Instant? = null,
    val hasLoadedOnce: Boolean = false,
) {
    /**
     * Why the list is empty, if it is.
     *
     * The two cases call for different words and different gestures: an empty
     * cache invites a refresh, a fruitless search invites clearing it.
     * Conflating them would amount to telling the user the network has no
     * stations because they made a typo.
     */
    val emptiness: Emptiness
        get() = when {
            !hasLoadedOnce || isRefreshing || stations.isNotEmpty() -> Emptiness.None
            query.isNotBlank() -> Emptiness.NoMatch
            else -> Emptiness.NothingLoaded
        }
}

/** What the screen must say when the list shows nothing. */
enum class Emptiness {
    /** There are stations on screen. */
    None,

    /** The cache is empty: no station has ever been fetched. */
    NothingLoaded,

    /** Stations exist, but none answers the search. */
    NoMatch,
}

/**
 * Presents the stations and drives their refreshing.
 *
 * Shared by the map and the list: both screens show the same stations, with the
 * same freshness policy. Only the list uses the search field.
 *
 * The model knows neither view nor resource: it exposes a state and events, and
 * the view chooses how to show them.
 */
class StationsViewModel(private val repository: StationRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(StationsUiState())

    /** The screen's current state. */
    val state: StateFlow<StationsUiState> = mutableState.asStateFlow()

    /**
     * The failures to report, once each.
     *
     * A channel rather than a state field: an error is an event, and showing it
     * again on every rotation of the screen would be a defect.
     */
    private val errorChannel = Channel<DataError>(Channel.BUFFERED)

    /** The stream of failures to present to the user. */
    val errors: Flow<DataError> = errorChannel.receiveAsFlow()

    /**
     * The stations as the repository supplies them, before filtering.
     *
     * Kept apart so that changing the search does not require re-reading the
     * cache, and so that clearing the field brings the whole list back.
     */
    private var allStations: List<StationWithAvailability> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                allStations = snapshot.stations
                mutableState.update { current ->
                    current.copy(
                        stations = filterStations(allStations, current.query),
                        fetchedAt = snapshot.fetchedAt,
                        hasLoadedOnce = true,
                    )
                }
            }
        }
    }

    /**
     * Takes a new search query into account.
     *
     * Filtering happens on every keystroke, without debouncing: it walks a few
     * hundred entries already in memory. It is the address search of SPEC §4.3,
     * over hundreds of thousands of house numbers, that will need one.
     */
    fun onQueryChanged(query: String) {
        mutableState.update { current ->
            if (current.query == query) {
                current
            } else {
                current.copy(query = query, stations = filterStations(allStations, query))
            }
        }
    }

    /**
     * Asks for the availability to be updated.
     *
     * @param force ignores the minimum delay between two calls. Reserved for
     *   the pull-to-refresh gesture: an explicit request must never be answered
     *   with a cache.
     */
    fun refresh(force: Boolean = false) {
        if (mutableState.value.isRefreshing) return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            val outcome = repository.refresh(force = force)
            mutableState.update { it.copy(isRefreshing = false) }
            if (outcome is Outcome.Failure) {
                errorChannel.send(outcome.error)
            }
        }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(private val repository: StationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationsViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StationsViewModel(repository) as T
        }
    }
}
