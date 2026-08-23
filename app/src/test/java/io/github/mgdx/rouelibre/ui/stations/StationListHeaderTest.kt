package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The header of the station list: what it offers, and in which order
 * (SPEC §7.2, §7.6).
 *
 * The way to the map used to be hidden unless this list was the screen the
 * application opened on, which left it gone on the very list one reaches from the
 * map. It is now unconditional, so nothing in the layout may take it away again.
 * The order matters as much: the title's box stops at the innermost icon, and a
 * box running under an icon is only harmless while the text is left-aligned.
 *
 * The row has two ends and they say different things: the three at the end lead
 * away from this screen, and the one at the start — "nearest station first" —
 * acts on the list under it. The title and the age line move over beside that
 * one, so the header keeps a single left edge instead of stepping.
 *
 * The layout and the drawable are read from the files the build ships, as
 * `IndicatorScaleTest` reads the dimensions: what is checked is what the
 * application will be built with, and no Android runtime is involved (SPEC §14).
 */
class StationListHeaderTest {

    @Test
    fun `the way to the map is never hidden`() {
        val button = viewOf("open_map")
        assertNull(
            "a visibility on open_map is the defect this header had",
            button.getAttribute("android:visibility").ifEmpty { null },
        )
    }

    @Test
    fun `three icons, settings at the edge and the map innermost`() {
        // From the parent's end inwards. The settings sit outermost because that
        // is where the map screen keeps them, so one gesture finds them on both.
        assertEquals("parent", viewOf("open_settings").endConstraint())
        assertEquals("@id/open_settings", viewOf("open_favourites").endConstraint())
        assertEquals("@id/open_favourites", viewOf("open_map").endConstraint())
    }

    @Test
    fun `the ordering button stands alone at the start of the row`() {
        val button = viewOf("locate_me")
        assertEquals("parent", button.getAttribute("app:layout_constraintStart_toStartOf"))
        assertEquals("@drawable/ic_my_location", button.getAttribute("app:icon"))
        assertEquals(
            "@string/stations_order_by_distance",
            button.getAttribute("android:contentDescription"),
        )
        // It is not one more of the three: those are pinned to the end, and a
        // fourth among them would leave the title nothing.
        assertNull(
            "locate_me joined the icons at the end of the row",
            button.endConstraint().ifEmpty { null },
        )
    }

    @Test
    fun `the title and the age line share one left edge`() {
        assertEquals(
            "the title has to start after the button, not under it",
            "@id/locate_me",
            viewOf("title").getAttribute("app:layout_constraintStart_toEndOf"),
        )
        assertEquals(
            "the age line has to sit under the title, not under the button",
            "@id/title",
            viewOf("freshness").getAttribute("app:layout_constraintStart_toStartOf"),
        )
    }

    @Test
    fun `the title stops at the innermost icon`() {
        assertEquals(
            "the title's box has to stop at the first icon it meets",
            "@id/open_map",
            viewOf("title").getAttribute("app:layout_constraintEnd_toStartOf"),
        )
    }

    @Test
    fun `nothing leads to the offline data from here`() {
        // The settings row of SPEC §4.4 is that way, and a fourth icon beside the
        // title leaves the title nothing.
        assertNull("open_storage is back on the station list", views()["open_storage"])
    }

    @Test
    fun `the map's button carries the map icon, not the bare pin`() {
        assertEquals("@drawable/ic_map_place", viewOf("open_map").getAttribute("app:icon"))
        assertEquals(
            "@string/settings_open",
            viewOf("open_settings").getAttribute("android:contentDescription"),
        )
    }

    @Test
    fun `the map icon is a 24 dp icon like the ones beside it`() {
        val icon = root(resource("drawable/ic_map_place.xml"))
        assertEquals("24dp", icon.getAttribute("android:width"))
        assertEquals("24dp", icon.getAttribute("android:height"))
        assertEquals("24", icon.getAttribute("android:viewportWidth"))
        assertEquals("24", icon.getAttribute("android:viewportHeight"))
    }

    @Test
    fun `the map icon stays inside its box, stroke included`() {
        val icon = root(resource("drawable/ic_map_place.xml"))
        val paths = icon.children().filter { it.tagName == "path" }
        assertTrue("ic_map_place draws nothing", paths.isNotEmpty())
        // Half the thickest stroke is how far a line spreads either side of the
        // coordinates it is drawn through: a path passing through the very edge
        // of the viewport would be shaved by the drawable's own bounds.
        val margin = paths
            .mapNotNull { it.getAttribute("android:strokeWidth").toFloatOrNull() }
            .maxOrNull()
            .let { (it ?: 0f) / 2f }
        paths.forEach { path ->
            val data = path.getAttribute("android:pathData")
            // Every command absolute and every point written out: a bound read
            // off relative steps or off an arc's flags would not be a bound.
            assertTrue(
                "ic_map_place: $data is not written in absolute M / L / C / Z",
                ABSOLUTE_ONLY.matches(data),
            )
            val figures = NUMBER.findAll(data).map { it.value.toFloat() }.toList()
            assertEquals("ic_map_place: an odd coordinate", 0, figures.size % 2)
            figures.forEach { figure ->
                assertTrue(
                    "ic_map_place: $figure leaves the 24 box at a stroke of $margin",
                    figure >= margin && figure <= VIEWPORT - margin,
                )
            }
        }
    }

    /** The header's views by their identifier, the `@+id/` prefix dropped. */
    private fun views(): Map<String, Element> = root(resource("layout/fragment_station_list.xml"))
        .descendants()
        .mapNotNull { view ->
            view.getAttribute("android:id")
                .removePrefix("@+id/")
                .ifEmpty { null }
                ?.let { it to view }
        }
        .toMap()

    private fun viewOf(id: String): Element =
        checkNotNull(views()[id]) { "$id is not in fragment_station_list.xml" }

    /** What this view's end is pinned to, whether the parent or another view. */
    private fun Element.endConstraint(): String = getAttribute("app:layout_constraintEnd_toEndOf")
        .takeIf { it == "parent" }
        ?: getAttribute("app:layout_constraintEnd_toStartOf")

    private fun root(file: File): Element = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun Element.children(): List<Element> = (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()

    private fun Element.descendants(): List<Element> =
        children() + children().flatMap { it.descendants() }

    private fun resource(path: String): File {
        val file = listOf(File("src/main/res/$path"), File("app/src/main/res/$path"))
            .firstOrNull(File::isFile)
        return checkNotNull(file) { "$path not found from ${File(".").absolutePath}" }
    }

    private companion object {
        const val VIEWPORT = 24f

        /** Absolute moves, lines and cubics, and the close: nothing else. */
        val ABSOLUTE_ONLY = Regex("""[MLCZ0-9.,\s-]+""")

        val NUMBER = Regex("""-?[0-9]+(\.[0-9]+)?""")
    }
}
