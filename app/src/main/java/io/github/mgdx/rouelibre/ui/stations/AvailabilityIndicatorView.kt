package io.github.mgdx.rouelibre.ui.stations

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.AvailabilityDisplay
import io.github.mgdx.rouelibre.core.station.AvailabilityLevel

/**
 * A station's availability indicator — the signature element (SPEC §7).
 *
 * Three pieces of information in a single glyph:
 *
 *  · **the figure** says how many bikes, or docks;
 *  · **the disc's density** gives the level without one having to read;
 *  · **the arc** shows what share of the station is occupied.
 *
 * Colour never carries the information alone: the figure is always there, and
 * every state has its own shape — filled ring, open ring, dashed ring with a
 * stroke through it, dotted ring. A colour-blind reader takes the same meaning
 * from it as anyone else, which SPEC §7.1 requires.
 *
 * The colour scale is a ramp of a single hue, not a red-to-green: that pair is
 * the worst possible choice for the commonest form of colour blindness. Here,
 * the more bikes there are, the more ink there is.
 *
 * Drawn by hand rather than assembled from views: the glyph appears once per
 * list row and once per map marker, where several hundred of them show at once.
 */
class AvailabilityIndicatorView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = 0,
) : View(context, attributes, defaultStyle) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.bricolage_bold)
            ?: Typeface.DEFAULT_BOLD
        // Fixed pitch: without it the column of indicators jitters from one
        // refresh to the next depending on the figures shown.
        fontFeatureSettings = "tnum"
    }

    private val ringBounds = RectF()

    // Preallocated: `onDraw` is called on every scroll of the list, and on the
    // map for each visible marker. Creating an object in it is the surest way
    // to make the scrolling stutter.
    private val outOfServiceDashes = DashPathEffect(OUT_OF_SERVICE_DASHES, 0f)
    private val unknownDashes = DashPathEffect(UNKNOWN_DASHES, 0f)

    /** The thickness of the ring and of the arc. */
    private val ringWidth = resources.getDimension(R.dimen.indicator_ring)

    /** What the indicator shows. Assignment triggers the redraw. */
    var display: AvailabilityDisplay = UNKNOWN
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Whether the figure is drawn at the larger of the two sizes
     * (SPEC §7, §7.6).
     *
     * Not a substitute for the system's own font size, which this view already
     * follows — both its tokens are in `sp`. It answers a different need: it
     * enlarges the one figure that is read a hundred times a week without
     * enlarging the whole interface, which is precisely what the system setting
     * cannot do.
     *
     * **The map's markers are deliberately left out of it.** They are not drawn
     * by this view, and they must not be: a marker's size decides how many
     * stations stay legible side by side at a given zoom, which is a question of
     * map drawing rather than of accessibility — enlarged, the discs would
     * overlap and the map would say less, not more (SPEC §7.1).
     *
     * The disc grows with the figure, and by the same fraction: the ratio is
     * what keeps three digits inside the ring.
     */
    var largeFigures: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** The size the figure is painted at, at whichever of the two settings. */
    private val figureSize: Float
        get() = resources.getDimension(
            if (largeFigures) R.dimen.text_indicator_large else R.dimen.text_indicator,
        )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square, so the disc stays a circle whatever the container.
        val preferred = resources.getDimensionPixelSize(
            if (largeFigures) R.dimen.indicator_size_large else R.dimen.indicator_size,
        )
        val width = resolveSize(preferred, widthMeasureSpec)
        val height = resolveSize(preferred, heightMeasureSpec)
        val side = minOf(width, height)
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val centreX = width / 2f
        val centreY = height / 2f
        val radius = minOf(width, height) / 2f - ringWidth / 2f
        ringBounds.set(
            centreX - radius,
            centreY - radius,
            centreX + radius,
            centreY + radius,
        )

        val palette = paletteFor(display)

        if (palette.fillColour != null) {
            fillPaint.color = palette.fillColour
            canvas.drawCircle(centreX, centreY, radius, fillPaint)
        }

        ringPaint.color = palette.ringColour
        ringPaint.strokeWidth = ringWidth
        ringPaint.pathEffect = palette.ringDashes
        canvas.drawCircle(centreX, centreY, radius, ringPaint)
        ringPaint.pathEffect = null

        val fraction = display.filledFraction
        if (fraction != null && fraction > 0f && !display.isOutOfService) {
            arcPaint.color = palette.inkColour
            arcPaint.strokeWidth = ringWidth
            // Starting at twelve o'clock, clockwise: read like a dial.
            canvas.drawArc(ringBounds, START_ANGLE_DEGREES, fraction * FULL_TURN, false, arcPaint)
        }

        if (display.isOutOfService) {
            slashPaint.color = palette.inkColour
            slashPaint.strokeWidth = ringWidth
            val reach = radius * SLASH_REACH
            canvas.drawLine(
                centreX - reach,
                centreY + reach,
                centreX + reach,
                centreY - reach,
                slashPaint,
            )
            return
        }

        val label = display.count?.toString() ?: UNKNOWN_LABEL
        textPaint.color = palette.inkColour
        textPaint.textSize = figureSize
        // Optical centring: `descent` and `ascent` bracket the text's real
        // height, whose middle is brought onto the disc's centre.
        val metrics = textPaint.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, centreX, baseline, textPaint)
    }

    private fun paletteFor(display: AvailabilityDisplay): IndicatorPalette {
        if (display.isOutOfService) {
            return IndicatorPalette(
                fillColour = null,
                ringColour = colour(R.color.ink_soft),
                inkColour = colour(R.color.ink_soft),
                ringDashes = outOfServiceDashes,
            )
        }
        return when (display.level) {
            null -> IndicatorPalette(
                fillColour = null,
                ringColour = colour(R.color.ink_soft),
                inkColour = colour(R.color.ink_soft),
                ringDashes = unknownDashes,
            )

            AvailabilityLevel.None -> IndicatorPalette(
                // An open ring, unfilled: the absence shows in the disc being
                // empty, not only in the figure being 0.
                fillColour = null,
                ringColour = colour(R.color.alert),
                inkColour = colour(R.color.alert),
                ringDashes = null,
            )

            AvailabilityLevel.Low -> IndicatorPalette(
                fillColour = colour(R.color.availability_low),
                ringColour = colour(R.color.availability_low),
                inkColour = colour(R.color.availability_low_ink),
                ringDashes = null,
            )

            AvailabilityLevel.Medium -> IndicatorPalette(
                fillColour = colour(R.color.availability_medium),
                ringColour = colour(R.color.availability_medium),
                inkColour = colour(R.color.availability_medium_ink),
                ringDashes = null,
            )

            AvailabilityLevel.Good -> IndicatorPalette(
                fillColour = colour(R.color.availability_good),
                ringColour = colour(R.color.availability_good),
                inkColour = colour(R.color.availability_good_ink),
                ringDashes = null,
            )
        }
    }

    private fun colour(resource: Int) = ContextCompat.getColor(context, resource)

    /** A state's four colours, and the ring's dash pattern if it has one. */
    private data class IndicatorPalette(
        val fillColour: Int?,
        val ringColour: Int,
        val inkColour: Int,
        val ringDashes: DashPathEffect?,
    )

    private companion object {
        /** The arc starts at twelve o'clock. */
        const val START_ANGLE_DEGREES = -90f
        const val FULL_TURN = 360f

        /** The length of the diagonal stroke, as a fraction of the radius. */
        const val SLASH_REACH = 0.6f

        const val UNKNOWN_LABEL = "?"

        /** Long dashes: the station is known but does not provide the service. */
        val OUT_OF_SERVICE_DASHES = floatArrayOf(14f, 10f)

        /** Tight dots: nothing is known about this station. */
        val UNKNOWN_DASHES = floatArrayOf(3f, 9f)

        val UNKNOWN = AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = false,
            filledFraction = null,
        )
    }
}
