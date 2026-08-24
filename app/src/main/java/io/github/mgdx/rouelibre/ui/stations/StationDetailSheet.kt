package io.github.mgdx.rouelibre.ui.stations

import android.app.Dialog
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.ServiceState
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.core.station.isBeyondCoveredArea
import io.github.mgdx.rouelibre.databinding.SheetStationDetailBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.inServedDigits
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.toRelativeText
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

    /**
     * The area the installed data covers, `null` until it has been read.
     *
     * A station outside it is a real station with real bikes, and no journey of
     * ours can reach it (see [isBeyondCoveredArea]).
     */
    private var coveredArea: BoundingBox? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: StationDetailViewModel by viewModels {
        StationDetailViewModel.Factory(
            stations = container.stationRepository.observeStations(),
            favouriteStationIds = container.preferences.favouriteStationIds,
            setFavourite = { container.preferences.toggleFavourite(it) },
            nearestAddress = { container.addressIndex.nearestAddress(it) },
            // The position filtered by the city served, the same one the
            // station list orders itself on: outside the conurbation being
            // consulted there is no distance worth saying (SPEC §7.6).
            knownPositionInCity = { container.knownPositionInsideActiveCity() },
            fleet = container.fleetRepository.fleet,
            stationId = requireArguments().getString(ARGUMENT_STATION_ID).orEmpty(),
        )
    }

    /**
     * Opens the sheet on its actions rather than on a strip of itself.
     *
     * The height a bottom sheet is born collapsed at is decided by a rule of
     * the framework's own — the screen less a 16:9 rectangle of it — which
     * knows nothing of what the sheet holds. In portrait that leaves more than
     * this detail needs, so the collapsed sheet already shows all of it; turn
     * the phone sideways and the same rule falls to the 64 dp floor, which
     * showed the station's name and left the three buttons under the edge of
     * the screen.
     *
     * The orientation is not asked about, because it is not the question: this
     * sheet holds one station's detail and the three things one can do with it,
     * and there is no longer list underneath for a collapsed state to preview.
     * It opens expanded wherever it is opened, and skips the collapsed state
     * altogether so that a downward swipe dismisses it instead of hiding its
     * actions again.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
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
            coveredArea = container.activeCity()?.boundingBox
            // The sheet may already be drawn: what has just been learnt is
            // whether the journey it offers can exist at all.
            show(viewModel.state.value)
        }

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
        val bikes = entry.displayFor(AvailabilityMode.Bikes)
        val docks = entry.displayFor(AvailabilityMode.Docks)
        views.bikesIndicator.display = bikes
        views.docksIndicator.display = docks
        // The label agrees with the figure standing in the disc beside it: the
        // count is passed as a quantity and never as an argument, the plural
        // holding the word alone. A disc with no figure — unknown, or out of
        // service — is read as the plural, which is how the pair is named when
        // no count settles it.
        views.bikesLabel.text =
            resources.getQuantityString(R.plurals.counterpart_bikes, bikes.count ?: 0)
        views.docksLabel.text =
            resources.getQuantityString(R.plurals.counterpart_docks, docks.count ?: 0)
        showBikeSplit(state.bikeSplit)
        showAddress(state.address, state.distanceInMetres)
        showServiceState(state)
        showJourneyOffer(state)
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
            // The digits of a postcode are moved to the numeration served,
            // never formatted: a number format would group them into
            // "59 260" (SPEC §9).
            getString(
                R.string.address_locality,
                requireContext().inServedDigits(address.postcode.orEmpty()),
                address.city,
            )
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
     *
     * Being beyond the installed data comes first, and in more words than the
     * rest: it is not a state of the station but of what we hold about it, it
     * will not right itself on the next refresh, and it is the reason the two
     * journey buttons below have gone quiet.
     *
     * A closure that has lasted says so. "Out of service" alone read the same
     * for a station shut this morning and for one that has reported nothing
     * for months, and the two do not call for the same decision: the first is
     * worth waiting out, the second is worth walking past. Under a day the
     * line stays as it was — a station closed two hours ago has nothing to add.
     *
     * `Unknown` gains nothing from the same treatment: a station the real-time
     * feed ignores has no measurement to date.
     */
    private fun showServiceState(state: StationDetailUiState) {
        val views = binding ?: return
        val entry = state.entry ?: return
        if (entry.station.isBeyondCoveredArea(coveredArea)) {
            views.serviceState.isVisible = true
            views.serviceState.setText(R.string.station_beyond_area)
            return
        }
        views.serviceState.isVisible = entry.serviceState != ServiceState.InService
        val silence = entry.silentClosureAge(Instant.now())
        views.serviceState.text = when {
            entry.serviceState != ServiceState.OutOfService ->
                getString(R.string.station_availability_unknown)

            silence == null -> getString(R.string.station_out_of_service)

            else -> getString(
                R.string.station_out_of_service_since,
                getString(R.string.station_out_of_service),
                silence.toRelativeText(requireContext()),
            )
        }
    }

    /**
     * Withdraws the journey a station beyond the data could never be given.
     *
     * The route is computed over a graph cut from the city's box, so a station
     * outside it has no path to or from anywhere: offering the button and
     * answering "no usable route" after the computation tells the user they got
     * something wrong, when it was never on offer.
     *
     * Handing the station to a navigation application stays: that one does not
     * run on our graph, and it is the answer left to somebody who does want to
     * go there.
     */
    private fun showJourneyOffer(state: StationDetailUiState) {
        val views = binding ?: return
        val station = state.entry?.station ?: return
        val reachable = !station.isBeyondCoveredArea(coveredArea)
        views.setAsOrigin.isEnabled = reachable
        views.setAsDestination.isEnabled = reachable
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
