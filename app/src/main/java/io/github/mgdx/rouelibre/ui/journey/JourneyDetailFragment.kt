package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.journey.apportionMinutes
import io.github.mgdx.rouelibre.core.journey.shownMinutes
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.elevationProfile
import io.github.mgdx.rouelibre.core.routing.smoothedOver
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.databinding.FragmentJourneyDetailBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyPlaceBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyStepBinding
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.BikeGlyphs
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.formatAltitude
import io.github.mgdx.rouelibre.ui.formatClimb
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatMinutes
import io.github.mgdx.rouelibre.ui.isReliefWorthDrawing
import io.github.mgdx.rouelibre.ui.withBikeFleet
import io.github.mgdx.rouelibre.ui.withFleet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The journey, said in full (SPEC §7.4.1).
 *
 * The result screen shares its height with a map and answers the question in a
 * figure, a sentence and a drawing. Everything that did not fit there is here:
 * each station by name and by street, what it held when the journey was worked
 * out, and every leg with its distance, its minutes and its climb.
 *
 * Nothing is fetched here save the stations' addresses, which are read off the
 * offline index (SPEC §4.3), and nothing is computed: the journey arrives
 * already made, through [ShownJourneyViewModel]. The one exception is a screen
 * coming back from a killed process, which finds that holder empty and has the
 * journey worked out again from the two ends it kept.
 */
class JourneyDetailFragment : Fragment() {

    private var binding: FragmentJourneyDetailBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val shownJourney: ShownJourneyViewModel by activityViewModels()

    private val viewModel: JourneyDetailViewModel by viewModels {
        JourneyDetailViewModel.Factory(container.addressIndex)
    }

    /**
     * Where the journey described sets off from, and where it goes.
     *
     * The two ends are the whole of what this screen keeps of a journey: two
     * labelled points, which go no further than its instance state (SPEC §8).
     * They are what lets it be found again after the process has been killed —
     * the journey itself, thousands of coordinates long, is not written
     * anywhere and is worked out afresh (see [ShownJourneyViewModel]).
     */
    private var origin: JourneyEndpoint? = null
    private var destination: JourneyEndpoint? = null

    /**
     * Whether the journey described is ridden on the user's own bike.
     *
     * Read off the journey itself while there is one, and kept beside the two
     * ends for the same reason they are: it is part of the question the screen
     * would have to ask again after a killed process, and a station journey
     * worked out in place of a ride would not be the journey being read
     * (SPEC §7.3).
     */
    private var usesOwnBike = false

    /**
     * Works the journey out again, on the one path that needs it.
     *
     * Built only when it is asked for, so arriving here the ordinary way — from
     * a result screen that has just computed the journey — computes nothing a
     * second time.
     */
    private val plannedAgain: JourneyViewModel by viewModels {
        JourneyViewModel.Factory(
            router = container.journeyRouter,
            repository = container.stationRepository,
            origin = checkNotNull(origin).position,
            destination = checkNotNull(destination).position,
            usesOwnBike = usesOwnBike,
        )
    }

    /**
     * Whether the network served lends pedal-assist bikes (SPEC §15).
     *
     * The ride's icon and the two station markers carry the bolt when it does.
     */
    private var fleet = BikeFleet.Mechanical

    /**
     * What the network lends, down to the vehicle types it counts by.
     *
     * The summary says what the two kinds of bike at the departure station
     * divide into, and reading that takes the identifier table (SPEC §7.4.1).
     */
    private var lentFleet: FleetDescription? = null

    /**
     * Takes back the two ends of the journey described.
     *
     * From the holder while it still holds one, and from the instance state
     * otherwise — which is the process having been killed while this screen was
     * open, the one case where the two differ.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shown = shownJourney.journey
        origin = shown?.origin ?: JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
        destination = shown?.destination
            ?: JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
        usesOwnBike = if (shown != null) {
            shown.plan is JourneyPlan.OwnBike
        } else {
            savedInstanceState?.getBoolean(STATE_OWN_BIKE) == true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentJourneyDetailBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)

        // The journey lives in memory only (SPEC §8): a process killed while
        // this screen was open comes back to an empty holder. The screen the
        // user left is the one they must find again, so it is worked out again
        // here rather than stepping back to the result screen — which is what
        // used to happen, and lost them the very page they were reading.
        val journey = shownJourney.journey
        if (journey == null) {
            workTheJourneyOutAgain()
            return
        }
        showJourneyInFull(journey)
    }

    /**
     * Keeps the two ends, so this screen can be found again after a kill.
     *
     * Two labelled points and nothing else: no track, no station, no history
     * (SPEC §8). They live in this bundle and go no further, exactly as the
     * result screen keeps its own.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        origin?.writeTo(outState, STATE_ORIGIN)
        destination?.writeTo(outState, STATE_DESTINATION)
        outState.putBoolean(STATE_OWN_BIKE, usesOwnBike)
    }

    /**
     * Composes the journey again, and shows it here when it arrives.
     *
     * Without the two ends there is nothing to compose — a state that should
     * not arise, since they are saved with the screen — and the result screen
     * underneath is then the only place left that can answer. The wait is
     * inhabited by the same bike as on that screen: this computation takes
     * exactly as long as the first one did.
     */
    private fun workTheJourneyOutAgain() {
        val from = origin
        val to = destination
        if (from == null || to == null) {
            parentFragmentManager.popBackStack()
            return
        }
        val views = checkNotNull(binding)
        views.computing.isVisible = true
        if (usesOwnBike) views.computingLabel.setText(R.string.journey_computing_own_bike)
        withBikeFleet { lent ->
            fleet = lent
            binding?.computingBike?.fleet = lent
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val settled = plannedAgain.state.first { !it.isComputing }
            // The screen may be in the background by now: laying a journey out
            // there — and, failing one, stepping back — has to wait for it to be
            // looked at again.
            viewLifecycleOwner.withStarted { showComputed(settled, from, to) }
        }
    }

    /**
     * Shows the journey just worked out, or gives up on describing it.
     *
     * A journey that can no longer be composed — the bikes gone from the
     * departure station while the application was away, the network not yet
     * fetched — has no detail to read. The result screen is where the reason for
     * that is said, and stepping back to it is what puts the reason on screen.
     */
    private fun showComputed(state: JourneyUiState, from: JourneyEndpoint, to: JourneyEndpoint) {
        val plan = state.plan
        if (plan == null || plan is JourneyPlan.Impossible) {
            parentFragmentManager.popBackStack()
            return
        }
        val journey = ShownJourney(from, to, plan)
        // Left where the result screen underneath reads it too: it is the
        // journey being shown, whichever of the two screens shows it.
        shownJourney.journey = journey
        binding?.computing?.isVisible = false
        showJourneyInFull(journey)
    }

    /** Lays the whole screen out for a journey that is ready to be described. */
    private fun showJourneyInFull(journey: ShownJourney) {
        showTotal(journey)
        showProfile(journey.plan)
        // The city's configuration is read from disk: the rows are laid with
        // the plain bike and drawn again, a moment later, with the network's
        // own.
        withBikeFleet { lent ->
            fleet = lent
            binding?.shape?.fleet = lent
            showJourney(journey, viewModel.addresses.value)
        }
        // The same reading, read in full: the summary splits the bikes waiting
        // at the departure station, which the bike glyph alone cannot say.
        withFleet { lent ->
            lentFleet = lent
            showTotal(journey)
        }
        viewModel.locate(stationsOf(journey.plan))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.addresses.collectLatest { showJourney(journey, it) }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /** The stations to place in a street: none at all on a walk. */
    private fun stationsOf(plan: JourneyPlan): List<Station> = when (plan) {
        is JourneyPlan.Found -> listOf(plan.best.departureStation, plan.best.arrivalStation)
        else -> emptyList()
    }

    /**
     * The two lines the previous screen showed, repeated verbatim.
     *
     * A detail that restated the total differently would read as a second
     * journey. The wording is therefore built the same way, from the same
     * resources — the bikes waiting at the departure station included.
     */
    private fun showTotal(journey: ShownJourney) {
        val views = binding ?: return
        val plan = journey.plan
        val option = (plan as? JourneyPlan.Found)?.best
        val walk = plan as? JourneyPlan.WalkOnly
        val ownBike = plan as? JourneyPlan.OwnBike
        // The legs rounded together, so this screen's total, sentence, band and
        // step rows all say the same journey — and say the same thing as the
        // screen it was opened from, which rounds it the same way.
        val minutes = legMinutesOf(plan)
        if (minutes.isEmpty()) return
        views.totalTime.text = requireContext().formatMinutes(minutes.sum())
        views.summary.text = when {
            option != null -> requireContext().journeySummary(
                option,
                minutes = option.shownMinutes(),
                atDeparture = requireContext()
                    .bikesAtDeparture(option.bikeSplitAtDeparture(lentFleet)),
            )
            walk != null -> requireContext().walkSummary(
                walk.directWalk,
                isQuickerThanTheBike = walk.reason == NoBikeJourney.WalkingIsQuicker,
            )

            ownBike != null -> requireContext().ownBikeSummary(ownBike.ride)
            else -> return
        }
        views.shape.legs = legsOf(plan).mapIndexed { index, leg ->
            JourneyShapeView.Leg(
                isRide = leg.isRide,
                duration = requireContext().formatMinutes(minutes[index]),
                distance = distanceOf(leg.route),
            )
        }
    }

    /**
     * The legs of [plan] in whole minutes, in the order [legsOf] gives them.
     *
     * Empty for a journey that could not be composed: it has no leg to show and
     * no total to announce.
     */
    private fun legMinutesOf(plan: JourneyPlan): List<Int> =
        apportionMinutes(legsOf(plan).map { it.route.duration })

    /**
     * The ground the ride runs over, where the graph has something to say
     * (SPEC §7.4.1).
     *
     * Only the bike leg is drawn: the walks are lived at a pace no hill
     * changes, and it is the ride one decides on. The drawing is silent
     * wherever the climb figure itself would be — under three hundred metres of
     * ground, or inside five metres of height, what the graph holds is the
     * error between two SRTM samples, and stretching it across the screen would
     * draw a hill that is not there.
     */
    private fun showProfile(plan: JourneyPlan) {
        val views = binding ?: return
        val ride = when (plan) {
            is JourneyPlan.Found -> plan.best.ride
            // The whole journey is the ride here, and it is decided on the same
            // question: what goes up.
            is JourneyPlan.OwnBike -> plan.ride
            is JourneyPlan.WalkOnly, is JourneyPlan.Impossible -> null
        }
        val profile = ride?.elevationProfile()?.smoothedOver(PROFILE_SMOOTHING_METRES).orEmpty()
        val lowest = profile.minOfOrNull { it.elevationMetres }
        val highest = profile.maxOfOrNull { it.elevationMetres }
        val worthDrawing = ride != null &&
            lowest != null &&
            highest != null &&
            isReliefWorthDrawing(ride.distanceMetres, highest - lowest)

        views.profileTitle.isVisible = worthDrawing
        views.profile.isVisible = worthDrawing
        if (!worthDrawing) return

        views.profile.lowestLabel = requireContext().formatAltitude(lowest)
        views.profile.highestLabel = requireContext().formatAltitude(highest)
        views.profile.profile = profile
        // A drawing says nothing to a screen reader: the sentence it stands for
        // is read instead, and it is the one the drawing draws.
        views.profile.contentDescription = getString(
            R.string.journey_detail_profile_description,
            requireContext().formatClimb(ride.ascentMetres, ride.distanceMetres),
            views.profile.lowestLabel,
            views.profile.highestLabel,
        )
    }

    /** One leg of the journey, and whether a bike is ridden along it. */
    private data class Leg(val route: RouteLeg, val isRide: Boolean)

    private fun legsOf(plan: JourneyPlan): List<Leg> = when (plan) {
        is JourneyPlan.Found -> listOf(
            Leg(plan.best.walkToStation, isRide = false),
            Leg(plan.best.ride, isRide = true),
            Leg(plan.best.walkToDestination, isRide = false),
        )

        is JourneyPlan.WalkOnly -> listOf(Leg(plan.directWalk, isRide = false))
        is JourneyPlan.OwnBike -> listOf(Leg(plan.ride, isRide = true))
        is JourneyPlan.Impossible -> emptyList()
    }

    /**
     * The journey in the order it is lived: each leg, and the stations between.
     *
     * Rebuilt whole whenever an address arrives: the rows are few, and a list
     * assembled in one pass is easier to follow than one patched in place.
     */
    private fun showJourney(journey: ShownJourney, addresses: Map<String, AddressResult>) {
        val views = binding ?: return
        views.steps.removeAllViews()

        // The rows carry the same minutes as the band above them, which is why
        // they are handed the apportioned figures rather than rounding the legs
        // again for themselves.
        val minutes = legMinutesOf(journey.plan)

        // The two ends are not rows of their own: they are named by the legs
        // that reach them — "walk to the destination" — and by the two fields
        // at the top of the screen this one was opened from. A row repeating
        // one of them said nothing the reader had not just read.
        when (val plan = journey.plan) {
            is JourneyPlan.Found -> addRide(plan.best, minutes, addresses)
            is JourneyPlan.WalkOnly -> addLeg(
                icon = R.drawable.ic_walk,
                label = getString(R.string.journey_step_walk_all),
                leg = plan.directWalk,
                minutes = minutes.first(),
            )

            // The plain bike, whatever the network lends: the bolt and the cog
            // describe what waits at a station (SPEC §15), and this bike is the
            // rider's own.
            is JourneyPlan.OwnBike -> addLeg(
                icon = R.drawable.ic_bike,
                label = getString(R.string.journey_step_ride_all),
                leg = plan.ride,
                minutes = minutes.first(),
            )

            is JourneyPlan.Impossible -> Unit
        }
        // The counts are the ones the journey was decided on, not the ones the
        // stations hold now: saying so is what keeps a figure read five minutes
        // ago from being taken for a promise (SPEC §6). Only a bike journey has
        // a station to count anything at.
        views.availabilityNote.isVisible = journey.plan is JourneyPlan.Found
    }

    /**
     * The three legs, and the two stations they run between.
     *
     * @param minutes the three legs' apportioned minutes, in that same order.
     */
    private fun addRide(
        option: JourneyOption,
        minutes: List<Int>,
        addresses: Map<String, AddressResult>,
    ) {
        addLeg(
            icon = R.drawable.ic_walk,
            label = getString(R.string.journey_step_to_station, option.departureStation.name),
            leg = option.walkToStation,
            minutes = minutes[0],
        )
        addStation(
            role = getString(R.string.journey_detail_departure_station),
            station = option.departureStation,
            address = addresses[option.departureStation.id],
            availability = availabilityOf(
                counted = resources.getQuantityString(
                    R.plurals.bikes_available,
                    option.bikesAtDeparture,
                    option.bikesAtDeparture,
                ),
                station = option.departureStation,
            ),
        )
        addLeg(
            icon = BikeGlyphs.icon(fleet),
            label = getString(R.string.journey_step_ride, option.arrivalStation.name),
            leg = option.ride,
            minutes = minutes[1],
        )
        addStation(
            role = getString(R.string.journey_detail_arrival_station),
            station = option.arrivalStation,
            address = addresses[option.arrivalStation.id],
            availability = availabilityOf(
                counted = resources.getQuantityString(
                    R.plurals.docks_available,
                    option.docksAtArrival,
                    option.docksAtArrival,
                ),
                station = option.arrivalStation,
            ),
        )
        addLeg(
            icon = R.drawable.ic_walk,
            label = getString(R.string.journey_step_to_destination),
            leg = option.walkToDestination,
            minutes = minutes[2],
        )
    }

    /**
     * What a station held when the journey was worked out, and how big it is.
     *
     * The capacity is only there when the feed publishes it: "8 bikes of 40
     * docks" places the count on a scale, and a bare count on a station whose
     * size is unknown is still worth reading.
     */
    private fun availabilityOf(counted: String, station: Station): String {
        val capacity = station.capacity ?: return counted
        return getString(
            R.string.address_detail,
            counted,
            resources.getQuantityString(R.plurals.docks_total, capacity, capacity),
        )
    }

    /**
     * A station of the journey: what it is called, where it stands, what it
     * held.
     *
     * The marker is the map's own, so the row and the point drawn on the
     * previous screen are recognised as the same thing.
     */
    private fun addStation(
        role: String,
        station: Station,
        address: AddressResult?,
        availability: String,
    ) {
        val views = binding ?: return
        val place = ItemJourneyPlaceBinding.inflate(layoutInflater, views.steps, false)
        place.marker.setImageResource(BikeGlyphs.stationMarker(fleet))
        place.role.text = role
        place.name.text = station.name
        place.address.isGone = address == null
        address?.let { place.address.text = addressLineOf(it) }
        place.availability.text = availability
        views.steps.addView(place.root)
    }

    /**
     * The street a station stands in, written as the station's sheet writes it.
     *
     * A station in the middle of a square has no address of its own: the
     * nearest street is named instead, and said to be nearby rather than
     * passed off as the station's own number.
     */
    private fun addressLineOf(address: AddressResult): String {
        val place = if (address.postcode.isNullOrBlank()) {
            address.city
        } else {
            getString(R.string.address_locality, address.postcode, address.city)
        }
        val what = if (address.houseNumber == null) {
            getString(R.string.station_address_nearby, address.streetName)
        } else {
            address.toTitle(requireContext())
        }
        return getString(R.string.address_detail, what, place)
    }

    /**
     * One leg, with what it costs under what it asks of you.
     *
     * The second line reads in the order the leg is lived: how far it runs, and
     * how much of it goes up. Flat ground names no climb (SPEC §7.4).
     *
     * @param minutes what this leg was apportioned out of the journey's total,
     *   rather than its own rounding: the rows are read against the total above
     *   them, and have to add up to it.
     */
    private fun addLeg(icon: Int, label: String, leg: RouteLeg, minutes: Int) {
        val views = binding ?: return
        val step = ItemJourneyStepBinding.inflate(layoutInflater, views.steps, false)
        step.modeIcon.setImageResource(icon)
        step.label.text = label
        step.duration.text = requireContext().formatMinutes(minutes)
        step.detail.text = listOfNotNull(distanceOf(leg), climbOf(leg))
            .reduce { read, next -> getString(R.string.address_detail, read, next) }
        views.steps.addView(step.root)
    }

    private fun distanceOf(leg: RouteLeg): String =
        requireContext().formatDistance(leg.distanceMetres.toDouble())

    /**
     * The climb of one leg, named as such, or nothing on flat ground.
     *
     * A bare figure would be unreadable beside a distance — "650 m · 20 m" says
     * nothing about which way the second one runs — so the metres never appear
     * without the word that turns them upright.
     */
    private fun climbOf(leg: RouteLeg): String? = requireContext()
        .formatClimb(leg.ascentMetres, leg.distanceMetres)
        ?.let { getString(R.string.journey_climb, it) }

    private companion object {
        private const val STATE_ORIGIN = "state-origin"
        private const val STATE_DESTINATION = "state-destination"
        private const val STATE_OWN_BIKE = "state-own-bike"

        /**
         * The ground a reading of the profile is averaged over.
         *
         * Half of what it takes for a climb to be worth naming (SPEC §7.4): a
         * rise of three hundred metres comes through whole, and the saw the
         * SRTM samples draw across flat ground — a metre up and a metre down
         * every fifty — does not.
         */
        const val PROFILE_SMOOTHING_METRES = 150.0
    }
}
