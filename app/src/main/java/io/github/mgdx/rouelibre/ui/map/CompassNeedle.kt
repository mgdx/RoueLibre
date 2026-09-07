package io.github.mgdx.rouelibre.ui.map

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the compass button shows for a given map bearing (SPEC §7.1, §7.4).
 *
 * @property isShown whether the button belongs on the screen at all. A control
 *   whose only office is to undo something appears when there is something to
 *   undo, and the map screen is asked to stay calm.
 * @property iconRotationDegrees how far the needle turns so that its north
 *   points at the map's north. The map is turned clockwise by the bearing, so
 *   the needle turns back by as much.
 * @property degreesFromNorth the bearing rounded to the whole degree, as the
 *   screen reader is told it.
 */
internal data class CompassNeedle(
    val isShown: Boolean,
    val iconRotationDegrees: Float,
    val degreesFromNorth: Int,
)

/**
 * Reads a map bearing into the state of the compass button.
 *
 * Held here rather than in the fragment so that the rule can be read, and
 * tested, without an Android runtime (SPEC §14). Both maps that can be turned
 * — the main screen and the journey result — read it, so neither can drift
 * away from the other.
 *
 * @param bearingDegrees the camera's bearing, clockwise from north. MapLibre
 *   reports it within `[0, 360[`, but a value from anywhere else is folded
 *   into that range rather than trusted: a bearing of 359.7° is three tenths
 *   of a degree away from north, not three hundred and fifty-nine.
 */
internal fun compassNeedle(bearingDegrees: Double): CompassNeedle {
    val bearing = bearingDegrees.mod(FULL_TURN)
    // The shorter way round the compass: north is as near from 359° as it is
    // from 1°, and a needle that shivers either side of it must not appear on
    // one side and not on the other.
    val offNorth = min(bearing, FULL_TURN - bearing)
    return CompassNeedle(
        isShown = offNorth >= NORTH_TOLERANCE_DEGREES,
        iconRotationDegrees = -bearing.toFloat(),
        degreesFromNorth = bearing.roundToInt().mod(FULL_TURN.toInt()),
    )
}

/**
 * How long the map takes to turn back to north.
 *
 * The same on both screens that can be turned, this being the same control:
 * six hundred milliseconds, the length every other camera move of the map
 * screen already takes. Long enough that the eye follows the map round and
 * keeps its place, short enough not to be waited on.
 */
internal const val FACE_NORTH_ANIMATION_MILLIS = 600

private const val FULL_TURN = 360.0

/**
 * How far off north the map may be and still count as facing north.
 *
 * Two degrees, and the figure is a threshold of legibility rather than of
 * precision. A two-finger twist cannot be held to less: the pair of fingers
 * settles a degree or so either way while the hand rests on the glass, and a
 * button that came and went with that shiver would be the noisiest thing on a
 * screen SPEC §7 asks to keep calm. Two degrees is also below what the map
 * shows: on a phone a thousand pixels wide, it tips the far edge of the screen
 * by some eighteen pixels, less than the width of a station marker, so a map
 * inside the tolerance does not read as turned. Above it the map visibly is,
 * and the button is there to put it back — where "back" is exact, the
 * animation landing on nought and not near it.
 */
private const val NORTH_TOLERANCE_DEGREES = 2.0
