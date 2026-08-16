package io.github.mgdx.rouelibre.ui.stations

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.RoueLibreApplication
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Says whether the availability figures are drawn larger, and says it again when
 * that changes (SPEC §7, §7.6).
 *
 * Written once for the three screens that show an indicator — the station list,
 * the favourites and a station's sheet — rather than three times: they must
 * answer to the same setting, and a screen that forgot to ask would be a figure
 * of a different size on the same phone.
 *
 * Followed rather than read once, so a setting changed applies to a list already
 * on screen when one comes back to it, without the application being restarted.
 *
 * @param apply what to redraw, run on the main thread while the view is started,
 *   and safe to run more than once.
 */
fun Fragment.withLargeAvailabilityNumbers(apply: (large: Boolean) -> Unit) {
    val container = (requireActivity().application as RoueLibreApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.preferences.largeAvailabilityNumbers
                // Every emission of the settings file reaches this flow, most of
                // them about something else entirely: redrawing a list of three
                // hundred rows on a theme being pressed would be work for
                // nothing.
                .distinctUntilChanged()
                .collect(apply)
        }
    }
}
