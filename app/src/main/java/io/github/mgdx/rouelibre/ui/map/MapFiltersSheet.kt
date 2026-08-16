package io.github.mgdx.rouelibre.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.StationFilter
import io.github.mgdx.rouelibre.databinding.SheetMapFiltersBinding

/**
 * The two switches deciding which stations the map draws (SPEC §7.1).
 *
 * A sheet rather than two more controls laid over the map: SPEC §7 asks for the
 * map to stay the scenery and the stations the subject, and this is a question
 * one puts once and then leaves alone. The sheet does not cover the map, so the
 * markers going and coming back are visible while the switches are being set —
 * which is the answer to "what does this do", given rather than promised.
 *
 * It answers with every flick rather than at a confirmation: there is nothing to
 * validate, and a filter one cannot see working is a filter one distrusts.
 */
class MapFiltersSheet : BottomSheetDialogFragment() {

    private var binding: SheetMapFiltersBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetMapFiltersBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        val arguments = requireArguments()
        val mode = AvailabilityMode.valueOf(
            arguments.getString(ARGUMENT_MODE) ?: AvailabilityMode.Bikes.name,
        )

        // "Empty" is not one thing: it means no bike to borrow while bikes are
        // counted, and no free dock to return one to while docks are. One
        // wording for both would be true of neither.
        views.hideEmpty.setText(
            when (mode) {
                AvailabilityMode.Bikes -> R.string.map_filter_hide_empty_bikes
                AvailabilityMode.Docks -> R.string.map_filter_hide_empty_docks
            },
        )
        views.hideOutOfService.isChecked = arguments.getBoolean(ARGUMENT_HIDE_OUT_OF_SERVICE)
        views.hideEmpty.isChecked = arguments.getBoolean(ARGUMENT_HIDE_EMPTY)

        views.hideOutOfService.setOnCheckedChangeListener { _, _ -> publish() }
        views.hideEmpty.setOnCheckedChangeListener { _, _ -> publish() }
    }

    /** Hands the map the filter as it now stands. */
    private fun publish() {
        val views = binding ?: return
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putBoolean(RESULT_HIDE_OUT_OF_SERVICE, views.hideOutOfService.isChecked)
                putBoolean(RESULT_HIDE_EMPTY, views.hideEmpty.isChecked)
            },
        )
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        /** The tag the sheet is shown under. */
        const val TAG: String = "map-filters"

        /** The key the chosen filter is returned under. */
        const val REQUEST_KEY: String = "map-filters-chosen"

        /** Whether the stations out of service are to be left out. */
        const val RESULT_HIDE_OUT_OF_SERVICE: String = "hide-out-of-service"

        /** Whether the stations read as empty are to be left out. */
        const val RESULT_HIDE_EMPTY: String = "hide-empty"

        private const val ARGUMENT_MODE = "mode"
        private const val ARGUMENT_HIDE_OUT_OF_SERVICE = "hide-out-of-service"
        private const val ARGUMENT_HIDE_EMPTY = "hide-empty"

        /**
         * Opens the sheet on the filter in force.
         *
         * @param filter what the map is leaving out right now.
         * @param mode what the markers count, which decides the wording of the
         *   second switch.
         */
        fun newInstance(filter: StationFilter, mode: AvailabilityMode): MapFiltersSheet =
            MapFiltersSheet().apply {
                arguments = Bundle().apply {
                    putString(ARGUMENT_MODE, mode.name)
                    putBoolean(ARGUMENT_HIDE_OUT_OF_SERVICE, filter.hideOutOfService)
                    putBoolean(ARGUMENT_HIDE_EMPTY, filter.hideEmpty)
                }
            }
    }
}
