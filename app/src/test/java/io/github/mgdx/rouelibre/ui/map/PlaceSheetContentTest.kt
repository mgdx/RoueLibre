package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the sheet of a place found on the map says, and what it offers
 * (SPEC §7.2, §7.8).
 *
 * The wording itself takes a `Context` and is left to the device; what is
 * checked here are the two decisions behind it — which heading the place gets,
 * and whether a journey may be composed from it at all.
 */
class PlaceSheetContentTest {

    private val lille = BoundingBox(south = 50.55, west = 2.95, north = 50.72, east = 3.20)
    private val inLille = Coordinates(50.63, 3.06)
    private val paris = Coordinates(48.86, 2.35)

    private fun content(
        label: String,
        position: Coordinates = inLille,
        coveredArea: BoundingBox? = lille,
    ) = placeSheetContent(label, position, coveredArea, whenUnnamed = "This place")

    @Test
    fun `a named place is its own heading`() {
        assertEquals("12 Rue Nationale", content("12 Rue Nationale").title)
    }

    @Test
    fun `a place nobody named is called this place`() {
        // A bare "geo:50.63,3.06" carries no label: the heading must not come
        // out blank, which reads as a sheet that failed to load.
        assertEquals("This place", content("").title)
    }

    @Test
    fun `a heading made of spaces is no heading`() {
        assertEquals("This place", content("   ").title)
    }

    @Test
    fun `a place inside the covered area may be an end of a journey`() {
        assertTrue(content("12 Rue Nationale").offersJourney)
    }

    @Test
    fun `a place beyond the covered area is offered no journey`() {
        // The route runs over a graph cut from the city's box: the computation
        // could only fail, and the button must not promise it (SPEC §7.8).
        assertFalse(content("Paris", position = paris).offersJourney)
    }

    @Test
    fun `an unknown covered area refuses nothing`() {
        // The box is read a beat after the sheet is drawn. Not knowing what was
        // downloaded is no ground to withdraw the journey.
        assertTrue(content("Paris", position = paris, coveredArea = null).offersJourney)
    }
}
