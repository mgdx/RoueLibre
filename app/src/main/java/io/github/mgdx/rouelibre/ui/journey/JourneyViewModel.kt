package io.github.mgdx.rouelibre.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.JourneyPlanner
import io.github.mgdx.rouelibre.core.journey.JourneySettings
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.journey.WalkingPace
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.asRiddenBike
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Two questions can be asked of it, and it is [usesOwnBike] that says which:
 * the walk → bike → walk journey through the network's stations, or the single
 * ride of somebody on their own bike (SPEC §7.3).
 *
 * Nothing is kept: neither the journey nor its points. SPEC §8 wants computed
 * routes to live in memory, for the session only.
 *
 * @property wantedBikeKind the kind of bike asked for on the search screen, or
 *   `null` for no kind at all. It is honoured only where the network really
 *   lends both kinds — see [bikeKindFilter].
 * @property fleet what the network in service lends, read at each computation:
 *   the kinds are the rider's vocabulary and the identifiers the producer's, and
 *   only this table joins them (SPEC §4.1).
 * @property walkingPace how fast the user walks (SPEC §7.6). Read at each
 *   computation rather than captured when the screen opened, so a pace changed
 *   in the settings applies to the next journey without a restart.
 * @property ownBikeKind what the rider says their own bike is (SPEC §7.6), read
 *   the same way and for the same reason. It reaches the ride of somebody on
 *   their own bike and nothing else: what the network lends says nothing about
 *   the bike in one's hallway, and the reverse holds just as firmly.
 * @property coveredArea the box the city's data was cut from, read at each
 *   computation like the rest. It is what lets a journey with an end outside
 *   that box be refused before anything is computed (SPEC §4, §7.8), and it is
 *   handed to the algorithm rather than examined here: the question belongs to
 *   the business core, where the map screen can ask it in the same words. A
 *   flow yielding `null` refuses nothing.
 */
class JourneyViewModel(
    private val router: Router,
    private val repository: StationRepository,
    origin: Coordinates,
    destination: Coordinates,
    private val usesOwnBike: Boolean = false,
    private val wantedBikeKind: WantedBikeKind? = null,
    private val fleet: Flow<FleetDescription?> = flowOf(null),
    private val walkingPace: Flow<WalkingPace> = flowOf(WalkingPace.Normal),
    private val ownBikeKind: Flow<OwnBikeKind> = flowOf(OwnBikeKind.Mechanical),
    private val coveredArea: Flow<BoundingBox?> = flowOf(null),
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
     * Availability changes: the station chosen five minutes ago may have
     * emptied. The computation therefore starts again from the most recent
     * station state the repository holds, never from the one the previous
     * answer was built on.
     */
    fun compute() {
        // The previous computation, still running, would race this one for the
        // same state: were it to finish last, the older result would overwrite
        // the fresher one. Cancelling it is also the cancellability SPEC §6
        // asks of the algorithm.
        computation?.cancel()
        computation = viewModelScope.launch {
            mutableState.update { it.copy(isComputing = true) }
            // On one's own bike the network is not consulted at all: no station
            // is chosen, so an empty station list is not a reason to give up on
            // the journey — a user who has never refreshed the feed still gets
            // their ride (SPEC §7.3). No kind is asked of the network either:
            // the bike is the rider's own, and what the network lends says
            // nothing about it. What the rider declared theirs to be does count,
            // and it is the one setting that reaches this ride: it decides the
            // profile it is traced with (SPEC §7.6). The walking pace is not
            // passed on, this journey being one leg and no step of it walked.
            if (usesOwnBike) {
                val settings = JourneySettings(riddenBike = ownBikeKind.first().asRiddenBike())
                val ride = JourneyPlanner(router, settings, coveredArea = coveredArea.first())
                    .planWithOwnBike(origin, destination)
                mutableState.update {
                    it.copy(plan = ride, isComputing = false, hasStations = true)
                }
                return@launch
            }
            val stations = repository.observeStations().first().stations
            if (stations.isEmpty()) {
                mutableState.update {
                    it.copy(isComputing = false, hasStations = false, plan = null)
                }
                return@launch
            }
            // The filter is read once and used twice: what the algorithm was
            // narrowed by must be what the ride is timed on. A kind the network
            // cannot honour is dropped here (see below), and a journey open to
            // every station must not be worked out on a bike it was not
            // promised (SPEC §6).
            val filter = bikeKindFilter()
            val planner = JourneyPlanner(
                router = router,
                settings = JourneySettings(
                    walkingPace = walkingPace.first(),
                    riddenBike = filter?.wanted.asRiddenBike(),
                ),
                wantedBike = filter,
                coveredArea = coveredArea.first(),
            )
            val plan = planner.plan(origin, destination, stations)
            mutableState.update {
                it.copy(plan = plan, isComputing = false, hasStations = true)
            }
        }
    }

    /**
     * The kind asked for, as the algorithm can use it, or `null` for no filter.
     *
     * Two things silence a kind, and both are read fresh at every computation
     * rather than captured when the screen opened.
     *
     * **A conurbation lending one kind only.** The choice is remembered across
     * cities, since it is a fact about the rider (SPEC §7.3), but a network with
     * one kind to lend cannot satisfy it and must not be filtered by it: the
     * journey would come back empty over a preference made somewhere else.
     * Nothing is erased, so coming back to a mixed city finds the choice again.
     *
     * **A table that says nothing.** Without the network's vehicle types no
     * identifier can be read as a kind, so no station could ever qualify.
     */
    private suspend fun bikeKindFilter(): BikeKindFilter? {
        val wanted = wantedBikeKind ?: return null
        val lent = fleet.first() ?: return null
        if (!lent.isMixed || lent.vehicleTypes.isEmpty()) return null
        return BikeKindFilter(wanted, lent.vehicleTypes)
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val router: Router,
        private val repository: StationRepository,
        private val origin: Coordinates,
        private val destination: Coordinates,
        private val usesOwnBike: Boolean = false,
        private val wantedBikeKind: WantedBikeKind? = null,
        private val fleet: Flow<FleetDescription?> = flowOf(null),
        private val walkingPace: Flow<WalkingPace> = flowOf(WalkingPace.Normal),
        private val ownBikeKind: Flow<OwnBikeKind> = flowOf(OwnBikeKind.Mechanical),
        private val coveredArea: Flow<BoundingBox?> = flowOf(null),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(JourneyViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return JourneyViewModel(
                router,
                repository,
                origin,
                destination,
                usesOwnBike,
                wantedBikeKind,
                fleet,
                walkingPace,
                ownBikeKind,
                coveredArea,
            ) as T
        }
    }
}
