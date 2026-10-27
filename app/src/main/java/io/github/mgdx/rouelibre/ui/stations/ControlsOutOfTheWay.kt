package io.github.mgdx.rouelibre.ui.stations

import android.animation.ValueAnimator
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

/**
 * Slides the controls floating over the station list out of the way while the
 * reader scrolls down it, and back the moment they scroll up (SPEC §7.6).
 *
 * The list carries the map's column of controls at the bottom end, and the
 * map's filters at the bottom start, so that each is found where it is on the
 * map (issue #12). Over a map that costs a few streets; over a list it covers
 * the counts at the end of four rows and the disc at the start of one, and
 * the room left under the last row only helps once the list is scrolled to
 * its end. So they go while one reads downwards — which is reading — and come
 * back on the first move upwards, which is what one does to go back or to
 * change screens. The pattern is the platform's own for a button floating over
 * a list, and it keeps the controls where they are rather than in a bar that
 * would take a row's height for good.
 *
 * **Never under a screen reader that explores by touch.** A control that
 * leaves the screen is one such a reader cannot find by sliding a finger
 * across it, and a reader moving from row to row scrolls the list without
 * meaning to put anything away. The controls then stay, and the reader
 * reaches the rows behind them by moving from one to the next.
 *
 * **Without animations when the system has them removed**: the controls then
 * go and come back at once.
 *
 * @param list the list whose scrolling moves the controls.
 * @param controls the views to slide away, `null` ones skipped.
 * @param ground the view whose bottom edge the controls slide past.
 */
internal class ControlsOutOfTheWay(
    private val list: RecyclerView,
    private val controls: () -> List<View?>,
    private val ground: View,
) : RecyclerView.OnScrollListener() {

    private var away = false

    /** Starts following the list. */
    fun attach() {
        list.addOnScrollListener(this)
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        when {
            // Back at the top there is nothing more to read upwards, and a
            // list too short to scroll never brings them back otherwise.
            !recyclerView.canScrollVertically(-1) -> bringBack()
            readerExploresByTouch() -> bringBack()
            dy > 0 -> putAway()
            dy < 0 -> bringBack()
        }
    }

    /**
     * Puts the controls away while the keyboard is up, and back when it goes.
     *
     * The keyboard takes the bottom half of the screen and the controls ride
     * up on its edge, over the few rows a search leaves in sight — which are
     * the answer being typed for. A screen reader exploring by touch keeps
     * them, for the reason the class gives.
     */
    fun followKeyboard(up: Boolean) {
        if (up && !readerExploresByTouch()) putAway() else bringBack()
    }

    /** Puts the controls back at once, whatever the list did meanwhile. */
    fun bringBack() {
        if (!away) return
        away = false
        controls().filterNotNull().forEach { slide(it, 0f) }
    }

    private fun putAway() {
        if (away) return
        val shown = controls().filterNotNull().filter { it.isVisible }
        val highest = shown.minOfOrNull { it.top } ?: return
        away = true
        // One distance for all of them, so the column slides as one piece
        // rather than each disc at a speed of its own.
        val distance = (ground.height - highest).toFloat()
        controls().filterNotNull().forEach { slide(it, distance) }
    }

    private fun slide(view: View, translation: Float) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.translationY = translation
            return
        }
        view.animate()
            .translationY(translation)
            .setDuration(view.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
            .start()
    }

    private fun readerExploresByTouch(): Boolean =
        list.context.getSystemService(AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
}
