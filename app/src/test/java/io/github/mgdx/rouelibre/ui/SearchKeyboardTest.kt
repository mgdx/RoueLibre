package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What the keyboard is allowed to do to the four search screens.
 *
 * In landscape a soft keyboard takes the whole window by default and puts its
 * own copy of the field in it — the "extract" editor. Every one of these fields
 * filters a list as it is typed into, and that list was invisible for the whole
 * of the typing: the answer only appeared once the keyboard was folded away,
 * which is the one moment the reader no longer needs it. `flagNoExtractUi` is
 * what refuses that full-screen editor, and it has to be on all four, the
 * defect being the platform's default rather than anything these screens do.
 *
 * The layouts are read from the files the build ships, as `StationListHeaderTest`
 * reads its own: what is checked is what the application will be built with, and
 * no Android runtime is involved (SPEC §14).
 */
class SearchKeyboardTest {

    @Test
    fun `every search field refuses the full-screen keyboard`() {
        SCREENS.forEach { screen ->
            val options = searchInputOf(screen).getAttribute("android:imeOptions")
            val flags = options.split("|")
            assertTrue(
                "$screen: its search field lets the keyboard cover the screen in landscape.",
                "flagNoExtractUi" in flags,
            )
            // The action key stays what it was: refusing the extract editor is
            // not an occasion to change what pressing "Search" does.
            assertTrue("$screen: the search action key is gone.", "actionSearch" in flags)
        }
    }

    /** The one text field of [screen], whatever it is called there. */
    private fun searchInputOf(screen: String): Element {
        val fields = root(resource("layout/$screen"))
            .descendants()
            .filter { it.tagName.endsWith("TextInputEditText") }
        return fields.singleOrNull()
            ?: error("$screen holds ${fields.size} text fields, not one")
    }

    private fun root(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun Element.children(): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()

    private fun Element.descendants(): List<Element> =
        children() + children().flatMap { it.descendants() }

    private fun resource(path: String): File = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
        path,
    )

    private companion object {
        /** The four screens that filter a list from a text field. */
        val SCREENS = listOf(
            "fragment_station_list.xml",
            "fragment_address_search.xml",
            "fragment_city.xml",
            "fragment_data_sources.xml",
        )
    }
}
