package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.routing.ElevationPoint

/**
 * The ground the ride runs over, drawn (SPEC §7.4.1).
 *
 * A total of metres climbed says how much there is; it never says where. A
 * hundred metres taken in one wall at the end of the ride and the same hundred
 * spread over ten kilometres are two different rides on a heavy share bike, and
 * this is what tells them apart.
 *
 * **The vertical scale is the leg's own**, stretched between its lowest and its
 * highest reading, and the two are written down. A common scale across cities
 * would draw Lille flat and say nothing about the one climb it has; the figures
 * at the ends are what keep an amplified bump from reading as a mountain.
 *
 * Drawn by hand: it is one path, its filled shadow and two labels, which is
 * less arithmetic than a chart library is dependency (SPEC §2, C4).
 */
class ElevationProfileView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0,
) : View(context, attributes, defaultStyle) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.signal)
        strokeWidth = resources.getDimension(R.dimen.journey_shape_ride_stroke)
    }

    /**
     * The ground under the line, in the ride's own colour, laid on thin.
     *
     * It is what makes the drawing read as ground rather than as a curve: the
     * eye follows the top of a filled shape without having to be told it is a
     * height.
     */
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(context, R.color.signal),
            GROUND_ALPHA,
        )
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink_soft)
        textSize = resources.getDimension(R.dimen.text_label)
    }

    private val labelGap = resources.getDimension(R.dimen.space_s)

    private val line = Path()
    private val ground = Path()

    /**
     * The readings drawn, in order. Assignment redraws.
     *
     * Fewer than two, or all at one height, and there is nothing to draw: the
     * screen decides whether the relief is worth a drawing at all (see
     * `isReliefWorthDrawing`), and this view simply keeps quiet when it is
     * handed a flat line.
     */
    var profile: List<ElevationPoint> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * How the lowest and the highest reading are written, set with [profile].
     *
     * The view formats nothing itself: metres are written in the interface's
     * language, through the resources that hold that rule (SPEC §9), and those
     * belong to the screen.
     */
    var lowestLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var highestLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val points = profile
        if (points.size < 2) return
        val length = points.last().distanceMetres
        val lowest = points.minOf { it.elevationMetres }
        val highest = points.maxOf { it.elevationMetres }
        if (length <= 0.0 || highest <= lowest) return

        // Half a line of label at the top and at the bottom: a label centred on
        // the highest reading must not be cut off by the edge above it.
        val halfLine = (textPaint.descent() - textPaint.ascent()) / 2
        val top = paddingTop + halfLine
        val bottom = height - paddingBottom - halfLine
        if (bottom <= top) return

        val gutter = maxOf(
            textPaint.measureText(lowestLabel),
            textPaint.measureText(highestLabel),
        ).let { if (it == 0f) 0f else it + labelGap }
        val span = width - paddingStart - paddingEnd - gutter
        if (span <= 0f) return
        // The drawing tells a sequence — the start of the ride, then its end —
        // so it runs in the direction the text around it is read, and the
        // labels take the side the reading starts from.
        val isRightToLeft = layoutDirection == LAYOUT_DIRECTION_RTL
        val trackStart = if (isRightToLeft) width - paddingEnd - gutter else paddingStart + gutter
        val direction = if (isRightToLeft) -1f else 1f

        fun xOf(point: ElevationPoint) =
            trackStart + direction * (point.distanceMetres / length).toFloat() * span

        fun yOf(point: ElevationPoint) = bottom -
            ((point.elevationMetres - lowest) / (highest - lowest)).toFloat() * (bottom - top)

        line.rewind()
        points.forEachIndexed { index, point ->
            val x = xOf(point)
            val y = yOf(point)
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // The ground is the line closed onto the bottom edge: the same path,
        // brought down and back, so the fill can never disagree with the stroke.
        ground.set(line)
        ground.lineTo(xOf(points.last()), bottom)
        ground.lineTo(xOf(points.first()), bottom)
        ground.close()

        canvas.drawPath(ground, groundPaint)
        canvas.drawPath(line, linePaint)
        drawLabels(canvas, top, bottom, isRightToLeft)
    }

    /**
     * The two heights the drawing is stretched between, written at its ends.
     *
     * They sit against the edge the reading starts from, level with the reading
     * each one names: the top label is where the highest point is, and the
     * curve rising to touch it says as much without a gridline.
     */
    private fun drawLabels(canvas: Canvas, top: Float, bottom: Float, isRightToLeft: Boolean) {
        textPaint.textAlign = if (isRightToLeft) Paint.Align.RIGHT else Paint.Align.LEFT
        val x = if (isRightToLeft) (width - paddingEnd).toFloat() else paddingStart.toFloat()
        val middleOfLine = -(textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(highestLabel, x, top + middleOfLine, textPaint)
        canvas.drawText(lowestLabel, x, bottom + middleOfLine, textPaint)
    }

    private companion object {
        /** Enough for the ground to read as a mass, little enough to stay scenery. */
        const val GROUND_ALPHA = 46
    }
}
