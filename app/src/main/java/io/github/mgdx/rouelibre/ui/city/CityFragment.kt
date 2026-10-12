package io.github.mgdx.rouelibre.ui.city

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.config.WithdrawnCity
import io.github.mgdx.rouelibre.core.config.filterCities
import io.github.mgdx.rouelibre.data.cities.CatalogueRefresh
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentCityBinding
import io.github.mgdx.rouelibre.ui.ConfirmationDialogFragment
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.textLocale
import io.github.mgdx.rouelibre.ui.withCountryFlag
import io.github.mgdx.rouelibre.ui.withoutCountryFlag
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

    /**
     * The country the list is narrowed to, `null` for every one.
     *
     * Kept as chosen even while the catalogue on show does not offer it: the
     * shipped catalogue shows before the downloaded one, and a choice restored
     * after the death of the process must survive that first, older list.
     */
    private var chosenCountry: String? = null

    /** The countries the filter offers, as last shown on the screen. */
    private var offeredCountries: List<CountryChoice> = emptyList()

    /** The filter the rows on show were drawn with, `null` before the first. */
    private var shownFilter: CityListFilter? = null

    /**
     * Whether the list still owes a return to its head after a new filter.
     *
     * Kept apart from the rows submitted: a row's progress submitted before the
     * new filter's rows have landed replaces them, and their callback with them.
     */
    private var returnToTopPending = false

    /** Counts the submissions to the three lists, so that only the last one's landing counts. */
    private var submission = 0

    /** How many of the three lists have yet to show the last submission. */
    private var listsAwaited = 0

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val adapter = CityAdapter(
        onChoose = { choose(it.id) },
        onDelete = { confirmDelete(it.id, it.displayName, R.string.city_delete_body) },
    )

    /**
     * The withdrawn networks whose data is still here (SPEC §15.1): the one
     * still chosen heads the list, being where its user came to leave it from,
     * and the others follow the cities that can be chosen.
     */
    private val chosenWithdrawnAdapter = WithdrawnCityAdapter(onDelete = ::confirmWithdrawnDelete)
    private val otherWithdrawnAdapter = WithdrawnCityAdapter(onDelete = ::confirmWithdrawnDelete)

    /** Those networks' rows, before the search hides them. */
    private var withdrawnRows: List<WithdrawnCityRow> = emptyList()

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
            // Written down for good, so the map does not ask again unprompted
            // when it opens on this very refusal's heels (SPEC §10) — two
            // dialogs for one "no" is what an F-Droid review calls insisting.
            viewLifecycleOwner.lifecycleScope.launch {
                container.automaticLocationRequest.noteRefused()
            }
            // This screen's own sentence, not the map's: there is neither map
            // nor address search here, and "pick your starting point" answers a
            // question nobody asked on the screen where one chooses a city.
            showMessage(getString(R.string.city_location_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chosenCountry = savedInstanceState?.getString(STATE_COUNTRY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_COUNTRY, chosenCountry)
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
        views.cities.adapter = ConcatAdapter(chosenWithdrawnAdapter, adapter, otherWithdrawnAdapter)
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
        // The filter's views go with the screen's: a return from the back
        // stack must build them again rather than take them as already shown.
        offeredCountries = emptyList()
        shownFilter = null
        returnToTopPending = false
        super.onDestroyView()
    }

    /**
     * Shows the known cities, then downloads the catalogue again.
     *
     * The shipped catalogue shows first: the list is there immediately, offline
     * included. The request that follows is this screen's only one, and it
     * happens because the screen has just been opened to learn which cities
     * exist — never in the background (SPEC §4.1). It is conditional, so the
     * usual answer is that nothing has changed, and the list is then left
     * exactly as it is rather than rebuilt from the same document.
     */
    private fun showCatalogue() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Before the first row shows, and off the main thread: the field
            // filters on every keystroke and must never wait on a file.
            letterFolds = withContext(Dispatchers.IO) {
                container.addressNormalizers.searchLetterFolds()
            }
            publish(container.cityCatalogueSource.catalogue())
            // No address is passed: where the catalogue is fetched from is
            // settled by the build, never by the document last downloaded.
            // Through the container, which forgets the active city's verdict
            // when a new catalogue comes into force.
            val refreshed = container.refreshCatalogue()
            if (refreshed is CatalogueRefresh.Updated) publish(refreshed.catalogue)
        }
    }

    private suspend fun publish(loaded: CityCatalogue) {
        catalogue = loaded
        val activeId = container.preferences.activeCityId()
        val store = container.datasetStore
        val known = container.cityCatalogueSource.knownCityIds()
        rows = loaded.cities
            .map { city ->
                CityRow(
                    entry = city,
                    isActive = city.id == activeId,
                    installedBytes = store.occupiedBytesOf(city.id),
                    isSupported = isCitySupported(city.id, known),
                )
            }
            .sortedWith(cityDisplayOrder())
        // A network the catalogue in force serves again is not among them: its
        // own row already says what can be done.
        withdrawnRows = container.cityCatalogueSource.withdrawnCities()
            .map { city ->
                WithdrawnCityRow(
                    city = city,
                    isActive = city.id == activeId,
                    installedBytes = store.occupiedBytesOf(city.id),
                )
            }
            // Listed only for what there is to delete, or to say why the city
            // still chosen is no longer served.
            .filter { it.isActive || it.installedBytes > 0 }
        val offered = countriesOffered(loaded.cities, requireContext().textLocale())
        if (offered != offeredCountries) {
            offeredCountries = offered
            showCountries()
        }
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
        val country = countryStillOffered(chosenCountry, offeredCountries)
        val inCountry = filterByCountry(rows.map { it.entry }, country)
        val shown = filterCities(inCountry, query, letterFolds)
            .mapNotNull { byIdentifier[it.id] }
        val filter = CityListFilter(query, chosenCountry)
        if (shouldReturnToTop(shownFilter, filter)) returnToTopPending = true
        shownFilter = filter
        // Once all three lists have landed: their differences are worked out
        // apart and in no set order, and the withdrawn network heading the list
        // may be the last to arrive.
        val generation = ++submission
        listsAwaited = LIST_COUNT
        val returnToTop = Runnable {
            if (generation == submission && --listsAwaited == 0 && returnToTopPending) {
                returnToTopPending = false
                binding?.cities?.scrollToPosition(0)
            }
        }
        adapter.submitList(shown, returnToTop)
        // A search looks for a city to choose, and these cannot be chosen; nor
        // can they be placed in a country, the catalogue no longer naming them.
        val withdrawn = withdrawnRows.takeIf { query.isBlank() && country == null }.orEmpty()
        chosenWithdrawnAdapter.submitList(withdrawn.filter { it.isActive }, returnToTop)
        otherWithdrawnAdapter.submitList(withdrawn.filterNot { it.isActive }, returnToTop)

        // Only once the catalogue has arrived: an empty list before that is a
        // screen still loading, and there would be nothing to clear.
        views.emptyState.isVisible = rows.isNotEmpty() && shown.isEmpty()
        if (views.emptyState.isVisible) showNoMatch(country)
    }

    /**
     * Says why the list is empty, and offers what would fill it.
     *
     * When the country chosen is what leaves the cities out, the way out is
     * every country, the search kept: it was right, and clearing it would
     * leave the person to type it again once they had found the country menu
     * themselves.
     */
    private fun showNoMatch(country: String?) {
        val views = binding ?: return
        val reason = noMatchReason(rows.map { it.entry }, query, country, letterFolds)
        val countryName = offeredCountries.firstOrNull { it.code == country }?.name
        if (reason == NoMatchReason.COUNTRY && countryName != null) {
            views.emptyMessage.text =
                getString(R.string.city_no_match_in_country, query, countryName)
            views.emptyAction.setText(R.string.city_country_all)
            views.emptyAction.setOnClickListener { showEveryCountry() }
        } else {
            views.emptyMessage.text = getString(R.string.city_no_match_message, query)
            views.emptyAction.setText(R.string.action_clear_search)
            views.emptyAction.setOnClickListener { views.searchInput.text?.clear() }
        }
    }

    /** Widens the list to every country, the menu saying so as well. */
    private fun showEveryCountry() {
        chosenCountry = null
        showCountries()
        showRows()
    }

    /**
     * Fills the country menu: every country first, then each one offered.
     *
     * The menu stays in sight during a search: it narrows the very list the
     * search does, and a country chosen out of sight would leave a search
     * coming up empty with nothing on the screen to say why.
     */
    private fun showCountries() {
        val views = binding ?: return
        val choices = listOf(null) + offeredCountries
        val labels = choices.map { choice ->
            choice?.let { withCountryFlag(it.name, it.code) }
                ?: getString(R.string.city_country_all)
        }
        val chosen = countryStillOffered(chosenCountry, offeredCountries)
        val menu = views.countryMenu
        menu.setAdapter(CountryMenuAdapter(requireContext(), labels))
        // Without filtering: the field's own text is not a search here.
        menu.setText(labels[choices.indexOfFirst { it?.code == chosen }], false)
        menu.contentDescription = withoutCountryFlag(menu.text)
        menu.setOnItemClickListener { _, _, position, _ ->
            chosenCountry = choices[position]?.code
            menu.contentDescription = withoutCountryFlag(labels[position])
            showRows()
        }
        views.countryFilter.isVisible = offeredCountries.isNotEmpty()
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
            // The nearest network can be one this build does not carry. Naming
            // it and then failing to install it would be the very trap the
            // greyed rows exist to avoid, so it is said here too.
            val known = container.cityCatalogueSource.knownCityIds()
            if (known.isNotEmpty() && proposed.id !in known) {
                showMessage(getString(R.string.city_needs_newer_version))
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
        val fromWelcome = arguments?.getBoolean(ARGUMENT_FROM_WELCOME) == true
        viewLifecycleOwner.lifecycleScope.launch {
            container.switchToCity(cityId)
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, StorageFragment.checkingForUpdates(fromWelcome))
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
    private fun confirmDelete(cityId: String, displayName: String, @StringRes body: Int) {
        ConfirmationDialogFragment.ask(
            manager = childFragmentManager,
            requestKey = DELETION_ANSWER,
            title = R.string.city_delete_title,
            message = getString(body, displayName),
            confirm = R.string.city_delete_confirm,
            payload = Bundle().apply {
                putString(CITY_ID, cityId)
                // The name travels with the identifier because the sentence
                // said afterwards names the city that has just gone, and the
                // row it was read from is about to leave the list.
                putString(CITY_NAME, displayName)
            },
        )
    }

    /**
     * The same question about a withdrawn network, which differs in two words.
     *
     * Its data cannot be downloaded again, so the sentence says it is of no
     * further use rather than that it will have to come down a second time.
     * And it is named with its conurbation: six of the networks withdrawn on
     * 6 October 2026 were all called "Donkey Republic".
     */
    private fun confirmWithdrawnDelete(city: WithdrawnCity) {
        confirmDelete(
            cityId = city.id,
            displayName = requireContext().cityLabel(city.displayName, city.mainCity),
            body = R.string.city_withdrawn_delete_body,
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

    companion object {
        /** The keys the two questions of this screen answer under. */
        private const val PROPOSAL_ANSWER = "city-proposal"
        private const val DELETION_ANSWER = "city-deletion"

        /** What a question carries across a rebuild about the city it names. */
        private const val CITY_ID = "city-id"
        private const val CITY_NAME = "city-name"

        private const val ARGUMENT_FROM_WELCOME = "from-welcome"

        /** The country chosen, across a rotation and the death of the process. */
        private const val STATE_COUNTRY = "country"

        /** The lists the screen joins end to end: two of withdrawn networks around the cities. */
        private const val LIST_COUNT = 3

        /**
         * The city choice as the welcome sequence opens it (SPEC §7.9).
         *
         * The city chosen here leads to a storage screen that hands over to the
         * map once its sets are installed. Carried in the arguments, so that it
         * survives a rotation and the death of the process.
         */
        fun inWelcomeSequence(): CityFragment = CityFragment().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_FROM_WELCOME, true) }
        }
    }
}
