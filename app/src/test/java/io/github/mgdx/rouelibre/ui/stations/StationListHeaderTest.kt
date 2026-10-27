package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The header and the thumb's column of the station list: what they offer, and
 * where (SPEC §7.2, §7.6).
 *
 * The way to the map used to be hidden unless this list was the screen the
 * application opened on, which left it gone on the very list one reaches from
 * the map. It is unconditional, so nothing in the layout may take it away
 * again.
 *
 * **Since 27 October 2026 the list keeps its controls where the map keeps
 * them** (issue #12): the settings at the top end, and down the bottom end,
 * from the bottom up, the way to the other screen, the search, the position
 * and the journey, with the filters at the bottom start. The way back to the
 * map used to be the innermost of three icons beside the title while the way
 * here is under the thumb on the map, and one did not come back with the
 * gesture one had come by. What is checked here is that each role keeps the
 * place the map gives it.
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
    fun `the header holds the settings at the edge and nothing else`() {
        // Where the map screen keeps them, so one gesture finds them on both.
        assertEquals("parent", viewOf("open_settings").endConstraint())
        assertEquals(
            "@id/open_settings",
            viewOf("title").getAttribute("app:layout_constraintEnd_toStartOf"),
        )
        assertNull("open_favourites is back in the header", views()["open_favourites"])
    }

    @Test
    fun `the way to the map is at the bottom of the thumb column, as on the map`() {
        val button = viewOf("open_map")
        assertEquals("parent", button.getAttribute("app:layout_constraintBottom_toBottomOf"))
        assertEquals("parent", button.getAttribute("app:layout_constraintEnd_toEndOf"))
        assertNull(
            "open_map joined the header row again",
            button.getAttribute("app:layout_constraintTop_toTopOf").ifEmpty { null },
        )
    }

    @Test
    fun `the column climbs in the map's order`() {
        // From the bottom up, as `fragment_map.xml` stacks its own: the other
        // screen, the search, the position, the journey.
        assertEquals("@id/open_map", viewOf("open_search").bottomConstraint())
        assertEquals("@id/open_search", viewOf("locate_me").bottomConstraint())
        assertEquals("@id/locate_me", viewOf("open_journey").bottomConstraint())
        listOf("open_search", "locate_me", "open_journey").forEach { id ->
            assertEquals("$id left the end edge", "parent", viewOf(id).endConstraint())
        }
        assertEquals(
            "@string/stations_order_by_distance",
            viewOf("locate_me").getAttribute("android:contentDescription"),
        )
        assertEquals("@drawable/ic_my_location", viewOf("locate_me").getAttribute("app:icon"))
        assertEquals(
            "@string/journey_open",
            viewOf("open_journey").getAttribute("android:contentDescription"),
        )
    }

    @Test
    fun `what the stations count is said at the bottom start, by the map's own buttons`() {
        val toggle = viewOf("mode_toggle")
        assertTrue(
            "the toggle is a row of buttons across the width again",
            toggle.tagName.endsWith("MaterialButton"),
        )
        assertEquals("parent", toggle.getAttribute("app:layout_constraintStart_toStartOf"))
        assertEquals("parent", toggle.getAttribute("app:layout_constraintBottom_toBottomOf"))
        val kind = viewOf("bike_kind_filter")
        assertEquals("@id/mode_toggle", kind.getAttribute("app:layout_constraintStart_toEndOf"))
        // It stops at the way to the map rather than running under it.
        assertEquals("@id/open_map", kind.getAttribute("app:layout_constraintEnd_toStartOf"))
    }

    @Test
    fun `the last row can be scrolled clear of the button floating over it`() {
        // Without this padding the last station of the network sits under the
        // control for good, on a list that has already been scrolled to its end.
        assertEquals(
            "@dimen/list_room_under_the_last_row",
            viewOf("stations").getAttribute("android:paddingBottom"),
        )
        assertEquals("false", viewOf("stations").getAttribute("android:clipToPadding"))
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

    /** What this view's bottom is pinned to, whether the parent or another view. */
    private fun Element.bottomConstraint(): String =
        getAttribute("app:layout_constraintBottom_toBottomOf")
            .takeIf { it == "parent" }
            ?: getAttribute("app:layout_constraintBottom_toTopOf")

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
