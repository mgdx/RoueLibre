package io.github.mgdx.rouelibre.ui.city

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentCityBinding
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.launch

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

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val adapter = CityAdapter(onChoose = ::choose, onDelete = ::confirmDelete)

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

        showCatalogue()
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
        val rows = loaded.cities
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
        adapter.submitList(rows)
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
                container.deviceLocation.current()
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.city_proposal_title)
            .setMessage(getString(R.string.city_proposal_body, city.displayName))
            .setPositiveButton(R.string.city_proposal_accept) { _, _ -> choose(city) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
    private fun choose(city: CityEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.switchToCity(city.id)
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.city_delete_title)
            .setMessage(getString(R.string.city_delete_body, city.displayName))
            .setPositiveButton(R.string.city_delete_confirm) { _, _ -> delete(city) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun delete(city: CityEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.datasetStore.deleteCity(city.id)
            // Deleting the data of the city in service means serving none:
            // keeping it active would leave an empty map with nothing to
            // explain why.
            if (container.preferences.activeCityId() == city.id) {
                container.switchToCity(null)
            }
            showMessage(getString(R.string.city_deleted, city.displayName))
            catalogue?.let { publish(it) }
        }
    }

    private fun showMessage(message: String) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }
}
