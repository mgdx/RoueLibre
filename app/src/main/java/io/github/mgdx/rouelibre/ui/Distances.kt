package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Puts a distance into words, in the interface's language.
 *
 * Formatting goes through the localisation APIs, never through a hand-built
 * string (SPEC §9): the French decimal comma and the English point are not
 * written the same way.
 *
 * Values are rounded to what the display can honestly promise: an address's
 * position is known to within a few metres, the user's to far less. Writing
 * "437 m" would suggest a precision that does not exist.
 *
 * @param metres the distance to write.
 * @return a distance ready to show, "250 m" or "1,4 km" for instance.
 */
fun Context.formatDistance(metres: Double): String {
    if (metres < METRES_PER_KILOMETRE) {
        val rounded = (metres / METRE_ROUNDING).roundToInt() * METRE_ROUNDING
        return getString(R.string.distance_metres, rounded)
    }
    val format = NumberFormat.getNumberInstance(textLocale()).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }
    return getString(
        R.string.distance_kilometres,
        format.format(metres / METRES_PER_KILOMETRE),
    )
}

/**
 * Puts a climb into words, or says there is none worth naming.
 *
 * A climb is read in metres however big it gets — a hill is counted in metres
 * by everyone who rides up one — so this is not [formatDistance] applied to a
 * vertical figure: no kilometre ever appears here.
 *
 * Two things silence it, and they are the same thing said twice: the elevation
 * of the routing graph comes from SRTM samples some thirty metres apart, whose
 * vertical error runs to several metres.
 *
 * - **A stretch too short to be described by them says nothing.** Forty metres
 *   of pavement announced five metres of climb — a twelve per cent grade on a
 *   street that has none — because two samples and the error between them were
 *   the whole of what the engine had to go on. Under [CLIMB_MEASURABLE_OVER]
 *   the figure is not the ground, it is the sampling.
 * - **Under five metres nothing is named either**, and the figure is written to
 *   five above it, which is as fine as those samples can honestly promise.
 *
 * What is *not* used here is a floor high enough to hide real relief: a ride
 * across flat country that gains seven metres has gained them, and the ten
 * metres tried first — the dip the engine's own filter forgives — silenced the
 * bike leg of half the journeys in a flat conurbation while the total, summing
 * three legs, still named one.
 *
 * @param metres the climb the routing engine measured.
 * @param overMetres the ground it was gained over: one leg's length, or the
 *   whole journey's.
 * @return the climb ready to show, "45 m" for instance, or null when the ground
 *   is flat enough, or short enough, that saying anything would be saying too
 *   much.
 */
fun Context.formatClimb(metres: Int, overMetres: Int): String? {
    if (overMetres < CLIMB_MEASURABLE_OVER) return null
    if (metres < CLIMB_ROUNDING) return null
    val rounded = (metres.toDouble() / CLIMB_ROUNDING).roundToInt() * CLIMB_ROUNDING
    return getString(R.string.distance_metres, rounded)
}

/**
 * Whether a leg's relief has a shape worth drawing (SPEC §7.4.1).
 *
 * The same two thresholds that silence [formatClimb], for the same reason: what
 * the graph holds under three hundred metres of ground, or inside five metres
 * of height, is the sampling of SRTM and its error rather than the ground. A
 * profile drawn from it would stretch that error across the width of the
 * screen, and read as a hill.
 *
 * @param overMetres the length of the leg.
 * @param rangeMetres the height between its lowest and its highest reading —
 *   not its climb, which can add up over ups and downs that a drawing would
 *   have to amplify to show at all.
 */
fun isReliefWorthDrawing(overMetres: Int, rangeMetres: Double): Boolean =
    overMetres >= CLIMB_MEASURABLE_OVER && rangeMetres >= CLIMB_ROUNDING

/**
 * Writes a height above sea level.
 *
 * Rounded to five metres, as a climb is and for the same reason: the readings
 * come from samples whose vertical error runs to several metres, and a figure
 * written to the metre would promise what they cannot.
 */
fun Context.formatAltitude(metres: Double): String {
    val rounded = (metres / CLIMB_ROUNDING).roundToInt() * CLIMB_ROUNDING
    return getString(R.string.distance_metres, rounded)
}

private const val METRES_PER_KILOMETRE = 1_000.0

/** Below a kilometre, the distance rounds to the nearest ten metres. */
private const val METRE_ROUNDING = 10

/** A climb is written to five metres, and under one step it is not written. */
private const val CLIMB_ROUNDING = 5

/**
 * The ground a climb needs to be gained over before it is worth naming.
 *
 * Three hundred metres is some ten SRTM samples: enough for the stretch to have
 * a shape of its own rather than to be one reading and its error. Shorter than
 * that, the engine's elevation filter has not had the length to work either —
 * it forgives dips as it goes, but the first rise of a leg always counts, and on
 * a leg of forty metres that first rise is the whole figure.
 */
private const val CLIMB_MEASURABLE_OVER = 300
