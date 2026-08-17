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
import androidx.fragment.app.FragmentManager
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
import io.github.mgdx.rouelibre.ui.MAP_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.STATION_LIST_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.ScreenBehind
import io.github.mgdx.rouelibre.ui.backStackEntryNames
import io.github.mgdx.rouelibre.ui.map.MapFragment
import io.github.mgdx.rouelibre.ui.screenBehind
import io.github.mgdx.rouelibre.ui.settings.SettingsFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val container = (requireActivity().application as RoueLibreApplication).container
        StationsViewModel.Factory(
            container.stationRepository,
            positionForOrdering = { container.positionInsideActiveCity() },
            readLetterFolds = {
                withContext(Dispatchers.IO) {
                    container.addressNormalizers.searchLetterFolds()
                }
            },
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

        views.openSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
        showWayToTheMap(views)
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

        // The nearest station first, when the position allows it (SPEC §7.2).
        // Asked for once per appearance: a list reordering itself under the
        // finger would be worse than one ordered a moment late.
        viewModel.orderByProximity()

        // The signature element at whichever size the reader asked for
        // (SPEC §7.6). It is the list that carries it, not the row: every row
        // writes its figure the same way.
        withLargeAvailabilityNumbers { adapter.largeFigures = it }

        observeState()
        observeErrors()
        keepAvailabilityFresh()
        showCoveredArea()
    }

    /**
     * Tells the rows which stations the installed data actually reaches.
     *
     * A network is under no obligation to keep its stations inside the box its
     * data was cut from, and fifteen of them do not (SPEC §4). Read once: the
     * box belongs to the city in service, and changing city rebuilds the
     * screen.
     */
    private fun showCoveredArea() {
        viewLifecycleOwner.lifecycleScope.launch {
            val area = (requireActivity().application as RoueLibreApplication)
                .container
                .activeCity()
                ?.boundingBox
            adapter.coveredArea = area
        }
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
                    adapter.origin = state.orderingOrigin
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

    /**
     * Offers the map, whatever stands behind this list (SPEC §7.6).
     *
     * It was offered only where the back stack was empty — where this list was
     * the screen the application opened on, the map being reachable by the back
     * gesture otherwise. The map's own way to the list puts a second list up
     * rather than coming back to the first, so the stack was no longer empty and
     * the button was gone, with nothing left but repeated back gestures to reach
     * the map again. A way out that comes and goes with the depth of the stack is
     * unpredictable to whoever is using it, which is worse than the duplicate
     * screen that rule was written to avoid.
     *
     * Pressed, it **comes back to the map already behind** where there is one, so
     * that pressing the two buttons in turn cannot stack map over list over map:
     * every one of these transactions is named, which is what makes an existing
     * map findable. Only with no map behind at all is one built.
     */
    private fun showWayToTheMap(views: FragmentStationListBinding) {
        views.openMap.setOnClickListener {
            val manager = parentFragmentManager
            when (
                screenBehind(
                    MAP_BACK_STACK_ENTRY,
                    STATION_LIST_BACK_STACK_ENTRY,
                    manager.backStackEntryNames(),
                )
            ) {
                ScreenBehind.Stacked -> manager.popBackStack(MAP_BACK_STACK_ENTRY, 0)

                ScreenBehind.Underneath -> manager.popBackStack(
                    STATION_LIST_BACK_STACK_ENTRY,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )

                ScreenBehind.Nowhere -> manager.beginTransaction()
                    .replace(R.id.content, MapFragment())
                    .addToBackStack(MAP_BACK_STACK_ENTRY)
                    .commit()
            }
        }
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
