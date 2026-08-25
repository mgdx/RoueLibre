package io.github.mgdx.rouelibre.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding one's own city on the sources page (SPEC §4.5).
 *
 * The page credits every network of the catalogue — dozens of conurbations,
 * one block each — and reaching one's own meant scrolling until it turned up.
 * It now carries the city screen's search field, and this test holds what makes
 * that reuse rather than a second invention: the same field, the same filtering
 * over the network's name and the conurbation's, the same empty state.
 *
 * The filtering itself is `CitySearchTest`'s: this one checks the screen goes
 * through it, and the things about the field that no unit of logic holds — that
 * it does not grab the focus of a page one comes to read, and that the letters
 * accent removal cannot reach are read once rather than at every keystroke.
 *
 * The files are read from the disk, as `LocalesTest` reads the locale folders:
 * what is checked is what the application will be built with, and no Android
 * runtime is involved (SPEC §14).
 */
class DataSourcesSearchTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** `app/src/main/java`, the sibling of the resources. */
    private val sources = File(resources.parentFile, "java/io/github/mgdx/rouelibre")

    private val screen by lazy { File(resources, "layout/fragment_data_sources.xml").readText() }

    private val fragment by lazy { File(sources, "ui/about/DataSourcesFragment.kt").readText() }

    @Test
    fun `the sources page carries a search field`() {
        listOf(
            "@+id/search_input",
            "@style/Widget.RoueLibre.SearchField",
            "app:endIconMode=\"clear_text\"",
            "android:imeOptions=\"actionSearch|flagNoExtractUi\"",
            "android:maxLines=\"1\"",
        ).forEach { expected ->
            assertTrue(
                "The sources page's search field is missing $expected.",
                screen.contains(expected),
            )
        }
    }

    /**
     * The city screen's search, not another one: it already knows that
     * "V'Lille" is typed "vlille", and that the conurbation's name finds the
     * network's.
     */
    @Test
    fun `the page filters through the catalogue search`() {
        assertTrue(
            "The sources page no longer filters through filterCities.",
            fragment.contains("filterCities("),
        )
    }

    /** The blocks are built once; a search moves their visibility, nothing else. */
    @Test
    fun `the catalogue is not read again at each keystroke`() {
        assertEquals(
            "The letter folds are read more than once.",
            1,
            Regex("searchLetterFolds\\(\\)").findAll(fragment).count(),
        )
        assertTrue(
            "The letter folds are read on the main thread.",
            fragment.contains("withContext(Dispatchers.IO)"),
        )
    }

    /**
     * One comes to this page to read credits, not to type: the field waits to
     * be pressed.
     */
    @Test
    fun `the field does not take the focus on opening`() {
        assertFalse(
            "The sources page asks for the focus on its search field.",
            screen.contains("<requestFocus"),
        )
        assertTrue(
            "Nothing holds the initial focus away from the search field.",
            screen.contains("android:focusableInTouchMode=\"true\""),
        )
    }

    /** An empty result is an invitation to act, not a blank page (SPEC §7). */
    @Test
    fun `a search matching nothing says so and offers a way out`() {
        listOf(
            "@string/city_no_match_title",
            "@+id/empty_message",
            "@string/action_clear_search",
        ).forEach { expected ->
            assertTrue(
                "The sources page's empty state is missing $expected.",
                screen.contains(expected),
            )
        }
        assertTrue(
            "The empty state never says what was searched for.",
            fragment.contains("R.string.city_no_match_message"),
        )
    }

    /**
     * The field borrows the city screen's wording rather than a second one:
     * the two say the same thing, and a translation is owed once.
     */
    @Test
    fun `the wording it borrows is translated everywhere`() {
        val folders = resources.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
        listOf("city_search_hint", "city_search_clear", "city_no_match_message")
            .forEach { name ->
                val missing = folders
                    .filterNot { File(it, "strings.xml").readText().contains("name=\"$name\"") }
                    .map { it.name }
                assertEquals("$name is missing from these folders", emptyList<String>(), missing)
            }
    }
}
