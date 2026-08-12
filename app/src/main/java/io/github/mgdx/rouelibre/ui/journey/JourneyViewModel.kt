package io.github.mgdx.rouelibre.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.JourneyPlanner
import io.github.mgdx.rouelibre.core.journey.JourneySettings
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * The state of the journey result screen.
 *
 * @property plan what the algorithm composed, or `null` while it computes.
 * @property isComputing a computation is under way.
 * @property hasStations false when no station is known: the network has to be
 *   fetched first, which is not the same thing as an impossible journey.
 */
data class JourneyUiState(
    val plan: JourneyPlan? = null,
    val isComputing: Boolean = true,
    val hasStations: Boolean = true,
) {
    /** The journey shown, or `null` when none could be composed. */
    val chosen: JourneyOption?
        get() = (plan as? JourneyPlan.Found)?.best
}

/**
 * Composes the requested journey and keeps it current (SPEC §6, §7.4).
 *
 * The computation happens off the main thread, inside the business module's
 * algorithm, and it is cancellable: leaving the screen while it runs interrupts
 * it along with the model.
 *
 * Nothing is kept: neither the journey nor its points. SPEC §8 wants computed
 * routes to live in memory, for the session only.
 */
class JourneyViewModel(
    private val router: Router,
    private val repository: StationRepository,
    private val preferences: AppPreferences,
    origin: Coordinates,
    destination: Coordinates,
) : ViewModel() {

    /** The two ends, as the result screen may correct them without going back. */
    private var origin: Coordinates = origin
    private var destination: Coordinates = destination

    private val mutableState = MutableStateFlow(JourneyUiState())

    /** The computation under way, so a new request can replace it. */
    private var computation: Job? = null

    /** The screen's current state. */
    val state: StateFlow<JourneyUiState> = mutableState.asStateFlow()

    init {
        compute()
    }

    /**
     * Works the journey out again between two ends that have changed (SPEC §7.4).
     *
     * Correcting a point on the result screen is a new question, not a refresh:
     * the previous answer is dropped and the computation starts over. Two ends
     * identical to the current ones ask nothing, and cancel nothing.
     */
    fun planBetween(origin: Coordinates, destination: Coordinates) {
        if (origin == this.origin && destination == this.destination) return
        this.origin = origin
        this.destination = destination
        compute()
    }

    /**
     * Recomputes the journey (SPEC §7.4).
     *
     * The recompute button exists because availability changes: the station
     * chosen five minutes ago may have emptied. The computation therefore
     * starts again from the most recent station state the repository holds.
     */
    fun compute() {
        // The previous computation, still running, would race this one for the
        // same state: were it to finish last, the older result would overwrite
        // the fresher one. Cancelling it is also the cancellability SPEC §6
        // asks of the algorithm.
        computation?.cancel()
        computation = viewModelScope.launch {
            mutableState.update { it.copy(isComputing = true) }
            val stations = repository.observeStations().first().stations
            if (stations.isEmpty()) {
                mutableState.update {
                    it.copy(isComputing = false, hasStations = false, plan = null)
                }
                return@launch
            }
            // The fixed handling times are re-read on every computation:
            // changing them in the settings must show on the next recompute,
            // without restarting the application (SPEC §7.6).
            val handling = preferences.handlingTimes.first()
            val planner = JourneyPlanner(
                router,
                JourneySettings(
                    pickupTime = handling.pickupSeconds.seconds,
                    dropoffTime = handling.dropoffSeconds.seconds,
                ),
            )
            val plan = planner.plan(origin, destination, stations)
            mutableState.update {
                it.copy(plan = plan, isComputing = false, hasStations = true)
            }
        }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val router: Router,
        private val repository: StationRepository,
        private val preferences: AppPreferences,
        private val origin: Coordinates,
        private val destination: Coordinates,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(JourneyViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return JourneyViewModel(router, repository, preferences, origin, destination) as T
        }
    }
}
