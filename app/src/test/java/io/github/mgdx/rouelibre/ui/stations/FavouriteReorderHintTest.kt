package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * When the favourites screen offers its reordering hint (SPEC §7.5).
 *
 * The screen used to state "Press and hold a row to move it." over the very
 * message inviting one to add a first favourite: two sentences contradicting
 * each other, on a screen holding no row to move. One favourite is no better —
 * it has nowhere to go. The hint belongs to lists of two or more, and it has to
 * follow the list rather than be placed once at creation, so that the second
 * favourite brings it and the removal of one takes it away.
 *
 * The rule is read here without an Android runtime, and the layout is read from
 * the file the build ships, as `StationListHeaderTest` reads it (SPEC §14).
 */
class FavouriteReorderHintTest {

    @Test
    fun `an empty list is not invited to reorder itself`() {
        assertFalse("nothing to move, and the empty screen says so", canReorderFavourites(0))
    }

    @Test
    fun `a single favourite has nowhere to move`() {
        assertFalse("one row cannot be dragged anywhere", canReorderFavourites(1))
    }

    @Test
    fun `two favourites are an order, and can be told so`() {
        assertTrue(canReorderFavourites(2))
        assertTrue(canReorderFavourites(3))
        assertTrue(canReorderFavourites(40))
    }

    @Test
    fun `the hint starts hidden, so it never shows before the list is known`() {
        // The state arrives one frame after the view: a hint visible in the
        // layout is a hint shown over an empty screen, however briefly.
        assertEquals(
            "the hint is back to being a fixture of the screen",
            "gone",
            viewOf("hint").getAttribute("android:visibility"),
        )
    }

    @Test
    fun `the empty message hangs on the list's box, not on the hint`() {
        // A hint that collapses must not drag the empty message with it: the
        // message is centred on the list's box, which stands on its own.
        val empty = viewOf("empty")
        assertEquals("@id/stations", empty.getAttribute("app:layout_constraintTop_toTopOf"))
        assertEquals("@id/stations", empty.getAttribute("app:layout_constraintBottom_toBottomOf"))
        assertEquals("@string/journey_no_favourite", empty.getAttribute("android:text"))
    }

    private fun viewOf(id: String): Element {
        val views = root(resource("layout/fragment_favourites.xml"))
            .descendants()
            .mapNotNull { view ->
                view.getAttribute("android:id")
                    .removePrefix("@+id/")
                    .ifEmpty { null }
                    ?.let { it to view }
            }
            .toMap()
        return checkNotNull(views[id]) { "$id is not in fragment_favourites.xml" }
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

    private fun resource(path: String): File {
        val file = listOf(File("src/main/res/$path"), File("app/src/main/res/$path"))
            .firstOrNull(File::isFile)
        return checkNotNull(file) { "$path not found from ${File(".").absolutePath}" }
    }
}
