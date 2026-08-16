package io.github.mgdx.rouelibre.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentMapBinding
import io.github.mgdx.rouelibre.ui.BikeGlyphs
import io.github.mgdx.rouelibre.ui.address.AddressSearchFragment
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.prefersReducedMotion
import io.github.mgdx.rouelibre.ui.settings.SettingsFragment
import io.github.mgdx.rouelibre.ui.stations.StationDetailSheet
import io.github.mgdx.rouelibre.ui.stations.StationListFragment
import io.github.mgdx.rouelibre.ui.stations.StationsViewModel
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import io.github.mgdx.rouelibre.ui.withBikeFleet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.Instant

/**
 * The station map — the main screen (SPEC §7.1).
 *
 * The base map is read from an MBTiles file installed on the device and the
 * glyphs are in the APK: showing, panning or zooming the map sends out no
 * request at all. Only availability comes from the network.
 *
 * This screen assumes the base map is installed. Checking that falls to the
 * caller — without it there is nothing to show, and the station list remains
 * the degraded mode SPEC §4.4 provides for.
 */
class MapFragment : Fragment() {

    private var binding: FragmentMapBinding? = null
    private var mapLibreMap: MapLibreMap? = null
    private var stationSource: GeoJsonSource? = null
    private var pickedPlaceSource: GeoJsonSource? = null
    private var styleLoaded = false

    /**
     * The address found by the search, until it is cleared.
     *
     * A designated point, never a history: SPEC §8 forbids keeping a
     * destination, and this one does not outlive the screen.
     */
    private var pickedPlace: PickedPlace? = null

    /**
     * The framing to restore when the view is rebuilt.
     *
     * Going through the list, the storage screen or the search destroys the
     * map's view without destroying the fragment: without this, coming back
     * brought the opening framing, and the user lost the place they were
     * looking at.
     *
     * A turn of the phone destroys the fragment as well, and this field with
     * it: it is therefore saved in the state too, or the map came back up on
     * the position of the user rather than on the address they had just gone
     * looking for.
     */
    private var lastCamera: CameraPosition? = null

    /**
     * A point the camera must reach as soon as the map exists.
     *
     * The chosen address is returned by the search screen **before** the map is
     * rebuilt: the move therefore has to wait.
     */
    private var pendingCameraTarget: LatLng? = null

    /** A point found by the address search, and its label. */
    private data class PickedPlace(val position: LatLng, val label: String)

    /**
     * The limits that pen the camera inside the served area.
     *
     * Kept to hand because a move the screen orders — an address found, a
     * cluster opened — has to stand them down while it runs, or they cut it
     * short (see [moveCameraTo]).
     */
    private var servedAreaCamera: ServedAreaCamera? = null

    private var userPositionSource: GeoJsonSource? = null

    /**
     * The city this map is drawn for, as soon as it is known.
     *
     * Kept to hand because "locate me" has to know where the served area stops:
     * the camera is penned inside that area (see [ServedAreaCamera]), so a
     * position outside it would move the map to the nearest edge and pass that
     * off as where the user stands.
     */
    private var servedCity: CityConfiguration? = null

    /**
     * The last position shown, held in memory for the session only.
     *
     * It survives a rebuild of the view — going through the list and coming
     * back must not make the point vanish — and nothing else: it is written
     * nowhere (SPEC §2, C3).
     */
    private var lastKnownPosition: Coordinates? = null

    /**
     * Whether the point may follow the device: the permission as it stands.
     *
     * A flow rather than a call, because the answer changes while the map is
     * up — granted from the button, or from the Android settings while the
     * application was away — and the following has to start on that answer.
     * Asking again on every return to the screen would mean subscribing again
     * on every return, and stacking one subscription per visit.
     */
    private val locationPermitted = MutableStateFlow(false)

    /**
     * Requests the location permissions, and never insists.
     *
     * SPEC §10 is explicit: a refusal must neither block a screen nor trigger a
     * second prompt. A user who says no keeps a fully usable application, in
     * which they designate their points by hand.
     */
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            locateMe()
            // Only now may the point start following: before this answer there
            // was nothing to follow it with (SPEC §10).
            locationPermitted.value = true
        } else {
            showMessage(R.string.map_location_denied)
        }
    }

    private val viewModel: StationsViewModel by viewModels {
        StationsViewModel.Factory(
            (requireActivity().application as RoueLibreApplication)
                .container
                .stationRepository,
        )
    }

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private var mode: AvailabilityMode = AvailabilityMode.Bikes

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Mandatory before the map view is inflated: MapLibre loads its native
        // libraries at that moment.
        MapLibre.getInstance(requireContext())
        val created = FragmentMapBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.map.onCreate(savedInstanceState)
        views.openSettings.setOnClickListener { show(SettingsFragment()) }
        views.openList.setOnClickListener { show(StationListFragment()) }
        views.openSearch.setOnClickListener { openAddressSearch() }
        views.locateMe.setOnClickListener { onLocateMeClicked() }
        views.openJourney.setOnClickListener { show(JourneySearchFragment()) }
        views.modeToggle.setOnClickListener { toggleMode() }
        views.pickedPlace.setOnClickListener { showPickedPlace(null) }
        applyModeLabel()
        // The button that opens the journey search carries the bike of the
        // network served: with a bolt where that network lends pedal-assist
        // bikes (SPEC §15).
        withBikeFleet { fleet ->
            binding?.openJourney?.setIconResource(BikeGlyphs.icon(fleet))
        }

        // The target depends on what is missing, and it is set together with
        // the label, once we know whether there is a city — see loadTilesFor.
        views.missingTilesList.setOnClickListener { show(StationListFragment()) }

        applyPickingMode(views)
        restoreCamera(savedInstanceState)
        restorePickedPlace(savedInstanceState)
        showRequestedPlace(savedInstanceState)
        listenForPickedAddress()
        applySystemInsets(views)
        views.map.getMapAsync(::onMapReady)

        observeStations()
        observeErrors()
        keepAvailabilityFresh()
        followUserPosition()
        askForLocation()
    }

    /**
     * Keeps the controls clear of the system bars.
     *
     * The map fills the whole screen, bars included — that is what gives it its
     * sweep. The controls laid on top, however, must stay reachable: without
     * this inset, the freshness label slid under the clock and the list button
     * under the navigation bar.
     */
    private fun applySystemInsets(views: FragmentMapBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(views.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            views.freshness.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + baseMargin
            }
            views.openSettings.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + baseMargin
            }
            views.attribution.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
            }
            windowInsets
        }
    }

    private val baseMargin: Int
        get() = resources.getDimensionPixelSize(R.dimen.space_m)

    private fun onMapReady(map: MapLibreMap) {
        mapLibreMap = map
        loadTilesIfInstalled()
    }

    /**
     * Loads the style if the base map is there, otherwise shows the
     * explanation.
     *
     * Called on returning to the screen too: the user may have gone off to
     * install the tiles in the meantime, and the map must then appear without
     * their having to restart the application.
     */
    private fun loadTilesIfInstalled() {
        // The active city is read from disk: the map is therefore drawn when
        // that read returns, not during it.
        viewLifecycleOwner.lifecycleScope.launch {
            loadTilesFor(container.activeCity())
        }
    }

    private fun loadTilesFor(configuration: CityConfiguration?) {
        // Noted before anything may return early: the served area is what
        // "locate me" measures itself against, and it is known here even when
        // the style has already been loaded.
        servedCity = configuration
        val views = binding ?: return
        val map = mapLibreMap ?: return
        if (styleLoaded) return

        // Without a chosen city there is no base map to load: that is the same
        // screen as without installed tiles, inviting the user to get some.
        val tiles = if (configuration == null) {
            null
        } else {
            container.datasetStore.fileOf(DatasetKind.Tiles)
        }
        views.missingTiles.isVisible = tiles == null
        views.attribution.isVisible = tiles != null
        // Without a city it is not data that is missing but the choice of
        // conurbation: offering to install tiles would make no sense while we
        // do not know whose tiles they would be.
        if (configuration == null) {
            views.missingTilesTitle.setText(R.string.map_needs_city_title)
            views.missingTilesMessage.setText(R.string.map_needs_city_message)
            views.missingTilesStorage.setText(R.string.city_choose)
            views.missingTilesStorage.setOnClickListener { show(CityFragment()) }
        } else {
            views.missingTilesTitle.setText(R.string.map_needs_tiles_title)
            views.missingTilesMessage.setText(R.string.map_needs_tiles_message)
            views.missingTilesStorage.setText(R.string.storage_open)
            views.missingTilesStorage.setOnClickListener { show(StorageFragment()) }
        }
        // The main screen's controls do not reappear when the map is being
        // used to designate a point: one came to aim, not to browse.
        val showsControls = tiles != null && !isPicking()
        views.modeToggle.isVisible = showsControls
        // Without a base map, an address found would have nowhere to land: the
        // search opens from the map, and presumes one.
        views.openSearch.isVisible = showsControls
        views.openJourney.isVisible = showsControls
        if (tiles == null || configuration == null) return

        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.setMinZoomPreference(configuration.map.minZoom.toDouble())
        // Past the tiles' maximum zoom, MapLibre scales up the last ones it
        // has. Allowing one step lets the user come a little closer without the
        // text turning illegible.
        map.setMaxZoomPreference(configuration.map.maxZoom.toDouble() + 1)
        map.cameraPosition = lastCamera?.takeIf { it.suits(configuration) }
            ?: openingCamera(configuration)
        holdCameraOverServedArea(map, configuration)

        map.addOnMapClickListener(::onMapClicked)

        map.setStyle(
            Style.Builder().fromJson(MapStyleLoader.load(requireContext(), tiles)),
        ) { style ->
            styleLoaded = true
            addStationLayers(style)
            publishStations()
            // An address chosen while the map did not exist: now is when it
            // can be reached.
            pendingCameraTarget?.let { target ->
                pendingCameraTarget = null
                moveCameraTo(target)
            }
        }
    }

    /**
     * Pens the camera inside the served area (SPEC §7.1).
     *
     * The city without a bounding box is the city whose configuration does not
     * declare one: there is then nothing to hold the camera to, and the map
     * stays as free as it was.
     *
     * The limits are read off the visible region, which the map only has once
     * it has been measured — hence the wait for the layout pass rather than an
     * application on the spot.
     */
    private fun holdCameraOverServedArea(map: MapLibreMap, configuration: CityConfiguration) {
        val area = configuration.boundingBox?.takeIf { it.isUsable } ?: return
        val view = binding?.map ?: return
        val camera = ServedAreaCamera(
            view = view,
            map = map,
            area = area,
            widestZoom = configuration.map.minZoom.toDouble(),
            closestZoom = configuration.map.maxZoom.toDouble() + 1,
        )
        // Known to the screen before the layout, not only after it: the style
        // becomes ready in between, and the move it sets off — the address the
        // search has just returned — must be able to stand these limits down
        // before they are laid on top of it.
        servedAreaCamera = camera
        view.doOnLayout { camera.hold() }
    }

    /**
     * Says whether a remembered framing still holds for this city.
     *
     * The camera survives the destruction of the view, so a trip to another
     * screen does not lose everything. But it also survives a change of city:
     * taken as is, it would reopen the map of Paris over Lille, outside the
     * tiles, on a grey screen that nothing explains.
     */
    private fun CameraPosition.suits(configuration: CityConfiguration): Boolean {
        val box = configuration.boundingBox ?: return true
        val centre = target ?: return false
        return Coordinates(centre.latitude, centre.longitude) in box
    }

    /**
     * The opening framing, for want of a known position.
     *
     * Location permission is not requested at launch (SPEC §10): the map
     * therefore opens on the centre declared in the city configuration, never
     * on a position obtained without the user's knowledge.
     */
    /**
     * How the map is framed when nothing is remembered.
     *
     * On the user's position when it is already known, since that is what they
     * came to see: the stations around them, not the middle of the
     * conurbation. Only what the system already holds is read — no fix is
     * requested and no permission is asked for (SPEC §10). Without a position,
     * or with one outside the served city, the configured centring stands.
     */
    private fun openingCamera(configuration: CityConfiguration): CameraPosition {
        val here = container.deviceLocation
            .takeIf { it.isPermitted() }
            ?.lastKnown()
            ?.takeIf { position -> configuration.boundingBox?.let { position in it } == true }
        if (here != null) {
            // Shown as well as framed: a map centred on nothing would leave the
            // user guessing which point it settled on.
            lastKnownPosition = here
            return CameraPosition.Builder()
                .target(LatLng(here.latitude, here.longitude))
                .zoom(USER_POSITION_ZOOM)
                .build()
        }
        return CameraPosition.Builder()
            .target(
                LatLng(
                    configuration.map.centre.latitude,
                    configuration.map.centre.longitude,
                ),
            )
            .zoom(configuration.map.defaultZoom)
            .build()
    }

    /**
     * Installs the station source and its four layers.
     *
     * Clustering is left to MapLibre (SPEC §7.1): past the zoom given, nearby
     * stations merge into a cluster bearing their count.
     */
    private fun addStationLayers(style: Style) {
        val source = GeoJsonSource(
            StationMarkers.SOURCE_ID,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(CLUSTER_RADIUS)
                .withClusterMaxZoom(CLUSTER_MAX_ZOOM),
        )
        stationSource = source
        style.addSource(source)

        val context = requireContext()
        style.addLayer(StationMarkers.circleLayer(context))
        style.addLayer(StationMarkers.countLayer(context))
        style.addLayer(StationMarkers.clusterLayer(context))
        style.addLayer(StationMarkers.clusterCountLayer(context))

        // The searched point is laid down AFTER the stations: it is what the
        // user has just asked for, so it goes in front.
        val picked = GeoJsonSource(
            PickedPlaceMarker.SOURCE_ID,
            PickedPlaceMarker.featureFor(null),
        )
        pickedPlaceSource = picked
        style.addSource(picked)
        PickedPlaceMarker.registerImage(context, style)
        style.addLayer(PickedPlaceMarker.layer())
        publishPickedPlace()

        val userPosition = GeoJsonSource(
            UserPositionMarker.SOURCE_ID,
            UserPositionMarker.featureFor(null),
        )
        userPositionSource = userPosition
        style.addSource(userPosition)
        style.addLayer(UserPositionMarker.layer(context))
        // The known position is shown again after the view is rebuilt, without
        // asking the system afresh.
        lastKnownPosition?.let { userPosition.setGeoJson(UserPositionMarker.featureFor(it)) }
    }

    private fun publishStations() {
        val source = stationSource ?: return
        source.setGeoJson(
            StationMarkers.toFeatureCollection(viewModel.state.value.stations, mode),
        )
    }

    /**
     * Opens the detail of the station touched, or zooms into a cluster.
     *
     * The sensitive area is widened around the finger: a marker is some fifteen
     * pixels across, which is less than the pad of a thumb. Without this
     * margin, one would have to aim.
     *
     * @return true if the gesture was consumed, so the map does not handle it
     *   in turn.
     */
    private fun onMapClicked(point: LatLng): Boolean {
        val map = mapLibreMap ?: return false
        val screenPoint = map.projection.toScreenLocation(point)
        val touchArea = RectF(
            screenPoint.x - TOUCH_SLOP_PIXELS,
            screenPoint.y - TOUCH_SLOP_PIXELS,
            screenPoint.x + TOUCH_SLOP_PIXELS,
            screenPoint.y + TOUCH_SLOP_PIXELS,
        )
        val touched = map.queryRenderedFeatures(
            touchArea,
            StationMarkers.STATION_CIRCLE_LAYER,
            StationMarkers.CLUSTER_CIRCLE_LAYER,
        ).firstOrNull() ?: return false

        // A cluster describes no station in particular: touching it zooms in,
        // which eventually resolves it into distinct markers.
        if (touched.hasProperty(CLUSTER_COUNT_PROPERTY)) {
            moveCameraTo(point, map.cameraPosition.zoom + CLUSTER_ZOOM_STEP)
            return true
        }

        val stationId = touched.getStringProperty(StationMarkers.STATION_ID_PROPERTY)
            ?: return false
        // In "pick a point" mode, opening a station sheet would divert the
        // gesture from what the user came to do.
        if (isPicking()) return false
        (touched.geometry() as? Point)?.let { centreOnStation(map, it) }
        StationDetailSheet.newInstance(stationId)
            .show(parentFragmentManager, StationDetailSheet.TAG)
        return true
    }

    /**
     * Brings the touched station to the middle of what stays visible.
     *
     * Not to the middle of the map: the detail sheet rises over the lower part
     * of the screen, and a station centred there would be behind it. The camera
     * therefore aims a little below the marker, which leaves the marker in the
     * visible half — the eye follows what it has just touched.
     *
     * The zoom is left alone. Touching a marker says "show me this one", not
     * "take me closer", and a zoom that changed under the finger would lose the
     * neighbouring stations one was comparing.
     */
    private fun centreOnStation(map: MapLibreMap, station: Point) {
        val target = LatLng(station.latitude(), station.longitude())
        val height = binding?.map?.height ?: return
        val onScreen = map.projection.toScreenLocation(target)
        val below = PointF(onScreen.x, onScreen.y + height * SHEET_CLEARANCE_FRACTION)
        moveCameraTo(map.projection.fromScreenLocation(below), map.cameraPosition.zoom)
    }

    // ------------------------------------------------ picking a point (§7.3) --

    /**
     * Prepares the screen when it serves to designate a point.
     *
     * The controls that have no business there disappear: one came to choose a
     * place, not to browse availability. The crosshair stays fixed at the
     * centre — it is the map that moves underneath, which leaves what is being
     * aimed at visible, unlike a finger placed on top of it.
     */
    private fun applyPickingMode(views: FragmentMapBinding) {
        if (!isPicking()) return
        views.pickCrosshair.isVisible = true
        views.pickConfirm.isVisible = true
        views.openList.isVisible = false
        views.openSettings.isVisible = false
        views.openSearch.isVisible = false
        views.openJourney.isVisible = false
        views.modeToggle.isVisible = false
        views.pickConfirm.setOnClickListener { confirmPickedPoint() }
    }

    private fun isPicking(): Boolean = arguments?.getBoolean(ARGUMENT_PICKING) == true

    /**
     * Returns the point aimed at, with its address when the index knows it.
     *
     * A readable label rather than two numbers: "12 Rue Nationale" reads back
     * on the search screen, "50.63 / 3.06" does not.
     */
    private fun confirmPickedPoint() {
        val map = mapLibreMap ?: return
        val target = map.cameraPosition.target ?: return
        val point = Coordinates(target.latitude, target.longitude)
        viewLifecycleOwner.lifecycleScope.launch {
            val address = container.addressIndex.nearestAddress(point)
            val label = address?.streetName ?: getString(R.string.journey_picked_point)
            setFragmentResult(
                PICK_REQUEST_KEY,
                Bundle().apply {
                    JourneyEndpoint(label, point).writeTo(this, PICK_RESULT_PREFIX)
                },
            )
            parentFragmentManager.popBackStack()
        }
    }

    // ----------------------------------------------------------- location --

    /**
     * Asks for the location permission when the map opens (SPEC §7.1).
     *
     * **Once per session, and only if it has never been granted.** The point
     * that follows the device is what this screen is for; asking again at each
     * return to the map, on the other hand, is the insistence SPEC §10
     * forbids. After a refusal the map stays whole, without a point, and the
     * "locate me" button is the way back.
     */
    private fun askForLocation() {
        if (container.deviceLocation.isPermitted()) return
        if (!container.rememberLocationRequest()) return
        requestLocationPermission.launch(DeviceLocation.PERMISSIONS)
    }

    /**
     * Answers the "locate me" button (SPEC §7.1).
     *
     * The other of the two moments where the permission is requested, and the
     * one that comes back: after a refusal at opening, this is what the user
     * has left to change their mind (SPEC §10).
     */
    private fun onLocateMeClicked() {
        val location = container.deviceLocation
        when {
            !location.isPermitted() ->
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)

            !location.isAvailable() -> showMessage(R.string.map_location_unavailable)

            else -> locateMe()
        }
    }

    /**
     * Looks for the position and brings the map to it.
     *
     * The position is neither kept nor written: it serves to frame the map,
     * then lives in the display source for the session (SPEC §2, C3).
     */
    private fun locateMe() {
        viewLifecycleOwner.lifecycleScope.launch {
            // A first fix can take several seconds indoors. Disabling the
            // button meanwhile avoids suggesting the press was lost — and
            // avoids stacking several of them.
            binding?.locateMe?.isEnabled = false
            val position = try {
                container.deviceLocation.current()
            } finally {
                binding?.locateMe?.isEnabled = true
            }
            if (position == null) {
                showMessage(R.string.map_location_unavailable)
                return@launch
            }
            lastKnownPosition = position
            userPositionSource?.setGeoJson(UserPositionMarker.featureFor(position))
            // Outside the city served there is nothing to move to: the camera
            // is held inside that city's box, so it would stop at the edge
            // nearest the user and show that as their position — a point on a
            // street they are a hundred kilometres from, with nothing saying
            // so. What the moment calls for is the other city, not a framing.
            if (isOutsideServedArea(position)) {
                sayWeAreElsewhere(position)
                return@launch
            }
            moveCameraTo(LatLng(position.latitude, position.longitude), USER_POSITION_ZOOM)
        }
    }

    /**
     * Says whether [position] falls outside the area this map covers.
     *
     * The city whose configuration declares no bounding box covers everything
     * as far as this screen is concerned: nothing pens its camera either, so
     * nothing is being passed off as anything.
     */
    private fun isOutsideServedArea(position: Coordinates): Boolean {
        val area = servedCity?.boundingBox?.takeIf { it.isUsable } ?: return false
        return position !in area
    }

    /**
     * Tells the user their position is off this map, and offers the way out.
     *
     * The way out is the network of the conurbation they are actually in, when
     * the catalogue knows one and its data is published — the same offer
     * §15.1 makes on opening the application, asked for here rather than
     * volunteered. Failing that, the city list, which is where one changes map.
     *
     * The catalogue read is the one already on the device: no request goes out,
     * and the position serves this single question before being forgotten
     * (SPEC §2, C3).
     *
     * A city already offered and turned down is not offered again: the press
     * is answered in one line instead. Reopening the dialogue on every press
     * was what made "locate me" unusable to anybody who meant to keep the city
     * they had chosen.
     */
    private suspend fun sayWeAreElsewhere(position: Coordinates) {
        val served = servedCity ?: return
        val servedLabel = requireContext()
            .cityLabel(served.network.displayName, served.network.city)
        // Designating a point is composing a journey in the city served:
        // changing map there would throw away the endpoint already chosen. All
        // that is left to do is say where we are.
        if (isPicking()) {
            showMessage(getString(R.string.map_outside_city_brief, servedLabel))
            return
        }
        val here = cityAround(position)
        // Asked before the answer is chosen: standing outside the network one
        // declined is what forgets that refusal, whatever is said next.
        val mayPropose = container.rememberCityProposal(here?.id)
        if (here != null && !mayPropose) {
            // Where we are is still worth saying: the press asked a question,
            // and silence would read as a button that does nothing.
            showMessage(getString(R.string.map_outside_city_brief, servedLabel))
            return
        }
        if (here == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.map_outside_city_title)
                .setMessage(getString(R.string.map_outside_city_message, servedLabel))
                .setPositiveButton(R.string.city_choose) { _, _ -> show(CityFragment()) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            return
        }
        val installed = container.datasetStore.occupiedBytesOf(here.id) > 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.city_here_title)
            .setMessage(
                getString(
                    if (installed) R.string.city_here_installed_body else R.string.city_here_body,
                    requireContext().cityLabel(here.displayName, here.mainCity),
                ),
            )
            .setPositiveButton(
                if (installed) R.string.city_here_use else R.string.city_here_install,
            ) { _, _ -> serve(here.id, installed) }
            .setNegativeButton(R.string.action_cancel) { _, _ -> declineCity(here.id) }
            .show()
    }

    /**
     * Takes note that the offer was turned down.
     *
     * Kept beyond the session, like the one the application makes on opening: a
     * refusal forgotten at the next launch is a question asked again.
     */
    private fun declineCity(cityId: String) {
        viewLifecycleOwner.lifecycleScope.launch { container.rememberCityRefusal(cityId) }
    }

    /**
     * The network serving where the user stands, if it is worth proposing.
     *
     * A city whose data is not published yet leads to a download with nothing
     * to fetch, and the city already in service is not a change of map: neither
     * is offered.
     */
    private suspend fun cityAround(position: Coordinates): CityEntry? =
        container.cityCatalogueSource.catalogue()
            .suggestionFor(position)
            ?.takeIf { it.isAvailable && it.id != servedCity?.network?.id }

    /**
     * Serves the city the user accepted.
     *
     * Its data already there, the map has everything it needs and is built
     * again on it — this screen loads its style once and keeps it, so the new
     * city's tiles need a new screen. Otherwise the storage screen takes over:
     * it announces the weight before fetching anything (SPEC §4.4).
     */
    private fun serve(cityId: String, installed: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.switchToCity(cityId)
            if (installed) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.content, MapFragment())
                    .commit()
            } else {
                show(StorageFragment.checkingForUpdates())
            }
        }
    }

    /**
     * Moves the point as the device moves (SPEC §7.1).
     *
     * **The point follows, the map does not.** Recentring at every fix would
     * take the map back from under the user each time they looked a street
     * further on; the framing stays theirs, and "locate me" is what brings it
     * back to the point.
     *
     * Only if the permission is granted: opening the map asks for nothing, and
     * a user who has refused keeps a map without a point rather than a second
     * prompt (SPEC §10). The permission granted later — from the button, or
     * from the Android settings — opens the gate, and the following starts
     * there and then.
     *
     * Subscribed once for the life of the view, and following the screen —
     * `repeatOnLifecycle` drops it as soon as the map is no longer displayed,
     * so nothing listens to the satellites in the background — and the position
     * is written nowhere (SPEC §2, C3).
     */
    private fun followUserPosition() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationPermitted.collectLatest { permitted ->
                    if (!permitted) return@collectLatest
                    container.deviceLocation.positions().collect { position ->
                        lastKnownPosition = position
                        userPositionSource?.setGeoJson(UserPositionMarker.featureFor(position))
                    }
                }
            }
        }
    }

    private fun showMessage(message: Int) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showMessage(message: CharSequence) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    // ---------------------------------------------------- address search --

    /** Opens the address search (SPEC §4.3). */
    private fun openAddressSearch() {
        // The map's centre serves as the ranking's reference point: it says
        // well enough where the user is looking, and obtaining it needs no
        // location permission at all (SPEC §10).
        val centre = mapLibreMap?.cameraPosition?.target?.let {
            Coordinates(it.latitude, it.longitude)
        }
        show(AddressSearchFragment.newInstance(centre))
    }

    /** Collects the address chosen by the search screen. */
    private fun listenForPickedAddress() {
        parentFragmentManager.setFragmentResultListener(
            AddressSearchFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            showPickedPlace(
                PickedPlace(
                    position = LatLng(
                        result.getDouble(AddressSearchFragment.RESULT_LATITUDE),
                        result.getDouble(AddressSearchFragment.RESULT_LONGITUDE),
                    ),
                    label = result.getString(AddressSearchFragment.RESULT_LABEL).orEmpty(),
                ),
            )
        }
    }

    /**
     * Lays the point found on the map, or clears it.
     *
     * @param place the chosen address, or `null` to remove the marker.
     */
    private fun showPickedPlace(place: PickedPlace?) {
        pickedPlace = place
        publishPickedPlace()
        showPickedPlaceLabel(place)
        if (place != null) moveCameraTo(place.position)
    }

    /**
     * Shows the label of the point found.
     *
     * The label is part of what a screen reader must speak: replacing the text
     * with the "clear" action alone would make the address vanish for anyone
     * who only has the voice.
     */
    private fun showPickedPlaceLabel(place: PickedPlace?) {
        val pill = binding?.pickedPlace ?: return
        pill.isVisible = place != null
        pill.text = place?.label.orEmpty()
        pill.contentDescription = place?.let {
            getString(R.string.map_picked_place_description, it.label)
        }
    }

    private fun publishPickedPlace() {
        pickedPlaceSource?.setGeoJson(
            PickedPlaceMarker.featureFor(
                pickedPlace?.let { Coordinates(it.position.latitude, it.position.longitude) },
            ),
        )
    }

    /**
     * Brings the map onto a point.
     *
     * The move is animated so one understands where one came from — unless the
     * device asks for reduced animations, in which case the map jumps straight
     * to the destination (SPEC §7).
     *
     * The limits over the served area stand down for its length: laying one
     * down cancels the move in flight, and this one was dying to the limits
     * being measured a few milliseconds after it started. They are taken up
     * again on arrival, where they are measured on the framing that landed.
     */
    private fun moveCameraTo(target: LatLng, zoom: Double = PICKED_PLACE_ZOOM) {
        val map = mapLibreMap
        if (map == null || !styleLoaded) {
            pendingCameraTarget = target
            return
        }
        val update = CameraUpdateFactory.newLatLngZoom(target, zoom)
        val servedArea = servedAreaCamera
        servedArea?.releaseForMove()
        if (requireContext().prefersReducedMotion()) {
            map.moveCamera(update)
            servedArea?.holdAgain()
            return
        }
        map.animateCamera(
            update,
            CAMERA_ANIMATION_MILLIS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = holdOverServedAreaAgain(servedArea)

                // A move cut short — by a finger on the map, or by another
                // move — leaves the camera somewhere legitimate all the same:
                // what matters is that the limits come back, wherever it is.
                override fun onCancel() = holdOverServedAreaAgain(servedArea)
            },
        )
    }

    /**
     * Takes the limits up again once a move of ours has ended.
     *
     * The screen may have gone in the meantime — one leaves for the list while
     * the map is still travelling — and the limits are then measured on a map
     * whose view no longer exists.
     */
    private fun holdOverServedAreaAgain(servedArea: ServedAreaCamera?) {
        if (mapLibreMap == null) return
        servedArea?.holdAgain()
    }

    /**
     * Lays down the point received from another application, if there is one
     * (SPEC §7.8).
     *
     * On the first display only: after a rotation it is the saved state that
     * prevails, and laying the point down again would erase what the user did
     * in the meantime.
     */
    private fun showRequestedPlace(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val requested = JourneyEndpoint.readFrom(arguments, ARGUMENT_SHOWN_PLACE) ?: return
        showPickedPlace(
            PickedPlace(
                position = LatLng(requested.position.latitude, requested.position.longitude),
                label = requested.label,
            ),
        )
    }

    /**
     * Takes back the framing the screen had before the phone was turned.
     *
     * Only the target and the zoom: the tilt and the bearing are not the
     * user's to set here — the map holds neither — and restoring them would be
     * restoring nothing.
     */
    private fun restoreCamera(savedInstanceState: Bundle?) {
        val saved = savedInstanceState ?: return
        if (!saved.containsKey(STATE_CAMERA_LATITUDE)) return
        lastCamera = CameraPosition.Builder()
            .target(
                LatLng(
                    saved.getDouble(STATE_CAMERA_LATITUDE),
                    saved.getDouble(STATE_CAMERA_LONGITUDE),
                ),
            )
            .zoom(saved.getDouble(STATE_CAMERA_ZOOM))
            .build()
    }

    private fun restorePickedPlace(savedInstanceState: Bundle?) {
        val saved = savedInstanceState ?: return
        if (!saved.containsKey(STATE_PICKED_LATITUDE)) return
        pickedPlace = PickedPlace(
            position = LatLng(
                saved.getDouble(STATE_PICKED_LATITUDE),
                saved.getDouble(STATE_PICKED_LONGITUDE),
            ),
            label = saved.getString(STATE_PICKED_LABEL).orEmpty(),
        )
        showPickedPlaceLabel(pickedPlace)
    }

    private fun toggleMode() {
        mode = when (mode) {
            AvailabilityMode.Bikes -> AvailabilityMode.Docks
            AvailabilityMode.Docks -> AvailabilityMode.Bikes
        }
        applyModeLabel()
        publishStations()
    }

    private fun applyModeLabel() {
        // The button is named after what is SHOWN, not after what a press
        // would do: it is a state label, like the list's own toggle.
        binding?.modeToggle?.setText(
            when (mode) {
                AvailabilityMode.Bikes -> R.string.mode_bikes
                AvailabilityMode.Docks -> R.string.mode_docks
            },
        )
    }

    private fun observeStations() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    publishStations()
                    showFreshness(state.fetchedAt)
                }
            }
        }
    }

    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { error ->
                    val views = binding ?: return@collect
                    Snackbar
                        .make(
                            views.root,
                            error.toUserMessage(requireContext()),
                            Snackbar.LENGTH_LONG,
                        )
                        .setAction(R.string.action_retry) { viewModel.refresh(force = true) }
                        .show()
                }
            }
        }
    }

    /** Refreshes and rewrites the displayed age while the map is visible. */
    private fun keepAvailabilityFresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refresh()
                    showFreshness(viewModel.state.value.fetchedAt)
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    private fun showFreshness(fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        views.freshness.text = freshness.toStatusLine(requireContext(), freshness.isStale)
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    // ------------------------------------------------- map lifecycle --
    // MapLibre manages a native graphics context: without these relays, it
    // leaks.

    override fun onStart() {
        super.onStart()
        binding?.map?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding?.map?.onResume()
        loadTilesIfInstalled()
        // The permission may have been granted, or withdrawn, from the Android
        // settings while the application was away: the point starts or stops
        // following on the way back, without a detour through the button.
        locationPermitted.value = container.deviceLocation.isPermitted()
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
        // Where the map was looking, so that turning the phone shows the same
        // place in the other shape rather than starting the screen over.
        val camera = mapLibreMap?.cameraPosition ?: lastCamera
        camera?.target?.let { target ->
            outState.putDouble(STATE_CAMERA_LATITUDE, target.latitude)
            outState.putDouble(STATE_CAMERA_LONGITUDE, target.longitude)
            outState.putDouble(STATE_CAMERA_ZOOM, camera.zoom)
        }
        // The chosen point survives a rotation, and nothing else: it is
        // written nowhere on disk (SPEC §8).
        pickedPlace?.let { place ->
            outState.putDouble(STATE_PICKED_LATITUDE, place.position.latitude)
            outState.putDouble(STATE_PICKED_LONGITUDE, place.position.longitude)
            outState.putString(STATE_PICKED_LABEL, place.label)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding?.map?.onLowMemory()
    }

    override fun onDestroyView() {
        lastCamera = mapLibreMap?.cameraPosition
        binding?.map?.onDestroy()
        stationSource = null
        pickedPlaceSource = null
        userPositionSource = null
        servedAreaCamera = null
        mapLibreMap = null
        styleLoaded = false
        binding = null
        super.onDestroyView()
    }

    companion object {
        /** The key the point picked on the map is returned under. */
        const val PICK_REQUEST_KEY: String = "point-picked-on-map"

        /** The prefix of the returned point's keys. */
        const val PICK_RESULT_PREFIX: String = "point"

        private const val ARGUMENT_PICKING = "picking-mode"
        private const val ARGUMENT_SHOWN_PLACE = "place-to-show"

        /** Opens the map to designate a point on it (SPEC §7.3). */
        fun forPicking(): MapFragment = MapFragment().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_PICKING, true) }
        }

        /**
         * Opens the map resting on a point, computing nothing.
         *
         * Used for places received from outside the covered area: the map shows
         * them if it can, but no route is attempted (§7.8).
         */
        fun showing(place: JourneyEndpoint): MapFragment = MapFragment().apply {
            arguments = Bundle().apply { place.writeTo(this, ARGUMENT_SHOWN_PLACE) }
        }

        /** The zoom the map settles at on a found address: the street. */
        const val PICKED_PLACE_ZOOM = 16.0

        /**
         * The margin around the finger on a touch, in pixels. A marker is some
         * fifteen pixels across: without this margin, one would have to aim.
         */
        const val TOUCH_SLOP_PIXELS = 32f

        /** The zoom the map settles at on the user's position. */
        const val USER_POSITION_ZOOM = 16.0

        /** How much a touch on a cluster zooms the map in. */
        const val CLUSTER_ZOOM_STEP = 2.0

        /**
         * How far below a touched station the camera aims, as a fraction of the
         * map's height. A quarter puts the marker halfway up the part the
         * detail sheet leaves visible.
         */
        const val SHEET_CLEARANCE_FRACTION = 0.25f

        /** The property MapLibre adds to the clusters it forms itself. */
        const val CLUSTER_COUNT_PROPERTY = "point_count"

        /** The camera move's duration, short enough not to keep anyone waiting. */
        const val CAMERA_ANIMATION_MILLIS = 600

        const val STATE_CAMERA_LATITUDE = "camera-latitude"
        const val STATE_CAMERA_LONGITUDE = "camera-longitude"
        const val STATE_CAMERA_ZOOM = "camera-zoom"

        const val STATE_PICKED_LATITUDE = "picked-latitude"
        const val STATE_PICKED_LONGITUDE = "picked-longitude"
        const val STATE_PICKED_LABEL = "picked-label"

        /**
         * The clustering radius, in pixels. Fifty keeps the stations of central
         * Lille distinct as soon as one comes near, without making the overview
         * swarm.
         */
        const val CLUSTER_RADIUS = 50

        /** Past this, every station takes back its own marker. */
        const val CLUSTER_MAX_ZOOM = 13

        const val FRESHNESS_TICK_MILLIS = 10_000L
    }
}
