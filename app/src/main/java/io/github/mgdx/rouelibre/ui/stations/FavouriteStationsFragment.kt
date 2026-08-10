package io.github.mgdx.rouelibre.ui.stations

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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.databinding.FragmentFavouritesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Les stations mises en favori (SPEC §7.5).
 *
 * Réorganisable par glissement : l'ordre est le seul réglage de cet écran, et
 * il vaut mieux qu'un tri automatique — la station qu'on veut voir en premier
 * est celle de son quartier, pas la première par ordre alphabétique.
 *
 * Les disponibilités y sont en direct, comme partout : une liste de favoris
 * qui n'afficherait pas les vélos obligerait à ouvrir chaque station pour
 * savoir laquelle vaut le détour.
 */
class FavouriteStationsFragment : Fragment() {

    private var binding: FragmentFavouritesBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: FavouriteStationsViewModel by viewModels {
        FavouriteStationsViewModel.Factory(container.stationRepository, container.preferences)
    }

    private val adapter = StationAdapter { entry ->
        StationDetailSheet.newInstance(entry.station.id)
            .show(parentFragmentManager, StationDetailSheet.TAG)
    }

    /** L'ordre affiché, qui suit les glissements avant d'être enregistré. */
    private var shown: MutableList<StationWithAvailability> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentFavouritesBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)

        views.stations.layoutManager = LinearLayoutManager(requireContext())
        views.stations.adapter = adapter
        views.stations.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )
        ItemTouchHelper(DragToReorder(::onMoved, ::onDropped)).attachToRecyclerView(views.stations)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favourites.collectLatest { favourites ->
                    val current = binding ?: return@collectLatest
                    shown = favourites.toMutableList()
                    adapter.submitList(favourites)
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

    /** Suit le doigt : la liste se réordonne pendant le glissement. */
    private fun onMoved(from: Int, to: Int) {
        if (from !in shown.indices || to !in shown.indices) return
        shown.add(to, shown.removeAt(from))
        adapter.submitList(shown.toList())
    }

    /** Enregistre l'ordre une fois le doigt levé, pas à chaque pixel parcouru. */
    private fun onDropped() {
        viewModel.reorder(shown.map { it.station.id })
    }

    /**
     * Le glissement vertical, et lui seul.
     *
     * Pas de balayage latéral pour supprimer : retirer un favori se fait par
     * l'étoile de la station, là où on l'a mis. Un geste qui supprime sans
     * confirmation, sur une liste que l'on manipule justement pour la
     * réorganiser, se déclencherait par accident.
     */
    private class DragToReorder(
        private val onMoved: (Int, Int) -> Unit,
        private val onDropped: () -> Unit,
    ) : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            onMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            onDropped()
        }
    }
}
