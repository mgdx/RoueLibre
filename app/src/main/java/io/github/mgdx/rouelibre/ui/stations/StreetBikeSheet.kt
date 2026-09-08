package io.github.mgdx.rouelibre.ui.stations

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.station.BikeCharge
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.core.station.isBeyondCoveredArea
import io.github.mgdx.rouelibre.databinding.SheetStreetBikeBinding
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.handOverToNavigation
import io.github.mgdx.rouelibre.ui.journey.JourneySearchFragment
import io.github.mgdx.rouelibre.ui.journey.StreetBikeHandle
import io.github.mgdx.rouelibre.ui.toStatusLine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The detail of a bike outside stations, in a sheet sliding up from the bottom
 * (SPEC §7.2.1).
 *
 * [StationDetailSheet]'s screen with less to say, because a bike is one bike
 * and nothing is counted on it: what it is, what charge it holds, how far it
 * is, and how old the reading is. It stays alive while it is shown, so a bike
 * that leaves the feed says so on the spot.
 *
 * **It says nothing about the right to take the bike**, which the network's
 * rules decide and GBFS does not carry (SPEC §4.1). What the application knows
 * is what the feed reports, and the row at the foot of the sheet reopens the
 * explanation that says exactly that.
 */
class StreetBikeSheet : BottomSheetDialogFragment() {

    private var binding: SheetStreetBikeBinding? = null

    /**
     * The area the installed data covers, `null` until it has been read.
     *
     * A bike outside it is a real bike the feed reports, and no journey of ours
     * can reach it: the same answer a station beyond the box already gets
     * (see [isBeyondCoveredArea]).
     */
    private var coveredArea: BoundingBox? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: StreetBikeViewModel by viewModels {
        StreetBikeViewModel.Factory(
            streetBikes = container.stationRepository.observeStreetBikes(),
            fleet = container.fleetRepository.fleet,
            // The position filtered by the city served, as the station sheet
            // takes it: outside the conurbation being consulted there is no
            // distance worth saying.
            knownPositionInCity = { container.knownPositionInsideActiveCity() },
            bikeId = requireArguments().getString(ARGUMENT_BIKE_ID).orEmpty(),
        )
    }

    /**
     * Opens the sheet on its actions rather than on a strip of itself.
     *
     * The station sheet's reasoning, and it holds here for the same measure:
     * the collapsed height a bottom sheet is born at is a rule of the framework
     * that knows nothing of what the sheet holds, and sideways it falls to the
     * 64 dp floor — which showed the title and left every action below the edge
     * of the screen.
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
        val created = SheetStreetBikeBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.journeyFromHere.setOnClickListener { prepareJourney() }
        views.openInNavigation.setOnClickListener { openInNavigationApp() }
        views.whatTheseBikesAre.setOnClickListener {
            StreetBikeIntroDialogFragment.explain(parentFragmentManager)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            coveredArea = container.activeCity()?.boundingBox
            // The sheet may already be drawn: what has just been learnt is
            // whether the bike stands where our data reaches at all.
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

    private fun show(state: StreetBikeUiState) {
        val views = binding ?: return
        views.kind.setText(
            when (state.kind) {
                StreetBikeKind.Mechanical -> R.string.street_bike_kind_mechanical
                StreetBikeKind.Electric -> R.string.street_bike_kind_electric
                StreetBikeKind.Cargo -> R.string.street_bike_kind_cargo
            },
        )
        showCharge(state.charge)
        showDistance(state.distanceInMetres)
        showState(state)
        showFreshness(state.fetchedAt)
    }

    /**
     * Says what charge the bike holds, or says nothing at all.
     *
     * The percentage where the network publishes one, the range only where it
     * and the type's maximum are both above zero, nothing otherwise — the
     * order is `core`'s and is a measurement rather than a preference
     * (SPEC §4.1). The line disappears rather than lie, which is the rule the
     * station sheet's own split already follows.
     */
    private fun showCharge(charge: BikeCharge?) {
        val views = binding ?: return
        views.charge.isVisible = charge != null
        views.charge.text = when (charge) {
            null -> ""
            is BikeCharge.Ratio -> getString(
                R.string.street_bike_charge,
                // A whole percentage: the feed's own figure carries decimals
                // no rider can act on, and "67.4 %" reads as a precision
                // nobody measured.
                (charge.value * PERCENT).roundToInt(),
            )

            is BikeCharge.Range -> getString(
                R.string.street_bike_range,
                requireContext().formatDistance(charge.metres.toDouble()),
            )
        }
    }

    private fun showDistance(distanceInMetres: Double?) {
        val views = binding ?: return
        views.distance.isVisible = distanceInMetres != null
        views.distance.text = distanceInMetres
            ?.let { requireContext().formatDistance(it) }
            .orEmpty()
    }

    /**
     * Says the one thing that stands in the way, when something does.
     *
     * Two states share the line, and both withdraw the journey. **A bike gone
     * comes first**: it is the newer fact and it settles the sheet whatever
     * else is true of the place it stood in. Beyond the data comes next — the
     * bike is real and the feed counts it, but the route is computed over a
     * graph cut from the city's box, so offering the button and answering "no
     * usable route" afterwards would tell the reader they got something wrong
     * when nothing was ever on offer.
     *
     * **The handover to a navigation application survives both**, that one not
     * running on our graph; it is only withdrawn for a bike that has gone,
     * there being nothing left to be guided to.
     */
    private fun showState(state: StreetBikeUiState) {
        val views = binding ?: return
        val bike = state.bike
        val beyond = bike != null && bike.position.isBeyondCoveredArea(coveredArea)
        views.state.isVisible = state.isGone || beyond
        if (state.isGone) {
            views.state.setText(R.string.street_bike_gone)
        } else if (beyond) {
            views.state.setText(R.string.journey_outside_coverage)
        }
        views.journeyFromHere.isEnabled = bike != null && !state.isGone && !beyond
        views.openInNavigation.isEnabled = bike != null && !state.isGone
    }

    /**
     * Writes how old the reading is, and marks it frozen past five minutes.
     *
     * It is the **feed's** age and not the bike's: a street bike's own
     * `last_reported` is optional, nextbike publishes none, and Fifteen stamps
     * the instant the feed was served (SPEC §7.2.1).
     */
    private fun showFreshness(fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        views.freshness.text = freshness.toStatusLine(requireContext(), freshness.isStale)
    }

    /**
     * Opens the journey search on this bike (SPEC §7.2.1, §7.3).
     *
     * The station sheet's move, with the one difference that makes this journey
     * what it is: a station is handed over as a point like any other, to be
     * placed at either end, where a bike is handed over as **the bike the
     * journey sets off on** — the only journey a street bike is ever part of
     * (SPEC §6). The search screen fills its origin from it and holds it there,
     * and what is left to ask is where one is going.
     *
     * The sheet closes first, as that one does: left open over the search
     * screen it would hide the field that has just been filled. The manager is
     * captured before dismissing, the sheet no longer being attached after it.
     */
    private fun prepareJourney() {
        val bike = viewModel.state.value.bike ?: return
        val handle = StreetBikeHandle(
            id = bike.id,
            position = bike.position,
            // The kind the ride is traced on, which is not always the word the
            // sheet says above it — see `StreetBikeUiState.rideKind`.
            kind = viewModel.state.value.rideKind,
            chargeRatio = bike.chargeRatio,
            rangeMetres = bike.rangeMetres,
        )
        val manager = requireActivity().supportFragmentManager
        dismiss()
        manager.beginTransaction()
            .replace(R.id.content, JourneySearchFragment.newInstance(streetBike = handle))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Hands the bike over to a navigation application (SPEC §7.2.1).
     *
     * The same handover the station sheet offers, through the same `geo:` URI
     * and with this application kept out of the chooser (see
     * [handOverToNavigation]). What travels as the label is what the bike is —
     * electric or not — since the receiving application shows a named place and
     * a bike left in a street has no name of its own.
     */
    private fun openInNavigationApp() {
        val state = viewModel.state.value
        val bike = state.bike ?: return
        val label = getString(
            if (state.kind == StreetBikeKind.Electric) {
                R.string.street_bike_marker_description_electric
            } else {
                R.string.street_bike_marker_description
            },
        )
        handOverToNavigation(label, bike.position) { message ->
            val views = binding ?: return@handOverToNavigation
            Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ARGUMENT_BIKE_ID = "bike-id"

        /** A ratio from the feed, written as a percentage. */
        private const val PERCENT = 100

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "detail-street-bike"

        /** Opens the sheet for the given bike. */
        fun newInstance(bikeId: String): StreetBikeSheet = StreetBikeSheet().apply {
            arguments = Bundle().apply { putString(ARGUMENT_BIKE_ID, bikeId) }
        }
    }
}
