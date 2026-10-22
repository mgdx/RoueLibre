package io.github.mgdx.rouelibre.ui.map

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.config.ActiveCity
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.message.MessageSubject
import io.github.mgdx.rouelibre.core.station.StationFilter
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.core.station.stationsShownOnMap
import io.github.mgdx.rouelibre.data.SavedPlaceKind
import io.github.mgdx.rouelibre.databinding.FragmentMapBinding
import io.github.mgdx.rouelibre.ui.BikeGlyphs
import io.github.mgdx.rouelibre.ui.MAP_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.MainActivity
import io.github.mgdx.rouelibre.ui.STATION_LIST_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.ScreenBehind
import io.github.mgdx.rouelibre.ui.address.AddressSearchFragment
import io.github.mgdx.rouelibre.ui.backStackEntryNames
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.screenBehind
import io.github.mgdx.rouelibre.ui.settings.SettingsFragment
import io.github.mgdx.rouelibre.ui.stations.StationListFragment
import io.github.mgdx.rouelibre.ui.stations.StationsUiState
import io.github.mgdx.rouelibre.ui.stations.StationsViewModel
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import io.github.mgdx.rouelibre.ui.withBikeFleet
import io.github.mgdx.rouelibre.ui.withFleet
import io.github.mgdx.rouelibre.ui.withFreeDocksShown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.io.File
import java.time.Instant

/**
 * Whether the map is to raise a banner for [error] at all.
 *
 * One error is held back, and only on this screen: having chosen no city. The
 * map already says that, full width and in the middle of the screen — the panel
 * `loadTilesFor` raises for a `null` configuration carries the sentence *and*
 * "Choose my city", and it is raised for exactly the state this error reports,
 * in both of the screen's roles. A banner underneath would repeat an offer
 * already made, and repeat it every ten seconds as the refresh loop ticks.
 *
 * The station list answers the same error by putting the chooser on its banner
 * instead, and for the same reason both screens have: "Try again" is no answer
 * to having chosen no city, since nothing is being asked of any network and
 * every press can only fail the same way (SPEC §14). The list has no panel to
 * lean on, so it must carry the gesture itself; the map has one, so it says
 * nothing rather than saying it twice.
 *
 * Nothing else is ever held back: a feed that fails says so and offers another
 * go, city or no city.
 */
internal fun worthSayingOnTheMap(error: DataError): Boolean = error != DataError.NoCityChosen

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

    /** The sources of the style in place, which go with it. */
    private var layers: MainMapLayers? = null
    private var styleLoaded = false

    /**
     * The base map the style in place was built from.
     *
     * A city's data can be replaced under the screen, and the file that
     * replaces it carries another name — `DatasetStore` names the base map
     * after its digest precisely so that its path changes with its content.
     * Comparing paths is therefore how this screen knows whether the style it
     * holds still describes what is on disk.
     */
    private var loadedTilesPath: String? = null

    /**
     * The city this map is drawn for, as soon as it is known.
     *
     * Kept to hand because "locate me" has to know where the served area stops:
     * the camera is penned inside that area (see [ServedAreaCamera]), so a
     * position outside it would move the map to the nearest edge and pass that
     * off as where the user stands.
     */
    private var servedCity: CityConfiguration? = null

    private val viewModel: StationsViewModel by viewModels {
        StationsViewModel.Factory(
            (requireActivity().application as RoueLibreApplication)
                .container
                .stationRepository,
        )
    }

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /** Keeps the controls clear of the system bars and of the banner. */
    private val bottomEdge = MapBottomEdge(binding = { binding })

    /** Where the map opens, where it is sent, and what it remembers. */
    private val camera = MainMapCamera(
        map = { mapLibreMap },
        isReady = { styleLoaded },
        context = { requireContext() },
    )

    /** The address found by the search, and the city it belongs to. */
    private val pickedPlace = PickedPlaceController(
        pill = { binding?.pickedPlace },
        cityNowServed = { servedCity?.network?.id },
        bringIntoView = { camera.moveTo(it) },
    )

    /** The user's point, and the permission it takes. */
    private val userPosition = UserPositionOnMap(
        fragment = this,
        display = { layers?.userPosition },
        locate = ::locateMe,
        onMessage = ::showMessage,
    )

    /** What is said, and offered, to somebody standing outside the city. */
    private val elsewhere = CityElsewhereOffer(
        fragment = this,
        container = { container },
        servedCity = { servedCity },
        isPicking = ::isPicking,
        onMessage = ::showMessage,
        open = ::show,
    )

    /** The turn and the tilt of the map, and the compass that gives both back. */
    private val attitude = MapAttitudeController(
        button = { binding?.faceNorth },
        map = { mapLibreMap },
    )

    /** Bikes or free docks, and which kind of bike: the map's two filters. */
    private val filters = MapFilterControls(
        modeToggle = { binding?.modeToggle },
        bikeKindButton = { binding?.bikeKindFilter },
        controlsUp = { showsMapControls },
        lentFleet = { lentFleet },
        rememberBikeKind = ::rememberMapBikeKind,
        onChanged = ::publishStations,
    )

    /** The bikes outside stations, while the settings have them drawn. */
    private val streetBikes = StreetBikesOnMap(
        source = { layers?.streetBikes },
        vehicleTypes = { lentFleet?.vehicleTypes.orEmpty() },
        container = { container },
    )

    /** The places the user has named: their markers and their buttons. */
    private val savedPlaces = SavedPlacesOnMap(
        source = { layers?.savedPlaces },
        button = { kind ->
            when (kind) {
                SavedPlaceKind.Home -> binding?.centreOnHome
                SavedPlaceKind.Work -> binding?.centreOnWork
            }
        },
        controlsUp = { showsMapControls },
        servedArea = { servedCity?.boundingBox },
        bringIntoView = { camera.moveTo(it) },
    )

    /** What a touch on a marker opens. */
    private val touches = MarkerTouches(
        map = { mapLibreMap },
        mapHeight = { binding?.map?.height },
        isPicking = ::isPicking,
        moveTo = camera::moveTo,
        sheets = { parentFragmentManager },
    )

    /**
     * Which stations the map leaves out, and at rest it leaves out none.
     *
     * Read from the settings and settled there alone (SPEC §7.1, §7.6), unlike
     * the two of [MapFilterControls], which are pressed on the map itself:
     * those are ways of looking at it, this is an answer one gives once. **The
     * screen says nothing about it** — no badge, no count, no lit control — which
     * is the accepted compromise SPEC §7.1 names: the settings are the only place
     * that explains a neighbourhood short of its stations. Adding a witness back
     * here would undo a decision, not fix an oversight.
     */
    private var mapFilter: StationFilter = StationFilter()

    /**
     * What the network in service lends, so the markers can be counted by kind.
     *
     * `null` until the reading lands. It decides whether the filter exists at
     * all — a network lending one kind is offered nothing to filter — and it
     * carries the vehicle type table without which no identifier can be read as
     * a kind (SPEC §4.1).
     */
    private var lentFleet: FleetDescription? = null

    /**
     * Whether the controls laid over the map are up at all.
     *
     * False until the base map is known to be installed, and false while a point
     * is being designated. The kind filter follows it — it is one of those
     * controls — and it has to be told rather than to read another button's
     * visibility, the two being settled by different coroutines racing to
     * finish.
     */
    private var showsMapControls = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once per fragment rather than per view: coming back from another
        // screen rebuilds the view on a fragment that still holds both filters.
        filters.restore(savedInstanceState, storedBikeKind = ::storedMapBikeKind)
    }

    /**
     * The kind the map's markers counted at the last launch, or `null`.
     *
     * **Read synchronously**, like the units at start-up and for a kindred
     * reason: read late, the map would first draw every bike and then take
     * some away, a flicker of figures that are not the ones asked for. The
     * read costs next to nothing — the application keeps its settings in
     * memory from the moment it starts, following the units — and happens once
     * per screen created, never on a turned phone, which has its saved state.
     *
     * Never read on a screen picking a point: the kind filter has no button
     * there, and markers counting one kind with nothing on the screen to say
     * so would be bikes hidden without a word.
     */
    private fun storedMapBikeKind(): WantedBikeKind? {
        if (isPicking()) return null
        return runBlocking { container.preferences.mapBikeKind.first() }
    }

    /**
     * Writes the kind just chosen down for the next launch.
     *
     * In the fragment's own scope rather than its view's: a press followed at
     * once by opening another screen, which takes the view away, is still
     * written down.
     */
    private fun rememberMapBikeKind(kind: WantedBikeKind?) {
        lifecycleScope.launch { container.preferences.setMapBikeKind(kind) }
    }

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
        views.openList.setOnClickListener { showTheList() }
        views.openSearch.setOnClickListener { openAddressSearch() }
        views.locateMe.setOnClickListener { userPosition.onLocateMeClicked() }
        attitude.bind()
        views.openJourney.setOnClickListener { show(JourneySearchFragment()) }
        views.pickedPlace.setOnClickListener { openPickedPlaceSheet() }
        views.centreOnHome.setOnClickListener { savedPlaces.centreOn(SavedPlaceKind.Home) }
        views.centreOnWork.setOnClickListener { savedPlaces.centreOn(SavedPlaceKind.Work) }
        filters.bind()
        followStationFilters()
        streetBikes.follow(viewLifecycleOwner)
        savedPlaces.follow(viewLifecycleOwner, container.preferences)
        // The button that opens the journey search carries the bike of the
        // network served: with a bolt where that network lends pedal-assist
        // bikes (SPEC §15).
        withBikeFleet { fleet ->
            binding?.openJourney?.setIconResource(BikeGlyphs.icon(fleet))
        }
        // The filter exists only where both kinds are lent, and the reading
        // that says so arrives after the screen and may change under it
        // (SPEC §4.1): following it puts the button there, or takes it away,
        // without the map being rebuilt.
        withFleet { lent ->
            lentFleet = lent
            filters.showBikeKindFilter()
            publishStations()
            // The same reading says which bikes outside stations bear the
            // bolt, and it may arrive after they are drawn.
            streetBikes.publish()
        }
        // Whether the network's free docks are worth a toggle at all is known
        // once its stations are read, the cache at the latest (SPEC §7.1).
        withFreeDocksShown(filters::showFreeDocks)

        // The target depends on what is missing, and it is set together with
        // the label, once we know whether there is a city — see loadTilesFor.
        views.missingTilesList.setOnClickListener { showTheList() }

        applyPickingMode(views)
        camera.restore(savedInstanceState)
        pickedPlace.restore(savedInstanceState)
        showRequestedPlace(savedInstanceState)
        listenForPickedAddress()
        listenForPlaceActions()
        elsewhere.listenForAnswers()
        bottomEdge.applySystemInsets(views)
        bottomEdge.standClearOfTheBanner(viewLifecycleOwner, activity as? MainActivity)
        views.map.getMapAsync(::onMapReady)

        observeStations()
        observeErrors()
        followTheInstalledMap()
        keepAvailabilityFresh()
        userPosition.follow()
        // Asked for when the map opens, the point that follows the device
        // being what this screen is for. After a refusal the map stays whole,
        // without a point, and the "locate me" button is the way back.
        userPosition.askUnprompted()
    }

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
            loadTilesFor(container.activeCityState())
        }
    }

    /**
     * Applies a city to the map: what survives it, how much of the map is
     * rebuilt for it, the panel when there is no base map, and the map itself
     * when there is one.
     *
     * Reached both when the city changes under a map that is up and when a
     * first configuration is applied to a screen just built — after a turn of
     * the phone, or after the process was killed.
     */
    private fun loadTilesFor(city: ActiveCity) {
        val configuration = (city as? ActiveCity.Served)?.configuration
        // Noted before anything may return early: the served area is what
        // "locate me" measures itself against, and it is known here even when
        // the style has already been loaded.
        val previousCity = servedCity
        servedCity = configuration
        // One question, put where the answer is already to hand: does the
        // point designated on the map belong to the city we are serving now?
        // An address found under Capital Bikeshare was seen still named at the
        // bottom of the map of Lille, marker and all, naming ground this
        // telephone has not got.
        //
        // Put here because this is where a city reaches the screen, on both
        // the paths that can turn the answer to "no": a city changed under a
        // map that is up, and a first configuration applied to a screen
        // rebuilt after the process was killed — the point then coming from
        // the state bundle with the city it was designated in. Nothing
        // designated, or the same city as before a turn of the phone, leaves
        // the point where it is, which is what the user expects of a rotation.
        //
        // Before every early return below: whether the point still has a right
        // to be there has nothing to do with there being a style to redraw.
        pickedPlace.keepOnlyFor(configuration?.network?.id)
        val views = binding ?: return
        val map = mapLibreMap ?: return

        // Without a chosen city there is no base map to load: that is the same
        // screen as without installed tiles, inviting the user to get some.
        val tiles = if (configuration == null) {
            null
        } else {
            container.datasetStore.fileOf(DatasetKind.Tiles)
        }
        // Nothing, the style alone, or everything — see mapRebuildFor, which
        // says why a style already built and the same city leave the framing
        // where the user put it.
        val rebuild = mapRebuildFor(
            styleLoaded = styleLoaded,
            loadedTilesPath = loadedTilesPath,
            tilesPath = tiles?.path,
            previousCityId = previousCity?.network?.id,
            cityId = configuration?.network?.id,
        )
        when (rebuild) {
            MapRebuild.Nothing -> return

            MapRebuild.StyleOnly -> {
                applyStyle(map, checkNotNull(tiles))
                return
            }

            MapRebuild.Everything -> Unit
        }
        views.showMissingTiles(
            city = city,
            panel = missingTilesPanel(
                city = city,
                transferRunning = container.datasetTransfer.state.value.isRunning,
                isPicking = isPicking(),
            ),
            hasTiles = tiles != null,
            open = ::show,
        )
        showControls(views, hasBaseMap = tiles != null)
        if (tiles == null || configuration == null) return

        setUpTheMap(views, map, configuration)
        applyStyle(map, tiles)
    }

    /**
     * Puts up the controls laid over the map for the role it serves, or none
     * of them without a base map.
     *
     * Nothing stays laid over the map while the panel saying it is missing
     * covers the screen: a control hidden behind it is invisible without
     * ceasing to be clickable, and the crosshair and "Choose this point" were
     * being pressed blind under the panel that asked for a city.
     */
    private fun showControls(views: FragmentMapBinding, hasBaseMap: Boolean) {
        val controls = mapControls(hasBaseMap = hasBaseMap, isPicking = isPicking())
        showsMapControls = controls.browsing
        filters.showModeToggle()
        // Without a base map, an address found would have nowhere to land: the
        // search opens from the map, and presumes one.
        views.openSearch.isVisible = controls.browsing
        views.openJourney.isVisible = controls.browsing
        views.openList.isVisible = controls.browsing
        views.openSettings.isVisible = controls.browsing
        views.pickCrosshair.isVisible = controls.picking
        views.pickConfirm.isVisible = controls.picking
        views.locateMe.isVisible = controls.locateMe
        attitude.isAllowed = controls.compass
        attitude.showTheBearing()
        filters.showBikeKindFilter()
        savedPlaces.showButtons()
    }

    /**
     * Gives the map the city's limits and framing, and the listeners it keeps
     * for as long as the view lives.
     *
     * Done once per view and per city — see [mapRebuildFor] — since doing it
     * twice would register a second click listener and move the camera out
     * from under the user.
     */
    private fun setUpTheMap(
        views: FragmentMapBinding,
        map: MapLibreMap,
        configuration: CityConfiguration,
    ) {
        attitude.configure(map)
        map.setMinZoomPreference(configuration.map.minZoom.toDouble())
        // Past the tiles' maximum zoom, MapLibre scales up the last ones it
        // has. Allowing one step lets the user come a little closer without the
        // text turning illegible.
        map.setMaxZoomPreference(configuration.map.maxZoom.toDouble() + 1)
        camera.open(views.map, map, configuration) { positionToOpenOn(configuration) }

        map.addOnMapClickListener(touches::onMapClicked)
        attitude.follow(map)
    }

    /**
     * Builds the style over a base map and puts the map's own layers back.
     *
     * Called again whenever the file changes: the layers are the style's, so
     * they go with it and have to be added to the new one.
     */
    private fun applyStyle(map: MapLibreMap, tiles: File) {
        map.setStyle(
            Style.Builder().fromJson(MapStyleLoader.load(requireContext(), tiles)),
        ) { style ->
            styleLoaded = true
            loadedTilesPath = tiles.path
            layers = MainMapLayers.addTo(style, requireContext(), pickedPlace)
            // The view may have been rebuilt over bikes already read, places
            // already named and a position already known: they are put back
            // without the feed, the settings or the system being asked again.
            streetBikes.publish()
            savedPlaces.publish()
            userPosition.showLastKnown()
            publishStations()
            // An address chosen while the map did not exist: now is when it
            // can be reached.
            camera.reachPendingTarget()
        }
    }

    /**
     * The position the map may open on, when the system already holds one.
     *
     * On the user's position when it is already known, since that is what they
     * came to see (see [openingFraming]). Only what the system already holds is
     * read — no fix is requested and no permission is asked for (SPEC §10) —
     * and only a position inside the served city is taken.
     */
    private fun positionToOpenOn(configuration: CityConfiguration): Coordinates? {
        val here = container.deviceLocation
            .takeIf { it.isPermitted() }
            ?.lastKnown()
            ?.takeIf { fix -> opensOnThePosition(fix.coordinates, configuration.boundingBox) }
            ?: return null
        // Shown as well as framed: a map centred on nothing would leave the
        // user guessing which point it settled on.
        userPosition.remember(here)
        return here.coordinates
    }

    /**
     * Redraws the markers for the stations the filter leaves standing.
     *
     * The stations are sifted **before** the features are built rather than by
     * redrawing them all and hiding some: the list runs to several hundred
     * entries and is replayed at every refresh.
     */
    private fun publishStations() {
        val kind = filters.bikeKindFilter()
        val mode = filters.mode
        val shown = stationsShownOnMap(viewModel.state.value.stations, mapFilter, mode, kind)
        val source = layers?.stations ?: return
        source.setGeoJson(StationMarkers.toFeatureCollection(shown, mode, kind))
    }

    /**
     * Follows the two filters set in the settings (SPEC §7.1, §7.6).
     *
     * The screen obeys and reports nothing: the markers go and come back, and
     * that is the whole of what happens here. Which is why the setting is read
     * as a flow rather than once — coming back from the settings must find the
     * map already redrawn, without the fragment being rebuilt for it.
     */
    private fun followStationFilters() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    container.preferences.hideOutOfServiceStations,
                    container.preferences.hideEmptyStations,
                ) { hideOutOfService, hideEmpty ->
                    StationFilter(hideOutOfService = hideOutOfService, hideEmpty = hideEmpty)
                }
                    // The two flows come from one file, so a single flick would
                    // otherwise redraw several hundred markers twice.
                    .distinctUntilChanged()
                    .collect { filter ->
                        mapFilter = filter
                        publishStations()
                    }
            }
        }
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
        views.openList.isVisible = false
        views.openSettings.isVisible = false
        views.openSearch.isVisible = false
        views.openJourney.isVisible = false
        views.modeToggle.isVisible = false
        // The crosshair, its button and "locate me" wait on loadTilesFor to
        // say whether there is a base map at all: with none there is nothing
        // to aim at, and they would only stand under the panel that says so.
        views.locateMe.isVisible = false
        views.pickConfirm.setOnClickListener { confirmPickedPoint() }
        // That panel offers the station list, which from here would abandon
        // the journey being composed. The way out of a picker is the way back
        // to the choice of the end it was opened for.
        views.missingTilesList.setText(R.string.action_back)
        views.missingTilesList.setOnClickListener { parentFragmentManager.popBackStack() }
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
     * Looks for the position and brings the map to it.
     *
     * The position is neither kept nor written: it serves to frame the map,
     * then lives in the display source for the session (SPEC §2, C3).
     */
    private fun locateMe() {
        viewLifecycleOwner.lifecycleScope.launch {
            // A first fix can take several seconds indoors. Disabling the
            // button meanwhile avoids suggesting the press was lost — and
            // avoids stacking several of them — and the breathing says the
            // wait is a search, a button merely dead for ten seconds reading
            // as a button that is broken (SPEC §7.1). The screen reader is
            // told the same thing in words.
            binding?.locateMe?.isEnabled = false
            val searching = binding?.locateMe?.let(::breathe)
            binding?.locateMe?.announceForAccessibility(getString(R.string.map_locating))
            val fix = try {
                container.deviceLocation.current()
            } finally {
                searching?.cancel()
                binding?.locateMe?.alpha = 1f
                binding?.locateMe?.isEnabled = true
            }
            if (fix == null) {
                showMessage(R.string.map_location_unavailable)
                return@launch
            }
            val position = fix.coordinates
            userPosition.show(fix)
            // Outside the city served there is nothing to move to: the camera
            // is held inside that city's box, so it would stop at the edge
            // nearest the user and show that as their position — a point on a
            // street they are a hundred kilometres from, with nothing saying
            // so. What the moment calls for is the other city, not a framing.
            if (liesOutside(position, servedCity?.boundingBox)) {
                elsewhere.sayWeAreElsewhere(position)
                return@launch
            }
            camera.moveTo(LatLng(position.latitude, position.longitude), USER_POSITION_ZOOM)
        }
    }

    /**
     * Makes [button] breathe until the returned animation is cancelled.
     *
     * What waiting for a fix looks like. Where the system's "remove
     * animations" setting is on, the button holds the faded state instead:
     * still a sign of life, without the movement the user asked to be spared.
     * The caller restores the alpha, whichever of the two was in force.
     */
    private fun breathe(button: View): Animator? {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            button.alpha = BREATH_FLOOR_ALPHA
            return null
        }
        return ObjectAnimator.ofFloat(button, View.ALPHA, 1f, BREATH_FLOOR_ALPHA).apply {
            duration = BREATH_MILLIS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun showMessage(message: Int) {
        showMessage(context?.getText(message) ?: return)
    }

    /**
     * Says something back about a gesture just made on the map.
     *
     * Handed to the activity, which owns the one banner the screen has: a
     * message put up from here would be taken down by the next failed refresh
     * without anything weighing the two (see `MainActivity.showMessage`).
     */
    private fun showMessage(message: CharSequence) {
        val host = activity as? MainActivity ?: return
        host.showMessage(message, MessageSubject.Answer)
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
            pickedPlace.designate(
                position = Coordinates(
                    result.getDouble(AddressSearchFragment.RESULT_LATITUDE),
                    result.getDouble(AddressSearchFragment.RESULT_LONGITUDE),
                ),
                label = result.getString(AddressSearchFragment.RESULT_LABEL).orEmpty(),
            )
        }
    }

    /**
     * Opens what can be done with the point resting on the map (SPEC §7.2).
     *
     * The pill used to have one answer — it erased the point — which is all a
     * found address was ever good for. The sheet is where the point becomes an
     * end of a journey, is handed to a navigation application, or goes.
     *
     * **Not while the map serves to designate a point** (SPEC §7.3): that
     * screen already asks one question and carries its own "choose this point"
     * button, and a sheet offering to compose a journey would answer a question
     * nobody put. The pill is in fact never shown there — the picker opens no
     * address search and receives no place — so this is the guard that keeps
     * that true rather than a case met today.
     */
    private fun openPickedPlaceSheet() {
        if (isPicking()) return
        val place = pickedPlace.place ?: return
        val endpoint = JourneyEndpoint(label = place.label, position = place.position)
        PlaceDetailSheet.newInstance(endpoint).show(childFragmentManager, PlaceDetailSheet.TAG)
    }

    /**
     * Collects what the place's sheet was asked for.
     *
     * Registered where the screen is built rather than where the sheet is put
     * up, for the reason [io.github.mgdx.rouelibre.ui.journey.JourneyHandover]
     * gives of its own menu: the sheet comes back on its own after the phone is
     * turned, and its answer would otherwise arrive with nobody listening.
     *
     * The point comes back with the answer rather than being read from the
     * field: the two agree, and it is the sheet that was asked about it.
     */
    private fun listenForPlaceActions() {
        childFragmentManager.setFragmentResultListener(
            PlaceDetailSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            val place = JourneyEndpoint.readFrom(result, PlaceDetailSheet.RESULT_PLACE)
                ?: return@setFragmentResultListener
            when (result.getString(PlaceDetailSheet.RESULT_ACTION)) {
                PlaceAction.Clear.name -> pickedPlace.clear()
                PlaceAction.LeaveFromHere.name ->
                    show(JourneySearchFragment.newInstance(origin = place))

                PlaceAction.GoThere.name ->
                    show(JourneySearchFragment.newInstance(destination = place))
            }
        }
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
        pickedPlace.designate(requested.position, requested.label)
    }

    /**
     * Rebuilds the map when the city's base map is replaced under the screen,
     * and says so while it is on its way.
     *
     * A download started from the storage screen runs on after the user has
     * left it: the transfer belongs to the application, not to that screen.
     * The install can therefore land while the map is the screen in front, and
     * nothing about coming back to the map would then reload it; it would draw
     * the version that was replaced until the application was killed, which is
     * what Washington's update did. The transfer starting and ending is
     * followed too, for the panel shown while there are no tiles yet.
     */
    private fun followTheInstalledMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    container.datasetStore.installed.map { it[DatasetKind.Tiles]?.sha256 },
                    container.datasetTransfer.state.map { it.isRunning },
                ) { tiles, running -> tiles to running }
                    .distinctUntilChanged()
                    .collect { loadTilesFor(container.activeCityState()) }
            }
        }
    }

    private fun observeStations() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    publishStations()
                    showFreshness(state)
                }
            }
        }
    }

    /**
     * Says what a failed refresh has to say, when it has anything (SPEC §7.8).
     *
     * The one error this screen keeps quiet about is having chosen no city —
     * see [worthSayingOnTheMap] for why the map answers it with silence where
     * the station list answers it with the chooser.
     */
    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { error ->
                    val host = activity as? MainActivity ?: return@collect
                    if (!worthSayingOnTheMap(error)) return@collect
                    // A refresh that failed gives way to an answer already on
                    // the banner (SPEC §7.8): offline is the ordinary state
                    // here, and this message used to wipe the one telling the
                    // user that the place they had just been sent was out of
                    // reach. It comes back at the next tick, and the freshness
                    // line says meanwhile how old the counters are.
                    host.showMessage(
                        error.toUserMessage(
                            requireContext(),
                            hasKnownAvailability = viewModel.state.value.fetchedAt != null,
                        ),
                        MessageSubject.Refresh,
                        actionLabel = R.string.action_retry,
                    ) {
                        viewModel.refresh(force = true)
                        // Asked for by hand: the gate that spares the feed is
                        // stood down, as it is for the stations.
                        viewLifecycleOwner.lifecycleScope.launch {
                            streetBikes.refresh(force = true)
                        }
                    }
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
                    // The same tick asks for the vehicle feed, which the
                    // repository serves from memory until the same minute is
                    // up (SPEC §4.1).
                    streetBikes.refresh()
                    showFreshness(viewModel.state.value)
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    /**
     * Rewrites the age line, or says the stations are loading while a network's
     * first availability is on its way (SPEC §4.1). Called on every emission of
     * the state as well as on the tick, so that the start and the end of that
     * first refresh show at once.
     */
    private fun showFreshness(state: StationsUiState) {
        val views = binding ?: return
        val freshness = freshnessOf(state.fetchedAt, Instant.now())
        views.freshness.text =
            freshness.toStatusLine(requireContext(), freshness.isStale, state.isRefreshing)
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Goes to the station list, coming back to the one behind where there is one.
     *
     * The other half of the rule the list's own way to the map follows
     * (SPEC §7.6): the two screens lead to each other, so building a second copy
     * on each press would stack them without end. Every transaction between the
     * pair is named, which is what lets either of them find the other behind it.
     */
    private fun showTheList() {
        val manager = parentFragmentManager
        when (
            screenBehind(
                STATION_LIST_BACK_STACK_ENTRY,
                MAP_BACK_STACK_ENTRY,
                manager.backStackEntryNames(),
            )
        ) {
            ScreenBehind.Stacked -> manager.popBackStack(STATION_LIST_BACK_STACK_ENTRY, 0)

            ScreenBehind.Underneath -> manager.popBackStack(
                MAP_BACK_STACK_ENTRY,
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )

            ScreenBehind.Nowhere -> manager.beginTransaction()
                .replace(R.id.content, StationListFragment())
                .addToBackStack(STATION_LIST_BACK_STACK_ENTRY)
                .commit()
        }
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
        camera.save(outState)
        pickedPlace.save(outState)
        filters.save(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding?.map?.onLowMemory()
    }

    override fun onDestroyView() {
        camera.rememberAndRelease()
        attitude.stopFollowing()
        binding?.map?.onDestroy()
        pickedPlace.detach()
        layers?.userPosition?.cancel()
        layers = null
        mapLibreMap = null
        styleLoaded = false
        loadedTilesPath = null
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

        const val FRESHNESS_TICK_MILLIS = 10_000L

        /**
         * The "locate me" button's breathing, one way, in milliseconds.
         *
         * Six hundred: the pace of a calm breath — fast enough to read as
         * work under way, slow enough not to read as an alarm.
         */
        const val BREATH_MILLIS = 600L

        /**
         * How far the breathing fades the button.
         *
         * Forty per cent: faded enough for the movement to be seen in
         * sunlight, present enough for the control to remain visibly a
         * button.
         */
        const val BREATH_FLOOR_ALPHA = 0.4f
    }
}
