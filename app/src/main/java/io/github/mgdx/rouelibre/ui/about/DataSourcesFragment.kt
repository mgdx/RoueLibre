package io.github.mgdx.rouelibre.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentDataSourcesBinding
import io.github.mgdx.rouelibre.databinding.ItemCitySourceBinding
import io.github.mgdx.rouelibre.ui.cityLabel
import kotlinx.coroutines.launch

/**
 * Who produces the availability data, city by city (SPEC §4.5).
 *
 * The "about" screen credits the network being served — the one whose data is
 * on screen. This page credits all of them, including the cities one has not
 * installed, and gives the address of each feed.
 *
 * A page of its own rather than a longer "about": the licences of the feeds
 * ask for the notice to travel with the work and to be reachable, not to be on
 * the first screen. One labelled tap away is how a mobile application does it,
 * and the map keeps its own attribution visible throughout.
 *
 * Everything shown comes from the city configurations shipped in the APK: this
 * screen sends no request and works offline.
 */
class DataSourcesFragment : Fragment() {

    private var binding: FragmentDataSourcesBinding? = null

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

        viewLifecycleOwner.lifecycleScope.launch { showCities() }
    }

    override fun onDestroyView() {
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
        val views = binding ?: return
        val inflater = LayoutInflater.from(requireContext())
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
        }
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
}
