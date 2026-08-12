package io.github.mgdx.rouelibre.ui.journey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.withScale
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.ui.prefersReducedMotion

/**
 * A bike crossing the screen while the journey is being worked out.
 *
 * The wait usually lasts a second or two, but a journey right across the
 * conurbation runs to half a minute on an older phone (SPEC §6, which sets no
 * deadline — which is exactly why the wait must be inhabited). It is the
 * only moment the application makes anybody wait, and a spinner says nothing
 * about what is happening. The bike of the journey's stations crosses from one
 * edge to the other, comes back along a higher line, and goes round again for as
 * long as the computation runs: motion in the service of understanding, which
 * is the only kind SPEC §7 accepts.
 *
 * It faces where it is going, and stops dead when the device asks for reduced
 * animations (SPEC §7) — the drawing then stands still in the middle, and the
 * sentence underneath does the explaining on its own.
 *
 * The animation lives with the view: it starts when the view is shown and
 * stops when it is hidden or detached, so nothing keeps running behind a
 * result that has arrived.
 */
class ComputingBikeView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0,
) : View(context, attributes, defaultStyle) {

    private val bike: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.marker_journey_station)

    private val bikeSize = resources.getDimensionPixelSize(R.dimen.computing_bike_size)

    /** How far apart the two lines are, the return one being the higher. */
    private val laneGap = resources.getDimensionPixelSize(R.dimen.computing_bike_lane_gap)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CROSSING_MILLIS * 2
        repeatCount = ValueAnimator.INFINITE
        // Constant speed: a bike crossing the screen has no reason to slow
        // down in the middle, and an eased sweep would read as hesitation.
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    /** Where the bike is in its round trip, from 0 to 1. */
    private val progress: Float
        get() = if (animator.isRunning) animator.animatedValue as Float else STILL_PROGRESS

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = paddingTop + bikeSize + laneGap + paddingBottom
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val drawable = bike ?: return
        val span = width + bikeSize
        // Two halves: out along the lower line, back along the higher one.
        val outward = progress < HALF
        val crossing = if (outward) progress * 2f else (progress - HALF) * 2f
        val travelled = if (outward) crossing else 1f - crossing
        val left = (-bikeSize + travelled * span).toInt()
        val top = paddingTop + if (outward) laneGap else 0

        drawable.setBounds(left, top, left + bikeSize, top + bikeSize)
        if (outward) {
            drawable.draw(canvas)
            return
        }
        // Coming back, it faces the other way: a bike riding backwards is the
        // first thing an eye notices.
        canvas.withScale(x = -1f, y = 1f, pivotX = left + bikeSize / 2f, pivotY = 0f) {
            drawable.draw(this)
        }
    }

    // `isShown` rather than this view's own visibility: what hides the bike is
    // the panel around it being taken away once the journey has been composed.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) start() else animator.cancel()
    }

    /** Nothing turns behind a screen that has gone to the background. */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isShown) start() else animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (animator.isRunning) return
        // A device asking for reduced animations gets none: the bike waits in
        // the middle of its crossing, where the drawing still reads (SPEC §7).
        if (context.prefersReducedMotion()) {
            invalidate()
            return
        }
        animator.start()
    }

    private companion object {
        /** How long the bike takes to cross the screen once, in milliseconds. */
        const val CROSSING_MILLIS = 1_100L

        const val HALF = 0.5f

        /** Where the bike stands when it does not move: mid-crossing. */
        const val STILL_PROGRESS = 0.25f
    }
}
