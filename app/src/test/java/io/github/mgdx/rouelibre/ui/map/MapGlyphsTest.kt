package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests of the glyphs the base map draws its labels with (SPEC §4.2).
 *
 * They are read from the directory `tools/build_glyphs.js` writes into the
 * APK's assets, and not from an example: what is verified is that the fonts
 * shipped answer for every character a place name may hold.
 *
 * This is not a matter of typography. A range MapLibre asks for and does not
 * receive fails the tile that needed it, and the tile is then not drawn at
 * all — no street, no river, no park, over a tile set holding all three.
 * Hunedoara came up empty for one Romanian letter.
 */
class MapGlyphsTest {

    @Test
    fun `the label fonts answer for every character of the plane`() {
        LABEL_STACKS.forEach { stack ->
            val directory = File(glyphsDirectory(), stack)
            assertTrue("no glyphs for $stack", directory.isDirectory)
            val missing = (0 until BMP_RANGE_COUNT)
                .map { "${it * 256}-${it * 256 + 255}.pbf" }
                .filterNot { File(directory, it).isFile }
            assertEquals("ranges missing for $stack", emptyList<String>(), missing)
        }
    }

    @Test
    fun `the digit font carries the range its figures are in`() {
        // The markers and the cluster counts are written with it, and figures
        // are all in the first range: the rest of a display face would weigh
        // fifty kilobytes for characters no label ever asks for.
        val directory = File(glyphsDirectory(), DIGIT_STACK)
        assertTrue("no glyphs for $DIGIT_STACK", directory.isDirectory)
        assertTrue("digits missing", File(directory, "0-255.pbf").isFile)
    }

    private fun glyphsDirectory(): File {
        val path = checkNotNull(System.getProperty("rouelibre.glyphs")) {
            "glyphs directory not supplied by the build"
        }
        return File(path)
    }

    private companion object {
        /** The stacks that write what the data says, and must hold anything. */
        val LABEL_STACKS = listOf(
            "Atkinson Hyperlegible Regular",
            "Atkinson Hyperlegible Bold",
        )

        /** The stack that writes figures, and nothing else. */
        const val DIGIT_STACK = "Bricolage Grotesque Bold"

        /** How many ranges of 256 the Basic Multilingual Plane holds. */
        const val BMP_RANGE_COUNT = 256
    }
}
