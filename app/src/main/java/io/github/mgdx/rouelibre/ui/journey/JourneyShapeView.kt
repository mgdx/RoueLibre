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
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.BikeGlyphs

/**
 * The shape of the journey being shown: walk, ride, walk, timed and measured.
 *
 * The same drawing as the search screen's illustration — outlined disc bearing
 * a walking figure at either end, filled disc bearing a bike at each station,
 * dotted strokes for the walks and an unbroken one for the ride — except that
 * this one carries the journey actually computed, and writes on each stroke how
 * far it runs above and how long it takes below.
 *
 * The two lines are not interchangeable: it is the minutes one leaves the
 * drawing with, so they sit on the side the reading goes on towards, and the
 * distance labels its stroke from above.
 *
 * It is the whole journey seen at once, where the step list reads one line at a
 * time. The two say the same thing; that is why the drawing is decorative for a
 * screen reader, which reads the steps instead.
 *
 * Drawn by hand rather than assembled from views: four discs, three strokes and
 * six labels whose widths depend on one another are a layout pass to write in
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
     * @property duration how long it takes, already put into words.
     * @property distance how far it runs, already put into words.
     */
    data class Leg(val isRide: Boolean, val duration: String, val distance: String)

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

    /**
     * The times, in full ink.
     *
     * A journey is chosen on its minutes, not on its metres: the two lines are
     * the same size, and the contrast alone says which of them one reads first.
     */
    private val durationPaint = Paint(textPaint).apply {
        color = ContextCompat.getColor(context, R.color.ink)
    }

    private val endpointMarker: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.marker_journey_endpoint)

    /**
     * The ends of a journey ridden from one end to the other (SPEC §7.3).
     *
     * The figure inside a disc says how that point is lived, and nothing of
     * that journey is walked. It is the filled bike disc the interface already
     * draws, rather than a fourth drawing invented for the occasion. It never
     * takes the cog, and it takes the bolt from the rider rather than from the
     * network: what the network lends says nothing about a bike that is not the
     * network's, whereas the rider saying they ride an electric one says
     * everything about it (SPEC §7.6).
     */
    private var ownBikeEndpointMarker: Drawable? = AppCompatResources.getDrawable(
        context,
        OwnBikeGlyphs.endpointMarker(OwnBikeKind.Mechanical),
    )

    /**
     * What the rider said their own bike is (SPEC §7.6).
     *
     * It reaches only the two ends of a journey ridden on that bike, and only
     * what is drawn: no minute of this drawing depends on it. Deliberately
     * separate from [fleet] beside it, which says what the **network** lends —
     * the two answer different questions and are never read off one another.
     */
    var ownBikeKind: OwnBikeKind = OwnBikeKind.Mechanical
        set(value) {
            if (field == value) return
            field = value
            ownBikeEndpointMarker = AppCompatResources.getDrawable(
                context,
                OwnBikeGlyphs.endpointMarker(value),
            )
            invalidate()
        }

    private var stationMarker: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.marker_journey_station)

    /**
     * What the network served lends (SPEC §15).
     *
     * The two station discs then bear the same badges as the map's markers and
     * the search screen's illustration: the same journey, drawn three times,
     * must be recognised from one screen to the next.
     */
    var fleet: BikeFleet = BikeFleet.Mechanical
        set(value) {
            if (field == value) return
            field = value
            stationMarker = AppCompatResources.getDrawable(
                context,
                BikeGlyphs.stationMarker(value),
            )
            invalidate()
        }

    private val nodeSize = resources.getDimensionPixelSize(R.dimen.journey_shape_node)

    private val labelGap = resources.getDimensionPixelSize(R.dimen.journey_shape_label_gap)

    /** How far a stroke stops short of the discs it joins. */
    private val strokeInset = resources.getDimension(R.dimen.space_xs)

    /** The legs shown, in the order one lives them. Assignment redraws. */
    var legs: List<Leg> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The height of one line of label, whichever of the two paints draws it. */
    private val lineHeight: Float
        get() = textPaint.fontMetrics.let { it.descent - it.ascent }

    /** Where the discs begin: one line of label, and its gap, sit above them. */
    private val nodeTop: Int
        get() = paddingTop + (lineHeight + labelGap).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A line above the discs and one below, each held off by the same gap.
        val height = nodeTop + nodeSize + labelGap + lineHeight.toInt() + paddingBottom
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

        val top = nodeTop
        val centreY = (top + nodeSize / 2).toFloat()
        val ascent = textPaint.fontMetrics.ascent
        // The line above hangs from the top edge, so its descent clears the
        // discs by the gap; the one below sits a gap under them.
        val distanceBaseline = paddingTop - ascent
        val timeBaseline = top + nodeSize + labelGap - ascent
        legs.forEachIndexed { index, leg ->
            // Read off the two discs the leg joins rather than from its own
            // rank: in a right-to-left layout the second of them is the one on
            // the left.
            val before = nodeLeft(index)
            val after = nodeLeft(index + 1)
            val start = minOf(before, after) + nodeSize + strokeInset
            val end = maxOf(before, after) - strokeInset
            val middle = (start + end) / 2f
            canvas.drawLine(start, centreY, end, centreY, if (leg.isRide) ridePaint else walkPaint)
            canvas.drawText(leg.distance, middle, distanceBaseline, textPaint)
            canvas.drawText(leg.duration, middle, timeBaseline, durationPaint)
        }

        // A journey of one ride is one made on the rider's own bike: no station
        // is reached, so no station disc is drawn, and the two ends carry the
        // bike rather than the walking figure — the drawing of the search
        // screen for that same journey (SPEC §7.3).
        val ends = if (legs.size == 1 && legs.first().isRide) {
            ownBikeEndpointMarker
        } else {
            endpointMarker
        }

        // The discs last: a stroke that overshot its inset would be covered
        // rather than crossing the disc it points at.
        repeat(nodes) { index ->
            // The ends are where the user stands, the ones between are
            // stations: the same distinction the illustration draws.
            val marker = if (index == 0 || index == nodes - 1) ends else stationMarker
            val left = nodeLeft(index).toInt()
            marker?.setBounds(left, top, left + nodeSize, top + nodeSize)
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
