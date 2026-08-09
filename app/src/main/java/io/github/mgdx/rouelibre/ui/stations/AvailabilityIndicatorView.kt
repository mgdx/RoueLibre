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
 * L'indicateur de disponibilité d'une station — l'élément signature (SPEC §7).
 *
 * Trois informations dans un seul glyphe :
 *
 *  · **le chiffre** dit combien de vélos, ou de places ;
 *  · **la densité du disque** donne le niveau sans qu'on ait à lire ;
 *  · **l'arc** montre quelle part de la station est occupée.
 *
 * La couleur ne porte jamais l'information seule : le chiffre est toujours
 * présent, et chaque état a sa propre forme — anneau plein, anneau ouvert,
 * anneau tireté barré, anneau pointillé. Un daltonien y lit la même chose que
 * tout le monde, ce qu'exige le SPEC §7.1.
 *
 * L'échelle de couleur est une rampe d'une seule teinte, et non un rouge vers
 * vert : ce couple est le pire choix possible pour la forme la plus répandue
 * de daltonisme. Ici, plus il y a de vélos, plus il y a d'encre.
 *
 * Dessiné à la main plutôt qu'assemblé de vues : le glyphe apparaît une fois
 * par ligne de liste et une fois par marqueur de carte, où il s'en affiche
 * plusieurs centaines.
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
        // Chasse fixe : sans elle, la colonne d'indicateurs tressaute d'un
        // rafraîchissement à l'autre selon les chiffres affichés.
        fontFeatureSettings = "tnum"
    }

    private val ringBounds = RectF()

    // Préalloués : `onDraw` est appelé à chaque défilement de la liste, et sur
    // la carte pour chacun des marqueurs visibles. Y créer un objet est le
    // moyen le plus sûr de faire saccader le défilement.
    private val outOfServiceDashes = DashPathEffect(OUT_OF_SERVICE_DASHES, 0f)
    private val unknownDashes = DashPathEffect(UNKNOWN_DASHES, 0f)

    /** Épaisseur de l'anneau et de l'arc. */
    private val ringWidth = resources.getDimension(R.dimen.indicator_ring)

    /** Ce que l'indicateur montre. Le redessin est déclenché à l'affectation. */
    var display: AvailabilityDisplay = UNKNOWN
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Toujours carré, pour que le disque reste un cercle quel que soit le
        // conteneur.
        val preferred = resources.getDimensionPixelSize(R.dimen.indicator_size)
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
            // Départ à midi, sens horaire : la lecture d'un cadran.
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
        textPaint.textSize = resources.getDimension(R.dimen.text_indicator)
        // Centrage optique : `descent` et `ascent` encadrent la hauteur réelle
        // du texte, dont on ramène le milieu sur le centre du disque.
        val metrics = textPaint.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, centreX, baseline, textPaint)
    }

    private fun paletteFor(display: AvailabilityDisplay): IndicatorPalette {
        if (display.isOutOfService) {
            return IndicatorPalette(
                fillColour = null,
                ringColour = colour(R.color.encre_douce),
                inkColour = colour(R.color.encre_douce),
                ringDashes = outOfServiceDashes,
            )
        }
        return when (display.level) {
            null -> IndicatorPalette(
                fillColour = null,
                ringColour = colour(R.color.encre_douce),
                inkColour = colour(R.color.encre_douce),
                ringDashes = unknownDashes,
            )

            AvailabilityLevel.None -> IndicatorPalette(
                // Anneau ouvert, sans remplissage : l'absence se voit à ce que
                // le disque est vide, pas seulement à ce que le chiffre est 0.
                fillColour = null,
                ringColour = colour(R.color.alerte),
                inkColour = colour(R.color.alerte),
                ringDashes = null,
            )

            AvailabilityLevel.Low -> IndicatorPalette(
                fillColour = colour(R.color.dispo_faible),
                ringColour = colour(R.color.dispo_faible),
                inkColour = colour(R.color.dispo_faible_encre),
                ringDashes = null,
            )

            AvailabilityLevel.Medium -> IndicatorPalette(
                fillColour = colour(R.color.dispo_moyenne),
                ringColour = colour(R.color.dispo_moyenne),
                inkColour = colour(R.color.dispo_moyenne_encre),
                ringDashes = null,
            )

            AvailabilityLevel.Good -> IndicatorPalette(
                fillColour = colour(R.color.dispo_bonne),
                ringColour = colour(R.color.dispo_bonne),
                inkColour = colour(R.color.dispo_bonne_encre),
                ringDashes = null,
            )
        }
    }

    private fun colour(resource: Int) = ContextCompat.getColor(context, resource)

    /** Les quatre couleurs d'un état, et le pointillé éventuel de l'anneau. */
    private data class IndicatorPalette(
        val fillColour: Int?,
        val ringColour: Int,
        val inkColour: Int,
        val ringDashes: DashPathEffect?,
    )

    private companion object {
        /** Départ de l'arc à midi. */
        const val START_ANGLE_DEGREES = -90f
        const val FULL_TURN = 360f

        /** Longueur de la barre oblique, en fraction du rayon. */
        const val SLASH_REACH = 0.6f

        const val UNKNOWN_LABEL = "?"

        /** Tirets longs : la station est connue mais ne rend pas le service. */
        val OUT_OF_SERVICE_DASHES = floatArrayOf(14f, 10f)

        /** Pointillés serrés : on ne sait rien de cette station. */
        val UNKNOWN_DASHES = floatArrayOf(3f, 9f)

        val UNKNOWN = AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = false,
            filledFraction = null,
        )
    }
}
