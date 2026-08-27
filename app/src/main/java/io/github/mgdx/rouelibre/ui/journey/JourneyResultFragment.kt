package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.AppContainer
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.core.journey.JourneyMinutes
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.journey.inShownMinutes
import io.github.mgdx.rouelibre.core.journey.shownMinutes
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentJourneyResultBinding
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration
import io.github.mgdx.rouelibre.ui.formatMinutes
import io.github.mgdx.rouelibre.ui.map.MapStyleLoader
import io.github.mgdx.rouelibre.ui.map.ServedAreaCamera
import io.github.mgdx.rouelibre.ui.map.UserPositionDisplay
import io.github.mgdx.rouelibre.ui.map.UserPositionMarker
import io.github.mgdx.rouelibre.ui.toUserMessage
import io.github.mgdx.rouelibre.ui.withBikeFleet
import io.github.mgdx.rouelibre.ui.withFleet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
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

    /** Draws the user's point, gliding it between fixes — see [UserPositionDisplay]. */
    private var userPosition: UserPositionDisplay? = null
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
     * framing can stand it down for the length of its own move and have it
     * measured again on the map as it then stands.
     */
    private var servedArea: ServedAreaCamera? = null

    /**
     * The city this map is drawn for, once it has been read from disk.
     *
     * Kept for the same reason as on the main screen: "locate me" has to know
     * where the served area stops, since the camera may not leave it.
     */
    private var servedCity: CityConfiguration? = null

    /**
     * The last fix shown, held for the life of the screen only.
     *
     * It survives a rebuild of the view — a rotation must not make the point,
     * nor the circle around it, vanish until the next fix — and nothing else:
     * it is written nowhere (SPEC §2, C3).
     */
    private var lastKnownPosition: PositionFix? = null

    /** The subscription that moves the point, while the screen is displayed. */
    private var following: Job? = null

    /**
     * Whether the network served lends pedal-assist bikes (SPEC §15).
     *
     * Read once, kept here because the style can load before or after the
     * answer: whichever comes second registers the station marker.
     */
    private var fleet = BikeFleet.Mechanical

    /**
     * What that network lends, down to the vehicle types it counts by.
     *
     * Kept beside [fleet] because the summary needs more than the bike drawn:
     * it says how many of the bikes waiting at the departure station are
     * electric, and reading that takes the identifier table (SPEC §7.4).
     */
    private var lentFleet: FleetDescription? = null

    /**
     * What the rider said their **own** bike is, or `null` if they have not
     * (SPEC §7.6).
     *
     * Kept here for the same reason as [fleet]: the style can load before or
     * after the answer, and whichever comes second registers the endpoint
     * marker. It is never read off [fleet] and never stands in for it — one
     * says what the network lends, the other what the rider owns.
     */
    private var ownBikeKind: OwnBikeKind = OwnBikeKind.Mechanical

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

    /**
     * Whether the journey is ridden on the user's own bike (SPEC §7.3).
     *
     * Asked for on the search screen and not changed here: correcting a point
     * is correcting the question, where changing bike is asking another one.
     * It survives a rotation with the two ends, so the journey worked out again
     * is the one that was on screen.
     */
    private var usesOwnBike = false

    /**
     * The kind of bike asked for on the search screen, or `null` for none
     * (SPEC §7.3).
     *
     * It travels here so that a rotation, or a process killed and restored, asks
     * the same question again — a journey worked out for an electric bike must
     * not come back as a journey worked out for any. It decides which stations
     * may be departed from and nothing else: what this screen and its detail
     * **show** of those stations is both counts, whatever was asked for
     * (SPEC §7.2, §7.4).
     */
    private var wantedBikeKind: WantedBikeKind? = null

    private val picker = JourneyEndpointPicker(
        fragment = this,
        onMessage = ::showMessage,
        onPicked = ::acceptEndpoint,
        onLocating = ::showLocating,
    )

    /** Hands a leg of the journey to a navigation application (SPEC §7.4). */
    private val handover = JourneyHandover(fragment = this, onMessage = ::showMessage)

    /**
     * Where the journey shown is left for the detail screen to read.
     *
     * Scoped to the activity, so it outlives this screen for exactly as long as
     * the detail screen sits on top of it, and no longer (SPEC §8).
     */
    private val shownJourney: ShownJourneyViewModel by activityViewModels()

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
            // Written down for good: a refusal pronounced here must also keep
            // the map from asking unprompted when it opens next (SPEC §10).
            viewLifecycleOwner.lifecycleScope.launch {
                container.automaticLocationRequest.noteRefused()
            }
            showMessage(getString(R.string.map_location_denied))
        }
    }

    private val viewModel: JourneyViewModel by viewModels {
        JourneyViewModel.Factory(
            router = container.journeyRouter,
            repository = container.stationRepository,
            origin = origin.position,
            destination = destination.position,
            usesOwnBike = usesOwnBike,
            wantedBikeKind = wantedBikeKind,
            fleet = container.fleetRepository.fleet,
            walkingPace = container.preferences.walkingPace,
            ownBikeKind = container.preferences.ownBikeKind,
            coveredArea = container.coveredArea(),
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
        usesOwnBike = savedInstanceState?.getBoolean(STATE_OWN_BIKE)
            ?: arguments?.getBoolean(ARGUMENT_OWN_BIKE) == true
        wantedBikeKind = WantedBikeKind.ofWireName(
            savedInstanceState?.getString(STATE_WANTED_BIKE_KIND)
                ?: arguments?.getString(ARGUMENT_WANTED_BIKE_KIND),
        )
        picker.readFrom(savedInstanceState)
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
        // Nothing is being weighed on one's own bike: there is one route to
        // trace, and the wait must not claim to be looking for the best of a
        // choice that does not exist.
        if (usesOwnBike) views.computingLabel.setText(R.string.journey_computing_own_bike)
        views.detail.summaryBlock.setOnClickListener { openDetail() }
        // What the press does, rather than the bare "activate" a screen reader
        // would otherwise announce over a block of four figures.
        ViewCompat.replaceAccessibilityAction(
            views.detail.summaryBlock,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.journey_detail_open),
            null,
        )
        views.detail.navigate.setOnClickListener { offerNavigation() }
        // The menu that press puts up survives a rotation, and its answer has
        // to find a listener waiting when it does.
        handover.listenForTheChosenLeg()
        views.origin.setOnClickListener { picker.choose(true, destination.position) }
        views.destination.setOnClickListener { picker.choose(false, origin.position) }
        views.swap.setOnClickListener { swapEndpoints() }
        views.locateMe.setOnClickListener { onLocateMeClicked() }
        // The way back from wherever the map has been taken to. Animated, unlike
        // the framing the screen lays by itself: this one answers a press, and
        // the move is what says the press was heard.
        views.frameJourney.setOnClickListener { applyFrame(animated = true) }

        views.map.onCreate(savedInstanceState)
        views.map.getMapAsync(::onMapReady)
        // The map changes height as the detail below it grows: the journey is
        // framed again each time, or it would stay fitted to a viewport it no
        // longer has.
        views.map.addOnLayoutChangeListener {
                _,
                left,
                top,
                right,
                bottom,
                oldLeft,
                oldTop,
                oldRight,
                oldBottom,
            ->
            val changed = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (changed) applyFrame()
        }

        picker.listen(viewLifecycleOwner)
        showEndpoints()
        followUserPosition()
        readBikeFleet()
        readLentFleet()
        readOwnBikeKind()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::show)
            }
        }
    }

    /**
     * Puts the bike of the network served on the three drawings of this screen.
     *
     * The bike being waited for, the two discs of the shape and the stations on
     * the map all carry a bolt where the network lends pedal-assist bikes
     * (SPEC §15). The configuration is read from disk, so the answer arrives
     * after the screen: the plain bike is drawn until then, and the style —
     * which may not have loaded yet — takes its image whenever it is ready.
     */
    private fun readBikeFleet() {
        withBikeFleet { lent ->
            fleet = lent
            val views = binding ?: return@withBikeFleet
            views.detail.shape.fleet = lent
            views.computingBike.fleet = lent
            if (styleLoaded) {
                mapLibreMap?.style?.let {
                    JourneyMarkers.registerImages(requireContext(), it, lent, ownBikeKind)
                }
            }
        }
    }

    /**
     * Follows what the network lends, for the summary alone.
     *
     * Only the summary is written again when a reading lands, never the whole
     * screen: redrawing the journey would frame the map on it, and take the
     * user back from wherever they had panned it to.
     */
    private fun readLentFleet() {
        withFleet { lent ->
            lentFleet = lent
            viewModel.state.value.chosen?.let(::showSummary)
        }
    }

    /**
     * Puts the rider's own bike on the drawing and in the sentence
     * (SPEC §7.3, §7.6).
     *
     * A reading of its own, and not a corner of [readBikeFleet]: that one says
     * what the **network** lends, this one what the rider says they own, and
     * neither may be read off the other. It reaches the two ends of the shape,
     * the two ends on the map, and the sentence under the total — nothing else,
     * and no minute anywhere: the ride was traced over the same graph with the
     * same profile whatever kind was declared (SPEC §6).
     *
     * The answer comes off disk, so it lands after the screen: the plain bike is
     * drawn until then, which is what an undeclared bike takes anyway. Only the
     * sentence is rewritten, never the whole screen — redrawing the journey
     * would frame the map again and take the user back from wherever they had
     * panned it, exactly as [readLentFleet] guards against.
     */
    private fun readOwnBikeKind() {
        withOwnBikeKind { declared ->
            ownBikeKind = declared
            val views = binding ?: return@withOwnBikeKind
            views.detail.shape.ownBikeKind = declared
            if (styleLoaded) {
                mapLibreMap?.style?.let {
                    JourneyMarkers.registerImages(requireContext(), it, fleet, declared)
                }
            }
            (viewModel.state.value.plan as? JourneyPlan.OwnBike)?.let {
                views.detail.summary.text = requireContext().ownBikeSummary(it.ride, declared)
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
            JourneyMarkers.registerImages(requireContext(), style, fleet, ownBikeKind)
            style.addLayer(JourneyMarkers.layer())

            // The user's position comes last of all, and therefore on top:
            // where one IS beats what one has planned, and the two coincide
            // often enough at the start of a journey for the order to matter.
            // Its circle of uncertainty goes just under it, above the tracks:
            // it is a doubt about the point, not about the journey.
            val userAccuracy = GeoJsonSource(UserPositionMarker.ACCURACY_SOURCE_ID)
            style.addSource(userAccuracy)
            style.addLayer(UserPositionMarker.accuracyLayer(requireContext()))

            val userPoint = GeoJsonSource(UserPositionMarker.SOURCE_ID)
            style.addSource(userPoint)
            style.addLayer(UserPositionMarker.layer(requireContext()))
            userPosition = UserPositionDisplay(userPoint, userAccuracy)
            showPosition(lastKnownPosition)

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
            servedCity = city
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
            val fix = try {
                container.deviceLocation.current()
            } finally {
                binding?.locateMe?.isEnabled = true
            }
            if (fix == null) {
                showMessage(getString(R.string.map_location_unavailable))
                return@launch
            }
            val position = fix.coordinates
            lastKnownPosition = fix
            showPosition(fix)
            // Outside the served area the camera cannot follow — it is penned
            // inside the city's box — and it would settle at the edge nearest
            // the user, passing that off as where they stand. This screen shows
            // one journey in one city: saying so is all it can usefully do,
            // changing city here throwing that journey away.
            val outside = servedCity?.boundingBox
                ?.takeIf { it.isUsable }
                ?.let { position !in it } == true
            if (outside) {
                val city = checkNotNull(servedCity).network
                showMessage(
                    getString(
                        R.string.map_outside_city_brief,
                        requireContext().cityLabel(city.displayName, city.city),
                    ),
                )
                return@launch
            }
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
                container.deviceLocation.positions().collect { fix ->
                    lastKnownPosition = fix
                    showPosition(fix)
                }
            }
        }
    }

    /**
     * Draws the point and, when the fix is a coarse one, the circle around it.
     *
     * Point and circle move together, and they glide rather than jump — see
     * [UserPositionDisplay] (SPEC §7.4).
     */
    private fun showPosition(fix: PositionFix?) {
        userPosition?.show(fix)
    }

    private fun show(state: JourneyUiState) {
        val views = binding ?: return
        views.computing.isVisible = state.isComputing
        views.detail.root.isVisible = !state.isComputing
        if (state.isComputing) return

        drawJourney(state)
        showWaysOn(state)
        val option = state.chosen
        if (option == null) {
            showJourneyWithoutStations(state)
            return
        }

        // Rounded once for the three figures the card carries: the total, the
        // sentence beside it and the band underneath describe the same journey,
        // and each rounding its own legs made them disagree by a minute or two
        // (see JourneyMinutes).
        val minutes = option.shownMinutes()
        showTotal(requireContext().formatMinutes(minutes.total))
        showSummary(option)
        showShape(option, minutes)
    }

    /**
     * Writes what stands where the total time goes.
     *
     * **A figure is one line, whatever digits the locale counts it in.** On a
     * device set to `ar` the total read "٦ min" — an Arabic-Indic six, then a
     * Latin word, two runs of opposite direction — and broke in two inside a
     * view measured at 143 px, which is the width that very text asks for: the
     * line's runs need a hair more than the width the measurement had promised,
     * and the wrap follows. The block then stood twice as tall, with the
     * chevron — centred on this view — beside the middle of the summary rather
     * than beside the figure, and the map lost the height. One line is also
     * what a figure is: it is read at a glance, and a duration split across two
     * lines is not read at all.
     *
     * A sentence is another matter and may take the lines it needs: "No
     * journey" is the one thing this view holds that is not a figure, and at
     * the largest text sizes it needs them.
     *
     * `setSingleLine` rather than `maxLines`, and the difference is the whole
     * repair: a maximum of one line still breaks the text and then shows the
     * first line alone — "٦" without its unit — where a single line is laid out
     * as one run and kept whole.
     *
     * @param figure true when the view is being handed a duration.
     */
    private fun showTotal(text: CharSequence, figure: Boolean = true) {
        val views = binding ?: return
        views.detail.totalTime.setSingleLine(figure)
        views.detail.totalTime.text = text
    }

    /**
     * What makes the total up, and what waits at the departure station.
     *
     * The two kinds are counted apart where the city lends both: it is what
     * decides whether the walk to the station is the one wanted (SPEC §7.4),
     * and the counts are the frozen ones the journey was worked out on, like
     * the total time beside them.
     */
    private fun showSummary(option: JourneyOption) {
        val views = binding ?: return
        views.detail.summary.text = requireContext().journeySummary(
            option,
            minutes = option.shownMinutes(),
            atDeparture = requireContext()
                .bikesAtDeparture(option.bikeSplitAtDeparture(lentFleet)),
        )
    }

    /**
     * The two ways on the screen offers, shown only when there is a journey.
     *
     * The summary block opens the journey in full, and the button hands a leg
     * of it to a navigation application. An impossible journey has neither a
     * detail to open nor a point to hand over: the chevron and the button go,
     * and the block stops answering to a press rather than opening an empty
     * screen.
     */
    private fun showWaysOn(state: JourneyUiState) {
        val views = binding ?: return
        val hasJourney = state.plan is JourneyPlan.Found ||
            state.plan is JourneyPlan.WalkOnly ||
            state.plan is JourneyPlan.OwnBike
        views.detail.summaryBlock.isClickable = hasJourney
        views.detail.summaryBlock.isFocusable = hasJourney
        views.detail.detailChevron.isVisible = hasJourney
        views.detail.navigate.isVisible = hasJourney
    }

    /**
     * Opens the journey in full (SPEC §7.4).
     *
     * The journey is left where the detail screen reads it, rather than written
     * into an argument: it carries the two tracks point by point, and a
     * `Bundle` is not the place for them (see [ShownJourneyViewModel]).
     */
    private fun openDetail() {
        val journey = journeyToShow() ?: return
        shownJourney.journey = journey
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, JourneyDetailFragment())
            .addToBackStack(null)
            .commit()
    }

    /** Offers the journey's legs to a navigation application (SPEC §7.4). */
    private fun offerNavigation() {
        val journey = journeyToShow() ?: return
        handover.offer(journey)
    }

    private fun journeyToShow(): ShownJourney? = viewModel.state.value.plan
        ?.let { ShownJourney(origin, destination, it) }

    /**
     * Draws the journey's shape, with how long each leg takes and how far it
     * runs.
     *
     * The step list reads one line at a time, and is folded away besides; this
     * is the whole journey seen at once, in the drawing the search screen uses
     * for it.
     */
    private fun showShape(option: JourneyOption, minutes: JourneyMinutes) {
        val views = binding ?: return
        views.detail.shape.legs = listOf(
            legOf(option.walkToStation, minutes.walkToStation, isRide = false),
            legOf(option.ride, minutes.ride, isRide = true),
            legOf(option.walkToDestination, minutes.walkToDestination, isRide = false),
        )
    }

    private fun legOf(leg: RouteLeg, minutes: Int, isRide: Boolean) = JourneyShapeView.Leg(
        isRide = isRide,
        duration = requireContext().formatMinutes(minutes),
        distance = distanceOf(leg),
    )

    private fun distanceOf(leg: RouteLeg): String =
        requireContext().formatDistance(leg.distanceMetres.toDouble())

    /**
     * Shows a journey with no station in it, or says what is missing.
     *
     * Two of them run from one end to the other in a single leg: the walk that
     * SPEC §6 requires when no nearby station has a bike — or when walking is
     * quicker than fetching one — and the ride of somebody on their own bike,
     * which never had a station in it to begin with (SPEC §7.3). What is left
     * is a journey that could not be composed at all, which says why.
     */
    private fun showJourneyWithoutStations(state: JourneyUiState) {
        val views = binding ?: return
        val plan = state.plan
        val ownBike = plan as? JourneyPlan.OwnBike
        val walkOnly = plan as? JourneyPlan.WalkOnly
        val soleLeg = ownBike?.ride ?: walkOnly?.directWalk
        val total = soleLeg?.let { requireContext().formatDuration(it.duration) }
        showTotal(total ?: getString(R.string.journey_none_title), figure = total != null)
        views.detail.summary.text = when {
            ownBike != null -> requireContext().ownBikeSummary(ownBike.ride, ownBikeKind)
            !state.hasStations -> getString(R.string.journey_no_stations)
            walkOnly != null -> requireContext().walkSummary(
                walkOnly.directWalk,
                isQuickerThanTheBike = walkOnly.reason == NoBikeJourney.WalkingIsQuicker,
            )
            else -> reasonOf(plan)
        }
        // One stroke between two ends — dotted for the walk, unbroken for the
        // ride: the journey there is, with no station on the way. A single leg
        // is its own total, so there is nothing to apportion here.
        views.detail.shape.legs = soleLeg
            ?.let {
                listOf(legOf(it, it.duration.inShownMinutes(), isRide = ownBike != null))
            }
            .orEmpty()
    }

    /**
     * Why there is no journey, in words.
     *
     * The wording lives with the other user-facing failures (`ErrorMessages`),
     * where SPEC §14 wants it: a plan with no reason at all — which does not
     * happen — falls back on the plainest of them.
     */
    private fun reasonOf(plan: JourneyPlan?): String {
        val reason = when (plan) {
            is JourneyPlan.Impossible -> plan.reason
            is JourneyPlan.WalkOnly -> plan.reason
            else -> null
        }
        return reason?.toUserMessage(requireContext()) ?: getString(R.string.journey_no_route)
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
            JourneyMarkers.featuresFor(
                origin.position,
                destination.position,
                option,
                isRidden = state.plan is JourneyPlan.OwnBike,
            ),
        )

        if (option == null) {
            // A journey with no station runs in one leg, and which of the two
            // sources carries it is the whole difference between them: dotted
            // for the walk, unbroken for the ride on one's own bike.
            val directWalk = (state.plan as? JourneyPlan.WalkOnly)?.directWalk
            val ownRide = (state.plan as? JourneyPlan.OwnBike)?.ride
            walk.setGeoJson(JourneyLines.featuresOf(directWalk))
            ride.setGeoJson(JourneyLines.featuresOf(ownRide))
            frameOn(
                (directWalk ?: ownRide)?.geometry.orEmpty()
                    .map { LatLng(it.latitude, it.longitude) },
            )
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

    /**
     * Frames the map on the whole track, with a comfortable margin.
     *
     * A journey of fewer than two points has no extent to frame — an impossible
     * one, most often. The framing is then dropped rather than kept from the
     * previous answer, and the button offering to come back to it goes with it:
     * there is nothing left to come back to.
     */
    private fun frameOn(points: List<LatLng>) {
        frame = points.takeIf { it.size >= 2 }?.let { LatLngBounds.Builder().includes(it).build() }
        binding?.frameJourney?.isVisible = frame != null
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
     *
     * @param animated true when a press asked for it, so the map travels back
     * rather than jumping: the movement is what tells where one has been taken
     * from. The screen's own re-framings stay abrupt — they follow a layout
     * change nobody asked for, and animating them would draw the eye to it.
     */
    private fun applyFrame(animated: Boolean = false) {
        val views = binding ?: return
        val map = mapLibreMap ?: return
        val bounds = frame ?: return
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

        // The camera limit stands down for the length of the move, and is
        // measured again on arrival: it was laid down for the framing being
        // left, and this screen leaves a very different one — the map fills the
        // screen while the answer is worked out, so the box in force pens the
        // camera as a viewport twice this height requires, and the framing that
        // arrives is clamped short of where it belongs. The zoom floor is
        // brought up to date rather than lifted, so nothing here can uncover
        // the edge of the served area (SPEC §7.1).
        //
        // That floor does not buy a journey right across the conurbation its
        // margins, and nothing here can: the base map holds no tile below zoom
        // 10, so on a phone the widest possible view spans some twenty
        // kilometres and a thirty-kilometre track cannot be shown whole. That
        // ceiling is in the data, not in the camera.
        val limits = servedArea
        limits?.releaseForMove()
        val update = CameraUpdateFactory
            .newLatLngBounds(bounds, margin, margin, margin, margin + band)
        if (!animated) {
            map.moveCamera(update)
            limits?.holdAgain()
            return
        }
        // The limits stay down for the whole flight, not just its start: laying
        // them again mid-move jumps the camera inside the box and kills the
        // animation where it stands. They are taken up on arrival, however the
        // move ends — a gesture during it cancels the animation, and a camera
        // left unpenned is what lets the edge of the served area show.
        map.animateCamera(
            update,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() = limits?.holdAgain() ?: Unit
                override fun onFinish() = limits?.holdAgain() ?: Unit
            },
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
        // The permission may have been granted from the Android settings while
        // the application was away: the following then starts here, the view
        // having been built when there was nothing to follow with. The map
        // screen does the same re-check on its resume (SPEC §7.4, §10).
        followUserPosition()
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
        outState.putBoolean(STATE_OWN_BIKE, usesOwnBike)
        outState.putString(STATE_WANTED_BIKE_KIND, wantedBikeKind?.wireName)
        picker.writeTo(outState)
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
        userPosition?.cancel()
        userPosition = null
        servedArea = null
        mapLibreMap = null
        styleLoaded = false
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARGUMENT_ORIGIN = "origin"
        private const val ARGUMENT_DESTINATION = "destination"
        private const val ARGUMENT_OWN_BIKE = "own-bike"
        private const val ARGUMENT_WANTED_BIKE_KIND = "wanted-bike-kind"
        private const val STATE_ORIGIN = "state-origin"
        private const val STATE_DESTINATION = "state-destination"
        private const val STATE_OWN_BIKE = "state-own-bike"
        private const val STATE_WANTED_BIKE_KIND = "state-wanted-bike-kind"

        /** The margin around the track, in dp, so it does not touch the edges. */
        private const val FRAME_PADDING_DP = 32

        /**
         * Opens the result for a pair of points already designated.
         *
         * @param usesOwnBike true for the journey of somebody riding their own
         *   bike: one ride, no station, and the network left out of it
         *   (SPEC §7.3).
         * @param wantedBikeKind the kind of bike asked for, or `null` for no
         *   kind at all — which is what a point arriving from another
         *   application asks for, nobody having chosen anything on its behalf.
         */
        fun newInstance(
            origin: JourneyEndpoint,
            destination: JourneyEndpoint,
            usesOwnBike: Boolean = false,
            wantedBikeKind: WantedBikeKind? = null,
        ): JourneyResultFragment = JourneyResultFragment().apply {
            arguments = Bundle().apply {
                origin.writeTo(this, ARGUMENT_ORIGIN)
                destination.writeTo(this, ARGUMENT_DESTINATION)
                putBoolean(ARGUMENT_OWN_BIKE, usesOwnBike)
                putString(ARGUMENT_WANTED_BIKE_KIND, wantedBikeKind?.wireName)
            }
        }
    }
}

/**
 * The box the city in service had its data cut from, as the journey screens
 * read it (SPEC §4).
 *
 * Written once for the two screens that compose a journey, so both refuse the
 * same points: a check spelt out twice is a check that drifts apart.
 *
 * **It follows the city rather than the screen.** Reading the box when the
 * screen opened would have pinned it to whichever conurbation was in service at
 * that moment, and a journey worked out after a change of city would then be
 * measured against the box of the one left behind. Hung off
 * [io.github.mgdx.rouelibre.data.AppPreferences.activeCityIdFlow], it is read
 * afresh at each computation and each change publishes a new one.
 *
 * `null` where no city is chosen or none carries a box, which the algorithm
 * reads as covering everything: not knowing what was downloaded is no ground to
 * refuse a point.
 */
internal fun AppContainer.coveredArea(): Flow<BoundingBox?> =
    preferences.activeCityIdFlow.map { activeCity()?.boundingBox }
