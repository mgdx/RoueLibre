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
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.elevationProfile
import io.github.mgdx.rouelibre.core.routing.smoothedOver
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.databinding.FragmentJourneyDetailBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyPlaceBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyStepBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.formatAltitude
import io.github.mgdx.rouelibre.ui.formatClimb
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration
import io.github.mgdx.rouelibre.ui.isReliefWorthDrawing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The journey, said in full (SPEC §7.4.1).
 *
 * The result screen shares its height with a map and answers the question in a
 * figure, a sentence and a drawing. Everything that did not fit there is here:
 * each station by name and by street, what it held when the journey was worked
 * out, and every leg with its distance, its minutes and its climb.
 *
 * Nothing is fetched and nothing is computed here — the journey arrives already
 * made, through [ShownJourneyViewModel] — save the stations' addresses, which
 * are read off the offline index (SPEC §4.3).
 */
class JourneyDetailFragment : Fragment() {

    private var binding: FragmentJourneyDetailBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val shownJourney: ShownJourneyViewModel by activityViewModels()

    private val viewModel: JourneyDetailViewModel by viewModels {
        JourneyDetailViewModel.Factory(container.addressIndex)
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
        // this screen was open comes back to an empty holder. Stepping back to
        // the result screen puts the user in front of the one place that can
        // work the journey out again, rather than in front of an empty screen.
        val journey = shownJourney.journey ?: run {
            parentFragmentManager.popBackStack()
            return
        }

        showTotal(journey)
        showProfile(journey.plan)
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
     * resources.
     */
    private fun showTotal(journey: ShownJourney) {
        val views = binding ?: return
        val plan = journey.plan
        val option = (plan as? JourneyPlan.Found)?.best
        val walk = plan as? JourneyPlan.WalkOnly
        views.totalTime.text = requireContext().formatDuration(
            option?.travelTime ?: walk?.directWalk?.duration ?: return,
        )
        views.summary.text = when {
            option != null -> requireContext().journeySummary(option)
            walk != null -> requireContext().walkSummary(
                walk.directWalk,
                isQuickerThanTheBike = walk.reason == NoBikeJourney.WalkingIsQuicker,
            )

            else -> return
        }
        views.shape.legs = legsOf(plan).map { leg ->
            JourneyShapeView.Leg(
                isRide = leg.isRide,
                duration = requireContext().formatDuration(leg.route.duration),
                distance = distanceOf(leg.route),
            )
        }
    }

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
        val ride = (plan as? JourneyPlan.Found)?.best?.ride
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

        // The two ends are not rows of their own: they are named by the legs
        // that reach them — "walk to the destination" — and by the two fields
        // at the top of the screen this one was opened from. A row repeating
        // one of them said nothing the reader had not just read.
        when (val plan = journey.plan) {
            is JourneyPlan.Found -> addRide(plan.best, addresses)
            is JourneyPlan.WalkOnly -> addLeg(
                icon = R.drawable.ic_walk,
                label = getString(R.string.journey_step_walk_all),
                leg = plan.directWalk,
            )

            is JourneyPlan.Impossible -> Unit
        }
        // The counts are the ones the journey was decided on, not the ones the
        // stations hold now: saying so is what keeps a figure read five minutes
        // ago from being taken for a promise (SPEC §6). Only a bike journey has
        // a station to count anything at.
        views.availabilityNote.isVisible = journey.plan is JourneyPlan.Found
    }

    /** The three legs, and the two stations they run between. */
    private fun addRide(option: JourneyOption, addresses: Map<String, AddressResult>) {
        addLeg(
            icon = R.drawable.ic_walk,
            label = getString(R.string.journey_step_to_station, option.departureStation.name),
            leg = option.walkToStation,
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
            icon = R.drawable.ic_bike,
            label = getString(R.string.journey_step_ride, option.arrivalStation.name),
            leg = option.ride,
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
     */
    private fun addLeg(icon: Int, label: String, leg: RouteLeg) {
        val views = binding ?: return
        val step = ItemJourneyStepBinding.inflate(layoutInflater, views.steps, false)
        step.modeIcon.setImageResource(icon)
        step.label.text = label
        step.duration.text = requireContext().formatDuration(leg.duration)
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
