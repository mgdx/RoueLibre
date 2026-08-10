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
 * Choix de la ville servie (SPEC §15).
 *
 * L'application ne suppose aucune agglomération : elle en propose une d'après
 * la position, et retient celle qu'on désigne. C'est le seul endroit d'où l'on
 * change de ville, et le seul d'où l'on supprime toutes les données de l'une
 * d'elles (SPEC §11.9).
 *
 * La position n'est demandée que sur appui du bouton prévu pour cela : personne
 * n'a besoin de dire où il est pour parcourir une liste de villes (SPEC §10).
 */
class CityFragment : Fragment() {

    private var binding: FragmentCityBinding? = null

    private var catalogue: CityCatalogue? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val adapter = CityAdapter(onChoose = ::choose, onDelete = ::confirmDelete)

    /**
     * Demande la permission de localisation, et n'insiste jamais.
     *
     * Un refus laisse l'écran entièrement utilisable : la liste est là, et
     * choisir sa ville à la main n'a rien d'un mode dégradé (SPEC §10).
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
     * Affiche les villes connues, puis retélécharge le catalogue.
     *
     * Le catalogue livré s'affiche d'abord : la liste est là immédiatement, y
     * compris hors ligne. La requête qui suit est la seule de cet écran, et
     * elle a lieu parce qu'on vient de l'ouvrir pour savoir quelles villes
     * existent — jamais en arrière-plan (SPEC §4.1).
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
            // La ville en service en tête, puis celles dont les données sont
            // déjà là : ce sont les lignes sur lesquelles on revient.
            .sortedWith(
                compareByDescending<CityRow> { it.isActive }
                    .thenByDescending { it.installedBytes > 0 }
                    .thenBy { it.entry.displayName },
            )
        adapter.submitList(rows)
    }

    // ------------------------------------------------------- localisation --

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
     * Propose la ville qui correspond à l'endroit où l'on se trouve.
     *
     * La position sert à cette seule question et n'est écrite nulle part
     * (SPEC §2, C3). Loin de tout réseau servi, le catalogue ne propose rien :
     * il vaut mieux le dire que de désigner une ville à l'autre bout du pays.
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

    // -------------------------------------------------------------- choix --

    /**
     * Retient la ville choisie et enchaîne sur ses données.
     *
     * Rien n'est téléchargé ici : l'écran de stockage annonce d'abord le poids
     * et attend un appui (SPEC §4.4).
     */
    private fun choose(city: CityEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.switchToCity(city.id)
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, StorageFragment.checkingForUpdates())
                .commit()
        }
    }

    // ---------------------------------------------------------- effacement --

    /**
     * Demande confirmation avant de supprimer les données d'une ville.
     *
     * Des dizaines de mégaoctets qu'il faudra retélécharger : c'est un geste
     * qui mérite qu'on s'assure de l'avoir voulu.
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
            // Supprimer les données de la ville en service, c'est ne plus en
            // servir aucune : la garder active laisserait une carte vide sans
            // que rien n'explique pourquoi.
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
