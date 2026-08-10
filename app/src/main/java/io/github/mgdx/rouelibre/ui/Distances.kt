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

private const val METRES_PER_KILOMETRE = 1_000.0

/** Below a kilometre, the distance rounds to the nearest ten metres. */
private const val METRE_ROUNDING = 10
