package io.github.mgdx.rouelibre.ui.map

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The user's point on a map screen: the permission it takes, the "locate me"
 * button that asks for it, and the following of the device once it is granted
 * (SPEC §7.1, §7.4, §10).
 *
 * The main map and the journey result do this the same way, and only this.
 * What each does with a fix once it has one — how close it frames, what it says
 * outside the city — is its own, and stays with it: the `locate` each hands
 * over.
 *
 * The position is neither kept nor written: it frames the map and lives in the
 * display source, for the session (SPEC §2, C3).
 *
 * Must be built as a field of the fragment: it registers a permission request,
 * which Android only accepts before the fragment is started.
 *
 * @param fragment the map screen.
 * @param display draws the point, while a style carries it.
 * @param locate looks for a fix and frames the map on it, the permission being
 *   granted.
 * @param onMessage says what could not be done, given as a string resource.
 */
internal class UserPositionOnMap(
    private val fragment: Fragment,
    private val display: () -> UserPositionDisplay?,
    private val locate: () -> Unit,
    private val onMessage: (Int) -> Unit,
) {

    /**
     * The last fix shown, held in memory for the life of the screen only.
     *
     * It survives a rebuild of the view — going through the list and coming
     * back, or a rotation, must not make the point vanish until the next
     * fix — and nothing else: it is written nowhere (SPEC §2, C3).
     *
     * The fix and not its coordinates alone: the circle of uncertainty has to
     * come back with the point it belongs to.
     */
    var lastKnown: PositionFix? = null
        private set

    /**
     * Whether the point may follow the device: the permission as it stands.
     *
     * A flow rather than a call, because the answer changes while the map is
     * up — granted from the button, or from the Android settings while the
     * application was away — and the following has to start on that answer.
     * Asking again on every return to the screen would mean subscribing again
     * on every return, and stacking one subscription per visit.
     */
    private val permitted = MutableStateFlow(false)

    private val container
        get() = (fragment.requireActivity().application as RoueLibreApplication).container

    /**
     * Requests the location permissions, and never insists.
     *
     * SPEC §10 is explicit: a refusal must neither block a screen nor trigger a
     * second prompt. A user who says no keeps a fully usable application, in
     * which they designate their points by hand.
     */
    private val requestLocationPermission = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            locate()
            // Only now may the point start following: before this answer there
            // was nothing to follow it with (SPEC §10).
            permitted.value = true
        } else {
            // Written down for good: after this, nothing asks unprompted
            // again — here or on any other screen, this launch or the next
            // (SPEC §10). The buttons remain the way to change one's mind.
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                container.automaticLocationRequest.noteRefused()
            }
            onMessage(R.string.map_location_denied)
        }
    }

    /**
     * Asks for the permission without being prompted, where that is still
     * allowed (SPEC §7.1).
     *
     * **Once per session, only if it has never been granted, and not at all
     * once a refusal has been recorded — this session or any earlier one.**
     * Asking again at each return to the map, or at the launch after a "no", is
     * the insistence SPEC §10 forbids.
     */
    fun askUnprompted() {
        if (container.deviceLocation.isPermitted()) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            if (container.automaticLocationRequest.mayAskUnprompted()) {
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)
            }
        }
    }

    /** Answers the "locate me" button — see [locateMeStep]. */
    fun onLocateMeClicked() {
        val location = container.deviceLocation
        when (locateMeStep(location.isPermitted(), location::isAvailable)) {
            LocateMeStep.AskPermission ->
                requestLocationPermission.launch(DeviceLocation.PERMISSIONS)

            LocateMeStep.SayUnavailable -> onMessage(R.string.map_location_unavailable)

            LocateMeStep.Locate -> locate()
        }
    }

    /**
     * Moves the point as the device moves.
     *
     * **The point follows, the map does not.** Recentring at every fix would
     * take the map back from under the user each time they looked a street
     * further on; the framing stays theirs, and "locate me" is what brings it
     * back to the point.
     *
     * Only if the permission is granted: opening a map asks for nothing it has
     * not been allowed to, and a user who has refused keeps a map without a
     * point rather than a second prompt (SPEC §10). The permission granted
     * later — from the button, or from the Android settings — opens the gate,
     * and the following starts there and then.
     *
     * Subscribed once for the life of the view, and following the screen —
     * `repeatOnLifecycle` drops it as soon as the map is no longer displayed,
     * so nothing listens to the satellites in the background.
     */
    fun follow() {
        val owner = fragment.viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permitted.collectLatest { allowed ->
                    if (!allowed) return@collectLatest
                    container.deviceLocation.positions().collect(::show)
                }
            }
        }
    }

    /**
     * Reads the permission again on the way back to the screen.
     *
     * It may have been granted, or withdrawn, from the Android settings while
     * the application was away: the point starts or stops following on the way
     * back, without a detour through the button.
     */
    fun readPermission() {
        permitted.value = container.deviceLocation.isPermitted()
    }

    /**
     * Draws the point and, when the fix is a coarse one, the circle around it.
     *
     * Point and circle move together, and they glide rather than jump — see
     * [UserPositionDisplay].
     */
    fun show(fix: PositionFix) {
        lastKnown = fix
        display()?.show(fix)
    }

    /** Draws the last fix again, after the style it was drawn in went. */
    fun showLastKnown() {
        display()?.show(lastKnown)
    }

    /**
     * Holds a fix without drawing it — the map is about to be framed on it, and
     * the style it will be drawn into is not there yet.
     */
    fun remember(fix: PositionFix) {
        lastKnown = fix
    }
}
