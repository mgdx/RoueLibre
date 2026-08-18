package io.github.mgdx.rouelibre.ui.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.databinding.FragmentAddressSearchBinding
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Searching for an address in the offline index (SPEC §4.3, §7.3).
 *
 * The screen returns the chosen address to whoever opened it, rather than
 * acting itself: the map centres its point on it, and the journey search takes
 * its origin or its destination from it.
 *
 * **No network call happens here, including while typing.** This is the
 * application's most sensitive data: what somebody searches for says where they
 * are going.
 */
class AddressSearchFragment : Fragment() {

    private var binding: FragmentAddressSearchBinding? = null

    private val viewModel: AddressSearchViewModel by viewModels {
        AddressSearchViewModel.Factory(
            (requireActivity().application as RoueLibreApplication).container.addressIndex,
            origin = readOrigin(),
        )
    }

    private val adapter = AddressAdapter(::pick)

    private val shortcutAdapter = SearchShortcutAdapter(::pick)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentAddressSearchBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        titleResource()?.let(views.toolbar::setTitle)

        views.results.layoutManager = LinearLayoutManager(requireContext())
        views.results.adapter = if (showsShortcuts()) {
            shortcutAdapter.submitList(SearchShortcut.entries)
            // The shortcuts head the list, and the addresses follow: two
            // adapters rather than one, so neither knows about the other.
            ConcatAdapter(shortcutAdapter, adapter)
        } else {
            adapter
        }
        views.results.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        views.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        views.searchInput.setOnEditorActionListener { field, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // The list updates by itself; confirming only gives the screen
                // back to the eye by folding the keyboard away.
                field.clearFocus()
                hideKeyboard(field)
                true
            } else {
                false
            }
        }

        // The keyboard opens with the screen: one only comes here to type.
        if (savedInstanceState == null) {
            views.searchInput.requestFocus()
            views.searchInput.post { insetsController(views.searchInput).show(ime()) }
        }

        observeState()
    }

    override fun onDestroyView() {
        binding?.results?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    val views = binding ?: return@collectLatest
                    adapter.submitList(state.results)
                    val panel = panelFor(state, showsShortcuts())
                    showEmptyState(panel, state)
                    // The shortcuts are part of the list: it therefore has
                    // something to show even before a single letter is typed.
                    // Whether they survive the panel above them is the panel's
                    // own answer, and it is not the same for all of them.
                    views.results.isVisible = panel.keepsList &&
                        (state.results.isNotEmpty() || showsShortcuts())
                }
            }
        }
    }

    /**
     * Shows what there is to say when the list is empty.
     *
     * Six situations, six different gestures: wait, install the index, clear a
     * fruitless query, type something, pick one of the shortcuts, or import an
     * unreadable file again. Conflating them would send the user looking for a
     * breakdown that does not exist — [panelFor] is where they are told apart.
     *
     * The panel stands above the list rather than over it, and the caller takes
     * the list away under those of them that speak for the whole screen.
     */
    private fun showEmptyState(panel: AddressSearchPanel, state: AddressSearchUiState) {
        val views = binding ?: return
        views.emptyState.isVisible = panel != AddressSearchPanel.None
        views.emptyAction.isVisible = false
        when (panel) {
            AddressSearchPanel.None -> return

            // A running search concludes nothing. It used to leave the previous
            // message standing, to spare a flicker between two queries following
            // one another; on a large index that message was an announced
            // absence, and it stood for three seconds over a query that was
            // about to succeed. Saying "searching" costs the flicker and cannot
            // be wrong.
            AddressSearchPanel.Searching -> {
                views.emptyTitle.setText(R.string.address_searching_title)
                views.emptyMessage.setText(R.string.address_searching_message)
            }

            AddressSearchPanel.NeedsIndex -> {
                views.emptyTitle.setText(R.string.address_needs_index_title)
                views.emptyMessage.setText(R.string.address_needs_index_message)
                views.emptyAction.setText(R.string.storage_open)
                views.emptyAction.isVisible = true
                views.emptyAction.setOnClickListener { openStorage() }
            }

            AddressSearchPanel.Unreadable -> {
                views.emptyTitle.setText(R.string.address_unreadable_title)
                views.emptyMessage.text = state.error?.toUserMessage(requireContext())
                views.emptyAction.setText(R.string.storage_open)
                views.emptyAction.isVisible = true
                views.emptyAction.setOnClickListener { openStorage() }
            }

            AddressSearchPanel.NoMatch -> {
                views.emptyTitle.setText(R.string.address_no_match_title)
                views.emptyMessage.text =
                    getString(R.string.address_no_match_message, boundedQuery(state.query))
            }

            AddressSearchPanel.Prompt -> {
                views.emptyTitle.setText(R.string.address_search_prompt_title)
                views.emptyMessage.setText(R.string.address_search_prompt_message)
            }
        }
    }

    /** Returns the chosen address to the screen that opened this one. */
    private fun pick(result: AddressResult) {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putDouble(RESULT_LATITUDE, result.position.latitude)
                putDouble(RESULT_LONGITUDE, result.position.longitude)
                putString(RESULT_LABEL, result.toTitle(requireContext()))
            },
        )
        binding?.searchInput?.let(::hideKeyboard)
        parentFragmentManager.popBackStack()
    }

    /**
     * Returns the chosen shortcut to the screen that opened this one.
     *
     * The screen does not act on it itself: designating a point through the
     * position, a favourite or the map are three journeys of their own, and
     * they belong to the search screen that knows which field is waiting.
     */
    private fun pick(shortcut: SearchShortcut) {
        setFragmentResult(
            SHORTCUT_REQUEST_KEY,
            Bundle().apply { putString(RESULT_SHORTCUT, shortcut.name) },
        )
        binding?.searchInput?.let(::hideKeyboard)
        parentFragmentManager.popBackStack()
    }

    /** True when the screen serves to fill a journey's end (SPEC §7.3). */
    private fun showsShortcuts(): Boolean = arguments?.containsKey(ARGUMENT_IS_ORIGIN) == true

    /**
     * The screen's title.
     *
     * Filling a journey's end, the title says which of the two is being
     * designated: the field one left is no longer on screen to say it.
     */
    private fun titleResource(): Int? = when {
        !showsShortcuts() -> null
        arguments?.getBoolean(ARGUMENT_IS_ORIGIN) == true -> R.string.journey_choose_origin
        else -> R.string.journey_choose_destination
    }

    private fun openStorage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, StorageFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun hideKeyboard(view: View) {
        insetsController(view).hide(ime())
    }

    /**
     * The means of opening and folding away the keyboard.
     *
     * Goes through the window rather than the view: the shortcut that takes
     * only a view is deprecated, and the window is the only thing that reliably
     * finds itself.
     */
    private fun insetsController(view: View): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(requireActivity().window, view)

    private fun ime(): Int = WindowInsetsCompat.Type.ime()

    /** The ranking's reference point, if one was supplied. */
    private fun readOrigin(): Coordinates? {
        val arguments = arguments ?: return null
        if (!arguments.containsKey(ARGUMENT_ORIGIN_LATITUDE)) return null
        return Coordinates(
            latitude = arguments.getDouble(ARGUMENT_ORIGIN_LATITUDE),
            longitude = arguments.getDouble(ARGUMENT_ORIGIN_LONGITUDE),
        )
    }

    companion object {
        /** The key the chosen address is returned under. */
        const val REQUEST_KEY = "chosen-address"

        /** The chosen point's latitude, in decimal degrees. */
        const val RESULT_LATITUDE = "latitude"

        /** The chosen point's longitude, in decimal degrees. */
        const val RESULT_LONGITUDE = "longitude"

        /** The label to show for this point, already put into words. */
        const val RESULT_LABEL = "label"

        /** The key a chosen shortcut is returned under. */
        const val SHORTCUT_REQUEST_KEY = "chosen-shortcut"

        /** The chosen shortcut, as the name of a [SearchShortcut]. */
        const val RESULT_SHORTCUT = "shortcut"

        private const val ARGUMENT_ORIGIN_LATITUDE = "origin-latitude"
        private const val ARGUMENT_ORIGIN_LONGITUDE = "origin-longitude"
        private const val ARGUMENT_IS_ORIGIN = "is-origin"

        /**
         * Opens the search.
         *
         * @param origin the reference point for ranking results by proximity —
         *   the map's centre, for want of a known position. No permission is
         *   requested to obtain it (SPEC §10).
         */
        fun newInstance(origin: Coordinates?): AddressSearchFragment =
            AddressSearchFragment().apply {
                arguments = origin?.let { Bundle().apply { putOrigin(it) } }
            }

        /**
         * Opens the search to fill one end of a journey (SPEC §7.3).
         *
         * The field of the search screen leads straight here rather than
         * through a menu of ways: one nearly always knows the address, and the
         * other three ways head the list, where a press reaches them just as
         * fast.
         *
         * @param isOrigin true for the point one leaves from.
         * @param origin the reference point for ranking results by proximity.
         */
        fun forJourneyEndpoint(isOrigin: Boolean, origin: Coordinates?): AddressSearchFragment =
            AddressSearchFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARGUMENT_IS_ORIGIN, isOrigin)
                    origin?.let { putOrigin(it) }
                }
            }

        private fun Bundle.putOrigin(origin: Coordinates) {
            putDouble(ARGUMENT_ORIGIN_LATITUDE, origin.latitude)
            putDouble(ARGUMENT_ORIGIN_LONGITUDE, origin.longitude)
        }
    }
}
