package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.SheetFavouriteStationsBinding
import io.github.mgdx.rouelibre.ui.stations.FavouriteStationsViewModel
import io.github.mgdx.rouelibre.ui.stations.StationAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Picks a favourite station as origin or destination (SPEC §7.3).
 *
 * Availability is shown here as everywhere else: choosing one's departure
 * station without seeing how many bikes it holds would make no sense.
 */
class FavouriteStationSheet : BottomSheetDialogFragment() {

    private var binding: SheetFavouriteStationsBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: FavouriteStationsViewModel by viewModels {
        FavouriteStationsViewModel.Factory(container.stationRepository, container.preferences)
    }

    private val adapter = StationAdapter { entry ->
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                JourneyEndpoint(entry.station.name, entry.station.position)
                    .writeTo(this, RESULT_PREFIX)
                putBoolean(RESULT_IS_ORIGIN, requireArguments().getBoolean(ARGUMENT_IS_ORIGIN))
            },
        )
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetFavouriteStationsBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.stations.layoutManager = LinearLayoutManager(requireContext())
        views.stations.adapter = adapter
        views.stations.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favourites.collectLatest { favourites ->
                    val current = binding ?: return@collectLatest
                    adapter.submitList(favourites)
                    // An empty screen is an invitation to act, not a statement
                    // of fact: it says how one marks a station as a favourite.
                    current.empty.isVisible = favourites.isEmpty()
                    current.stations.isVisible = favourites.isNotEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding?.stations?.adapter = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        /** The key the chosen station is returned under. */
        const val REQUEST_KEY: String = "chosen-favourite-station"

        /** The prefix of the returned point's keys. */
        const val RESULT_PREFIX: String = "point"

        /** True if the choice was for the origin. */
        const val RESULT_IS_ORIGIN: String = "is-origin"

        /** The tag the sheet is added to the manager under. */
        const val TAG: String = "favoris"

        private const val ARGUMENT_IS_ORIGIN = "is-origin"

        /** Opens the sheet for the origin or for the destination. */
        fun newInstance(isOrigin: Boolean): FavouriteStationSheet = FavouriteStationSheet().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_IS_ORIGIN, isOrigin) }
        }
    }
}
