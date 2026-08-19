package io.github.mgdx.rouelibre.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.config.filterCities
import io.github.mgdx.rouelibre.databinding.FragmentDataSourcesBinding
import io.github.mgdx.rouelibre.databinding.ItemCitySourceBinding
import io.github.mgdx.rouelibre.ui.cityLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who produces the availability data, city by city (SPEC §4.5).
 *
 * This page carries the networks' credits, all of them — the city installed
 * as much as the ones never downloaded — each with the address of its feed.
 * The "about" screen used to repeat the served network's credit, which had it
 * read twice over two screens in a row; it is written here alone now.
 *
 * A page of its own rather than a longer "about": the licences of the feeds
 * ask for the notice to travel with the work and to be reachable, not to be on
 * the first screen. One labelled tap away is how a mobile application does it,
 * and the map keeps its own attribution visible throughout.
 *
 * Crediting every network means dozens of blocks, so a search field narrows
 * them, the same one the city screen offers: same field, same filtering
 * ([filterCities]), same empty state. Reading the credit of one's own city
 * stopped being a matter of scrolling until it turned up.
 *
 * Everything shown comes from the city configurations shipped in the APK: this
 * screen sends no request and works offline.
 */
class DataSourcesFragment : Fragment() {

    private var binding: FragmentDataSourcesBinding? = null

    /** One credit block, kept with the city it was built for. */
    private class CreditRow(val entry: CityEntry, val view: View)

    /** The blocks in the order they were added, all of them, shown or not. */
    private var rows: List<CreditRow> = emptyList()

    /** What is typed in the search field, raw. */
    private var query: String = ""

    /**
     * The letters accent removal cannot reach, read once with the catalogue.
     *
     * The catalogue spans some forty countries and names cities with letters no
     * keyboard here carries (SPEC §4.3); without the folds, two of them answer
     * to no ASCII typing at all.
     */
    private var letterFolds: Map<Char, String> = emptyMap()

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentDataSourcesBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)

        // Filtering on every keystroke: the blocks are already built and only
        // their visibility changes, so no debounce is warranted here.
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

        viewLifecycleOwner.lifecycleScope.launch { showCities() }
    }

    override fun onDestroyView() {
        rows = emptyList()
        binding = null
        super.onDestroyView()
    }

    /**
     * Lists the cities of the catalogue, each with its credit.
     *
     * A city the catalogue names but whose configuration this version does not
     * carry is skipped: a downloaded catalogue may be ahead of the
     * application, and there would be nothing to credit for it here.
     */
    private suspend fun showCities() {
        val catalogue = container.cityCatalogueSource.catalogue()
        // Before the first block shows, and off the main thread: the field
        // filters on every keystroke and must never wait on a file.
        letterFolds = withContext(Dispatchers.IO) {
            container.addressNormalizers.searchLetterFolds()
        }
        val views = binding ?: return
        val inflater = LayoutInflater.from(requireContext())
        val built = mutableListOf<CreditRow>()
        for (entry in catalogue.cities) {
            val configuration = container.cityCatalogueSource.configuration(entry.id) ?: continue
            val row = ItemCitySourceBinding.inflate(inflater, views.cities, false)
            row.cityName.text = requireContext().cityLabel(entry.displayName, entry.mainCity)
            row.attribution.text = configuration.gbfs.attribution
            row.attribution.isVisible = configuration.gbfs.attribution.isNotBlank()
            val address = configuration.gbfs.attributionUrl
            row.openSource.isVisible = !address.isNullOrBlank()
            row.openSource.setOnClickListener { open(address.orEmpty()) }
            views.cities.addView(row.root)
            built += CreditRow(entry, row.root)
        }
        rows = built
        showRows()
    }

    /**
     * Shows the credits the search field leaves.
     *
     * Every block is built once and only its visibility moves: the catalogue is
     * a few dozen entries, and a list already on screen has nothing to gain
     * from being taken apart at each keystroke.
     */
    private fun showRows() {
        val views = binding ?: return
        val shown = filterCities(rows.map { it.entry }, query, letterFolds)
            .mapTo(mutableSetOf()) { it.id }
        rows.forEach { row -> row.view.isVisible = row.entry.id in shown }

        // Only once the credits are built: an empty page before that is a
        // screen still loading, and there would be nothing to clear.
        views.emptyState.isVisible = rows.isNotEmpty() && shown.isEmpty()
        views.emptyMessage.text = getString(R.string.city_no_match_message, query)
    }

    /** Opens an address in the browser, without pretending one exists. */
    private fun open(address: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, address.toUri()))
        } catch (_: ActivityNotFoundException) {
            val views = binding ?: return
            Snackbar.make(views.root, R.string.about_no_browser, Snackbar.LENGTH_LONG).show()
        }
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
}
