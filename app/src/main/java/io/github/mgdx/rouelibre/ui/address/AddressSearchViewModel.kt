package io.github.mgdx.rouelibre.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.WordMatching
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The state of the address search screen.
 *
 * @property query what is typed, raw.
 * @property results the addresses found, best first.
 * @property isSearching a search is owed or under way. True from the keystroke
 *   onwards, not from the moment the scan starts: what lies between the two is
 *   time during which the screen must conclude nothing.
 * @property isIndexInstalled false until the address index is on the device:
 *   the screen then explains what to do instead of finding nothing.
 * @property error the read failure to report, if there is one.
 */
data class AddressSearchUiState(
    val query: String = "",
    val results: List<AddressResult> = emptyList(),
    val isSearching: Boolean = false,
    val isIndexInstalled: Boolean = true,
    val error: DataError? = null,
) {
    /** True when the query brings nothing back, once the search is done. */
    val hasNoMatch: Boolean
        get() = isIndexInstalled &&
            error == null &&
            !isSearching &&
            query.isNotBlank() &&
            results.isEmpty()
}

/**
 * Drives the offline address search (SPEC §4.3).
 *
 * Two rules govern this model.
 *
 * **No search on the main thread.** The fuzzy scan covers tens of thousands of
 * entries; it lives in [AddressIndex], on the IO dispatcher.
 *
 * **Every keystroke cancels the previous one.** A debounce lets a pause in
 * typing go by before searching, and `collectLatest` abandons the running
 * computation as soon as another letter arrives. Without that, a fifteen-letter
 * query would launch fifteen full scans, fourteen of which would be thrown
 * away.
 *
 * The index reaches this model as the two operations it uses, rather than as
 * the object holding them: what drives the screen is a state machine over a
 * query and an answer, and it is worth being able to run it on the JVM against
 * an answer that takes its time.
 *
 * @property isIndexInstalled whether the address index is on the device.
 * @property lookUpAddresses runs one search and suspends until it answers.
 */
class AddressSearchViewModel(
    private val isIndexInstalled: () -> Boolean,
    private val lookUpAddresses: suspend (query: String) -> Outcome<List<AddressResult>>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        AddressSearchUiState(isIndexInstalled = isIndexInstalled()),
    )

    /** The screen's current state. */
    val state: StateFlow<AddressSearchUiState> = mutableState.asStateFlow()

    private val typed = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // `debounce` is still marked as preview by kotlinx.coroutines
            // although it has been stable in practice for years; the 150 ms
            // debounce is a requirement of SPEC §4.3, not a comfort choice.
            @OptIn(FlowPreview::class)
            typed.debounce(TYPING_PAUSE_MILLIS).collectLatest(::search)
        }
    }

    /**
     * Takes a new query into account.
     *
     * The search is owed from this instant, not from the one where it manages
     * to start, and that is why [AddressSearchUiState.isSearching] is raised
     * here rather than in [search]. Between the two lie the debounce and, far
     * longer, the wait for the previous scan: `collectLatest` cancels it, but a
     * scan already inside SQLite only gives the coroutine back when it returns.
     * On Strasbourg's hundred and sixty-two thousand streets that took some
     * three seconds, during which the state still held the empty result of an
     * earlier query while showing the new one — and the screen concluded "no
     * address found" about a query it had not yet run.
     *
     * The results of the previous query are kept meanwhile: a list that empties
     * itself on every keystroke flickers, and stale rows on screen are a
     * smaller lie than an announced absence.
     */
    fun onQueryChanged(query: String) {
        if (mutableState.value.query == query) return
        mutableState.update {
            it.copy(query = query, error = null, isSearching = query.isNotBlank())
        }
        typed.value = query
    }

    private suspend fun search(query: String) {
        if (query.isBlank()) {
            mutableState.update {
                it.copy(results = emptyList(), isSearching = false, error = null)
            }
            return
        }
        if (!isIndexInstalled()) {
            mutableState.update { it.copy(isIndexInstalled = false, isSearching = false) }
            return
        }

        mutableState.update { it.copy(isSearching = true, isIndexInstalled = true) }
        // Typed letter by letter: a word begun stands for the word meant, and
        // the list it produces is a proposal the user still chooses from.
        when (val outcome = lookUpAddresses(query)) {
            is Outcome.Success -> mutableState.update {
                it.copy(results = outcome.value, isSearching = false, error = null)
            }

            is Outcome.Failure -> mutableState.update {
                it.copy(results = emptyList(), isSearching = false, error = outcome.error)
            }
        }
    }

    /**
     * Builds the model with its dependencies, without an injection framework.
     *
     * @property index the offline index queried.
     * @property origin the reference point for proximity ranking — the map's
     *   centre at the moment the screen opens. No location permission is
     *   requested for that (SPEC §10).
     */
    class Factory(private val index: AddressIndex, private val origin: Coordinates?) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AddressSearchViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return AddressSearchViewModel(
                isIndexInstalled = index::isInstalled,
                lookUpAddresses = { index.search(it, origin, WordMatching.Prefixes) },
            ) as T
        }
    }

    private companion object {
        /**
         * The pause in typing before searching, in milliseconds.
         *
         * A hundred and fifty, as SPEC §4.3 asks: short enough for the list to
         * follow the typing, long enough that continuous typing triggers only
         * one search.
         */
        const val TYPING_PAUSE_MILLIS = 150L
    }
}
