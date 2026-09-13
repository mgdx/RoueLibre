package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The station's sheet says its two counts out loud (SPEC §7.1).
 *
 * The counts are the subject of that screen and they are **painted**:
 * `AvailabilityIndicatorView` writes its figure onto a canvas, which no screen
 * reader can read. A `uiautomator` dump of the sheet held the two labels and
 * not one number — "BIKES", "FREE DOCKS", and a reader left to guess how many.
 *
 * Three things keep that from coming back, and this file checks all three: the
 * sheet says the counts on the row holding them, the discs and their labels are
 * taken out of the accessibility tree so that no word is read twice or out of
 * its figure's company, and the sentence is built in one place — the list row
 * speaks it too, and a second copy of it would agree with the first only until
 * one of them was edited.
 *
 * The sources and the layout are read as text, as `BannerRoomLayoutPassTest`
 * reads its fragment: no Android runtime is involved (SPEC §14).
 */
class StationCountsAreSpokenTest {

    @Test
    fun `the sheet says both counts on the row holding them`() {
        assertTrue(
            "The sheet leaves the row of counts with no description, so a " +
                "screen reader is given the labels and neither figure.",
            sheet.contains(
                "views.counts.contentDescription = requireContext().spokenAvailability(",
            ),
        )
    }

    @Test
    fun `the discs and their labels are out of the accessibility tree`() {
        val counts = elementById("counts")
        val blocks = counts.childNodes.let { children ->
            (0 until children.length).mapNotNull { children.item(it) as? Element }
        }
        assertEquals("The row of counts no longer holds two blocks.", 2, blocks.size)
        blocks.forEach { block ->
            assertEquals(
                "A block of the counts row is still read on its own, which says " +
                    "a label with no figure beside it.",
                "noHideDescendants",
                block.getAttribute("android:importantForAccessibility"),
            )
        }
    }

    @Test
    fun `the spoken sentence is built in one place`() {
        listOf("The sheet" to sheet, "The list row" to adapter).forEach { (screen, source) ->
            assertTrue(
                "$screen no longer asks for the shared sentence.",
                source.contains("spokenAvailability("),
            )
            assertFalse(
                "$screen counts the bikes itself, so the two screens can drift apart.",
                source.contains("R.plurals.bikes_available"),
            )
        }
    }

    /** The element the layout declares under [id]. */
    private fun elementById(id: String): Element {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(resources, "layout/sheet_station_detail.xml"))
        val elements = document.getElementsByTagName("*")
        return (0 until elements.length)
            .map { elements.item(it) as Element }
            .first { it.getAttribute("android:id") == "@+id/$id" }
    }

    /** `app/src/main/res`, handed to the test by the build. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val sheet by lazy { sourceOf("StationDetailSheet.kt") }

    private val adapter by lazy { sourceOf("StationAdapter.kt") }

    /** `app/src/main/java`, the sibling of the resources the build hands over. */
    private fun sourceOf(name: String): String = File(
        resources.parentFile,
        "java/io/github/mgdx/rouelibre/ui/stations/$name",
    ).readText()
}
