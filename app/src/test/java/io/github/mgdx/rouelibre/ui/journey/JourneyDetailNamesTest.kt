package io.github.mgdx.rouelibre.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The journey in detail writes names, not paragraphs (SPEC §7.4, §14).
 *
 * The theme justifies every text view — `Widget.RoueLibre.Text` on
 * `android:textViewStyle` — because running text is what most of the
 * application's long lines are. A line that names one thing wants the opposite,
 * and `Widget.RoueLibre.Name` is the house answer to it: no justification, no
 * hyphenation. The station rows and the address rows have asked for it since
 * they were written; this screen had not, so at 130 % text size "Ride to
 * Theatre Sebastopol" came out as "Ride␣␣␣␣to␣␣␣␣Theatre" with "Sebastopol"
 * alone underneath.
 *
 * **The check is a width rather than a list of view names.** A text view given
 * `0dp` has been handed a share of its row and can wrap; one sized
 * `wrap_content` — the minutes on the right of a leg, the count in a disc —
 * never has a second line for justification to show on. So the rule is: on
 * these three layouts, whatever can wrap names something and refuses the
 * justification. A row added later is held to it without anyone remembering to
 * add it here.
 *
 * The paragraphs of this screen are deliberately outside the rule and keep
 * their justification: the note about frozen availability counts, and the
 * sentence read while the journey is being worked out.
 *
 * The layouts are read from the disk, as `LocalesTest` reads the locale folders
 * and `UnitTypographyTest` the strings: what is checked is what the application
 * will be built with, and no Android runtime is involved (SPEC §14).
 */
class JourneyDetailNamesTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `every line of the detail that can wrap refuses the justification`() {
        val offending = LAYOUTS.flatMap { layout ->
            textViewsOf(layout)
                .filter { it.contains("""android:layout_width="0dp"""") }
                .filterNot { it.contains(NAME_STYLE) }
                .map { "$layout: ${identifierOf(it)}" }
        }
        assertEquals(
            "these lines name a thing and would be justified when they wrap",
            emptyList<String>(),
            offending,
        )
    }

    /**
     * Each `<TextView …/>` of a layout, as the text of its opening tag.
     *
     * Attributes rather than a parsed tree: what is asked of each view is
     * whether two attributes are on it, and a DOM would be a dependency and a
     * schema to satisfy for that one question.
     */
    private fun textViewsOf(layout: String): List<String> {
        val text = File(resources, "layout/$layout").readText()
        return TEXT_VIEW.findAll(text).map { it.value }.toList()
    }

    private fun identifierOf(view: String): String =
        IDENTIFIER.find(view)?.groupValues?.get(1) ?: view.take(40)

    private companion object {
        /** The screen and the two rows it is built out of. */
        private val LAYOUTS = listOf(
            "fragment_journey_detail.xml",
            "item_journey_place.xml",
            "item_journey_step.xml",
        )

        private const val NAME_STYLE = """style="@style/Widget.RoueLibre.Name""""

        private val TEXT_VIEW = Regex("""<TextView\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL)

        private val IDENTIFIER = Regex("""android:id="@\+id/([^"]+)"""")
    }
}
