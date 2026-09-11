package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The titles are set tight, except where tightening them cuts them short
 * (SPEC §7, §9).
 *
 * `TextAppearance.RoueLibre.Title` draws its letters a hundredth of an em
 * together, which is a decision about Bricolage Grotesque's own letterforms.
 * Bricolage holds no Arabic letter, and Arabic is cursive: asking for letter
 * spacing there changes how the run is shaped, and the width Android measures
 * the title at stops matching the width it lays the title out at. A toolbar
 * sizes its title — single-line, end-ellipsized — to exactly the measured
 * width, so the difference fires the ellipsis: the settings screen read
 * "الإعدادا…" and the journey screen "الرح…" on the FP3, with most of the bar
 * empty beside them.
 *
 * The tightening therefore lives in a token of its own, withdrawn under
 * `values-ldrtl`. Two things have to stay true for that to keep working, and
 * neither can be seen from a device once it is wrong in the other direction:
 * the style must read the token rather than a figure of its own, and the
 * right-to-left folder must still hold the token at zero. Read from the
 * resources, the way `LocalesTest` reads them.
 */
class TitleKerningTest {

    private companion object {
        private const val TOKEN = "title_letter_spacing"
    }

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** The body of the named style, as written in `values<qualifier>/type.xml`. */
    private fun style(qualifier: String, name: String): String {
        val file = File(resources, "values$qualifier/type.xml")
        assertTrue("values$qualifier/type.xml exists", file.exists())
        val declared = file.readText().substringAfter("<style name=\"$name\"", "")
        assertTrue("$name is declared in values$qualifier/type.xml", declared.isNotEmpty())
        return declared.substringBefore("</style>")
    }

    /** The value the named float token holds in `values<qualifier>/type.xml`. */
    private fun token(qualifier: String, name: String): String {
        val file = File(resources, "values$qualifier/type.xml")
        assertTrue("values$qualifier/type.xml exists", file.exists())
        val declared = Regex("<item name=\"$name\"[^>]*>([^<]*)</item>")
            .find(file.readText())
        checkNotNull(declared) { "$name is not declared in values$qualifier/type.xml" }
        return declared.groupValues[1].trim()
    }

    @Test
    fun `the title reads its tightening from a token rather than from a figure`() {
        val title = style("", "TextAppearance.RoueLibre.Title")
        assertTrue(
            "The tightening is a token, so that a folder can withdraw it",
            title.contains("<item name=\"android:letterSpacing\">@dimen/$TOKEN</item>"),
        )
    }

    @Test
    fun `a left-to-right title keeps the tightening it was drawn with`() {
        assertEquals(
            "The typography of every script but the cursive ones is unchanged",
            "-0.01",
            token("", TOKEN),
        )
    }

    @Test
    fun `a right-to-left title is not tightened at all`() {
        assertEquals(
            "Letter spacing on a cursive script is what cut the Arabic titles short",
            0.0,
            token("-ldrtl", TOKEN).toDouble(),
            0.0,
        )
    }

    @Test
    fun `no title style smuggles a tightening of its own back in`() {
        val styles = listOf(
            "TextAppearance.RoueLibre.Title",
            // Inherits the title's face one size up, so it inherits the token
            // with it — and is the application's name on the opening screen,
            // which is translated too.
            "TextAppearance.RoueLibre.Intro",
        )
        styles.forEach { name ->
            val body = style("", name)
            val literal = Regex("<item name=\"android:letterSpacing\">\\s*-?[0-9]")
            assertFalse(
                "$name states a figure instead of reading @dimen/$TOKEN",
                literal.containsMatchIn(body),
            )
        }
    }
}
