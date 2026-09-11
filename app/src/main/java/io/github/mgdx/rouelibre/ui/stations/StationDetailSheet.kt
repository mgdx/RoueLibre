package io.github.mgdx.rouelibre.ui.stations

import android.app.Dialog
import android.icu.text.ListFormatter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import io.github.mgdx.rouelibre.core.station.BikeCharge
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.ServiceState
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationBikesDetail
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.core.station.isBeyondCoveredArea
import io.github.mgdx.rouelibre.databinding.SheetStationDetailBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.handOverToNavigation
import io.github.mgdx.rouelibre.ui.inServedDigits
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.toRelativeText
import io.github.mgdx.rouelibre.ui.toStatusLine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

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
            vehicles = container.stationRepository.observeStreetBikes(),
            bikesDetailWanted = container.preferences.showStreetBikes,
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
        views.bikesListToggle.setOnClickListener { viewModel.toggleBikesList() }

        viewLifecycleOwner.lifecycleScope.launch {
            coveredArea = container.activeCity()?.boundingBox
            // The sheet may already be drawn: what has just been learnt is
            // whether the journey it offers can exist at all.
            show(viewModel.state.value)
        }
        askForBikesDetail()

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

    /**
     * Asks for the vehicle feed, where the setting of SPEC §7.6 wants it.
     *
     * The map asks on its own tick, but this sheet also opens from the list
     * and from the favourites, where nobody has asked yet; and the
     * repository's gate holds either way, so a sheet opened twice in a minute
     * costs one read. The outcome is not raised: a network publishing no
     * such feed is an ordinary answer, and the line simply stays away.
     */
    private fun askForBikesDetail() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (container.preferences.showStreetBikes.first()) {
                container.stationRepository.refreshStreetBikes()
            }
        }
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
        showBikesDetail(state.bikesDetail)
        showBikesList(state.bikesDetail, state.isBikesListUnfolded, state.bikesFeedsDisagree)
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

    /**
     * Says what the vehicle feed adds about the bikes standing there
     * (SPEC §7.2): the charge of each electric bike, fullest first, and how
     * many are out of service. One line, at most three parts, and absent
     * whenever the model has nothing to say — the setting is off, the
     * network publishes no such feed, or it lists no vehicle here.
     *
     * The percentages are one part and the ranges another, since a producer
     * publishes one or the other. Up to [CHARGES_LISTED] bikes each is named
     * — the reader is comparing them — and beyond that only the spread, "91 %
     * to 99 %": ten figures on a line are no longer read one by one, and the
     * list below names them all. Each list is joined by the system's own
     * formatter in the reader's language, so that "92 %, 78 % and 40 %" is
     * not three strings glued with a comma.
     */
    private fun showBikesDetail(detail: StationBikesDetail?) {
        val views = binding ?: return
        val hasSummary = detail?.hasSummary == true
        views.bikesDetail.isVisible = hasSummary
        if (detail == null || !hasSummary) return
        val parts = buildList {
            chargesLine(
                detail.charges.filterIsInstance<BikeCharge.Ratio>().map { chargeText(it) },
                listed = R.string.station_bikes_battery,
                spread = R.string.station_bikes_battery_spread,
            )?.let(::add)
            chargesLine(
                detail.charges.filterIsInstance<BikeCharge.Range>().map { chargeText(it) },
                listed = R.string.station_bikes_range,
                spread = R.string.station_bikes_range_spread,
            )?.let(::add)
            if (detail.outOfService > 0) {
                add(
                    resources.getQuantityString(
                        R.plurals.station_bikes_out_of_service,
                        detail.outOfService,
                        detail.outOfService,
                    ),
                )
            }
        }
        views.bikesDetail.text =
            parts.joinToString(getString(R.string.station_bikes_detail_separator))
    }

    /**
     * One part of the summary: the charges named, or their spread, or
     * nothing where there is none. The charges arrive fullest first, so the
     * spread runs from the last to the first.
     */
    private fun chargesLine(
        charges: List<String>,
        @StringRes listed: Int,
        @StringRes spread: Int,
    ): String? = when {
        charges.isEmpty() -> null
        charges.size <= CHARGES_LISTED -> getString(
            listed,
            ListFormatter.getInstance(resources.configuration.locales[0]).format(charges),
        )

        else -> getString(spread, charges.last(), charges.first())
    }

    /** "92 %" or "12 km", the figure a bike's charge is written as. */
    private fun chargeText(charge: BikeCharge): String = when (charge) {
        is BikeCharge.Ratio -> getString(
            R.string.station_bike_charge_value,
            (charge.value * PERCENT).roundToInt(),
        )
        is BikeCharge.Range -> requireContext().formatDistance(charge.metres.toDouble())
    }

    /**
     * The bike-by-bike list under the summary, unfolded on request
     * (SPEC §7.2): a row opening the list, and under it one
     * line per bike — the producer's identifier, cut down past twenty
     * characters, its kind where the table
     * knows it, its charge where one can be read, and "reserved" or "out of
     * service" where the feed says so. Absent with the summary's own
     * silences, since it is read from the same feed.
     *
     * The row names no figure. It used to count the bikes it was about to
     * show — "15 bikes at this station" — and that was a second count on a
     * screen that already carries one, read from the other feed: where the
     * two are out of step, the row contradicted the disc three lines above
     * it, and a reader had no way of telling which of the two was the
     * network's mistake.
     *
     * That disagreement is now said in words rather than left to be
     * discovered, and only with the list open: folded, there is nothing on
     * the screen for the count to contradict.
     */
    private fun showBikesList(
        detail: StationBikesDetail?,
        unfolded: Boolean,
        feedsDisagree: Boolean,
    ) {
        val views = binding ?: return
        val bikes = detail?.bikes.orEmpty()
        views.bikesListToggle.isVisible = bikes.isNotEmpty()
        views.bikesList.isVisible = bikes.isNotEmpty() && unfolded
        views.bikesListWarning.isVisible = bikes.isNotEmpty() && unfolded && feedsDisagree
        if (bikes.isEmpty()) return
        views.bikesListToggle.setText(R.string.station_bikes_list_title)
        views.bikesListWarning.setText(R.string.station_bikes_feeds_disagree)
        views.bikesListToggle.setIconResource(
            if (unfolded) R.drawable.ic_fold else R.drawable.ic_unfold,
        )
        if (!unfolded) return
        val separator = getString(R.string.station_bikes_detail_separator)
        views.bikesList.text = bikes.joinToString("\n") { bike ->
            buildList {
                add(bike.label)
                when (bike.kind) {
                    VehicleKind.Mechanical -> add(getString(R.string.street_bike_kind_mechanical))
                    VehicleKind.Electric -> add(getString(R.string.street_bike_kind_electric))
                    VehicleKind.Other, null -> Unit
                }
                bike.charge?.let { add(chargeText(it)) }
                if (bike.isDisabled) add(getString(R.string.station_out_of_service))
                if (bike.isReserved) add(getString(R.string.station_bike_reserved))
            }.joinToString(separator)
        }
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
     * The handover itself is shared with the other screens that offer one, and
     * with it the reason this application is kept out of the chooser: it
     * answers `geo:` too (SPEC §7.8), and the press used to reopen Roue Libre
     * on the very station one was leaving (see [handOverToNavigation]).
     */
    private fun openInNavigationApp() {
        val station = viewModel.state.value.entry?.station ?: return
        handOverToNavigation(station.name, station.position) { message ->
            // On a device with nothing to guide with, saying so beats doing
            // nothing whatsoever — on the sheet's own root, where the press was.
            val views = binding ?: return@handOverToNavigation
            Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        /** A ratio from the feed, written as a percentage. */
        private const val PERCENT = 100

        /**
         * How many charges the summary names before giving their spread
         * instead. Four is what one compares at a glance — the reader is
         * choosing a bike among them — and past it the figures were read as a
         * row of near-identical numbers on the Berlin hubs, ten times "99 %".
         * The list under the line names every bike, so nothing is lost.
         */
        private const val CHARGES_LISTED = 4

        private const val ARGUMENT_STATION_ID = "station-id"

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "detail-station"

        /** Opens the sheet for the given station. */
        fun newInstance(stationId: String): StationDetailSheet = StationDetailSheet().apply {
            arguments = Bundle().apply { putString(ARGUMENT_STATION_ID, stationId) }
        }
    }
}
