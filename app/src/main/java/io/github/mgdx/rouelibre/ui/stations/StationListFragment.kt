package io.github.mgdx.rouelibre.ui.stations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
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
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.message.MessageSubject
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentStationListBinding
import io.github.mgdx.rouelibre.ui.MAP_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.MainActivity
import io.github.mgdx.rouelibre.ui.STATION_LIST_BACK_STACK_ENTRY
import io.github.mgdx.rouelibre.ui.ScreenBehind
import io.github.mgdx.rouelibre.ui.backStackEntryNames
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.cityLabel
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

    /**
     * Whether the next list committed is to be shown from its first row.
     *
     * Raised by the two gestures that hand the reader **another set of
     * stations** — the press of "nearest station first", and a search that
     * keeps other stations than the one before it. Not by the order the list
     * gives itself when it appears, which scrolls nothing (SPEC §7.6), and not
     * by the availability refreshed every minute, which hands back the same
     * stations with fresher counts.
     *
     * The order is settled by the model, the rows are handed to `ListAdapter`,
     * and the diff that reorders them runs on another thread: scrolling
     * straight after asking for the order scrolls the list that is still on
     * screen, and the reordering that lands a moment later keeps the position
     * it finds. So the scroll waits for the commit, which is what makes the
     * nearest station the one actually looked at.
     */
    private var showFromTheTop = false

    private val viewModel: StationsViewModel by viewModels {
        val container = (requireActivity().application as RoueLibreApplication).container
        StationsViewModel.Factory(
            container.stationRepository,
            positionAlreadyKnown = { container.knownPositionInsideActiveCity() },
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

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /**
     * Requests the location permission, and never insists (SPEC §10).
     *
     * Launched from the button and from nowhere else: this screen shows no
     * position of its own, so opening it asks for nothing. A refusal leaves the
     * list whole, in the alphabet, and the button is what the user has left to
     * change their mind.
     */
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            orderFromAFreshFix()
        } else {
            showMessage(getString(R.string.stations_location_denied))
        }
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
        views.locateMe.setOnClickListener { onLocateMeClicked() }
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
            val query = text?.toString().orEmpty()
            // A search that keeps other stations hands the reader another
            // list, and it is shown from its first row like the reordered one
            // — see [showFromTheTop]. Without this the list kept the row it
            // was anchored on and found it again in the next one: three
            // matches shown from the one 1.3 km away, and the whole list
            // brought back by clearing the field still shown from it, the
            // station 40 m off being somewhere above the top of the screen.
            //
            // **Only where the query really changes**, which is what tells
            // this gesture from the field being written to again as the
            // screen turns over: the same question asked twice must leave the
            // reader where they had scrolled to.
            if (viewModel.state.value.searchWouldChangeTheList(query)) showFromTheTop = true
            viewModel.onQueryChanged(query)
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

        // The nearest station first, when the position allows it (SPEC §7.6).
        // What the system holds orders the list at once, then a fix is asked
        // for and settles it — both without a word and without a prompt. Once
        // per appearance: an order already settled is kept, so a rotation does
        // not fetch a second time.
        viewModel.orderByProximity()

        observeState()
        observeErrors()
        keepAvailabilityFresh()
        showCoveredArea()
    }

    /**
     * Answers the "nearest first" button (SPEC §7.2).
     *
     * The list orders itself on what the system already holds when it appears,
     * and that is nothing at all on a phone where no other application has
     * asked for a position: the button is what asks for one. Its three
     * outcomes are the map's, in the same order and with the same words for
     * two of them — the permission is missing, location is switched off, or
     * there is a fix to wait for.
     */
    private fun onLocateMeClicked() {
        val location = container.deviceLocation
        when {
            !location.isPermitted() ->
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)

            !location.isAvailable() -> showMessage(getString(R.string.map_location_unavailable))

            else -> orderFromAFreshFix()
        }
    }

    /**
     * Waits for a position and puts the nearest station at the top.
     *
     * **A fix asked for, not the one already known**: the button is pressed by
     * somebody who has walked somewhere, and a position from two minutes ago
     * would order the list around the street they set off from. The wait can
     * run to ten seconds indoors, so the button goes dead meanwhile and
     * **turns a ring while it waits** — otherwise the press reads as lost and
     * is made again. The ring shows itself only where the wait is long enough
     * to need showing, which the layout settles rather than any timing written
     * here.
     *
     * The position serves that single ordering and is written nowhere
     * (SPEC §2, C3).
     */
    private fun orderFromAFreshFix() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding?.locateMe?.isEnabled = false
            binding?.locating?.show()
            val fix = try {
                container.deviceLocation.current()
            } finally {
                binding?.locating?.hide()
                binding?.locateMe?.isEnabled = true
            }
            val position = fix?.coordinates
            if (position == null) {
                showMessage(getString(R.string.map_location_unavailable))
                return@launch
            }
            if (saidWeAreElsewhere(position)) return@launch
            // Back to the top, where the nearest station now is: an answer
            // nobody can see reads as a button that does nothing, and the list
            // may be scrolled halfway down.
            //
            // **Whether the order changes or not**, and the two cases are not
            // answered the same way. A press from the same street as the last
            // one gives the same position, so the model's state does not
            // change, nothing is emitted and no list is committed — the
            // callback that carries the scroll would never run, which is
            // exactly what left the reader mid-list on the second press.
            // Nothing is moving there, so the scroll happens on the spot.
            // Where the order does change, it waits for the reordered rows to
            // be committed — see [showFromTheTop].
            if (viewModel.state.value.orderingOrigin == position) {
                binding?.stations?.scrollToPosition(0)
            } else {
                showFromTheTop = true
            }
            viewModel.orderFrom(position)
        }
    }

    /**
     * Says so where [position] falls outside the conurbation served, and orders
     * nothing.
     *
     * Distances measured from a hundred kilometres away rank the stations of a
     * city one is not in by which end of it one is pointing at — an order that
     * looks like an answer and is arithmetic on an irrelevance. The alphabet is
     * kept, and the sentence names the network in service so that the button is
     * not simply silent. Changing city is the map's offer to make, this screen
     * having no map to be elsewhere on.
     *
     * A city whose configuration declares no usable box covers everything as
     * far as this screen is concerned: there is nothing to be outside of.
     *
     * @return true if the user was told, in which case nothing else is to be
     *   done with this position.
     */
    private suspend fun saidWeAreElsewhere(position: Coordinates): Boolean {
        val city = container.activeCity() ?: return false
        val area = city.boundingBox?.takeIf { it.isUsable } ?: return false
        if (position in area) return false
        showMessage(
            getString(
                R.string.map_outside_city_brief,
                requireContext().cityLabel(city.network.displayName, city.network.city),
            ),
        )
        return true
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
                    adapter.submitList(state.stations) {
                        if (!showFromTheTop) return@submitList
                        showFromTheTop = false
                        binding?.stations?.scrollToPosition(0)
                    }
                    views.swipeRefresh.isRefreshing = state.isRefreshing
                    showEmptyState(state)
                    showFreshness(state.fetchedAt)
                }
            }
        }
    }

    /**
     * Says why the list could not be refreshed, and offers the way out of it.
     *
     * "Try again" is the answer to a failure a second press may well settle — a
     * feed that timed out, a connection that has come back. It is not the answer
     * to having chosen no city: nothing is being asked of any network, and every
     * press can only fail the same way. That one failure is offered the city
     * chooser instead, which is the gesture the sentence asks for (SPEC §14).
     */
    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { error ->
                    val host = activity as? MainActivity ?: return@collect
                    // **A failed refresh gives way to an answer already up.**
                    // Offline is this application's ordinary state, so this is
                    // the message most apt to arrive at the wrong moment: with
                    // no network the loop raises one every ten seconds, and it
                    // wiped the sentence telling the user their position falls
                    // outside the city they are consulting — within a fraction
                    // of a second, so that the press of "nearest station
                    // first" looked answered by nothing at all. It comes back
                    // at the next tick, and the freshness line meanwhile says
                    // how old the counters are.
                    val (label, action) = if (error == DataError.NoCityChosen) {
                        // "Try again" is no answer to having chosen no city:
                        // nothing is being asked of any network and every
                        // press can only fail the same way (SPEC §14).
                        R.string.city_choose to { showCityChooser() }
                    } else {
                        R.string.action_retry to { viewModel.refresh(force = true) }
                    }
                    host.showMessage(
                        error.toUserMessage(requireContext()),
                        MessageSubject.Refresh,
                        actionLabel = label,
                    ) {
                        // The banner belongs to the activity and outlives this
                        // screen: a press landing after it is gone would ask a
                        // fragment with no manager for a transaction, and its
                        // own model for a refresh. That was a crash on the
                        // first screen of the first launch, when the list is
                        // replaced a frame later by the welcome sequence.
                        if (isAdded) action()
                    }
                }
            }
        }
    }

    /**
     * Opens the city chooser, the way the map and the settings open it.
     *
     * Nothing is named on the back stack: what is put up here is a detour, and
     * coming back from it is the back gesture's business — unlike the map, which
     * this list must be able to find again rather than build a second one of.
     */
    private fun showCityChooser() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, CityFragment())
            .addToBackStack(null)
            .commit()
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

    /**
     * Says something back about a gesture just made on this screen.
     *
     * Handed to the activity, which owns the one banner the screen has: put up
     * from here it would be taken down by the next failed refresh without
     * anything weighing the two, and with no network there is one of those
     * every ten seconds (see `MainActivity.showMessage`).
     *
     * Every one of these answers a press of "nearest station first" — the
     * permission refused, location switched off, no fix obtained, or a
     * position outside the city being consulted — so they are all
     * [MessageSubject.Answer], and they replace one another as the later of
     * two answers should.
     */
    private fun showMessage(text: CharSequence) {
        val host = activity as? MainActivity ?: return
        host.showMessage(text, MessageSubject.Answer)
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
