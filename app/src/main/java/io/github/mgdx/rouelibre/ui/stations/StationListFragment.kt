package io.github.mgdx.rouelibre.ui.stations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.databinding.FragmentStationListBinding
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The list of stations and their availability.
 *
 * Refreshing only happens while the screen is visible, and never in the
 * background: that is constraint C3 of SPEC §2 as much as the policy of §4.1. A
 * loop tied to the lifecycle stops by itself when the screen goes to the
 * background.
 */
class StationListFragment : Fragment() {

    private var binding: FragmentStationListBinding? = null

    private val viewModel: StationsViewModel by viewModels {
        StationsViewModel.Factory(
            (requireActivity().application as RoueLibreApplication)
                .container
                .stationRepository,
        )
    }

    private val adapter = StationAdapter { entry ->
        StationDetailSheet.newInstance(entry.station.id)
            .show(parentFragmentManager, StationDetailSheet.TAG)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentStationListBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = requireBinding()

        views.stations.layoutManager = LinearLayoutManager(requireContext())
        views.stations.adapter = adapter
        views.stations.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )
        // The rows never change size: saying so saves a layout pass on every
        // refresh, across 268 stations.
        views.stations.setHasFixedSize(true)

        views.openStorage.setOnClickListener { openStorage() }
        views.openFavourites.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, FavouriteStationsFragment())
                .addToBackStack(null)
                .commit()
        }
        views.swipeRefresh.setOnRefreshListener { viewModel.refresh(force = true) }

        // Filtering on every keystroke: a few hundred entries already in
        // memory, no debounce is warranted here.
        views.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        views.searchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // The list is already filtered; all that remains is to give
                // the screen back to the eye by folding the keyboard away.
                view.clearFocus()
                hideKeyboard(view)
                true
            } else {
                false
            }
        }

        views.modeBikes.contentDescription = getString(R.string.mode_bikes_description)
        views.modeDocks.contentDescription = getString(R.string.mode_docks_description)
        views.modeToggle.check(R.id.mode_bikes)
        views.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            adapter.mode = when (checkedId) {
                R.id.mode_docks -> AvailabilityMode.Docks
                else -> AvailabilityMode.Bikes
            }
        }

        observeState()
        observeErrors()
        keepAvailabilityFresh()
    }

    override fun onDestroyView() {
        // The RecyclerView outlives the view through its adapter; detaching it
        // avoids holding on to the destroyed view.
        binding?.stations?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    val views = binding ?: return@collectLatest
                    adapter.submitList(state.stations)
                    views.swipeRefresh.isRefreshing = state.isRefreshing
                    showEmptyState(state)
                    showFreshness(state.fetchedAt)
                }
            }
        }
    }

    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { error ->
                    val views = binding ?: return@collect
                    Snackbar
                        .make(
                            views.root,
                            error.toUserMessage(requireContext()),
                            Snackbar.LENGTH_LONG,
                        )
                        .setAction(R.string.action_retry) { viewModel.refresh(force = true) }
                        .show()
                }
            }
        }
    }

    /**
     * Refreshes while the screen is visible, and rewrites the displayed age.
     *
     * The repository applies the one-minute minimum delay itself; the loop
     * comes round more often to keep "12 seconds ago" current, which would
     * otherwise age on screen with nothing to correct it.
     */
    private fun keepAvailabilityFresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refresh()
                    showFreshness(viewModel.state.value.fetchedAt)
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    /**
     * Shows, where applicable, why the list holds nothing.
     *
     * Two situations, two gestures: an empty cache is refreshed, a fruitless
     * search is cleared. Offering "Refresh" to somebody who made a typo would
     * send them looking for a breakdown that does not exist.
     */
    private fun showEmptyState(state: StationsUiState) {
        val views = binding ?: return
        views.emptyState.isVisible = state.emptiness != Emptiness.None
        when (state.emptiness) {
            Emptiness.None -> Unit

            Emptiness.NothingLoaded -> {
                views.emptyTitle.setText(R.string.stations_empty_title)
                views.emptyMessage.setText(R.string.stations_empty_message)
                views.emptyAction.setText(R.string.action_refresh)
                views.emptyAction.setOnClickListener { viewModel.refresh(force = true) }
            }

            Emptiness.NoMatch -> {
                views.emptyTitle.setText(R.string.stations_no_match_title)
                views.emptyMessage.text =
                    getString(R.string.stations_no_match_message, state.query)
                views.emptyAction.setText(R.string.action_clear_search)
                views.emptyAction.setOnClickListener { views.searchInput.text?.clear() }
            }
        }
    }

    /** Opens the offline data management screen (SPEC §4.4). */
    private fun openStorage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, StorageFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun hideKeyboard(view: View) {
        val manager = requireContext().getSystemService(InputMethodManager::class.java)
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Rewrites the age line, staleness included.
     *
     * Recomputed on every tick rather than frozen into the state: without that,
     * a screen left open would go on announcing "updated just now" half an hour
     * later.
     */
    private fun showFreshness(fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        views.freshness.text = freshness.toStatusLine(requireContext(), freshness.isStale)
    }

    private fun requireBinding(): FragmentStationListBinding =
        checkNotNull(binding) { "view not created" }

    private companion object {
        /**
         * The loop's cadence. Short enough for the displayed age to stay
         * truthful, long enough to cost nothing: the repository, for its part,
         * will only contact the network once a minute.
         */
        const val FRESHNESS_TICK_MILLIS = 10_000L
    }
}
