package io.github.mgdx.rouelibre.ui.map

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a map is held: turned from north, and tilted from flat (SPEC §7.1).
 *
 * The two travel together because the compass gives back both at once, and
 * because the trap that lies under reading either of them from the map is the
 * same one — see [attitudeAfterOrderingNorthAndFlat].
 *
 * @property bearingDegrees clockwise from north.
 * @property pitchDegrees from flat.
 */
internal data class MapAttitude(val bearingDegrees: Double, val pitchDegrees: Double)

/** The attitude a map opens at, and the one a press on the compass orders. */
internal val NORTH_AND_FLAT = MapAttitude(NORTH_BEARING, FLAT_PITCH)

/**
 * What the compass button shows for a given attitude (SPEC §7.1, §7.4).
 *
 * @property isTurned whether the map is off north by more than the tolerance.
 * @property isTilted whether it is off flat by more than the tolerance.
 * @property iconRotationDegrees how far the needle turns so that its north
 *   points at the map's north. The map is turned clockwise by the bearing, so
 *   the needle turns back by as much. The tilt does not enter it: the button
 *   is drawn flat on the screen, whatever the map under it is doing.
 * @property degreesFromNorth the bearing rounded to the whole degree, as the
 *   screen reader is told it. The tilt has no such figure, and is not given
 *   one: a degree of tilt compares to nothing a reader could picture, where a
 *   degree of turn compares to the compass rose everybody carries.
 */
internal data class CompassNeedle(
    val isTurned: Boolean,
    val isTilted: Boolean,
    val iconRotationDegrees: Float,
    val degreesFromNorth: Int,
) {
    /**
     * Whether the button belongs on the screen at all.
     *
     * A control whose only office is to undo something appears when there is
     * something to undo, and the map screen is asked to stay calm. **Either
     * departure from the map's opening attitude is something to undo**, and
     * one button gives back both: a tilted map with nothing on screen to
     * straighten it is exactly the state that argument condemns, and a second
     * intermittent button would be one too many on a screen SPEC §7 wants
     * quiet.
     */
    val isShown: Boolean get() = isTurned || isTilted
}

/**
 * Reads a map's attitude into the state of the compass button.
 *
 * Held here rather than in the fragment so that the rule can be read, and
 * tested, without an Android runtime (SPEC §14). Both maps that can be turned
 * — the main screen and the journey result — read it, so neither can drift
 * away from the other.
 *
 * @param attitude how the map is held. MapLibre reports a bearing within
 *   `[0, 360[`, but a value from anywhere else is folded into that range
 *   rather than trusted: a bearing of 359.7° is three tenths of a degree away
 *   from north, not three hundred and fifty-nine.
 */
internal fun compassNeedle(attitude: MapAttitude): CompassNeedle {
    val bearing = attitude.bearingDegrees.mod(FULL_TURN)
    // The shorter way round the compass: north is as near from 359° as it is
    // from 1°, and a needle that shivers either side of it must not appear on
    // one side and not on the other.
    val offNorth = min(bearing, FULL_TURN - bearing)
    return CompassNeedle(
        isTurned = offNorth >= TOLERANCE_DEGREES,
        isTilted = abs(attitude.pitchDegrees - FLAT_PITCH) >= TOLERANCE_DEGREES,
        iconRotationDegrees = -bearing.toFloat(),
        degreesFromNorth = bearing.roundToInt().mod(FULL_TURN.toInt()),
    )
}

/**
 * The attitude the screen knows the map to have, once it has ordered it back
 * to north and flat.
 *
 * **The screen does not read the map back to learn what it has just asked it
 * for.** `MapLibreMap.cameraPosition` is a value cached in the library's
 * `Transform`, refreshed only by a gesture and at the end of a move the
 * library itself is animating — the frames of that move refresh nothing. A
 * press on the compass therefore left the button on screen: everything read
 * from the map at that moment still described the map as it stood before the
 * press, and only the next touch, which goes through the gesture detector,
 * brought the reading up to date and took the button away. The button lingered
 * over a map already facing north, which is the one thing it means the
 * opposite of. **The trap holds for the tilt exactly as it did for the
 * bearing**, which is why the two are given back through one value rather than
 * two readings.
 *
 * The screen has no need of that reading. It ordered north and flat, so north
 * and flat is what it knows — the exact values the animation lands on, not
 * ones near them. The single case where the order does not hold is the move
 * cut short by a finger back on the map, and the library reports that one by
 * itself, through `onCancel`: there, and there alone, the map is the only one
 * who knows where it stopped, and it is asked.
 *
 * @param theOrderStands false only for a move that was cut short.
 * @param mapAttitude what the map reports, believed only when the order was
 *   not carried through.
 */
internal fun attitudeAfterOrderingNorthAndFlat(
    theOrderStands: Boolean,
    mapAttitude: MapAttitude,
): MapAttitude = if (theOrderStands) NORTH_AND_FLAT else mapAttitude

/** The bearing of a map facing north, and the one the compass orders. */
internal const val NORTH_BEARING = 0.0

/**
 * How long the map takes to come back to north and flat.
 *
 * The same on both screens that can be turned, this being the same control:
 * six hundred milliseconds, the length every other camera move of the map
 * screen already takes. Long enough that the eye follows the map round and
 * keeps its place, short enough not to be waited on.
 */
internal const val FACE_NORTH_ANIMATION_MILLIS = 600

private const val FULL_TURN = 360.0

/**
 * How far off north, and how far off flat, the map may be and still count as
 * held the way it opened.
 *
 * Two degrees, and the figure is a threshold of legibility rather than of
 * precision. Neither a two-finger twist nor a two-finger shove can be held to
 * less: the pair of fingers settles a degree or so either way while the hand
 * rests on the glass, and a button that came and went with that shiver would
 * be the noisiest thing on a screen SPEC §7 asks to keep calm.
 *
 * **One figure for both, because both move the picture by about as much.** Two
 * degrees of turn tip the far edge of a thousand-pixel screen by some eighteen
 * pixels, less than the width of a station marker. Two degrees of tilt push
 * the top of the screen six thousandths of a screen height further out — a
 * dozen pixels on a phone, and `MapPitchTest` holds it there. Below either the
 * map does not read as moved; above either it does, and the button is there to
 * put it back, where "back" is exact, the animation landing on nought and not
 * near it.
 */
private const val TOLERANCE_DEGREES = 2.0
