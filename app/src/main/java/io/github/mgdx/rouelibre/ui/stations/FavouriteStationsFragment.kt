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
 * The stations marked as favourites (SPEC §7.5).
 *
 * Reorderable by dragging: the order is this screen's only setting, and it
 * beats an automatic sort — the station one wants to see first is the one in
 * one's neighbourhood, not the first alphabetically.
 *
 * Availability is live here, as everywhere: a favourites list that did not show
 * the bikes would force one to open every station to learn which is worth the
 * detour.
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

    /** The displayed order, which follows the drags before being saved. */
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

        // The same rows as the full list, so the same figures at the same size
        // (SPEC §7.6): a favourite is an extract of that list, not another
        // screen.
        withLargeAvailabilityNumbers { adapter.largeFigures = it }

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

    /** Follows the finger: the list reorders during the drag. */
    private fun onMoved(from: Int, to: Int) {
        if (from !in shown.indices || to !in shown.indices) return
        shown.add(to, shown.removeAt(from))
        adapter.submitList(shown.toList())
    }

    /** Saves the order once the finger lifts, not on every pixel travelled. */
    private fun onDropped() {
        viewModel.reorder(shown.map { it.station.id })
    }

    /**
     * Vertical dragging, and that alone.
     *
     * No swipe-to-delete: removing a favourite is done through the station's
     * star, where it was added. A gesture that deletes without confirmation, on
     * a list one is handling precisely in order to reorder it, would fire by
     * accident.
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
