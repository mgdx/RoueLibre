package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a scroll of the station list does to its floating controls (SPEC §7.6).
 *
 * They go while the list is read downwards, come back on the first move
 * upwards or at the top — and stay away while the keyboard is up, where every
 * keystroke commits another list and reports a scroll of nothing at its top.
 */
class ControlsMoveOnScrollTest {

    private fun move(
        dy: Int,
        atTheTop: Boolean = false,
        keyboardUp: Boolean = false,
        readerExploresByTouch: Boolean = false,
    ) = controlsMoveOnScroll(dy, atTheTop, keyboardUp, readerExploresByTouch)

    @Test
    fun `reading downwards puts them away`() {
        assertEquals(ControlsMove.AWAY, move(dy = 40))
    }

    @Test
    fun `the first move upwards brings them back`() {
        assertEquals(ControlsMove.BACK, move(dy = -10))
    }

    @Test
    fun `the top of the list brings them back`() {
        assertEquals(ControlsMove.BACK, move(dy = 0, atTheTop = true))
    }

    @Test
    fun `a search committed under the keyboard leaves them away`() {
        assertEquals(ControlsMove.STAY, move(dy = 0, atTheTop = true, keyboardUp = true))
    }

    @Test
    fun `scrolling up under the keyboard leaves them away`() {
        assertEquals(ControlsMove.STAY, move(dy = -10, keyboardUp = true))
    }

    @Test
    fun `a screen reader exploring by touch always keeps them`() {
        assertEquals(
            ControlsMove.BACK,
            move(dy = 40, keyboardUp = true, readerExploresByTouch = true),
        )
    }
}
