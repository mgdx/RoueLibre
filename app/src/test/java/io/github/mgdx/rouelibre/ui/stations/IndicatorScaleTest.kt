package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The availability indicator against the system's own font size (SPEC §7).
 *
 * Android already offers a text size, and an application that ignores it has no
 * business offering one of its own. The figure inside the indicator's disc has
 * always followed it — it is painted at `text_indicator`, in `sp` — but the disc
 * that holds it was declared in `dp`, and `AvailabilityIndicatorView` paints
 * onto a canvas clipped to its own bounds: at a raised font scale the count was
 * cut off inside its ring, worst at a station of more than a hundred docks.
 *
 * The tokens are read from the resource file itself rather than from a copy,
 * as `MapGlyphsTest` reads the glyphs the build ships: what is checked is what
 * the application will actually be built with. No Android runtime is involved,
 * which is what keeps this on the JVM (SPEC §14).
 */
class IndicatorScaleTest {

    @Test
    fun `the indicator's disc and figure both follow the system font size`() {
        val declared = dimensions()
        SCALED_WITH_THE_TEXT.forEach { name ->
            val value = checkNotNull(declared[name]) { "$name is not declared" }
            assertTrue(
                "$name is $value: a box holding a figure must be in sp, not in dp",
                value.endsWith("sp"),
            )
        }
    }

    @Test
    fun `the disc keeps room for a three-digit count`() {
        val declared = dimensions()
        val disc = sizeOf(declared.getValue("indicator_size"))
        val figure = sizeOf(declared.getValue("text_indicator"))
        // Bricolage's figures run to some 0.6 em wide, so three of them and the
        // ring on either side have to fit inside the disc. Held as a ratio and
        // not as two absolute sizes, because it is the ratio that has to
        // survive both of them being changed.
        assertTrue(
            "a disc of $disc for a figure of $figure leaves nothing for three digits",
            disc / figure >= MINIMUM_DISC_TO_FIGURE,
        )
    }

    @Test
    fun `every text token is in sp`() {
        // The rule the bug above broke, applied to the whole scale: not one
        // size of text in this application is written in dp.
        val inDp = dimensions()
            .filterKeys { it.startsWith("text_") }
            .filterValues { !it.endsWith("sp") }
        assertEquals(emptyMap<String, String>(), inDp)
    }

    /** The figure a token is written with, whichever unit it carries. */
    private fun sizeOf(declaration: String): Float =
        checkNotNull(SIZE.find(declaration)) { "$declaration is not a size" }
            .value
            .toFloat()

    private fun dimensions(): Map<String, String> {
        val file = listOf(
            File("src/main/res/values/dimens.xml"),
            File("app/src/main/res/values/dimens.xml"),
        ).firstOrNull(File::isFile)
        checkNotNull(file) { "dimens.xml not found from ${File(".").absolutePath}" }
        return DECLARATION.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private companion object {
        /** The tokens that describe a box drawn around a figure. */
        val SCALED_WITH_THE_TEXT = listOf("indicator_size", "text_indicator")

        /**
         * How much wider than its figure the disc must stay. Three digits at
         * 0.6 em, plus the ring at either side: the 52 / 24 the interface was
         * drawn with sits just above it, and dropping under it would truncate
         * the very stations the figure is read for.
         */
        const val MINIMUM_DISC_TO_FIGURE = 2.1f

        val DECLARATION = Regex("""<dimen name="([^"]+)">([^<]+)</dimen>""")

        /** The figure at the head of a token, before whatever unit follows. */
        val SIZE = Regex("""[0-9]+(\.[0-9]+)?""")
    }
}
