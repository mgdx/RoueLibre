package io.github.mgdx.rouelibre.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.databinding.FragmentMapBinding
import io.github.mgdx.rouelibre.ui.stations.StationListFragment
import io.github.mgdx.rouelibre.ui.stations.StationsViewModel
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import java.time.Instant

/**
 * Carte des stations — l'écran principal (SPEC §7.1).
 *
 * Le fond de carte est lu depuis un fichier MBTiles installé sur l'appareil et
 * les glyphes sont dans l'APK : afficher, déplacer ou zoomer la carte ne fait
 * sortir aucune requête. Seules les disponibilités viennent du réseau.
 *
 * Cet écran suppose que le fond de carte est installé. C'est à l'appelant de
 * le vérifier — sans quoi il n'y a rien à afficher, et la liste des stations
 * reste le mode dégradé prévu par le SPEC §4.4.
 */
class MapFragment : Fragment() {

    private var binding: FragmentMapBinding? = null
    private var mapLibreMap: MapLibreMap? = null
    private var stationSource: GeoJsonSource? = null
    private var styleLoaded = false

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
        // Obligatoire avant que la vue de carte ne soit gonflée : MapLibre
        // charge ses bibliothèques natives à ce moment-là.
        MapLibre.getInstance(requireContext())
        val created = FragmentMapBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.map.onCreate(savedInstanceState)
        views.openStorage.setOnClickListener { show(StorageFragment()) }
        views.openList.setOnClickListener { show(StationListFragment()) }
        views.modeToggle.setOnClickListener { toggleMode() }
        applyModeLabel()

        views.missingTilesStorage.setOnClickListener { show(StorageFragment()) }
        views.missingTilesList.setOnClickListener { show(StationListFragment()) }

        applySystemInsets(views)
        views.map.getMapAsync(::onMapReady)

        observeStations()
        observeErrors()
        keepAvailabilityFresh()
    }

    /**
     * Écarte les commandes des barres système.
     *
     * La carte occupe tout l'écran, barres comprises — c'est ce qui lui donne
     * son ampleur. Les commandes posées dessus, elles, doivent rester
     * atteignables : sans cette marge, l'étiquette de fraîcheur passait sous
     * l'heure et le bouton de liste sous la barre de navigation.
     */
    private fun applySystemInsets(views: FragmentMapBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(views.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            views.freshness.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + baseMargin
            }
            views.openStorage.updateLayoutParams<ViewGroup.MarginLayoutParams> {
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
     * Charge le style si le fond de carte est là, sinon montre l'explication.
     *
     * Appelée aussi au retour à l'écran : l'utilisateur peut être parti
     * installer les tuiles entre-temps, et la carte doit alors apparaître
     * sans qu'il ait à relancer l'application.
     */
    private fun loadTilesIfInstalled() {
        val views = binding ?: return
        val map = mapLibreMap ?: return
        if (styleLoaded) return

        val tiles = container.datasetStore.fileOf(DatasetKind.Tiles)
        views.missingTiles.isVisible = tiles == null
        views.modeToggle.isVisible = tiles != null
        views.attribution.isVisible = tiles != null
        if (tiles == null) return

        val configuration = container.cityConfiguration

        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.setMinZoomPreference(configuration.map.minZoom.toDouble())
        // Au-delà du zoom maximal des tuiles, MapLibre agrandit les dernières
        // disponibles. Laisser un cran permet de s'approcher un peu sans que
        // le texte devienne illisible.
        map.setMaxZoomPreference(configuration.map.maxZoom.toDouble() + 1)
        map.cameraPosition = openingCamera(configuration)

        map.setStyle(
            Style.Builder().fromJson(MapStyleLoader.load(requireContext(), tiles)),
        ) { style ->
            styleLoaded = true
            addStationLayers(style)
            publishStations()
        }
    }

    /**
     * Cadrage d'ouverture, faute de position connue.
     *
     * La permission de localisation n'est pas demandée au lancement (SPEC
     * §10) : la carte s'ouvre donc sur le centre déclaré dans la configuration
     * de ville, jamais sur une position obtenue à l'insu de l'utilisateur.
     */
    private fun openingCamera(configuration: CityConfiguration): CameraPosition =
        CameraPosition.Builder()
            .target(
                LatLng(
                    configuration.map.centre.latitude,
                    configuration.map.centre.longitude,
                ),
            )
            .zoom(configuration.map.defaultZoom)
            .build()

    /**
     * Installe la source des stations et ses quatre couches.
     *
     * Le regroupement est confié à MapLibre (SPEC §7.1) : au-delà du zoom
     * indiqué, les stations proches fusionnent en un amas portant leur nombre.
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
    }

    private fun publishStations() {
        val source = stationSource ?: return
        source.setGeoJson(
            StationMarkers.toFeatureCollection(viewModel.state.value.stations, mode),
        )
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
        // Le bouton porte le nom de ce qui est AFFICHÉ, pas de ce qu'un appui
        // ferait : c'est une étiquette d'état, comme la bascule de la liste.
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

    /** Rafraîchit et réécrit l'âge affiché tant que la carte est visible. */
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

    // ------------------------------------------------- cycle de vie carte --
    // MapLibre gère un contexte graphique natif : sans ces relais, il fuit.

    override fun onStart() {
        super.onStart()
        binding?.map?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding?.map?.onResume()
        loadTilesIfInstalled()
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
        stationSource = null
        mapLibreMap = null
        styleLoaded = false
        binding = null
        super.onDestroyView()
    }

    private companion object {
        /**
         * Rayon de regroupement, en pixels. Cinquante laisse les stations du
         * centre de Lille distinctes dès qu'on s'en approche, sans faire
         * grouiller la vue d'ensemble.
         */
        const val CLUSTER_RADIUS = 50

        /** Au-delà, chaque station reprend son marqueur propre. */
        const val CLUSTER_MAX_ZOOM = 13

        const val FRESHNESS_TICK_MILLIS = 10_000L
    }
}
