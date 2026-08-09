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
 * Choisit une station favorite comme départ ou arrivée (SPEC §7.3).
 *
 * Les disponibilités y sont montrées comme partout ailleurs : choisir sa
 * station de départ sans voir combien elle a de vélos n'aurait pas de sens.
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
                    // Un écran vide est une invitation à agir, pas un constat :
                    // il dit comment on met une station en favori.
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
        /** Clé sous laquelle la station choisie est rendue. */
        const val REQUEST_KEY: String = "station-favorite-choisie"

        /** Préfixe des clés du point rendu. */
        const val RESULT_PREFIX: String = "point"

        /** Vrai si le choix portait sur le départ. */
        const val RESULT_IS_ORIGIN: String = "est-depart"

        /** Étiquette sous laquelle la feuille est ajoutée au gestionnaire. */
        const val TAG: String = "favoris"

        private const val ARGUMENT_IS_ORIGIN = "est-depart"

        /** Ouvre la feuille pour le départ ou pour l'arrivée. */
        fun newInstance(isOrigin: Boolean): FavouriteStationSheet = FavouriteStationSheet().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_IS_ORIGIN, isOrigin) }
        }
    }
}
