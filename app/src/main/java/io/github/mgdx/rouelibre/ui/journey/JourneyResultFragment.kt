package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
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
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.databinding.FragmentJourneyResultBinding
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.map.MapAttitudeController
import io.github.mgdx.rouelibre.ui.map.MapStyleLoader
import io.github.mgdx.rouelibre.ui.map.UserPositionDisplay
import io.github.mgdx.rouelibre.ui.map.UserPositionMarker
import io.github.mgdx.rouelibre.ui.map.UserPositionOnMap
import io.github.mgdx.rouelibre.ui.map.liesOutside
import io.github.mgdx.rouelibre.ui.withBikeFleet
import io.github.mgdx.rouelibre.ui.withFleet
import io.github.mgdx.rouelibre.ui.withFreeDocksShown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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

    /** Draws the user's point, gliding it between fixes — see [UserPositionDisplay]. */
    private var userPositionDisplay: UserPositionDisplay? = null
    private var styleLoaded = false

    /**
     * The city this map is drawn for, once it has been read from disk.
     *
     * Kept for the same reason as on the main screen: "locate me" has to know
     * where the served area stops, since the camera may not leave it.
     */
    private var servedCity: CityConfiguration? = null

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

    /**
     * Whether the network's free docks are worth showing (SPEC §7.1), `true`
     * until the stations say otherwise.
     */
    private var freeDocksShown = true

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /** The journey drawn on the map, and the camera framed on it. */
    private val track = JourneyTrackOnMap(
        binding = { binding },
        map = { mapLibreMap },
    )

    /** The card under the map: how long, what makes it up, and its shape. */
    private val summary = JourneySummaryCard(
        detail = { binding?.detail },
        lentFleet = { lentFleet },
        ownBikeKind = { ownBikeKind },
        freeDocksShown = { freeDocksShown },
    )

    /**
     * The turn and the tilt of the map, and the compass that gives both back —
     * the main map's own, so that the two answer a hand the same way.
     */
    private val attitude = MapAttitudeController(
        button = { binding?.faceNorth },
        map = { mapLibreMap },
    )

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

    /**
     * The bike outside the stations this journey sets off from, or `null`
     * (SPEC §7.2.1).
     *
     * It travels with the two ends and for the same reason: a rotation, or a
     * process killed and restored, must ask the same question again — a journey
     * worked out from a bike must not come back as a journey worked out from
     * the point it stood on.
     */
    private var streetBike: StreetBikeHandle? = null

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
     * The user's point, and the permission it takes — asked for from the
     * "locate me" button alone: opening this screen asks for nothing
     * (SPEC §10), and a refusal leaves the journey and its map whole.
     */
    private val userPosition = UserPositionOnMap(
        fragment = this,
        display = { userPositionDisplay },
        locate = ::locateMe,
        onMessage = { showMessage(getString(it)) },
    )

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
            streetBike = streetBike,
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
        streetBike = StreetBikeHandle.readFrom(savedInstanceState, STATE_STREET_BIKE)
            ?: StreetBikeHandle.readFrom(arguments, ARGUMENT_STREET_BIKE)
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
        lockTheOriginOnTheBike()
        views.locateMe.setOnClickListener { userPosition.onLocateMeClicked() }
        // The way back from wherever the map has been taken to. Animated, unlike
        // the framing the screen lays by itself: this one answers a press, and
        // the move is what says the press was heard.
        views.frameJourney.setOnClickListener { track.applyFrame(animated = true) }
        attitude.bind()

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
            if (changed) track.applyFrame()
        }

        picker.listen(viewLifecycleOwner)
        showEndpoints()
        userPosition.follow()
        readBikeFleet()
        readLentFleet()
        readOwnBikeKind()
        readFreeDocksShown()

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
            viewModel.state.value.chosen?.let(summary::showSummary)
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
            summary.showOwnBikeSentence(viewModel.state.value.plan)
        }
    }

    /**
     * Follows whether the network's free docks are worth showing (SPEC §7.1),
     * for the one sentence on this screen that could name them: why no
     * journey could end near the destination.
     *
     * Only a journey with no station is written again, for the reason
     * [readLentFleet] gives: that sentence is all there is to rewrite, and the
     * card holding it frames nothing.
     */
    private fun readFreeDocksShown() {
        withFreeDocksShown { shown ->
            freeDocksShown = shown
            val state = viewModel.state.value
            if (!state.isComputing && state.chosen == null) summary.show(state)
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
     * Holds the origin on the bike the journey was asked from (SPEC §7.3).
     *
     * The same rule as on the search screen, and it has to be the same: this
     * screen offers to correct a point without going back, and correcting this
     * one would be asking an ordinary journey, which never departs from a
     * street bike (SPEC §6). The swap button goes with the editing, there being
     * nothing to exchange when one of the two ends cannot move — and a bike is
     * the one kind of point one only ever leaves from (SPEC §7.2.1).
     *
     * The row stays readable and stops answering, rather than being disabled: a
     * screen reader still meets it, and the sentence it carries says why.
     */
    private fun lockTheOriginOnTheBike() {
        if (streetBike == null) return
        val views = binding ?: return
        views.origin.setOnClickListener(null)
        views.origin.isClickable = false
        views.origin.contentDescription = getString(
            R.string.address_detail,
            getString(R.string.journey_departure_street_bike),
            getString(R.string.journey_origin_street_bike_locked),
        )
        views.swap.isVisible = false
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
        // Turned and tilted by hand, exactly as the main map is (SPEC §7.1,
        // §7.4): a map one learns to handle on one screen must answer the same
        // way on the other, and the ceiling on the tilt is the same figure for
        // the same reason.
        attitude.configure(map)
        attitude.follow(map)
        limitCamera(map, hasTiles = tiles != null)
        // Without a base map the track is still drawn on an empty background:
        // less telling, but the route is computed and the detail reads.
        if (tiles == null) return

        map.setStyle(
            Style.Builder().fromJson(MapStyleLoader.load(requireContext(), tiles)),
        ) { style ->
            track.addTo(style, requireContext(), fleet, ownBikeKind)

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
            // The direction cone goes under the disc, as on the main screen.
            UserPositionMarker.registerImage(requireContext(), style)
            style.addLayer(UserPositionMarker.bearingLayer())
            style.addLayer(UserPositionMarker.layer(requireContext()))
            userPositionDisplay = UserPositionDisplay(userPoint, userAccuracy)
            userPosition.showLastKnown()

            styleLoaded = true
            track.draw(viewModel.state.value, origin.position, destination.position)
        }
    }

    /**
     * Bounds where the camera may go, once the city is read from disk — see
     * [JourneyTrackOnMap.limitTo].
     */
    private fun limitCamera(map: MapLibreMap, hasTiles: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val city = container.activeCity() ?: return@launch
            servedCity = city
            track.limitTo(map, city, hasTiles)
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
            userPosition.show(fix)
            // Outside the served area the camera cannot follow — it is penned
            // inside the city's box — and it would settle at the edge nearest
            // the user, passing that off as where they stand. This screen shows
            // one journey in one city: saying so is all it can usefully do,
            // changing city here throwing that journey away.
            if (liesOutside(position, servedCity?.boundingBox)) {
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

    private fun show(state: JourneyUiState) {
        val views = binding ?: return
        views.computing.isVisible = state.isComputing
        views.detail.root.isVisible = !state.isComputing
        if (state.isComputing) return

        track.draw(state, origin.position, destination.position)
        summary.show(state)
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

    // ---------------------------------------------------- map lifecycle --

    override fun onStart() {
        super.onStart()
        binding?.map?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding?.map?.onResume()
        // The permission may have been granted from the Android settings while
        // the application was away: the following then starts on this answer,
        // exactly as on the map screen's own resume (SPEC §7.4, §10).
        userPosition.readPermission()
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
        streetBike?.writeTo(outState, STATE_STREET_BIKE)
        picker.writeTo(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding?.map?.onLowMemory()
    }

    override fun onDestroyView() {
        attitude.stopFollowing()
        binding?.map?.onDestroy()
        track.detach()
        userPositionDisplay?.cancel()
        userPositionDisplay = null
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
        private const val ARGUMENT_STREET_BIKE = "street-bike"
        private const val STATE_ORIGIN = "state-origin"
        private const val STATE_DESTINATION = "state-destination"
        private const val STATE_OWN_BIKE = "state-own-bike"
        private const val STATE_WANTED_BIKE_KIND = "state-wanted-bike-kind"
        private const val STATE_STREET_BIKE = "state-street-bike"

        /**
         * Opens the result for a pair of points already designated.
         *
         * @param usesOwnBike true for the journey of somebody riding their own
         *   bike: one ride, no station, and the network left out of it
         *   (SPEC §7.3).
         * @param wantedBikeKind the kind of bike asked for, or `null` for no
         *   kind at all — which is what a point arriving from another
         *   application asks for, nobody having chosen anything on its behalf.
         * @param streetBike the bike outside the stations the journey sets off
         *   from (SPEC §7.2.1). It settles the question on its own: the two
         *   arguments above are then what they are at rest, and neither is
         *   read.
         */
        fun newInstance(
            origin: JourneyEndpoint,
            destination: JourneyEndpoint,
            usesOwnBike: Boolean = false,
            wantedBikeKind: WantedBikeKind? = null,
            streetBike: StreetBikeHandle? = null,
        ): JourneyResultFragment = JourneyResultFragment().apply {
            arguments = Bundle().apply {
                origin.writeTo(this, ARGUMENT_ORIGIN)
                destination.writeTo(this, ARGUMENT_DESTINATION)
                putBoolean(ARGUMENT_OWN_BIKE, usesOwnBike)
                putString(ARGUMENT_WANTED_BIKE_KIND, wantedBikeKind?.wireName)
                streetBike?.writeTo(this, ARGUMENT_STREET_BIKE)
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
