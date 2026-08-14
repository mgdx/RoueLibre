package io.github.mgdx.rouelibre.ui.stations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.ServiceState
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.databinding.SheetStationDetailBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A station's detail, in a sheet sliding up from the bottom (SPEC §7.2).
 *
 * Opened from the map as from the list: it is the same station, it deserves the
 * same screen. The sheet stays alive while it is shown — the counts follow the
 * refreshing, they are not frozen at opening time.
 */
class StationDetailSheet : BottomSheetDialogFragment() {

    private var binding: SheetStationDetailBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: StationDetailViewModel by viewModels {
        StationDetailViewModel.Factory(
            repository = container.stationRepository,
            preferences = container.preferences,
            addressIndex = container.addressIndex,
            deviceLocation = container.deviceLocation,
            fleet = container.fleetRepository.fleet,
            stationId = requireArguments().getString(ARGUMENT_STATION_ID).orEmpty(),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetStationDetailBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.favourite.setOnClickListener { viewModel.toggleFavourite() }
        views.setAsOrigin.setOnClickListener { prepareJourney(asOrigin = true) }
        views.setAsDestination.setOnClickListener { prepareJourney(asOrigin = false) }
        views.openInNavigation.setOnClickListener { openInNavigationApp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::show)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun show(state: StationDetailUiState) {
        val views = binding ?: return
        val entry = state.entry ?: return

        views.name.text = entry.station.name
        views.bikesIndicator.display = entry.displayFor(AvailabilityMode.Bikes)
        views.docksIndicator.display = entry.displayFor(AvailabilityMode.Docks)
        showBikeSplit(state.bikeSplit)
        showAddress(state.address, state.distanceInMetres)
        showServiceState(state)
        showCapacityAndFreshness(entry.station, state.fetchedAt)
        showFavourite(state.isFavourite)
    }

    /**
     * Says what the bikes standing there are (SPEC §7.2).
     *
     * A line under the count rather than a second figure inside the disc: the
     * disc answers "is there a bike", which is asked from a map holding fifty
     * stations, while "which bike" is asked once one station is being looked
     * at. Absent whenever the model could not settle it — the city lends one
     * kind, or the feed's breakdown does not add up.
     */
    private fun showBikeSplit(split: BikeSplit?) {
        val views = binding ?: return
        views.bikesSplit.isVisible = split != null
        if (split == null) return
        views.bikesSplit.text = getString(
            R.string.station_bikes_split,
            resources.getQuantityString(
                R.plurals.bikes_mechanical,
                split.mechanical,
                split.mechanical,
            ),
            resources.getQuantityString(
                R.plurals.bikes_electric,
                split.electric,
                split.electric,
            ),
        )
    }

    private fun showAddress(address: AddressResult?, distanceInMetres: Double?) {
        val views = binding ?: return
        val distance = distanceInMetres?.let { requireContext().formatDistance(it) }
        views.address.isGone = address == null && distance == null
        if (address == null) {
            // With no address but a known position, the distance is still
            // worth saying: it places the station relative to oneself.
            views.address.text = distance.orEmpty()
            return
        }
        val place = if (address.postcode.isNullOrBlank()) {
            address.city
        } else {
            getString(R.string.address_locality, address.postcode, address.city)
        }
        // A station standing in the middle of a roundabout has no address: the
        // neighbouring street is named instead, said to be a neighbourhood.
        val what = if (address.houseNumber == null) {
            getString(R.string.station_address_nearby, address.streetName)
        } else {
            address.toTitle(requireContext())
        }
        val located = getString(R.string.address_detail, what, place)
        views.address.text = distance
            ?.let { getString(R.string.address_detail, located, it) }
            ?: located
    }

    /**
     * Says the state only when it stands in the way.
     *
     * A station that works has no need to announce itself: its figures speak.
     * A station out of service, on the other hand, must say so before the user
     * walks over to it.
     */
    private fun showServiceState(state: StationDetailUiState) {
        val views = binding ?: return
        val entry = state.entry ?: return
        views.serviceState.isVisible = entry.serviceState != ServiceState.InService
        views.serviceState.setText(
            when (entry.serviceState) {
                ServiceState.OutOfService -> R.string.station_out_of_service
                else -> R.string.station_availability_unknown
            },
        )
    }

    private fun showCapacityAndFreshness(station: Station, fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        val age = freshness.toStatusLine(requireContext(), freshness.isStale)
        views.capacity.text = station.capacity?.let { capacity ->
            getString(
                R.string.station_capacity_and_age,
                resources.getQuantityString(R.plurals.docks_total, capacity, capacity),
                age,
            )
        } ?: age
    }

    private fun showFavourite(isFavourite: Boolean) {
        val views = binding ?: return
        views.favourite.setIconResource(
            if (isFavourite) R.drawable.ic_favourite_filled else R.drawable.ic_favourite,
        )
        views.favourite.contentDescription = getString(
            if (isFavourite) R.string.station_favourite_remove else R.string.station_favourite_add,
        )
    }

    /**
     * Opens the journey search with this station already placed (SPEC §7.2).
     *
     * A station is a point like any other to the journey algorithm: it is not
     * necessarily the one that will be reached by bike, only the place one
     * leaves from or goes to. The choice of pick-up and drop-off stations
     * remains §6's own.
     *
     * The sheet closes: leaving it open over the search screen would hide the
     * very field that has just been filled.
     */
    private fun prepareJourney(asOrigin: Boolean) {
        val station = viewModel.state.value.entry?.station ?: return
        val endpoint = JourneyEndpoint(station.name, station.position)
        // The manager is captured before dismissing: after that, the sheet is
        // no longer attached to its activity.
        val manager = requireActivity().supportFragmentManager
        dismiss()
        manager.beginTransaction()
            .replace(
                R.id.content,
                if (asOrigin) {
                    JourneySearchFragment.newInstance(origin = endpoint)
                } else {
                    JourneySearchFragment.newInstance(destination = endpoint)
                },
            )
            .addToBackStack(null)
            .commit()
    }

    /**
     * Hands the station over to a navigation application (SPEC §7.2).
     *
     * The `geo:` URI carries the station's name alongside its coordinates: the
     * application receiving the intent then shows a named landmark rather than
     * an anonymous point.
     */
    private fun openInNavigationApp() {
        val station = viewModel.state.value.entry?.station ?: return
        val label = Uri.encode(station.name)
        val uri = (
            "geo:${station.position.latitude},${station.position.longitude}" +
                "?q=${station.position.latitude},${station.position.longitude}($label)"
            ).toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // On a device with no mapping application at all, saying so beats
            // doing nothing whatsoever.
            val views = binding ?: return
            Snackbar.make(
                views.root,
                R.string.station_no_navigation_app,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val ARGUMENT_STATION_ID = "station-id"

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "detail-station"

        /** Opens the sheet for the given station. */
        fun newInstance(stationId: String): StationDetailSheet = StationDetailSheet().apply {
            arguments = Bundle().apply { putString(ARGUMENT_STATION_ID, stationId) }
        }
    }
}
