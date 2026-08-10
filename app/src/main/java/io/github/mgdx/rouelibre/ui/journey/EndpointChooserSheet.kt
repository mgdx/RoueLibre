package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.SheetEndpointChooserBinding

/**
 * How to designate an origin or a destination (SPEC §7.3).
 *
 * Four ways, and the specification wants them all: one's position, a favourite,
 * a point picked on the map, an address. They are not equally useful at any
 * given moment — one knows the address one is going to, but rarely the address
 * one is standing at.
 *
 * The sheet only returns the choice; it is the search screen that knows what to
 * do with it.
 */
class EndpointChooserSheet : BottomSheetDialogFragment() {

    private var binding: SheetEndpointChooserBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetEndpointChooserBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        views.title.setText(
            if (isForOrigin()) {
                R.string.journey_choose_origin
            } else {
                R.string.journey_choose_destination
            },
        )
        views.myPosition.setOnClickListener { choose(SOURCE_MY_POSITION) }
        views.favourite.setOnClickListener { choose(SOURCE_FAVOURITE) }
        views.onMap.setOnClickListener { choose(SOURCE_ON_MAP) }
        views.address.setOnClickListener { choose(SOURCE_ADDRESS) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun isForOrigin(): Boolean = requireArguments().getBoolean(ARGUMENT_IS_ORIGIN)

    private fun choose(source: String) {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_SOURCE, source)
                putBoolean(RESULT_IS_ORIGIN, isForOrigin())
            },
        )
        dismiss()
    }

    companion object {
        /** The key the chosen way is returned under. */
        const val REQUEST_KEY: String = "source-point"

        /** The way chosen, one of the four `SOURCE_` constants. */
        const val RESULT_SOURCE: String = "source"

        /** True if the choice was for the origin. */
        const val RESULT_IS_ORIGIN: String = "is-origin"

        /** Use where one is. */
        const val SOURCE_MY_POSITION: String = "ma-position"

        /** Pick a station marked as a favourite. */
        const val SOURCE_FAVOURITE: String = "favori"

        /** Designate a point on the map. */
        const val SOURCE_ON_MAP: String = "carte"

        /** Look up an address in the offline index. */
        const val SOURCE_ADDRESS: String = "adresse"

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "choix-point"

        private const val ARGUMENT_IS_ORIGIN = "is-origin"

        /** Opens the sheet for the origin or for the destination. */
        fun newInstance(isOrigin: Boolean): EndpointChooserSheet = EndpointChooserSheet().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_IS_ORIGIN, isOrigin) }
        }
    }
}
