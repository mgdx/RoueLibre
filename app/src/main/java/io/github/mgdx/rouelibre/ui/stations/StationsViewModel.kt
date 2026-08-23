package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.filterStations
import io.github.mgdx.rouelibre.core.station.orderStations
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @property orderingOrigin where the distances are measured from when the list
 *   is ordered by proximity, `null` when it is alphabetical. The rows show it,
 *   so that an order that is not alphabetical says what it rests on.
 */
data class StationsUiState(
    val stations: List<StationWithAvailability> = emptyList(),
    val query: String = "",
    val isRefreshing: Boolean = false,
    val fetchedAt: Instant? = null,
    val hasLoadedOnce: Boolean = false,
    val orderingOrigin: Coordinates? = null,
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
class StationsViewModel(
    private val repository: StationRepository,
    private val positionAlreadyKnown: suspend () -> Coordinates? = { null },
    private val positionForOrdering: suspend () -> Coordinates? = { null },
    private val readLetterFolds: suspend () -> Map<Char, String> = { emptyMap() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(StationsUiState())

    /** The screen's current state. */
    val state: StateFlow<StationsUiState> = mutableState.asStateFlow()

    /**
     * The failures to report, once each, to the screen on display.
     *
     * An event and not a state field: kept as state, the same failure would
     * come back on every rotation of the screen. Nothing is kept for an absent
     * reader either — no replay, and a buffer that only serves a screen already
     * listening: a failure that arrives while the screen is gone describes a
     * state that will have changed by the time it comes back. That is how the
     * very first launch used to end on "no city is selected" over the map of
     * the city that had just been downloaded — the failure was raised by the
     * map built under the welcome sequence, before a city was chosen, and
     * delivered on the way back from the download.
     */
    private val failures = MutableSharedFlow<DataError>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The stream of failures to present to the user. */
    val errors: Flow<DataError> = failures.asSharedFlow()

    /**
     * The stations as the repository supplies them, before filtering.
     *
     * Kept apart so that changing the search does not require re-reading the
     * cache, and so that clearing the field brings the whole list back.
     */
    private var allStations: List<StationWithAvailability> = emptyList()

    /**
     * Where the user is, when that orders the list (SPEC §7.2).
     *
     * Read once, when the screen asks for it, and not on every refresh: a list
     * that reorders itself while being read is worse than one ordered a little
     * late.
     */
    private var orderingPosition: Coordinates? = null

    /**
     * The letters accent removal cannot reach, for the search field.
     *
     * A network names its stops in its own language — "Ludwigsstraße",
     * "Białostocka" — and its rider types on whatever keyboard they have
     * (SPEC §4.3). Read off the main thread, because the rules sit in files and
     * this field filters on every keystroke; until they arrive the fold is the
     * plain one, and the list is filtered again as soon as they do.
     */
    private var letterFolds: Map<Char, String> = emptyMap()

    init {
        viewModelScope.launch {
            letterFolds = readLetterFolds()
            mutableState.update { it.copy(stations = visibleStations(it.query)) }
        }
    }

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                allStations = snapshot.stations
                mutableState.update { current ->
                    current.copy(
                        stations = visibleStations(current.query),
                        fetchedAt = snapshot.fetchedAt,
                        hasLoadedOnce = true,
                    )
                }
            }
        }
    }

    /**
     * Orders the list by proximity, if the position allows it.
     *
     * Called by the screen when it appears, and it orders **twice**, which is
     * one step more than it looks. What the system already holds comes first
     * and costs nothing: arriving from the map, that is the very point the map
     * was drawing, and the list is in order before the eye has settled on it.
     * Then a fix is asked for, because on a phone where nothing has asked for
     * a position in a while the first step answers nothing at all — and that
     * one can take a few seconds, so the list is read as it stands meanwhile
     * and settles when the fix arrives.
     *
     * Without a usable position — refused, switched off, not obtained, or
     * outside the served city — the alphabetical order stays, and nothing at
     * all is asked of the user: no permission prompt, no message.
     *
     * **An order already settled is left alone.** This model outlives the
     * screen, so a rotation calls this again over a position the user waited
     * for at the button, and what the system holds is not always as good as
     * what was fetched — a two-minute-old network fix would take the place of
     * a satellite one and reshuffle the list under the eye.
     */
    fun orderByProximity() {
        if (orderingPosition != null) return
        viewModelScope.launch {
            positionAlreadyKnown()?.let { orderFrom(it) }
            // Kept where the fix comes to nothing: an order already given is
            // better than the alphabet it would fall back to.
            positionForOrdering()?.let { orderFrom(it) }
        }
    }

    /**
     * Orders the list from a position the user has just asked for.
     *
     * The other way in, and the one that answers a button rather than the
     * screen appearing. The two differ in what they may ask of the user:
     * [orderByProximity] takes what it can get in silence, whereas a press
     * may put the permission dialog up and must answer a refusal in words.
     * The screen fetches that fix itself and hands it over here, so the wait,
     * the permission and the refusal stay its business and this model goes on
     * knowing nothing of Android (SPEC §14).
     *
     * @param position where the user stands, or `null` to go back to the
     *   alphabet.
     */
    fun orderFrom(position: Coordinates?) {
        orderingPosition = position
        mutableState.update { current ->
            current.copy(
                stations = visibleStations(current.query),
                orderingOrigin = position,
            )
        }
    }

    /** The stations to show: those the search keeps, in the order that suits. */
    private fun visibleStations(query: String): List<StationWithAvailability> =
        orderStations(filterStations(allStations, query, letterFolds), orderingPosition)

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
                current.copy(query = query, stations = visibleStations(query))
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
                failures.tryEmit(outcome.error)
            }
        }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val repository: StationRepository,
        private val positionAlreadyKnown: suspend () -> Coordinates? = { null },
        private val positionForOrdering: suspend () -> Coordinates? = { null },
        private val readLetterFolds: suspend () -> Map<Char, String> = { emptyMap() },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationsViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return StationsViewModel(
                repository,
                positionAlreadyKnown,
                positionForOrdering,
                readLetterFolds,
            ) as T
        }
    }
}
