package io.github.mgdx.rouelibre.ui.map

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.databinding.SheetPlaceDetailBinding
import io.github.mgdx.rouelibre.ui.handOverToNavigation
import io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint
import kotlinx.coroutines.launch

/**
 * What can be done with a place found on the map, in a sheet sliding up from
 * the bottom (SPEC §7.2, §7.3).
 *
 * The station sheet's counterpart for a point that is not a station: an address
 * the search found, a place another application sent us (SPEC §7.8), the point
 * the map still held when the phone was turned. A point on the map one wants to
 * make an end of a journey out of is the same kind of object whether a network
 * put a stand on it or not, so this sheet borrows that one's wording, its
 * button order and its hierarchy rather than inventing a second arrangement.
 *
 * **The sheet decides nothing itself**: it answers with what was pressed and
 * lets the map act, which is what lets that answer survive the phone being
 * turned — see [MapFragment.listenForPlaceActions].
 *
 * It holds nothing but its argument: the place is read back from the bundle at
 * every rebuild, and reaches no disk (SPEC §8).
 */
class PlaceDetailSheet : BottomSheetDialogFragment() {

    private var binding: SheetPlaceDetailBinding? = null

    /**
     * The area the installed data covers, `null` until it has been read.
     *
     * A place outside it is a real place, and no journey of ours can reach it
     * (see [placeSheetContent]).
     */
    private var coveredArea: BoundingBox? = null

    private val place: JourneyEndpoint
        get() = checkNotNull(JourneyEndpoint.readFrom(arguments, ARGUMENT_PLACE)) {
            "the sheet was opened without a place"
        }

    /**
     * Opens the sheet on its actions rather than on a strip of itself.
     *
     * The station sheet's reasoning, and for the same reason: in landscape the
     * framework's collapsed height falls to the 64 dp floor, which would show
     * the place's name and leave every button under the edge of the screen.
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
        val created = SheetPlaceDetailBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.setAsOrigin.setOnClickListener { answer(PlaceAction.LeaveFromHere) }
        views.setAsDestination.setOnClickListener { answer(PlaceAction.GoThere) }
        views.clear.setOnClickListener { answer(PlaceAction.Clear) }
        views.openInNavigation.setOnClickListener { openInNavigationApp() }

        show()
        viewLifecycleOwner.lifecycleScope.launch {
            val container = (requireActivity().application as RoueLibreApplication).container
            coveredArea = container.activeCity()?.boundingBox
            // The sheet is already drawn: what has just been learnt is whether
            // the journey it offers can exist at all.
            show()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun show() {
        val views = binding ?: return
        val content = placeSheetContent(
            label = place.label,
            position = place.position,
            coveredArea = coveredArea,
            whenUnnamed = getString(R.string.place_sheet_title),
        )
        views.name.text = content.title
        views.beyondArea.isVisible = !content.offersJourney
        views.setAsOrigin.isEnabled = content.offersJourney
        views.setAsDestination.isEnabled = content.offersJourney
    }

    /**
     * Says what was pressed and stands down.
     *
     * The place travels back with the answer: the screen behind is rebuilt on
     * its own state, and handing it the point it is being asked about spares
     * both sides having to agree on which point that was.
     */
    private fun answer(action: PlaceAction) {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action.name)
                place.writeTo(this, RESULT_PLACE)
            },
        )
        dismiss()
    }

    /**
     * Hands the place over to a navigation application (SPEC §7.2).
     *
     * The sheet stays open: unlike the two journey buttons, this one leaves for
     * another application, and coming back to a map that had quietly forgotten
     * the point would be a press that undid itself.
     */
    private fun openInNavigationApp() {
        handOverToNavigation(place.label, place.position) { message ->
            val views = binding ?: return@handOverToNavigation
            Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ARGUMENT_PLACE = "place"

        /** The key the sheet answers under. */
        const val REQUEST_KEY: String = "place-detail"

        /** What was pressed, as the name of a [PlaceAction]. */
        const val RESULT_ACTION: String = "action"

        /** The prefix the place the answer is about is written under. */
        const val RESULT_PLACE: String = "place"

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "detail-place"

        /** Opens the sheet on the given place. */
        fun newInstance(place: JourneyEndpoint): PlaceDetailSheet = PlaceDetailSheet().apply {
            arguments = Bundle().apply { place.writeTo(this, ARGUMENT_PLACE) }
        }
    }
}

/** What the user asked of a place, from its sheet. */
enum class PlaceAction {
    /** Make it the point the journey starts from. */
    LeaveFromHere,

    /** Make it the point the journey ends at. */
    GoThere,

    /** Take it off the map. */
    Clear,
}
