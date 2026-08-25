package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import io.github.mgdx.rouelibre.ui.address.AddressSearchFragment
import io.github.mgdx.rouelibre.ui.address.SearchShortcut
import io.github.mgdx.rouelibre.ui.map.MapFragment
import kotlinx.coroutines.launch

/**
 * The four ways of designating an end of a journey, wired to a screen (SPEC §7.3).
 *
 * Two screens fill those two fields — the search screen, and the result screen
 * where one corrects a point without going back. They fill them identically:
 * straight to the address search, which heads its list with one's position, a
 * point on the map and a favourite station. This holds that wiring once, rather
 * than in each of them: the round trip through the other screens, the field
 * that is waiting for the answer, and the permission asked for only at the
 * moment the user has understood what it is for (SPEC §10).
 *
 * Nothing here is written down. The points travel in fragment results and land
 * in a screen's state, which SPEC §8 wants gone with the screen.
 *
 * Must be built as a field of the fragment: it registers a permission request,
 * which Android only accepts before the fragment is started.
 *
 * @param fragment the screen being filled.
 * @param onMessage says what could not be done — a refused permission, a
 *   position out of reach.
 * @param onPicked hands over the point designated, and the field it is for.
 * @param onLocating says that a field is waiting on the satellites, and when it
 *   has stopped. A fix takes up to ten seconds; a field that says nothing for
 *   ten seconds reads as a press that was lost.
 */
class JourneyEndpointPicker(
    private val fragment: Fragment,
    private val onMessage: (String) -> Unit,
    private val onPicked: (endpoint: JourneyEndpoint, isOrigin: Boolean) -> Unit,
    private val onLocating: (isOrigin: Boolean, searching: Boolean) -> Unit,
) {

    /** The field the chosen way is to fill, for the length of the round trip. */
    private var awaitingOrigin = true

    private val container
        get() = (fragment.requireActivity().application as RoueLibreApplication).container

    /**
     * Requests the location permissions, and never insists.
     *
     * SPEC §10 is explicit: a refusal must neither block a screen nor trigger a
     * second prompt. A user who says no keeps the three other ways of
     * designating a point, whole.
     */
    private val requestLocationPermission = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            useMyPosition()
        } else {
            onMessage(fragment.getString(R.string.map_location_denied))
        }
    }

    /**
     * Opens what fills a field (SPEC §7.3).
     *
     * Straight to the address search, without a menu of ways in between: one
     * nearly always knows the address one is going to, and the three other ways
     * — one's position first — head the result list, a press away.
     *
     * @param isOrigin the field being filled.
     * @param otherEnd the end already known, if there is one: the results are
     *   then ranked by proximity to it, which is usually the right guess.
     * @param query a text to open the field with, for a designation that comes
     *   from elsewhere and still has to be worked on — the sentence shared by
     *   another application (SPEC §7.8). Nothing is chosen from it: it is
     *   written in the field, and the user searches from there.
     */
    fun choose(isOrigin: Boolean, otherEnd: Coordinates?, query: String? = null) {
        awaitingOrigin = isOrigin
        show(
            AddressSearchFragment.forJourneyEndpoint(
                isOrigin = isOrigin,
                origin = otherEnd,
                query = query,
            ),
        )
    }

    /**
     * Collects what the designation screens return.
     *
     * Each returns a point under its own key; here is where they meet the field
     * that was waiting for them. To be called from `onViewCreated`: the screen
     * is destroyed while one designates a point, and the result is delivered
     * when it comes back.
     */
    fun listen(owner: LifecycleOwner) {
        val manager = fragment.parentFragmentManager

        manager.setFragmentResultListener(
            AddressSearchFragment.SHORTCUT_REQUEST_KEY,
            owner,
        ) { _, result ->
            when (result.getString(AddressSearchFragment.RESULT_SHORTCUT)) {
                SearchShortcut.MyPosition.name -> askForMyPosition()
                SearchShortcut.OnMap.name -> show(MapFragment.forPicking())
                SearchShortcut.Favourite.name -> openFavourites()
            }
        }

        manager.setFragmentResultListener(AddressSearchFragment.REQUEST_KEY, owner) { _, result ->
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

        manager.setFragmentResultListener(MapFragment.PICK_REQUEST_KEY, owner) { _, result ->
            JourneyEndpoint.readFrom(result, MapFragment.PICK_RESULT_PREFIX)?.let(::accept)
        }

        manager.setFragmentResultListener(FavouriteStationSheet.REQUEST_KEY, owner) { _, result ->
            awaitingOrigin = result.getBoolean(FavouriteStationSheet.RESULT_IS_ORIGIN, true)
            JourneyEndpoint.readFrom(result, FavouriteStationSheet.RESULT_PREFIX)?.let(::accept)
        }
    }

    /** Keeps the awaited field across a rebuild of the screen. */
    fun writeTo(outState: Bundle) {
        outState.putBoolean(STATE_AWAITING_ORIGIN, awaitingOrigin)
    }

    /** Reads back what [writeTo] kept, if the screen is being rebuilt. */
    fun readFrom(state: Bundle?) {
        awaitingOrigin = state?.getBoolean(STATE_AWAITING_ORIGIN, true) ?: true
    }

    private fun accept(endpoint: JourneyEndpoint) = onPicked(endpoint, awaitingOrigin)

    private fun askForMyPosition() {
        if (container.deviceLocation.isPermitted()) {
            useMyPosition()
        } else {
            requestLocationPermission.launch(DeviceLocation.PERMISSIONS)
        }
    }

    /**
     * Goes and gets a fresh position for the field that is waiting.
     *
     * The wait is shown in that field: a first fix takes several seconds
     * indoors, and until this said so, the screen came back from the address
     * search with nothing changed — which reads as a press that went nowhere,
     * and gets pressed again.
     *
     * The field being filled is settled here rather than read back later: the
     * user may designate the other end while this waits.
     */
    private fun useMyPosition() {
        val isOrigin = awaitingOrigin
        onLocating(isOrigin, true)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val position = try {
                container.deviceLocation.current()?.coordinates
            } finally {
                onLocating(isOrigin, false)
            }
            if (position == null) {
                onMessage(fragment.getString(R.string.map_location_unavailable))
                return@launch
            }
            onPicked(
                JourneyEndpoint(
                    fragment.getString(R.string.journey_source_my_position),
                    position,
                ),
                isOrigin,
            )
        }
    }

    private fun openFavourites() {
        FavouriteStationSheet.newInstance(awaitingOrigin)
            .show(fragment.parentFragmentManager, FavouriteStationSheet.TAG)
    }

    private fun show(destination: Fragment) {
        fragment.parentFragmentManager.beginTransaction()
            .replace(R.id.content, destination)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        const val STATE_AWAITING_ORIGIN = "awaited-field"
    }
}
