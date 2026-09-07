package io.github.mgdx.rouelibre.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * How far a tilted map sees, and how far either map may be tilted (SPEC §7.1).
 *
 * Pure geometry, so that the ceiling can be derived and checked without an
 * Android runtime (SPEC §14). The camera it describes is MapLibre's own: it
 * stands at one and a half screen heights from the point it looks at, which is
 * what gives it the field of view the library publishes as
 * `MapLibreConstants.DEFAULT_FOV`.
 */

/** The pitch a map opens at, and the one the compass gives it back. */
internal const val FLAT_PITCH = 0.0

/**
 * The steepest tilt either map allows, and the figure is derived rather than
 * chosen (SPEC §14).
 *
 * **What bounds a tilt here is not the drawing but the edge of the served
 * area.** SPEC §7.1 forbids that edge ever coming into view, and
 * [ServedAreaCamera] keeps it off the screen by holding the camera at least
 * one reach away from every side of the city's box. A tilt looks towards the
 * horizon, so it lengthens that reach — every degree of it takes room from
 * where the map may be panned, and asks a wider box than the one the city has.
 *
 * **Two things bound it, and the second is the one that binds.**
 *
 * The first is the ground itself. The top of the screen looks half a field of
 * view above the camera's axis — [HALF_FIELD_OF_VIEW_DEGREES], eighteen and a
 * half degrees — and it meets the ground only while that axis is more than as
 * much from the horizontal. Past [PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN],
 * some seventy-one degrees, the top of the screen is sky: there is an edge
 * across the map that no bounding box can hide, and the corners of the screen
 * no longer stand anywhere the projection can name — which would make
 * [ServedAreaCamera]'s own reading of the visible region meaningless. That
 * bound is above MapLibre's maximum of sixty degrees, so it never binds; what
 * it says is that the reach grows without limit as it is approached, and that
 * anything near the library's maximum is out of the question.
 *
 * The second is what the screen then covers of the ground. Flat, it reaches
 * half a screen height ahead of its centre. At thirty degrees that is 0.71, at
 * **forty-five 1.06**, at sixty 2.37 — nearly five times, for a picture only
 * fifteen degrees steeper, and three whole screen heights of ground from the
 * top edge to the bottom one against the flat map's single one.
 *
 * **Forty-five degrees is where that curve is still gentle, and where the
 * arithmetic closes exactly.** The half field of view being `atan(1/3)`,
 * forty-five degrees puts the top of the screen at `atan(2)` from the vertical
 * and the bottom at `atan(1/2)`: the map reaches exactly **twice as far ahead
 * of its centre as behind it**, and covers **1.59 screen heights** of ground
 * against one flat. A city's box carries three kilometres of margin beyond its
 * stations (SPEC §4), so that much is room most conurbations have; where they
 * have not, the zoom floor rises by two thirds of a step — perceptible, and
 * not a map pinned where it stands. Past that the tilt costs more room than a
 * conurbation of ordinary size has to give.
 */
internal const val MAX_PITCH_DEGREES = 45.0

/**
 * Half of MapLibre's field of view, in degrees.
 *
 * The library publishes the whole as `MapLibreConstants.DEFAULT_FOV`,
 * 36.86989764584402°, which is `2·atan(1/3)` — the angle a camera standing at
 * one and a half screen heights subtends over half a screen.
 * `MapPitchTest` holds this half against the library's own figure, so a
 * version that moved the camera would be caught rather than quietly change
 * every reach computed here.
 */
internal const val HALF_FIELD_OF_VIEW_DEGREES = 18.43494882292201

/**
 * How far the camera stands from the point it looks at, in screen heights.
 *
 * One and a half, which is the same statement as the field of view above:
 * `tan(HALF_FIELD_OF_VIEW) = 0.5 / 1.5`. Both are written because both are
 * used, and the test holds them to each other.
 */
internal const val CAMERA_DISTANCE_IN_SCREEN_HEIGHTS = 1.5

/**
 * The tilt past which the top of the screen no longer meets the ground.
 *
 * Not a setting: a consequence of the field of view, and the reason nothing
 * near MapLibre's own maximum could ever be allowed. See [MAX_PITCH_DEGREES].
 */
internal const val PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN = 90.0 - HALF_FIELD_OF_VIEW_DEGREES

/**
 * How far ahead of the point it is centred on the map reaches, in screen
 * heights.
 *
 * The camera orbits the centre at [CAMERA_DISTANCE_IN_SCREEN_HEIGHTS], so a
 * tilt of `p` lifts it to `d·cos p` above the ground and sets it `d·sin p`
 * behind the centre; the top of the screen looks `p + half a field of view`
 * from the vertical. What is left is the difference of the two.
 *
 * @param pitchDegrees the tilt, from flat. Beyond
 *   [PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN] the answer is meaningless — the
 *   top of the screen has no ground under it — and [MAX_PITCH_DEGREES] is what
 *   keeps every caller far from there.
 */
internal fun forwardReachInScreenHeights(pitchDegrees: Double): Double {
    val pitch = inRadians(pitchDegrees)
    val topOfTheScreen = inRadians(pitchDegrees + HALF_FIELD_OF_VIEW_DEGREES)
    return CAMERA_DISTANCE_IN_SCREEN_HEIGHTS * (cos(pitch) * tan(topOfTheScreen) - sin(pitch))
}

/**
 * How far behind that point the map reaches, in screen heights.
 *
 * The same construction as [forwardReachInScreenHeights], read at the bottom
 * of the screen: a tilt brings that edge *in* while it pushes the other out,
 * which is why the two are counted apart and not doubled.
 */
internal fun backwardReachInScreenHeights(pitchDegrees: Double): Double {
    val pitch = inRadians(pitchDegrees)
    val bottomOfTheScreen = inRadians(pitchDegrees - HALF_FIELD_OF_VIEW_DEGREES)
    return CAMERA_DISTANCE_IN_SCREEN_HEIGHTS * (sin(pitch) - cos(pitch) * tan(bottomOfTheScreen))
}

/**
 * The ground the screen covers from its top edge to its bottom one, in screen
 * heights: one flat, and 1.59 at [MAX_PITCH_DEGREES].
 *
 * This is what a tilt asks of the city's box, and what decides the ceiling.
 */
internal fun groundSpanInScreenHeights(pitchDegrees: Double): Double =
    forwardReachInScreenHeights(pitchDegrees) + backwardReachInScreenHeights(pitchDegrees)

private fun inRadians(degrees: Double): Double = degrees * PI / 180.0
