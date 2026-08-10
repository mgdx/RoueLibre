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
 * Journey search: from where to where (SPEC §7.3).
 *
 * Two points to designate, each in four ways, and a button to swap them.
 * Nothing is computed here: the screen only gathers what the computation needs,
 * and that happens on the result screen.
 *
 * None of these designations leaves the device, and none is kept: SPEC §8
 * forbids holding on to a destination.
 */
class JourneySearchFragment : Fragment() {

    private var binding: FragmentJourneySearchBinding? = null

    private var origin: JourneyEndpoint? = null
    private var destination: JourneyEndpoint? = null

    /** The field the chosen way is to fill, for the length of the round trip. */
    private var awaitingOrigin = true

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            useMyPosition()
        } else {
            // The refusal is not argued with: the other three ways of
            // designating a point remain whole (SPEC §10).
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

        // Only when the screen is rebuilt after being destroyed — rotation, a
        // return from the background. Going through the address search destroys
        // the VIEW alone: the fields already filled still live in the fragment,
        // and re-reading them from an absent bundle erased them. The second
        // point then overwrote the first.
        if (savedInstanceState != null) {
            origin = JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
            destination = JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
            awaitingOrigin = savedInstanceState.getBoolean(STATE_AWAITING_ORIGIN, true)
        } else {
            // A point received from elsewhere: from another application
            // (SPEC §7.8) or from a station just consulted (SPEC §7.2). Only
            // the other end remains to be filled.
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
     * Collects what the three designation screens return.
     *
     * Each returns a point under its own key; here is where they meet the field
     * that was waiting for them.
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
        private const val STATE_ORIGIN = "origin"
        private const val STATE_DESTINATION = "destination"
        private const val STATE_AWAITING_ORIGIN = "awaited-field"
        private const val ARGUMENT_DESTINATION = "received-destination"
        private const val ARGUMENT_ORIGIN = "received-origin"

        /**
         * Opens the search, possibly with one end already known.
         *
         * @param origin the point one leaves from, if it is already designated.
         * @param destination the point one goes to, if it is already
         *   designated. Both null gives a blank screen.
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
