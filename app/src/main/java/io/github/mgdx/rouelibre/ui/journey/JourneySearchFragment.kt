package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.databinding.FragmentJourneySearchBinding
import io.github.mgdx.rouelibre.ui.address.AddressSearchFragment
import io.github.mgdx.rouelibre.ui.map.MapFragment
import kotlinx.coroutines.launch

/**
 * Recherche d'itinéraire : d'où à où (SPEC §7.3).
 *
 * Deux points à désigner, chacun de quatre façons, et un bouton pour les
 * intervertir. Rien n'est calculé ici : l'écran ne fait que rassembler ce
 * qu'il faut au calcul, qui a lieu sur l'écran de résultat.
 *
 * Aucune de ces désignations ne quitte l'appareil, et aucune n'est conservée :
 * le SPEC §8 interdit de garder une destination.
 */
class JourneySearchFragment : Fragment() {

    private var binding: FragmentJourneySearchBinding? = null

    private var origin: JourneyEndpoint? = null
    private var destination: JourneyEndpoint? = null

    /** Le champ que la façon choisie doit remplir, le temps de l'aller-retour. */
    private var awaitingOrigin = true

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            useMyPosition()
        } else {
            // Le refus n'est pas discuté : les trois autres façons de désigner
            // un point restent entières (SPEC §10).
            showMessage(getString(R.string.map_location_denied))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentJourneySearchBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        // Uniquement quand l'écran est reconstruit après avoir été détruit —
        // rotation, retour depuis l'arrière-plan. Passer par la recherche
        // d'adresse ne détruit que la VUE : les champs déjà remplis vivent
        // toujours dans le fragment, et les relire d'un paquet absent les
        // effaçait. Le second point venait alors écraser le premier.
        if (savedInstanceState != null) {
            origin = JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
            destination = JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
            awaitingOrigin = savedInstanceState.getBoolean(STATE_AWAITING_ORIGIN, true)
        } else {
            // Point reçu d'ailleurs : d'une autre application (SPEC §7.8) ou
            // d'une station qu'on vient de consulter (SPEC §7.2). Il ne reste
            // à remplir que l'autre extrémité.
            if (origin == null) origin = JourneyEndpoint.readFrom(arguments, ARGUMENT_ORIGIN)
            if (destination == null) {
                destination = JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)
            }
        }

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.origin.setOnClickListener { chooseEndpoint(isOrigin = true) }
        views.destination.setOnClickListener { chooseEndpoint(isOrigin = false) }
        views.swap.setOnClickListener { swap() }
        views.compute.setOnClickListener { openResult() }

        listenForChoices()
        showEndpoints()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        origin?.writeTo(outState, STATE_ORIGIN)
        destination?.writeTo(outState, STATE_DESTINATION)
        outState.putBoolean(STATE_AWAITING_ORIGIN, awaitingOrigin)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun chooseEndpoint(isOrigin: Boolean) {
        awaitingOrigin = isOrigin
        EndpointChooserSheet.newInstance(isOrigin)
            .show(parentFragmentManager, EndpointChooserSheet.TAG)
    }

    /**
     * Recueille ce que rendent les trois écrans de désignation.
     *
     * Chacun rend un point sous sa propre clé ; c'est ici qu'ils rejoignent le
     * champ qui les attendait.
     */
    private fun listenForChoices() {
        parentFragmentManager.setFragmentResultListener(
            EndpointChooserSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            awaitingOrigin = result.getBoolean(EndpointChooserSheet.RESULT_IS_ORIGIN, true)
            when (result.getString(EndpointChooserSheet.RESULT_SOURCE)) {
                EndpointChooserSheet.SOURCE_MY_POSITION -> askForMyPosition()
                EndpointChooserSheet.SOURCE_ADDRESS -> openAddressSearch()
                EndpointChooserSheet.SOURCE_ON_MAP -> openMapPicker()
                EndpointChooserSheet.SOURCE_FAVOURITE -> openFavourites()
            }
        }

        parentFragmentManager.setFragmentResultListener(
            AddressSearchFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            accept(
                JourneyEndpoint(
                    label = result.getString(AddressSearchFragment.RESULT_LABEL).orEmpty(),
                    position = Coordinates(
                        result.getDouble(AddressSearchFragment.RESULT_LATITUDE),
                        result.getDouble(AddressSearchFragment.RESULT_LONGITUDE),
                    ),
                ),
            )
        }

        parentFragmentManager.setFragmentResultListener(
            MapFragment.PICK_REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            JourneyEndpoint.readFrom(result, MapFragment.PICK_RESULT_PREFIX)?.let(::accept)
        }

        parentFragmentManager.setFragmentResultListener(
            FavouriteStationSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            awaitingOrigin = result.getBoolean(FavouriteStationSheet.RESULT_IS_ORIGIN, true)
            JourneyEndpoint.readFrom(result, FavouriteStationSheet.RESULT_PREFIX)?.let(::accept)
        }
    }

    private fun accept(endpoint: JourneyEndpoint) {
        if (awaitingOrigin) origin = endpoint else destination = endpoint
        showEndpoints()
    }

    private fun askForMyPosition() {
        if (container.deviceLocation.isPermitted()) {
            useMyPosition()
        } else {
            requestLocationPermission.launch(DeviceLocation.PERMISSIONS)
        }
    }

    private fun useMyPosition() {
        viewLifecycleOwner.lifecycleScope.launch {
            val position = container.deviceLocation.current()
            if (position == null) {
                showMessage(getString(R.string.map_location_unavailable))
                return@launch
            }
            accept(JourneyEndpoint(getString(R.string.journey_source_my_position), position))
        }
    }

    private fun openAddressSearch() {
        show(AddressSearchFragment.newInstance(origin?.position ?: destination?.position))
    }

    private fun openMapPicker() {
        show(MapFragment.forPicking())
    }

    private fun openFavourites() {
        FavouriteStationSheet.newInstance(awaitingOrigin)
            .show(parentFragmentManager, FavouriteStationSheet.TAG)
    }

    private fun swap() {
        val previousOrigin = origin
        origin = destination
        destination = previousOrigin
        showEndpoints()
    }

    private fun showEndpoints() {
        val views = binding ?: return
        views.origin.text = origin?.label ?: getString(R.string.journey_origin_empty)
        views.destination.text = destination?.label
            ?: getString(R.string.journey_destination_empty)
        views.compute.isEnabled = origin != null && destination != null
    }

    private fun openResult() {
        val from = origin ?: return
        val to = destination ?: return
        show(JourneyResultFragment.newInstance(from, to))
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showMessage(message: String) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val STATE_ORIGIN = "depart"
        private const val STATE_DESTINATION = "arrivee"
        private const val STATE_AWAITING_ORIGIN = "champ-attendu"
        private const val ARGUMENT_DESTINATION = "arrivee-recue"
        private const val ARGUMENT_ORIGIN = "depart-recu"

        /**
         * Ouvre la recherche, éventuellement avec une extrémité déjà connue.
         *
         * @param origin le point d'où l'on part, s'il est déjà désigné.
         * @param destination le point où l'on va, s'il est déjà désigné.
         *   Les deux nuls donnent un écran vierge.
         */
        fun newInstance(
            origin: JourneyEndpoint? = null,
            destination: JourneyEndpoint? = null,
        ): JourneySearchFragment = JourneySearchFragment().apply {
            if (origin == null && destination == null) return@apply
            arguments = Bundle().apply {
                origin?.writeTo(this, ARGUMENT_ORIGIN)
                destination?.writeTo(this, ARGUMENT_DESTINATION)
            }
        }
    }
}
