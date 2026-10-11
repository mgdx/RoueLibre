package io.github.mgdx.rouelibre.ui.map

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.FragmentMapBinding
import io.github.mgdx.rouelibre.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Keeps the controls laid over the main map clear of the system bars and of
 * the activity's banner (SPEC §7.1).
 *
 * The map fills the whole screen, bars included — that is what gives it its
 * sweep. The controls laid on top, however, must stay reachable.
 *
 * @param binding the screen's views, while they exist.
 */
internal class MapBottomEdge(private val binding: () -> FragmentMapBinding?) {

    /**
     * The room the system bars take at the bottom, and the room the banner
     * takes above them — see [holdTheBottomEdge].
     *
     * Both are held because both push the same edge up and neither knows about
     * the other: the insets arrive from the window, the banner's room from the
     * activity, and either may change while the other stands.
     */
    private var barsAtTheBottom = 0
    private var roomForTheBanner = 0

    /**
     * Keeps the controls clear of the system bars.
     *
     * Without this inset, the freshness label slid under the clock and the list
     * button under the navigation bar.
     */
    fun applySystemInsets(views: FragmentMapBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(views.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val baseMargin = views.root.resources.getDimensionPixelSize(R.dimen.space_m)
            views.freshness.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + baseMargin
            }
            views.openSettings.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + baseMargin
            }
            barsAtTheBottom = insets.bottom
            holdTheBottomEdge()
            windowInsets
        }
    }

    /**
     * Follows the room the banner takes, so the controls under it rise rather
     * than be covered (SPEC §7.1).
     *
     * The banner belongs to the activity and is laid over the whole window, so
     * nothing shifts these controls out of its way by itself — see
     * `MainActivity.roomTakenByTheBanner`.
     */
    fun standClearOfTheBanner(owner: LifecycleOwner, host: MainActivity?) {
        if (host == null) return
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.roomTakenByTheBanner.collect { room ->
                    roomForTheBanner = room
                    holdTheBottomEdge()
                }
            }
        }
    }

    /**
     * Keeps the controls laid along the bottom clear of the banner, as well as
     * of the system bars.
     *
     * The whole bottom of this screen hangs off the attribution — the toggles
     * and the place found from it, the four buttons up the right-hand edge
     * from it in turn — so raising that one view raises them all, and the
     * constraints keep them in their order. That is the only handle there is:
     * a `translationY` moves the view it is set on and leaves everything
     * constrained to it where it was.
     *
     * The room the banner takes is added to the bars' rather than replacing
     * it: the banner clears the bars on its own, and this margin is counted
     * from the window's edge.
     */
    private fun holdTheBottomEdge() {
        val attribution = binding()?.attribution ?: return
        // How tall the banner stands is read from the banner's own layout, so
        // this is called in the middle of the window's layout pass. A view that
        // asks for a new layout during one makes Android throw the pass away
        // and run it again — `requestLayout() improperly called ... running
        // second layout pass`. Waiting for the pass to end costs the one frame
        // the banner spends sliding in, and nothing is drawn differently.
        if (attribution.isInLayout) {
            attribution.post { holdTheBottomEdge() }
            return
        }
        attribution.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = barsAtTheBottom + roomForTheBanner
        }
    }
}
