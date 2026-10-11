package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What "locate me" decides, on both map screens (SPEC §7.1, §7.4, §10). */
class LocatingTest {

    @Test
    fun `without the permission it is asked for, and nothing else is looked at`() {
        assertEquals(
            LocateMeStep.AskPermission,
            locateMeStep(permitted = false) { error("availability means nothing yet") },
        )
    }

    @Test
    fun `location switched off is said`() {
        assertEquals(LocateMeStep.SayUnavailable, locateMeStep(permitted = true) { false })
    }

    @Test
    fun `permitted and available, the position is looked for`() {
        assertEquals(LocateMeStep.Locate, locateMeStep(permitted = true) { true })
    }

    @Test
    fun `a position in the city is not outside it`() {
        assertFalse(liesOutside(IN_LILLE, LILLE))
    }

    @Test
    fun `a position in another city is`() {
        assertTrue(liesOutside(IN_PARIS, LILLE))
    }

    /** Nothing pens the camera of such a city, so nothing is passed off as anything. */
    @Test
    fun `a city without a usable box covers everything`() {
        assertFalse(liesOutside(IN_PARIS, null))
        assertFalse(liesOutside(IN_PARIS, BoundingBox(50.6, 3.0, 50.6, 3.0)))
    }

    private companion object {
        val LILLE = BoundingBox(south = 50.5, west = 2.9, north = 50.8, east = 3.3)
        val IN_LILLE = Coordinates(50.63, 3.06)
        val IN_PARIS = Coordinates(48.8566, 2.3522)
    }
}
