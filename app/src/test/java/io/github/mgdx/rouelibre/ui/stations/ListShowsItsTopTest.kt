package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the station list counts as showing its first row (SPEC §7.6).
 *
 * The answer decides whether favourites arriving above the rows are scrolled
 * into sight. A list freshly rebuilt as the screen turns over has nothing laid
 * out and cannot scroll, and was taken for a list at its top: the reader was
 * sent back to the favourites' heading at every rotation.
 */
class ListShowsItsTopTest {

    @Test
    fun `a list laid out at its first row shows its top`() {
        assertTrue(listShowsItsTop(laidOutRows = 12, canScrollUp = false))
    }

    @Test
    fun `a list scrolled down does not`() {
        assertFalse(listShowsItsTop(laidOutRows = 12, canScrollUp = true))
    }

    @Test
    fun `a list with nothing laid out yet has no place to keep`() {
        assertFalse(listShowsItsTop(laidOutRows = 0, canScrollUp = false))
    }
}
