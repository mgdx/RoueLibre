package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.constants.MapLibreConstants
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.tan

/**
 * The tilt ceiling, and the geometry it is derived from (SPEC §7.1, §14).
 *
 * The ceiling is a coefficient, so it is derived and not chosen, and what
 * follows is the derivation written as assertions: the camera MapLibre uses,
 * how far a tilted screen then reaches over the ground, and why forty-five
 * degrees is where that stops being affordable to a city's bounding box.
 */
class MapPitchTest {

    @Test
    fun `the field of view is the library's own`() {
        assertEquals(
            "half of what MapLibre publishes, or every reach here is measured on another camera",
            MapLibreConstants.DEFAULT_FOV / 2.0,
            HALF_FIELD_OF_VIEW_DEGREES,
            1e-9,
        )
    }

    @Test
    fun `the field of view and the camera's distance are one statement`() {
        // A camera one and a half screen heights away subtends half a screen
        // at atan(0.5 / 1.5): the two constants must say the same thing.
        assertEquals(
            0.5 / CAMERA_DISTANCE_IN_SCREEN_HEIGHTS,
            tan(HALF_FIELD_OF_VIEW_DEGREES * PI / 180.0),
            1e-12,
        )
    }

    @Test
    fun `a flat map reaches half a screen height each way`() {
        assertEquals(0.5, forwardReachInScreenHeights(FLAT_PITCH), 1e-9)
        assertEquals(0.5, backwardReachInScreenHeights(FLAT_PITCH), 1e-9)
        assertEquals(1.0, groundSpanInScreenHeights(FLAT_PITCH), 1e-9)
    }

    /**
     * The ceiling is where the arithmetic closes: the half field of view being
     * `atan(1/3)`, forty-five degrees puts the top of the screen at `atan(2)`
     * from the vertical and the bottom at `atan(1/2)`.
     */
    @Test
    fun `at the ceiling the screen reaches exactly twice as far ahead as behind`() {
        val forward = forwardReachInScreenHeights(MAX_PITCH_DEGREES)
        val backward = backwardReachInScreenHeights(MAX_PITCH_DEGREES)
        assertEquals(2.0, forward / backward, 1e-9)
        assertEquals(1.0607, forward, 1e-4)
    }

    @Test
    fun `at the ceiling the screen covers 1_59 screen heights of ground`() {
        assertEquals(1.5910, groundSpanInScreenHeights(MAX_PITCH_DEGREES), 1e-4)
    }

    /**
     * What the ceiling buys: past it the reach runs away. Fifteen degrees more
     * than double what the screen reaches ahead of itself, which is the room
     * `ServedAreaCamera` must find inside the city's box before it will let the
     * map be panned at all.
     */
    @Test
    fun `past the ceiling the reach runs away`() {
        val atTheCeiling = forwardReachInScreenHeights(MAX_PITCH_DEGREES)
        val atTheLibrarysMaximum = forwardReachInScreenHeights(MAPLIBRE_MAXIMUM_PITCH)
        assertTrue(
            "fifteen degrees more than double the reach: $atTheCeiling to $atTheLibrarysMaximum",
            atTheLibrarysMaximum > 2.0 * atTheCeiling,
        )
        assertEquals(2.366, atTheLibrarysMaximum, 1e-3)
        assertEquals(
            "three screen heights of ground where a flat map covers one",
            3.0,
            groundSpanInScreenHeights(MAPLIBRE_MAXIMUM_PITCH),
            1e-9,
        )
    }

    @Test
    fun `the ceiling is one the library allows`() {
        assertTrue(MAX_PITCH_DEGREES in FLAT_PITCH..MAPLIBRE_MAXIMUM_PITCH)
    }

    /**
     * The other bound, and the reason nothing near the library's maximum could
     * be allowed: past it the top of the screen is sky. There is then an edge
     * across the map that no bounding box can hide, and the corners of the
     * screen stand nowhere the projection can name — which is what
     * `ServedAreaCamera` measures its limits from.
     */
    @Test
    fun `the ground leaves the screen a field of view short of the horizontal`() {
        assertEquals(71.565, PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN, 1e-3)
        // The top of the screen looks along atan(1 / tan(half a field of view))
        // from the vertical, which is the same angle read the other way round.
        assertEquals(
            atan(1.0 / tan(HALF_FIELD_OF_VIEW_DEGREES * PI / 180.0)) * 180.0 / PI,
            PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN,
            1e-9,
        )
        assertTrue(
            "and the ceiling stands well clear of it",
            PITCH_WHERE_THE_GROUND_LEAVES_THE_SCREEN - MAX_PITCH_DEGREES > 25.0,
        )
    }

    /**
     * The compass's tolerance, read on the ground rather than on the protractor
     * (see `CompassNeedle.kt`): two degrees of tilt must not be a tilt anybody
     * can see.
     */
    @Test
    fun `inside the tolerance the picture does not visibly change`() {
        val moved = forwardReachInScreenHeights(2.0) - forwardReachInScreenHeights(FLAT_PITCH)
        assertTrue("$moved screen heights is a dozen pixels on a phone", moved < 0.01)
    }

    private companion object {
        /** `MapLibreConstants.MAXIMUM_PITCH`, which the ceiling stands under. */
        const val MAPLIBRE_MAXIMUM_PITCH = 60.0
    }
}
