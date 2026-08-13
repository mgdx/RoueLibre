package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentJourneyResultBinding
import io.github.mgdx.rouelibre.databinding.ItemJourneyStepBinding
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration
import io.github.mgdx.rouelibre.ui.map.MapStyleLoader
import io.github.mgdx.rouelibre.ui.map.ServedAreaCamera
import io.github.mgdx.rouelibre.ui.map.UserPositionMarker
import kotlinx.coroutines.Job
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
    private var markerSource: GeoJsonSource? = null
    private var userPositionSource: GeoJsonSource? = null
    private var styleLoaded = false

    /**
     * The track the camera must show, kept for as long as it is displayed.
     *
     * The map is not always the size it will end up being when the journey is
     * drawn: holding the framing lets it be laid again once it is.
     */
    private var frame: LatLngBounds? = null

    /**
     * The limit penning the camera inside the served area, kept so that the
     * framing can have it measured again against the map's current height.
     */
    private var servedArea: ServedAreaCamera? = null

    /**
     * The last position shown, held for the life of the screen only.
     *
     * It survives a rebuild of the view — a rotation must not make the point
     * vanish until the next fix — and nothing else: it is written nowhere
     * (SPEC §2, C3).
     */
    private var lastKnownPosition: Coordinates? = null

    /** The subscription that moves the point, while the screen is displayed. */
    private var following: Job? = null

    /**
     * Whether the step list is unfolded, for the life of the screen.
     *
     * Folded to begin with: the total time, the summary and the drawing answer
     * the question, and the three lines naming the stations are what one asks
     * for afterwards. The answer survives a rotation — and a recomputation,
     * which changes the steps but not the wish to see them.
     */
    private var stepsShown = false

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /**
     * Where the journey sets off from.
     *
     * The screen opens on the point it was given, and that point can be
     * corrected here: a mistaken address is worth a press, not a way back.
     */
    private lateinit var origin: JourneyEndpoint

    /** Where it goes, likewise correctable. */
    private lateinit var destination: JourneyEndpoint

    private val picker = JourneyEndpointPicker(
        fragment = this,
        onMessage = ::showMessage,
        onPicked = ::acceptEndpoint,
        onLocating = ::showLocating,
    )

    /**
     * Requests the location permissions for the "locate me" button, and never
     * insists: a refusal leaves the journey and its map whole (SPEC §10).
     */
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            locateMe()
            // Only now may the point start following: before this answer there
            // was nothing to follow it with (SPEC §10).
            followUserPosition()
        } else {
            showMessage(getString(R.string.map_location_denied))
        }
    }

    private val viewModel: JourneyViewModel by viewModels {
        JourneyViewModel.Factory(
            router = container.journeyRouter,
            repository = container.stationRepository,
            origin = origin.position,
            destination = destination.position,
        )
    }

    /**
     * Establishes the two ends before anything reads them.
     *
     * What the screen was opened with, unless it has been corrected since: a
     * point changed here outlives a rotation, and it is that one the journey
     * must be worked out between.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        origin = JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
            ?: checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_ORIGIN)) {
                "origin point missing"
            }
        destination = JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
            ?: checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)) {
                "destination point missing"
            }
        picker.readFrom(savedInstanceState)
        stepsShown = savedInstanceState?.getBoolean(STATE_STEPS_SHOWN) ?: false
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
        views.stepsToggle.setOnClickListener {
            stepsShown = !stepsShown
            showStepsOrNot()
        }
        views.origin.setOnClickListener { picker.choose(true, destination.position) }
        views.destination.setOnClickListener { picker.choose(false, origin.position) }
        views.swap.setOnClickListener { swapEndpoints() }
        views.locateMe.setOnClickListener { onLocateMeClicked() }

        views.map.onCreate(savedInstanceState)
        views.map.getMapAsync(::onMapReady)
        // The map changes height as the detail below it grows: the journey is
        // framed again each time, or it would stay fitted to a viewport it no
        // longer has.
        views.map.addOnLayoutChangeListener { _, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom ->
            val changed = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (changed) applyFrame()
        }

        picker.listen(viewLifecycleOwner)
        showEndpoints()
        followUserPosition()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::show)
            }
        }
    }

    // ----------------------------------------------------------- the ends --

    /**
     * Takes a corrected end and asks for the journey between the new pair.
     *
     * Nothing is kept from the previous answer: the question has changed.
     */
    private fun acceptEndpoint(endpoint: JourneyEndpoint, isOrigin: Boolean) {
        if (isOrigin) origin = endpoint else destination = endpoint
        showEndpoints()
        viewModel.planBetween(origin.position, destination.position)
    }

    /** Reverses the journey (SPEC §7.3): the way back is not the way there. */
    private fun swapEndpoints() {
        val previousOrigin = origin
        origin = destination
        destination = previousOrigin
        showEndpoints()
        viewModel.planBetween(origin.position, destination.position)
    }

    private fun showEndpoints() {
        val views = binding ?: return
        views.origin.text = origin.label
        views.destination.text = destination.label
    }

    /**
     * Shows, in the field itself, that the position is being looked for.
     *
     * The journey on screen is left alone meanwhile: it is still the answer to
     * the question as it stands, and blanking it would say the opposite. The
     * end of the wait restores what the field said, whether a point was found
     * or not.
     */
    private fun showLocating(isOrigin: Boolean, searching: Boolean) {
        val views = binding ?: return
        if (!searching) {
            showEndpoints()
            return
        }
        val field = if (isOrigin) views.origin else views.destination
        field.setText(R.string.journey_locating)
    }

    // ----------------------------------------------------------- the map --

    private fun onMapReady(map: MapLibreMap) {
        mapLibreMap = map
        val tiles = container.datasetStore.fileOf(DatasetKind.Tiles)
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        limitCamera(map, hasTiles = tiles != null)
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

            // The four points come after the tracks: a marker sitting under
            // the line it belongs to would be half hidden by it.
            val markers = GeoJsonSource(JourneyMarkers.SOURCE_ID)
            markerSource = markers
            style.addSource(markers)
            JourneyMarkers.registerImages(requireContext(), style)
            style.addLayer(JourneyMarkers.layer())

            // The user's position comes last of all, and therefore on top:
            // where one IS beats what one has planned, and the two coincide
            // often enough at the start of a journey for the order to matter.
            val userPosition = GeoJsonSource(UserPositionMarker.SOURCE_ID)
            userPositionSource = userPosition
            style.addSource(userPosition)
            style.addLayer(UserPositionMarker.layer(requireContext()))
            userPosition.setGeoJson(UserPositionMarker.featureFor(lastKnownPosition))

            styleLoaded = true
            drawJourney(viewModel.state.value)
        }
    }

    /**
     * Bounds where the camera may go, at the tiles' own limits.
     *
     * Past their maximum zoom MapLibre scales the last ones it has: one step is
     * allowed, which lets "locate me" come right down onto the pavement without
     * the labels turning to mush. The city is read from disk, so the cap
     * arrives a moment after the map — before any gesture, in practice.
     *
     * With a base map underneath, the camera is also penned inside the served
     * area, as on the main screen: the edge of the data must not show here
     * either. It costs the framing of a journey crossing the conurbation from
     * end to end, which then loses its margins — a fair trade against a screen
     * half empty. Without tiles there is no edge to hide, and nothing to gain
     * from holding a track that is drawn on bare background.
     */
    private fun limitCamera(map: MapLibreMap, hasTiles: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val city = container.activeCity() ?: return@launch
            val closestZoom = city.map.maxZoom.toDouble() + 1
            map.setMaxZoomPreference(closestZoom)
            val area = city.boundingBox?.takeIf { it.isUsable } ?: return@launch
            if (!hasTiles) return@launch
            // The visible region is only measurable once the map view has been
            // laid out, and the limits are read off it.
            val view = binding?.map ?: return@launch
            view.doOnLayout {
                servedArea = ServedAreaCamera(
                    view = view,
                    map = map,
                    area = area,
                    widestZoom = city.map.minZoom.toDouble(),
                    closestZoom = closestZoom,
                ).apply { hold() }
                // The limit was measured on the map as it was when it was laid
                // out; the framing waiting for it was computed for that same
                // moment. Laying it again now puts the two back in step.
                applyFrame()
            }
        }
    }

    /**
     * Answers the "locate me" button.
     *
     * The three cases are told apart: no permission yet, location switched off,
     * or a position to go and get. Only the first asks for anything, and asks
     * once (SPEC §10).
     */
    private fun onLocateMeClicked() {
        val location = container.deviceLocation
        when {
            !location.isPermitted() ->
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)

            !location.isAvailable() ->
                showMessage(getString(R.string.map_location_unavailable))

            else -> locateMe()
        }
    }

    /**
     * Brings the map down onto the walker, as close as the tiles allow.
     *
     * On this screen the map is small and framed on the whole journey: getting
     * back to where one actually stands, at the closest zoom, is what tells the
     * next street corner apart. The position frames the map and feeds the
     * display source, and is written nowhere else (SPEC §2, C3).
     */
    private fun locateMe() {
        viewLifecycleOwner.lifecycleScope.launch {
            // A first fix can take several seconds indoors. Disabling the
            // button meanwhile avoids suggesting the press was lost.
            binding?.locateMe?.isEnabled = false
            val position = try {
                container.deviceLocation.current()
            } finally {
                binding?.locateMe?.isEnabled = true
            }
            if (position == null) {
                showMessage(getString(R.string.map_location_unavailable))
                return@launch
            }
            lastKnownPosition = position
            userPositionSource?.setGeoJson(UserPositionMarker.featureFor(position))
            val map = mapLibreMap ?: return@launch
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(position.latitude, position.longitude),
                    map.maxZoomLevel,
                ),
            )
        }
    }

    private fun showMessage(message: String) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Follows the position and moves the point on the map (SPEC §7.4).
     *
     * Only if the permission has already been granted: opening this screen asks
     * for nothing (SPEC §10). Without the permission, or with location switched
     * off, no point is shown and nothing says otherwise — and it is the "locate
     * me" button, once answered, that starts the following.
     *
     * The subscription stops with the screen, and the position is written
     * nowhere: it lives in the display source, for as long as it is displayed
     * (SPEC §2, C3).
     */
    private fun followUserPosition() {
        if (following?.isActive == true) return
        if (!container.deviceLocation.isPermitted()) return
        following = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.deviceLocation.positions().collect { position ->
                    lastKnownPosition = position
                    userPositionSource?.setGeoJson(UserPositionMarker.featureFor(position))
                }
            }
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
        showSteps(option)
        showShape(option)
    }

    /**
     * Draws the journey's shape, with how long each leg takes and how far it
     * runs.
     *
     * The step list reads one line at a time, and is folded away besides; this
     * is the whole journey seen at once, in the drawing the search screen uses
     * for it.
     */
    private fun showShape(option: JourneyOption) {
        val views = binding ?: return
        views.shape.legs = listOf(
            legOf(option.walkToStation, isRide = false),
            legOf(option.ride, isRide = true),
            legOf(option.walkToDestination, isRide = false),
        )
    }

    private fun legOf(leg: RouteLeg, isRide: Boolean) = JourneyShapeView.Leg(
        isRide = isRide,
        duration = requireContext().formatDuration(leg.duration),
        distance = distanceOf(leg),
    )

    private fun distanceOf(leg: RouteLeg): String =
        requireContext().formatDistance(leg.distanceMetres.toDouble())

    /**
     * Shows the walk, or says what is missing when there is not even one.
     *
     * SPEC §6 requires it: when no nearby station has a bike, that has to be
     * said, not an impossible journey proposed. The walk that replaces a bike
     * journey the walk itself beats arrives here too, and reads as the journey
     * it is — the reason only chooses how the summary phrases it.
     */
    private fun showWithoutJourney(state: JourneyUiState) {
        val views = binding ?: return
        views.steps.removeAllViews()

        val plan = state.plan
        val walk = (plan as? JourneyPlan.WalkOnly)?.directWalk
        views.totalTime.text = walk
            ?.let { requireContext().formatDuration(it.duration) }
            ?: getString(R.string.journey_none_title)
        views.summary.text = when {
            !state.hasStations -> getString(R.string.journey_no_stations)
            walk != null -> walkSummaryOf(plan, walk)
            else -> reasonOf(plan)
        }
        // One dotted stroke between two ends: the journey there is, with no
        // station on the way.
        views.shape.legs = walk
            ?.let { listOf(legOf(it, isRide = false)) }
            .orEmpty()
        if (walk != null) {
            addStep(R.drawable.ic_walk, getString(R.string.journey_step_walk_all), null, walk)
        }
        showStepsOrNot()
    }

    /**
     * How the walk's summary reads: as the better of the two, or as the only
     * one left.
     *
     * Naming what was missing is left to the cases where a walk arrives without
     * a bike journey at all. When the walk simply won, the reason is the whole
     * summary: it got there sooner.
     */
    private fun walkSummaryOf(plan: JourneyPlan?, walk: RouteLeg): String = getString(
        if ((plan as? JourneyPlan.WalkOnly)?.reason == NoBikeJourney.WalkingIsQuicker) {
            R.string.journey_walk_is_quicker
        } else {
            R.string.journey_walk_only
        },
        distanceOf(walk),
    )

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
                // A walk that beat the bike never reaches here: it comes with a
                // route, and its summary is the one above.
                NoBikeJourney.WalkingIsQuicker, null -> R.string.journey_no_route
            },
        )
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
        showStepsOrNot()
    }

    /**
     * Shows the steps, or the invitation to open them.
     *
     * The button says what the press will do, not what state the list is in: a
     * label reading "details" while the details are already there would be a
     * button describing itself. And it disappears altogether when there is no
     * step to open — an impossible journey has nothing folded behind it.
     */
    private fun showStepsOrNot() {
        val views = binding ?: return
        val hasSteps = views.steps.isNotEmpty()
        views.stepsToggle.isVisible = hasSteps
        views.steps.isVisible = hasSteps && stepsShown
        views.stepsToggle.setText(
            if (stepsShown) R.string.journey_steps_hide else R.string.journey_steps_show,
        )
        views.stepsToggle.setIconResource(
            if (stepsShown) R.drawable.ic_minus else R.drawable.ic_plus,
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

    /** Draws the chosen option and frames the map on it. */
    private fun drawJourney(state: JourneyUiState) {
        if (!styleLoaded) return
        val option = state.chosen
        val walk = walkSource ?: return
        val ride = rideSource ?: return

        // The two ends are drawn even when no journey was composed: they say
        // what was asked for, which is worth showing when the answer is empty.
        markerSource?.setGeoJson(
            JourneyMarkers.featuresFor(origin.position, destination.position, option),
        )

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
        if (points.size < 2) return
        frame = LatLngBounds.Builder().includes(points).build()
        applyFrame()
    }

    /**
     * Brings the camera onto the track, for the map as it stands right now.
     *
     * The detail underneath is as tall as the journey it describes, and it is
     * absent while the journey is being worked out: the map fills the screen
     * then, and shrinks by half when the answer arrives. A framing computed
     * before that layout fits the track into a viewport that no longer exists —
     * one or two zoom steps too close, which cuts a journey running north-south
     * while leaving a wide one apparently right. Hence the framing is kept and
     * laid again at the map's real size, whenever that size changes.
     */
    private fun applyFrame() {
        val views = binding ?: return
        val map = mapLibreMap ?: return
        val bounds = frame ?: return
        // The camera's widest zoom depends on the shape of the map view, and
        // this one halves in height when the answer arrives: measured while the
        // map still filled the screen, the limit in force is tighter than the
        // one this framing is entitled to. It is worth the recomputation — but
        // it does not buy a journey right across the conurbation its margins.
        // Nothing here can: the base map holds no tile below zoom 10, so on a
        // phone the widest possible view spans some twenty kilometres and a
        // thirty-kilometre track cannot be shown whole. That ceiling is in the
        // data, not in the camera.
        servedArea?.remeasure()
        // The band of attribution lies over the map: the track is framed above
        // it, not underneath.
        val band = views.attribution.height
        val width = views.map.width
        val height = views.map.height - band
        if (width <= 0 || height <= 0) return

        // A margin wide enough for the markers, which are drawn centred on
        // their point and would otherwise be cut in half at the edge. MapLibre
        // counts it in screen pixels and divides by the density itself, so it
        // is stated in dp: the same margin on every screen. And it never eats
        // more than a quarter of the map — on a short one it gives way rather
        // than leaving the framing nothing to work with.
        val margin = (FRAME_PADDING_DP * resources.displayMetrics.density).toInt()
            .coerceAtMost(minOf(width, height) / 4)
        map.moveCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, margin, margin, margin, margin + band),
        )
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
        // The two ends as they now stand, which is not always how the screen
        // was opened. They go no further than this bundle (SPEC §8).
        origin.writeTo(outState, STATE_ORIGIN)
        destination.writeTo(outState, STATE_DESTINATION)
        picker.writeTo(outState)
        outState.putBoolean(STATE_STEPS_SHOWN, stepsShown)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding?.map?.onLowMemory()
    }

    override fun onDestroyView() {
        binding?.map?.onDestroy()
        // The subscription dies with the view's scope; the reference must go
        // with it, or the next view would believe the point already followed.
        following = null
        walkSource = null
        rideSource = null
        markerSource = null
        userPositionSource = null
        servedArea = null
        mapLibreMap = null
        styleLoaded = false
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARGUMENT_ORIGIN = "origin"
        private const val ARGUMENT_DESTINATION = "destination"
        private const val STATE_ORIGIN = "state-origin"
        private const val STATE_DESTINATION = "state-destination"
        private const val STATE_STEPS_SHOWN = "state-steps-shown"

        /** The margin around the track, in dp, so it does not touch the edges. */
        private const val FRAME_PADDING_DP = 32

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
