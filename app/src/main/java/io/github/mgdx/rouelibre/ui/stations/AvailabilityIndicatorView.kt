package io.github.mgdx.rouelibre.ui.stations

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.AvailabilityDisplay
import io.github.mgdx.rouelibre.core.station.AvailabilityLevel
import io.github.mgdx.rouelibre.ui.textLocale
import java.text.NumberFormat
import kotlin.math.roundToInt

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

    /**
     * The digits the figure is painted in (SPEC §9).
     *
     * Written with `toString()`, the count came out in Latin digits beside a
     * label whose own figures Android had already put into those of the locale
     * served — "٢٠ docks" under a disc reading "20". A disc and the words
     * beside it are one phrase, and a phrase does not count in two systems.
     *
     * Held rather than made in `onDraw`, like everything else here: the glyph
     * is painted for every visible marker on the map. The language cannot
     * change under it, AppCompat rebuilding the screens — and with them these
     * views — when it is chosen.
     */
    private val figures = NumberFormat.getIntegerInstance(context.textLocale())

    private val ringBounds = RectF()

    // Preallocated: `onDraw` is called on every scroll of the list, and on the
    // map for each visible marker. Creating an object in it is the surest way
    // to make the scrolling stutter.
    private val outOfServiceDashes = DashPathEffect(OUT_OF_SERVICE_DASHES, 0f)
    private val unknownDashes = DashPathEffect(UNKNOWN_DASHES, 0f)

    /** The thickness of the ring and of the arc. */
    private val ringWidth = resources.getDimension(R.dimen.indicator_ring)

    /**
     * How much taller the figure is than the text it sits beside, and how much
     * wider the disc is than the figure — both taken from what the tokens
     * declare rather than from what they resolve to.
     *
     * The declared numbers are read straight out of the resource table, before
     * any font scale is applied to them: 24 over 16, and 52 over 24. So the two
     * design tokens stay the one place the sizes are decided, as SPEC §7 asks,
     * and nothing here is a figure somebody typed twice.
     */
    private val figureToBodyText = declaredSize(R.dimen.text_indicator) /
        declaredSize(R.dimen.text_body)
    private val discToFigure = declaredSize(R.dimen.indicator_size) /
        declaredSize(R.dimen.text_indicator)

    /** What the indicator shows. Assignment triggers the redraw. */
    var display: AvailabilityDisplay = UNKNOWN
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * The size the figure is painted at: a fixed multiple of the body text, at
     * whatever size the system is giving body text now.
     *
     * **It is not read from `text_indicator` directly, and that is the repair.**
     * Android 14 and later scale `sp` along a curve rather than by the factor
     * asked for — the larger the declared size, the less of the factor it gets —
     * and measured on a Fairphone 3 at ×2.0 the three tokens involved come out
     * at ×1.75 for the 16sp of body text, ×1.50 for this 24sp figure and ×1.11
     * for the 52sp disc. The three had been read at three points of one curve,
     * so they no longer stood in the proportion they were drawn in: the disc
     * grew a tenth while the name beside it grew three quarters, and the ratio
     * holding three digits inside the ring fell from 2.17 to 1.60 — under the
     * 2.1 `IndicatorScaleTest` requires, on the device, while the declared
     * numbers still satisfied it on paper.
     *
     * The curve is not re-implemented here and not fought: it is **read once**,
     * at the size of the text the indicator sits beside, and everything else is
     * held in proportion to that. Android still decides how big text is at ×2.0;
     * what this settles is that the count is the same size relative to the
     * station's name at every setting, which is what the reader who turned the
     * size up was asking for.
     */
    private val figureSize: Float
        get() = resources.getDimension(R.dimen.text_body) * figureToBodyText

    /**
     * The side of the square the disc is drawn in, from the figure it holds.
     *
     * Rounded and not truncated, which is what `getDimensionPixelSize` does to
     * the tokens this replaces: at the default text size the disc comes to
     * 136.5 pixels on a Fairphone 3, and truncating would have shaved it to 136
     * where it has always been 137. A screen nobody asked to change may not
     * change by a pixel either.
     */
    private val discSize: Int
        get() = (figureSize * discToFigure).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square, so the disc stays a circle whatever the container.
        val width = resolveSize(discSize, widthMeasureSpec)
        val height = resolveSize(discSize, heightMeasureSpec)
        val side = minOf(width, height)
        setMeasuredDimension(side, side)
    }

    /**
     * The number a dimension token is written with, in the unit it is written
     * in, untouched by any density or font scale.
     *
     * `Resources.getDimension` would hand back pixels with the font curve
     * already folded in, which is precisely what must not enter a ratio: a ratio
     * of two scaled values is a ratio that changes with the setting.
     */
    private fun declaredSize(token: Int): Float {
        val value = TypedValue()
        resources.getValue(token, value, true)
        return TypedValue.complexToFloat(value.data)
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

        val label = display.count?.let(figures::format) ?: UNKNOWN_LABEL
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
