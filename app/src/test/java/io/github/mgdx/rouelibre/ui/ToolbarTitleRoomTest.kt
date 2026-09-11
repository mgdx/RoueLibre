package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A toolbar title is a name, not a paragraph (SPEC §7, §9).
 *
 * A toolbar builds its own title view, and that view — an `AppCompatTextView`
 * made single-line and ellipsized by `Toolbar.setTitle` — reads its default
 * style from `android:textViewStyle`. The theme points that at
 * `Widget.RoueLibre.Text`, which justifies and hyphenates: settings decided
 * for running text, landing on a line that can never have a line after it.
 *
 * A left-to-right title never shows it, because `BoringLayout` carries it and
 * knows neither setting. A right-to-left one refuses that path, is measured by
 * `Layout.getDesiredWidth` — which honours neither — and laid out by a
 * `StaticLayout` — which honours both: in Arabic the journey screen's title
 * lost its last two letters to an ellipsis with three quarters of the bar
 * empty beside it.
 *
 * `ThemeOverlay.RoueLibre.Toolbar` puts those two settings back where every
 * other name in the application has them. It is worth nothing on the toolbar
 * that forgets it, so the check is that none does; read from the resources,
 * the way `LocalesTest` and `BottomEdgeOutsideTheLayoutTest` read them, since
 * a title is only measured on a device (SPEC §14).
 */
class ToolbarTitleRoomTest {

    private companion object {
        private const val TOOLBAR = "com.google.android.material.appbar.MaterialToolbar"
        private const val OVERLAY = "android:theme=\"@style/ThemeOverlay.RoueLibre.Toolbar\""

        /** The screens that carried a toolbar when this was written. */
        private const val TOOLBARS_EXPECTED = 12
    }

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** Every layout file, whatever configuration its folder qualifies. */
    private val layouts: List<File>
        get() = checkNotNull(resources.listFiles()) { "No resource folder was found." }
            .filter { it.isDirectory && it.name.startsWith("layout") }
            .flatMap { it.listFiles()?.asList().orEmpty() }
            .filter { it.extension == "xml" }
            .sortedBy { it.path }

    /** The opening tag of each toolbar declared in [file], attributes included. */
    private fun toolbarsIn(file: File): List<String> =
        Regex("<${Regex.escape(TOOLBAR)}\\b.*?/?>", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { it.value }
            .toList()

    @Test
    fun `every toolbar dresses its title as a name rather than as a paragraph`() {
        val bare = layouts.filter { file ->
            toolbarsIn(file).any { !it.contains(OVERLAY) }
        }
        assertTrue(
            "These toolbars justify and hyphenate their title: " +
                bare.joinToString { it.name },
            bare.isEmpty(),
        )
    }

    @Test
    fun `the check covers the toolbars the application has`() {
        val found = layouts.sumOf { toolbarsIn(it).size }
        assertTrue(
            "Only $found toolbars were read, against $TOOLBARS_EXPECTED expected: " +
                "the search no longer finds them.",
            found >= TOOLBARS_EXPECTED,
        )
    }

    @Test
    fun `the overlay names the style the rest of the application gives a name`() {
        val themes = File(resources, "values/themes.xml").readText()
        val overlay = themes.substringAfter("<style name=\"ThemeOverlay.RoueLibre.Toolbar\"", "")
            .substringBefore("</style>")
        assertTrue(
            "The toolbar overlay puts the name style back on the title view",
            overlay.contains(
                "<item name=\"android:textViewStyle\">@style/Widget.RoueLibre.Name</item>",
            ),
        )
    }

    @Test
    fun `that style still refuses justification and hyphenation`() {
        val type = File(resources, "values/type.xml").readText()
        val name = type.substringAfter("<style name=\"Widget.RoueLibre.Name\"", "")
            .substringBefore("</style>")
        assertTrue(
            "A name is not justified",
            name.contains("<item name=\"android:justificationMode\">none</item>"),
        )
        assertTrue(
            "A name is not hyphenated",
            name.contains("<item name=\"android:hyphenationFrequency\">none</item>"),
        )
    }
}
