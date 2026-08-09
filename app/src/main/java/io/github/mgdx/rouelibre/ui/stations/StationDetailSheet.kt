package io.github.mgdx.rouelibre.ui.stations

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.ServiceState
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.databinding.SheetStationDetailBinding
import io.github.mgdx.rouelibre.ui.address.toTitle
import io.github.mgdx.rouelibre.ui.toStatusLine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Détail d'une station, en feuille glissante depuis le bas (SPEC §7.2).
 *
 * Ouverte depuis la carte comme depuis la liste : c'est la même station, elle
 * mérite le même écran. La feuille reste vivante tant qu'elle est affichée —
 * les comptes suivent le rafraîchissement, ils ne sont pas figés à
 * l'ouverture.
 */
class StationDetailSheet : BottomSheetDialogFragment() {

    private var binding: SheetStationDetailBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: StationDetailViewModel by viewModels {
        StationDetailViewModel.Factory(
            repository = container.stationRepository,
            preferences = container.preferences,
            addressIndex = container.addressIndex,
            stationId = requireArguments().getString(ARGUMENT_STATION_ID).orEmpty(),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = SheetStationDetailBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.favourite.setOnClickListener { viewModel.toggleFavourite() }
        views.openInNavigation.setOnClickListener { openInNavigationApp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest(::show)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun show(state: StationDetailUiState) {
        val views = binding ?: return
        val entry = state.entry ?: return

        views.name.text = entry.station.name
        views.bikesIndicator.display = entry.displayFor(AvailabilityMode.Bikes)
        views.docksIndicator.display = entry.displayFor(AvailabilityMode.Docks)
        showAddress(state.address)
        showServiceState(state)
        showCapacityAndFreshness(entry.station, state.fetchedAt)
        showFavourite(state.isFavourite)
    }

    private fun showAddress(address: AddressResult?) {
        val views = binding ?: return
        views.address.isGone = address == null
        if (address == null) return
        val place = if (address.postcode.isNullOrBlank()) {
            address.city
        } else {
            getString(R.string.address_locality, address.postcode, address.city)
        }
        // Une station posée au milieu d'un rond-point n'a pas d'adresse : on
        // nomme alors la rue voisine, en disant que c'est un voisinage.
        val what = if (address.houseNumber == null) {
            getString(R.string.station_address_nearby, address.streetName)
        } else {
            address.toTitle(requireContext())
        }
        views.address.text = getString(R.string.address_detail, what, place)
    }

    /**
     * Ne dit l'état que lorsqu'il fait obstacle.
     *
     * Une station qui fonctionne n'a pas à s'annoncer : ce sont ses chiffres
     * qui parlent. En revanche, une station hors service doit le dire avant
     * que l'utilisateur ne s'y rende.
     */
    private fun showServiceState(state: StationDetailUiState) {
        val views = binding ?: return
        val entry = state.entry ?: return
        views.serviceState.isVisible = entry.serviceState != ServiceState.InService
        views.serviceState.setText(
            when (entry.serviceState) {
                ServiceState.OutOfService -> R.string.station_out_of_service
                else -> R.string.station_availability_unknown
            },
        )
    }

    private fun showCapacityAndFreshness(station: Station, fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        val age = freshness.toStatusLine(requireContext(), freshness.isStale)
        views.capacity.text = station.capacity?.let { capacity ->
            getString(
                R.string.station_capacity_and_age,
                resources.getQuantityString(R.plurals.docks_total, capacity, capacity),
                age,
            )
        } ?: age
    }

    private fun showFavourite(isFavourite: Boolean) {
        val views = binding ?: return
        views.favourite.setIconResource(
            if (isFavourite) R.drawable.ic_favourite_filled else R.drawable.ic_favourite,
        )
        views.favourite.contentDescription = getString(
            if (isFavourite) R.string.station_favourite_remove else R.string.station_favourite_add,
        )
    }

    /**
     * Confie la station à une application de guidage (SPEC §7.2).
     *
     * L'URI `geo:` porte le nom de la station en plus de ses coordonnées :
     * l'application qui reçoit l'intention affiche ainsi un repère nommé
     * plutôt qu'un point anonyme.
     */
    private fun openInNavigationApp() {
        val station = viewModel.state.value.entry?.station ?: return
        val label = Uri.encode(station.name)
        val uri = (
            "geo:${station.position.latitude},${station.position.longitude}" +
                "?q=${station.position.latitude},${station.position.longitude}($label)"
            ).toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // Sur un appareil sans aucune application de cartographie, le dire
            // vaut mieux que de ne rien faire du tout.
            val views = binding ?: return
            Snackbar.make(
                views.root,
                R.string.station_no_navigation_app,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        private const val ARGUMENT_STATION_ID = "station-id"

        /** Étiquette sous laquelle la feuille est ajoutée au gestionnaire. */
        const val TAG: String = "detail-station"

        /** Ouvre la feuille pour la station donnée. */
        fun newInstance(stationId: String): StationDetailSheet = StationDetailSheet().apply {
            arguments = Bundle().apply { putString(ARGUMENT_STATION_ID, stationId) }
        }
    }
}
