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
 * Résultat d'un trajet marche → vélo → marche (SPEC §7.4).
 *
 * Le tracé occupe le haut de l'écran en trois segments distincts, le détail se
 * lit dessous : on regarde d'abord où l'on va, on lit ensuite combien de temps
 * et par quelles stations.
 *
 * Rien n'est enregistré. Le trajet vit en mémoire le temps de l'écran, comme
 * l'exige le SPEC §8.
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
            "point de départ absent"
        }
        val destination = checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)) {
            "point d'arrivée absent"
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
        // Sans fond de carte, le tracé reste dessiné sur un fond vide : c'est
        // moins parlant, mais l'itinéraire est calculé et le détail se lit.
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
     * Dit ce qui manque quand aucun trajet à vélo n'a pu être composé.
     *
     * Le SPEC §6 l'exige : quand aucune station proche n'a de vélo, il faut le
     * dire, pas proposer un trajet impossible.
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

    /** Prévient quand la marche directe va plus vite que le vélo (SPEC §6). */
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

    /** Les trois étapes, dans l'ordre où on les vit. */
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
     * Les autres couples de stations (SPEC §6).
     *
     * Ils existent parce que le plus rapide n'est pas toujours le meilleur :
     * une station un peu plus loin mais mieux fournie peut valoir la minute
     * qu'elle coûte.
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

    /** Trace la proposition retenue et cadre la carte dessus. */
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

    /** Cadre la carte sur l'ensemble du tracé, avec une marge confortable. */
    private fun frameOn(points: List<LatLng>) {
        val map = mapLibreMap ?: return
        if (points.size < 2) return
        val bounds = LatLngBounds.Builder().includes(points).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, FRAME_PADDING_PIXELS))
    }

    // ------------------------------------------------- cycle de vie carte --

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
        private const val ARGUMENT_ORIGIN = "depart"
        private const val ARGUMENT_DESTINATION = "arrivee"

        /** Marge autour du tracé, en pixels, pour qu'il ne touche pas les bords. */
        private const val FRAME_PADDING_PIXELS = 80

        /** Ouvre le résultat pour un couple de points déjà désignés. */
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
