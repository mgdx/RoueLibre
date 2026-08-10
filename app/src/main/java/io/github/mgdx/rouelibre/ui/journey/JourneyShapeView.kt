package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R

/**
 * The shape of the journey being shown: walk, ride, walk, with its distances.
 *
 * The same drawing as the search screen's illustration — outlined disc bearing
 * a walking figure at either end, filled disc bearing a bike at each station,
 * dotted strokes for the walks and an unbroken one for the ride — except that
 * this one carries the journey actually computed, and writes under each stroke
 * how far it runs.
 *
 * It is the whole journey seen at once, where the step list underneath reads
 * one line at a time. The two say the same thing; that is why the drawing is
 * decorative for a screen reader, which reads the steps instead.
 *
 * Drawn by hand rather than assembled from views: four discs, three strokes and
 * three labels whose widths depend on one another are a layout pass to write in
 * XML and six lines of arithmetic here.
 */
class JourneyShapeView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0,
) : View(context, attributes, defaultStyle) {

    /**
     * One leg of the journey.
     *
     * @property isRide true for the bike leg, false for a walk.
     * @property distance how far it runs, already put into words.
     */
    data class Leg(val isRide: Boolean, val distance: String)

    private val walkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ink_soft)
        strokeWidth = resources.getDimension(R.dimen.journey_shape_walk_stroke)
        // The same tight pattern as the map's walking legs: on a phone screen,
        // wide dashes stop reading as a succession of steps.
        pathEffect = DashPathEffect(
            floatArrayOf(
                resources.getDimension(R.dimen.journey_shape_dash),
                resources.getDimension(R.dimen.journey_shape_dash_gap),
            ),
            0f,
        )
    }

    private val ridePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.signal)
        strokeWidth = resources.getDimension(R.dimen.journey_shape_ride_stroke)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.ink_soft)
        textSize = resources.getDimension(R.dimen.text_label)
    }

    private val endpointMarker: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.marker_journey_endpoint)

    private val stationMarker: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.marker_journey_station)

    private val nodeSize = resources.getDimensionPixelSize(R.dimen.journey_shape_node)

    private val labelGap = resources.getDimensionPixelSize(R.dimen.space_s)

    /** How far a stroke stops short of the discs it joins. */
    private val strokeInset = resources.getDimension(R.dimen.space_xs)

    /** The legs shown, in the order one lives them. Assignment redraws. */
    var legs: List<Leg> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = textPaint.fontMetrics
        val height = paddingTop + nodeSize + labelGap +
            (metrics.descent - metrics.ascent).toInt() + paddingBottom
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (legs.isEmpty()) return
        val span = width - paddingStart - paddingEnd
        val nodes = legs.size + 1
        // What the strokes may occupy, once every disc has its own width.
        val strokeSpan = (span - nodes * nodeSize).toFloat() / legs.size
        if (strokeSpan <= 0f) return

        val centreY = (paddingTop + nodeSize / 2).toFloat()
        val baseline = paddingTop + nodeSize + labelGap - textPaint.fontMetrics.ascent
        legs.forEachIndexed { index, leg ->
            // Read off the two discs the leg joins rather than from its own
            // rank: in a right-to-left layout the second of them is the one on
            // the left.
            val before = nodeLeft(index)
            val after = nodeLeft(index + 1)
            val start = minOf(before, after) + nodeSize + strokeInset
            val end = maxOf(before, after) - strokeInset
            canvas.drawLine(start, centreY, end, centreY, if (leg.isRide) ridePaint else walkPaint)
            canvas.drawText(leg.distance, (start + end) / 2f, baseline, textPaint)
        }

        // The discs last: a stroke that overshot its inset would be covered
        // rather than crossing the disc it points at.
        repeat(nodes) { index ->
            // The ends are where the user stands, the ones between are
            // stations: the same distinction the illustration draws.
            val marker = if (index == 0 || index == nodes - 1) endpointMarker else stationMarker
            val left = nodeLeft(index).toInt()
            marker?.setBounds(left, paddingTop, left + nodeSize, paddingTop + nodeSize)
            marker?.draw(canvas)
        }
    }

    /**
     * Where the disc of rank [index] starts.
     *
     * Right to left in a right-to-left language: the drawing tells a sequence,
     * and a sequence is read in the direction of the text around it.
     */
    private fun nodeLeft(index: Int): Float {
        val span = width - paddingStart - paddingEnd
        val strokeSpan = (span - (legs.size + 1) * nodeSize).toFloat() / legs.size
        val rank = if (layoutDirection == LAYOUT_DIRECTION_RTL) legs.size - index else index
        return paddingStart + rank * (nodeSize + strokeSpan)
    }
}
