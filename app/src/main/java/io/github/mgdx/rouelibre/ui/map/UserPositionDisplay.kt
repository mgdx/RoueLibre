package io.github.mgdx.rouelibre.ui.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.core.geo.interpolatedTowards
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Draws the user's point on a map, gliding it from fix to fix.
 *
 * The following delivers a fix every couple of seconds and five metres at
 * best (see `DeviceLocation`), and a point redrawn at each arrival teleports:
 * a jump of eight metres every two seconds reads as a glitch, where a point
 * that walks reads as "me". Each new fix is therefore reached by animation
 * from the point currently drawn (SPEC §7.1), the circle of uncertainty
 * travelling with it — a circle left waiting at the far end would circle a
 * place the point has not reached yet.
 *
 * A [ValueAnimator] carries the glide, which is what makes the system's
 * "remove animations" accessibility setting respected without a line here:
 * with the animator scale at zero the glide collapses back to the plain jump.
 *
 * One instance per loaded map style, cancelled with the view: a frame drawn
 * into a destroyed map's sources would reach freed native memory.
 */
class UserPositionDisplay(
    private val positionSource: GeoJsonSource,
    private val accuracySource: GeoJsonSource,
) {

    private var glide: ValueAnimator? = null

    /** The fix the sources hold at this instant — mid-glide, an interpolated one. */
    private var drawn: PositionFix? = null

    /**
     * Draws [fix], gliding there from the point already on screen.
     *
     * The first fix of a view is drawn where it is, there being nothing yet
     * to glide from. `null` clears the point at once: a glide toward nothing
     * would linger on a position nobody is reporting.
     */
    fun show(fix: PositionFix?) {
        glide?.cancel()
        val from = drawn
        if (fix == null || from == null) {
            draw(fix)
            return
        }
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLIDE_MILLIS
            // Linear on purpose: the glides chain one after another while the
            // person walks, and an eased one reads as stop-and-go.
            interpolator = LinearInterpolator()
            addUpdateListener {
                draw(from.interpolatedTowards(fix, (it.animatedValue as Float).toDouble()))
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    // A cancelled glide must not land: the fix it was walking
                    // to has been replaced by the one that cancelled it.
                    if (!cancelled) draw(fix)
                }
            })
            start()
        }
    }

    /** Stops any glide under way; the display is then inert. */
    fun cancel() {
        glide?.cancel()
        glide = null
    }

    private fun draw(fix: PositionFix?) {
        drawn = fix
        positionSource.setGeoJson(UserPositionMarker.featureFor(fix))
        accuracySource.setGeoJson(UserPositionMarker.accuracyFeatureFor(fix))
    }

    companion object {
        /**
         * How long the point takes to reach a new fix, in milliseconds.
         *
         * One second: fixes arrive at best every two (`DeviceLocation`'s
         * following interval), so the point spends half its time walking and
         * half at rest — visibly alive without ever lagging a full fix
         * behind.
         */
        private const val GLIDE_MILLIS = 1_000L
    }
}
