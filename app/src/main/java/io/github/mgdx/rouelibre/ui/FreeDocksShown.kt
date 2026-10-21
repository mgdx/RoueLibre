package io.github.mgdx.rouelibre.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.RoueLibreApplication
import kotlinx.coroutines.launch

/**
 * Says whether the network in service has free docks worth talking about
 * (SPEC §7.1), and again whenever that changes.
 *
 * Where nearly every station takes back any bike, every screen stops talking
 * about free docks — no Bikes / Free docks toggle, no figure, no "∞", no word —
 * and a screen that shows them asks here first. The answer is the one the
 * station repository settles once per city and per session, so the map, the
 * list, the favourites, a station's sheet and a journey's detail cannot
 * disagree. It is `true` until the stations can be weighed, which leaves every
 * docked network exactly as it was.
 *
 * [apply] must be safe to run more than once: it shows or hides, it does not
 * append.
 *
 * @param apply what to show or hide, run on the main thread while the view is
 *   started.
 */
fun Fragment.withFreeDocksShown(apply: (shown: Boolean) -> Unit) {
    val container = (requireActivity().application as RoueLibreApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.stationRepository.observeFreeDocksShown().collect(apply)
        }
    }
}
