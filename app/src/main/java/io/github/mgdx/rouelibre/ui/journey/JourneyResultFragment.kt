package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.databinding.FragmentJourneyResultBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyStepBinding
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration
import io.github.mgdx.rouelibre.ui.map.MapStyleLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The result of a walk → bike → walk journey (SPEC §7.4).
 *
 * The track fills the top of the screen in three distinct legs, the detail
 * reads underneath: one first looks at where one is going, then reads how long
 * it takes and through which stations.
 *
 * Nothing is stored. The journey lives in memory for the life of the screen, as
 * SPEC §8 requires.
 */
class JourneyResultFragment : Fragment() {

    private var binding: FragmentJourneyResultBinding? = null
    private var mapLibreMap: MapLibreMap? = null
    private var walkSource: GeoJsonSource? = null
    private var rideSource: GeoJsonSource? = null
    private var styleLoaded = false

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: JourneyViewModel by viewModels {
        val origin = checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_ORIGIN)) {
            "origin point missing"
        }
        val destination = checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)) {
            "destination point missing"
        }
        JourneyViewModel.Factory(
            router = container.journeyRouter,
            repository = container.stationRepository,
            preferences = container.preferences,
            origin = origin.position,
            destination = destination.position,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        MapLibre.getInstance(requireContext())
        val created = FragmentJourneyResultBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.recompute.setOnClickListener { viewModel.compute() }

        views.map.onCreate(savedInstanceState)
        views.map.getMapAsync(::onMapReady)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::show)
            }
        }
    }

    private fun onMapReady(map: MapLibreMap) {
        mapLibreMap = map
        val tiles = container.datasetStore.fileOf(DatasetKind.Tiles)
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        // Without a base map the track is still drawn on an empty background:
        // less telling, but the route is computed and the detail reads.
        if (tiles == null) return

        map.setStyle(
            Style.Builder().fromJson(MapStyleLoader.load(requireContext(), tiles)),
        ) { style ->
            val walk = GeoJsonSource(JourneyLines.WALK_SOURCE_ID)
            val ride = GeoJsonSource(JourneyLines.RIDE_SOURCE_ID)
            walkSource = walk
            rideSource = ride
            style.addSource(walk)
            style.addSource(ride)
            style.addLayer(JourneyLines.rideLayer(requireContext()))
            style.addLayer(JourneyLines.walkLayer(requireContext()))
            styleLoaded = true
            drawJourney(viewModel.state.value)
        }
    }

    private fun show(state: JourneyUiState) {
        val views = binding ?: return
        views.computing.isVisible = state.isComputing
        views.detail.isVisible = !state.isComputing
        if (state.isComputing) return

        drawJourney(state)
        val option = state.chosen
        if (option == null) {
            showWithoutJourney(state)
            return
        }

        views.totalTime.text = requireContext().formatDuration(option.travelTime)
        views.summary.text = getString(
            R.string.journey_summary,
            requireContext().formatDuration(
                option.walkToStation.duration + option.walkToDestination.duration,
            ),
            requireContext().formatDuration(option.ride.duration),
            requireContext().formatDistance(option.distanceMetres.toDouble()),
        )
        showNotice(state)
        showSteps(option)
        showAlternatives(state)
    }

    /**
     * Says what is missing when no bike journey could be composed.
     *
     * SPEC §6 requires it: when no nearby station has a bike, that has to be
     * said, not an impossible journey proposed.
     */
    private fun showWithoutJourney(state: JourneyUiState) {
        val views = binding ?: return
        views.steps.removeAllViews()
        views.alternatives.removeAllViews()
        views.alternativesTitle.isVisible = false

        val plan = state.plan
        val walk = (plan as? JourneyPlan.WalkOnly)?.directWalk
        views.totalTime.text = walk
            ?.let { requireContext().formatDuration(it.duration) }
            ?: getString(R.string.journey_none_title)
        views.summary.text = when {
            !state.hasStations -> getString(R.string.journey_no_stations)
            walk != null -> getString(
                R.string.journey_walk_only,
                requireContext().formatDistance(walk.distanceMetres.toDouble()),
            )
            else -> reasonOf(plan)
        }
        views.notice.isVisible = false
        if (walk != null) {
            addStep(R.drawable.ic_walk, getString(R.string.journey_step_walk_all), null, walk)
        }
    }

    private fun reasonOf(plan: JourneyPlan?): String {
        val reason = when (plan) {
            is JourneyPlan.Impossible -> plan.reason
            is JourneyPlan.WalkOnly -> plan.reason
            else -> null
        }
        return getString(
            when (reason) {
                NoBikeJourney.NoBikeNearby -> R.string.journey_no_bike_nearby
                NoBikeJourney.NoDockNearby -> R.string.journey_no_dock_nearby
                NoBikeJourney.NoRouteBetweenStations -> R.string.journey_no_route
                NoBikeJourney.GraphMissing -> R.string.journey_graph_missing
                NoBikeJourney.OutsideCoverage -> R.string.journey_outside_coverage
                null -> R.string.journey_no_route
            },
        )
    }

    /** Warns when walking straight there beats the bike (SPEC §6). */
    private fun showNotice(state: JourneyUiState) {
        val views = binding ?: return
        val fasterOnFoot = (state.plan as? JourneyPlan.Found)
            ?.takeIf { it.walkingIsFaster }
            ?.directWalk
        views.notice.isVisible = fasterOnFoot != null
        if (fasterOnFoot != null) {
            views.notice.text = getString(
                R.string.journey_walking_is_faster,
                requireContext().formatDuration(fasterOnFoot.duration),
            )
        }
    }

    /** The three steps, in the order one lives them. */
    private fun showSteps(option: JourneyOption) {
        val views = binding ?: return
        views.steps.removeAllViews()

        addStep(
            icon = R.drawable.ic_walk,
            label = getString(R.string.journey_step_to_station, option.departureStation.name),
            detail = resources.getQuantityString(
                R.plurals.bikes_available,
                option.bikesAtDeparture,
                option.bikesAtDeparture,
            ),
            leg = option.walkToStation,
        )
        addStep(
            icon = R.drawable.ic_bike,
            label = getString(R.string.journey_step_ride, option.arrivalStation.name),
            detail = resources.getQuantityString(
                R.plurals.docks_available,
                option.docksAtArrival,
                option.docksAtArrival,
            ),
            leg = option.ride,
        )
        addStep(
            icon = R.drawable.ic_walk,
            label = getString(R.string.journey_step_to_destination),
            detail = null,
            leg = option.walkToDestination,
        )
    }

    private fun addStep(icon: Int, label: String, detail: String?, leg: RouteLeg) {
        val views = binding ?: return
        val step = ItemJourneyStepBinding.inflate(layoutInflater, views.steps, false)
        step.modeIcon.setImageResource(icon)
        step.label.text = label
        step.duration.text = requireContext().formatDuration(leg.duration)
        val distance = requireContext().formatDistance(leg.distanceMetres.toDouble())
        step.detail.text = detail
            ?.let { getString(R.string.address_detail, distance, it) }
            ?: distance
        views.steps.addView(step.root)
    }

    /**
     * The other station pairs (SPEC §6).
     *
     * They exist because the fastest is not always the best: a station slightly
     * further away but better stocked can be worth the minute it costs.
     */
    private fun showAlternatives(state: JourneyUiState) {
        val views = binding ?: return
        views.alternatives.removeAllViews()
        val options = state.options
        views.alternativesTitle.isVisible = options.size > 1
        if (options.size <= 1) return

        options.forEachIndexed { index, option ->
            val step = ItemJourneyStepBinding.inflate(layoutInflater, views.alternatives, false)
            step.modeIcon.setImageResource(
                if (index == state.chosenIndex) R.drawable.ic_pin else R.drawable.ic_bike,
            )
            step.label.text = getString(
                R.string.journey_alternative_stations,
                option.departureStation.name,
                option.arrivalStation.name,
            )
            step.detail.text = getString(
                R.string.journey_alternative_detail,
                resources.getQuantityString(
                    R.plurals.bikes_available,
                    option.bikesAtDeparture,
                    option.bikesAtDeparture,
                ),
                resources.getQuantityString(
                    R.plurals.docks_available,
                    option.docksAtArrival,
                    option.docksAtArrival,
                ),
            )
            step.duration.text = requireContext().formatDuration(option.travelTime)
            step.root.setOnClickListener { viewModel.choose(index) }
            views.alternatives.addView(step.root)
        }
    }

    /** Draws the chosen option and frames the map on it. */
    private fun drawJourney(state: JourneyUiState) {
        if (!styleLoaded) return
        val option = state.chosen
        val walk = walkSource ?: return
        val ride = rideSource ?: return

        if (option == null) {
            val directWalk = (state.plan as? JourneyPlan.WalkOnly)?.directWalk
            walk.setGeoJson(JourneyLines.featuresOf(directWalk))
            ride.setGeoJson(JourneyLines.featuresOf(null))
            frameOn(directWalk?.geometry.orEmpty().map { LatLng(it.latitude, it.longitude) })
            return
        }
        walk.setGeoJson(JourneyLines.walkFeatures(option))
        ride.setGeoJson(JourneyLines.rideFeatures(option))
        frameOn(
            (
                option.walkToStation.geometry + option.ride.geometry +
                    option.walkToDestination.geometry
                )
                .map { LatLng(it.latitude, it.longitude) },
        )
    }

    /** Frames the map on the whole track, with a comfortable margin. */
    private fun frameOn(points: List<LatLng>) {
        val map = mapLibreMap ?: return
        if (points.size < 2) return
        val bounds = LatLngBounds.Builder().includes(points).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, FRAME_PADDING_PIXELS))
    }

    // ---------------------------------------------------- map lifecycle --

    override fun onStart() {
        super.onStart()
        binding?.map?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding?.map?.onResume()
    }

    override fun onPause() {
        binding?.map?.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding?.map?.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.map?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding?.map?.onLowMemory()
    }

    override fun onDestroyView() {
        binding?.map?.onDestroy()
        walkSource = null
        rideSource = null
        mapLibreMap = null
        styleLoaded = false
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARGUMENT_ORIGIN = "origin"
        private const val ARGUMENT_DESTINATION = "destination"

        /** The margin around the track, in pixels, so it does not touch the edges. */
        private const val FRAME_PADDING_PIXELS = 80

        /** Opens the result for a pair of points already designated. */
        fun newInstance(
            origin: JourneyEndpoint,
            destination: JourneyEndpoint,
        ): JourneyResultFragment = JourneyResultFragment().apply {
            arguments = Bundle().apply {
                origin.writeTo(this, ARGUMENT_ORIGIN)
                destination.writeTo(this, ARGUMENT_DESTINATION)
            }
        }
    }
}
