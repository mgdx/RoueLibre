package io.github.mgdx.rouelibre.ui.city

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.config.filterCities
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentCityBinding
import io.github.mgdx.rouelibre.ui.ConfirmationDialogFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Choosing the city served (SPEC §15).
 *
 * The application assumes no conurbation: it proposes one from the user's
 * position, and keeps the one they designate. This is the only place a city is
 * changed, and the only one from which all of a city's data is deleted
 * (SPEC §11.9).
 *
 * The position is only asked for on a press of the button meant for it: nobody
 * needs to say where they are to browse a list of cities (SPEC §10).
 */
class CityFragment : Fragment() {

    private var binding: FragmentCityBinding? = null

    private var catalogue: CityCatalogue? = null

    /** The catalogue's cities, in display order, before the search narrows it. */
    private var rows: List<CityRow> = emptyList()

    /** What is typed in the search field, raw. */
    private var query: String = ""

    /**
     * The letters accent removal cannot reach, read once with the catalogue.
     *
     * Without them seven of the three hundred and thirty-two cities answer to
     * no ordinary keyboard, two of them to no ASCII typing at all (SPEC §4.3).
     */
    private var letterFolds: Map<Char, String> = emptyMap()

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val adapter = CityAdapter(onChoose = { choose(it.id) }, onDelete = ::confirmDelete)

    /**
     * Requests location permission, and never insists.
     *
     * A refusal leaves the screen entirely usable: the list is there, and
     * choosing one's city by hand is in no way a degraded mode (SPEC §10).
     */
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            proposeFromPosition()
        } else {
            showMessage(getString(R.string.map_location_denied))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentCityBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.cities.layoutManager = LinearLayoutManager(requireContext())
        views.cities.adapter = adapter
        views.locateMe.setOnClickListener { onLocateMeClicked() }

        // Filtering on every keystroke: a few hundred entries already in
        // memory, no debounce is warranted here.
        views.searchInput.doAfterTextChanged { text ->
            query = text?.toString().orEmpty()
            showRows()
        }
        views.searchInput.setOnEditorActionListener { field, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // The list is already filtered; all that remains is to give the
                // screen back to the eye by folding the keyboard away.
                field.clearFocus()
                hideKeyboard(field)
                true
            } else {
                false
            }
        }
        views.emptyAction.setOnClickListener { views.searchInput.text?.clear() }

        listenForAnswers()
        showCatalogue()
    }

    /**
     * Collects the answers to the two questions this screen asks.
     *
     * Registered as the screen is built, and not where each question is put:
     * a question the phone turned over on is already back up by then, and its
     * answer would arrive with nobody listening for it.
     */
    private fun listenForAnswers() {
        ConfirmationDialogFragment.onAnswer(
            childFragmentManager,
            viewLifecycleOwner,
            PROPOSAL_ANSWER,
        ) { confirmed, payload ->
            val cityId = payload.getString(CITY_ID) ?: return@onAnswer
            if (confirmed) choose(cityId)
        }
        ConfirmationDialogFragment.onAnswer(
            childFragmentManager,
            viewLifecycleOwner,
            DELETION_ANSWER,
        ) { confirmed, payload ->
            val cityId = payload.getString(CITY_ID) ?: return@onAnswer
            val displayName = payload.getString(CITY_NAME) ?: return@onAnswer
            if (confirmed) delete(cityId, displayName)
        }
    }

    override fun onDestroyView() {
        binding?.cities?.adapter = null
        binding = null
        super.onDestroyView()
    }

    /**
     * Shows the known cities, then downloads the catalogue again.
     *
     * The shipped catalogue shows first: the list is there immediately, offline
     * included. The request that follows is this screen's only one, and it
     * happens because the screen has just been opened to learn which cities
     * exist — never in the background (SPEC §4.1).
     */
    private fun showCatalogue() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Before the first row shows, and off the main thread: the field
            // filters on every keystroke and must never wait on a file.
            letterFolds = withContext(Dispatchers.IO) {
                container.addressNormalizers.searchLetterFolds()
            }
            publish(container.cityCatalogueSource.catalogue())
            val url = catalogue?.catalogueUrl ?: return@launch
            val refreshed = container.cityCatalogueSource.refresh(url)
            if (refreshed is Outcome.Success) publish(refreshed.value)
        }
    }

    private suspend fun publish(loaded: CityCatalogue) {
        catalogue = loaded
        val activeId = container.preferences.activeCityId()
        val store = container.datasetStore
        rows = loaded.cities
            .map { city ->
                CityRow(
                    entry = city,
                    isActive = city.id == activeId,
                    installedBytes = store.occupiedBytesOf(city.id),
                )
            }
            // The city in service at the head, then those whose data is
            // already there: these are the rows one comes back to.
            .sortedWith(
                compareByDescending<CityRow> { it.isActive }
                    .thenByDescending { it.installedBytes > 0 }
                    .thenBy { it.entry.displayName },
            )
        showRows()
    }

    /**
     * Shows the catalogue as the search field narrows it.
     *
     * The order stays the one [publish] settled: a search filters the list, it
     * does not rearrange it.
     */
    private fun showRows() {
        val views = binding ?: return
        val byIdentifier = rows.associateBy { it.entry.id }
        val shown = filterCities(rows.map { it.entry }, query, letterFolds)
            .mapNotNull { byIdentifier[it.id] }
        adapter.submitList(shown)

        // Only once the catalogue has arrived: an empty list before that is a
        // screen still loading, and there would be nothing to clear.
        views.emptyState.isVisible = rows.isNotEmpty() && shown.isEmpty()
        views.emptyMessage.text = getString(R.string.city_no_match_message, query)
    }

    // ----------------------------------------------------------- location --

    private fun onLocateMeClicked() {
        val location = container.deviceLocation
        when {
            !location.isPermitted() ->
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)

            !location.isAvailable() -> showMessage(getString(R.string.map_location_unavailable))

            else -> proposeFromPosition()
        }
    }

    /**
     * Proposes the city that matches where one happens to be.
     *
     * The position serves that single question and is written nowhere
     * (SPEC §2, C3). Far from every network served, the catalogue proposes
     * nothing: better to say so than to name a city at the other end of the
     * country.
     */
    private fun proposeFromPosition() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding?.locateMe?.isEnabled = false
            val position = try {
                container.deviceLocation.current()?.coordinates
            } finally {
                binding?.locateMe?.isEnabled = true
            }
            if (position == null) {
                showMessage(getString(R.string.map_location_unavailable))
                return@launch
            }
            val proposed = catalogue?.suggestionFor(position)
            if (proposed == null) {
                showMessage(getString(R.string.city_none_nearby))
                return@launch
            }
            confirmProposal(proposed)
        }
    }

    private fun confirmProposal(city: CityEntry) {
        ConfirmationDialogFragment.ask(
            manager = childFragmentManager,
            requestKey = PROPOSAL_ANSWER,
            title = R.string.city_proposal_title,
            message = getString(R.string.city_proposal_body, city.displayName),
            confirm = R.string.city_proposal_accept,
            payload = Bundle().apply { putString(CITY_ID, city.id) },
        )
    }

    // ------------------------------------------------------------ choice --

    /**
     * Keeps the chosen city and moves on to its data.
     *
     * Nothing is downloaded here: the storage screen announces the weight first
     * and waits for a press (SPEC §4.4).
     *
     * The storage screen joins the back stack. Without that, choosing a city
     * left no way back: the first press of Back did nothing, and the second
     * left the application altogether.
     */
    private fun choose(cityId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.switchToCity(cityId)
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, StorageFragment.checkingForUpdates())
                .addToBackStack(null)
                .commit()
        }
    }

    // ---------------------------------------------------------- deletion --

    /**
     * Asks for confirmation before deleting a city's data.
     *
     * Tens of megabytes that will have to be downloaded again: it is a gesture
     * worth making sure of.
     */
    private fun confirmDelete(city: CityEntry) {
        ConfirmationDialogFragment.ask(
            manager = childFragmentManager,
            requestKey = DELETION_ANSWER,
            title = R.string.city_delete_title,
            message = getString(R.string.city_delete_body, city.displayName),
            confirm = R.string.city_delete_confirm,
            payload = Bundle().apply {
                putString(CITY_ID, city.id)
                // The name travels with the identifier because the sentence
                // said afterwards names the city that has just gone, and the
                // row it was read from is about to leave the list.
                putString(CITY_NAME, city.displayName)
            },
        )
    }

    private fun delete(cityId: String, displayName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.datasetStore.deleteCity(cityId)
            // Deleting the data of the city in service means serving none:
            // keeping it active would leave an empty map with nothing to
            // explain why.
            if (container.preferences.activeCityId() == cityId) {
                container.switchToCity(null)
            }
            showMessage(getString(R.string.city_deleted, displayName))
            catalogue?.let { publish(it) }
        }
    }

    private fun showMessage(message: String) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Folds the keyboard away.
     *
     * Goes through the window rather than the view: the shortcut that takes
     * only a view is deprecated, and the window is the only thing that reliably
     * finds itself.
     */
    private fun hideKeyboard(view: View) {
        WindowCompat.getInsetsController(requireActivity().window, view)
            .hide(WindowInsetsCompat.Type.ime())
    }

    private companion object {
        /** The keys the two questions of this screen answer under. */
        const val PROPOSAL_ANSWER = "city-proposal"
        const val DELETION_ANSWER = "city-deletion"

        /** What a question carries across a rebuild about the city it names. */
        const val CITY_ID = "city-id"
        const val CITY_NAME = "city-name"
    }
}
