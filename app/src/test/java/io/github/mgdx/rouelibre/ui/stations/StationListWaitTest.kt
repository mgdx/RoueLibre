package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What the station list shows while it waits for a fix (SPEC §7.6).
 *
 * Pressing "nearest station first" can run to ten seconds indoors, and the
 * button going dead was the whole of the answer for that long: a tester
 * pressing it from outside the served city reported that nothing happened at
 * all, the sentence that came at the end of the wait having faded before the
 * next look. A ring turns on the button meanwhile.
 *
 * The delays are the point of this test as much as the ring is. A fix obtained
 * seconds ago and precise enough comes back on the spot, and an indicator
 * appearing and vanishing within two frames is worse than the greyed button it
 * was added to improve on. Both are the library's own doing — `showDelay` holds
 * the ring back, `minHideDelay` keeps it up once shown — and removing either
 * brings the flicker back on every phone that answers quickly.
 *
 * The layout is read from the file the build ships, as `StationListHeaderTest`
 * reads it: no Android runtime is involved (SPEC §14).
 */
class StationListWaitTest {

    @Test
    fun `a ring turns on the button while the fix is waited for`() {
        val ring = viewOf("locating")
        assertTrue(
            "the wait is shown by something other than a progress indicator",
            ring.tagName.endsWith("CircularProgressIndicator"),
        )
        assertEquals("true", ring.getAttribute("android:indeterminate"))
        // On the button and nowhere else: the thumb has just pressed there, and
        // that is where the eye is.
        listOf(
            "app:layout_constraintTop_toTopOf",
            "app:layout_constraintBottom_toBottomOf",
            "app:layout_constraintStart_toStartOf",
            "app:layout_constraintEnd_toEndOf",
        ).forEach { constraint ->
            assertEquals(
                "$constraint takes the ring off the button",
                "@id/locate_me",
                ring.getAttribute(constraint),
            )
        }
    }

    @Test
    fun `nothing is shown for a wait too short to need showing`() {
        val ring = viewOf("locating")
        assertEquals(
            "a press answered on the spot would flash the ring for two frames",
            HALF_A_SECOND,
            ring.getAttribute("app:showDelay"),
        )
        assertEquals(
            "a ring shown then taken away at once is a flicker",
            HALF_A_SECOND,
            ring.getAttribute("app:minHideDelay"),
        )
        // Nothing is waited for until the button is pressed.
        assertEquals("gone", ring.getAttribute("android:visibility"))
    }

    @Test
    fun `the ring says nothing a screen reader has to hear`() {
        // The button it rings is announced, and disabled for the whole of the
        // wait; the answer comes as a message, which is spoken. A second thing
        // to read on the same square of screen would only be in the way.
        assertEquals("no", viewOf("locating").getAttribute("android:importantForAccessibility"))
    }

    private fun viewOf(id: String): Element {
        val views = root(resource("layout/fragment_station_list.xml"))
            .descendants()
            .mapNotNull { view ->
                view.getAttribute("android:id")
                    .removePrefix("@+id/")
                    .ifEmpty { null }
                    ?.let { it to view }
            }
            .toMap()
        return checkNotNull(views[id]) { "$id is not in fragment_station_list.xml" }
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

    private companion object {
        /**
         * Long enough to sit out a press answered from a fix already held,
         * short enough that a real wait is not silent — the ten seconds
         * `DeviceLocation` allows itself are two hundred times this.
         */
        const val HALF_A_SECOND = "500"
    }
}
