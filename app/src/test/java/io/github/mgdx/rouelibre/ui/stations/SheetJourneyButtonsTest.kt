package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The two journey buttons of a station's sheet end at the same depth
 * (SPEC §7).
 *
 * They used to hold each other: "Aller ici" hung by its top and its bottom on
 * "Partir d'ici", which centres it in that button's height rather than giving
 * it that height. At the system's largest text size the first label takes two
 * lines and the second one, and the pair came out as two buttons of different
 * heights with their bottoms adrift — measured on a Fairphone 3 under
 * Android 15 at ×2.0.
 *
 * What is pinned here is the arrangement and not a number: the two buttons
 * share a row, and each asks that row for the height of the tallest of them.
 * No height is written down, so it goes on following the size of the text; and
 * the repair works whichever of the two labels wraps, which is what changes
 * from one language to the next.
 *
 * The layout is read as a document rather than measured, for the reason
 * `IndicatorScaleTest` gives: what is checked is what the application will be
 * built with, and no Android runtime is involved (SPEC §14). What a device
 * actually draws is `SheetTextSizeLayoutTest`'s to say.
 */
class SheetJourneyButtonsTest {

    @Test
    fun `the two buttons share a row that gives them one height`() {
        val origin = elementById("set_as_origin")
        val destination = elementById("set_as_destination")
        val row = origin.parentNode as Element

        assertTrue(
            "The two journey buttons no longer stand in the same row.",
            destination.parentNode === row,
        )
        assertEquals(
            "The buttons' row is not a horizontal row.",
            "horizontal",
            row.getAttribute("android:orientation"),
        )
        // A row of buttons has no baseline to share: left on, the row lines the
        // two labels' first lines up and pushes the shorter button down by the
        // height of the other's second line — the very defect this pins.
        assertEquals(
            "The buttons' row still aligns its children on their baselines.",
            "false",
            row.getAttribute("android:baselineAligned"),
        )
        listOf(origin, destination).forEach { button ->
            assertEquals(
                "${button.getAttribute("android:id")} does not take the row's height, " +
                    "so it is centred in its neighbour instead of matching it.",
                "match_parent",
                button.getAttribute("android:layout_height"),
            )
        }
    }

    @Test
    fun `neither button hangs on the other`() {
        listOf("set_as_origin", "set_as_destination").forEach { id ->
            val button = elementById(id)
            HANGING.forEach { constraint ->
                assertEquals(
                    "$id is held by $constraint, which centres it in its neighbour.",
                    "",
                    button.getAttribute(constraint),
                )
            }
        }
    }

    @Test
    fun `the third button still stands under the two others`() {
        assertEquals(
            "The navigation button no longer follows the row of the two others.",
            "@id/journey_actions",
            elementById("open_in_navigation").getAttribute("app:layout_constraintTop_toBottomOf"),
        )
    }

    /** The element the layout declares under [id]. */
    private fun elementById(id: String): Element {
        val elements = layout.getElementsByTagName("*")
        return (0 until elements.length)
            .map { elements.item(it) as Element }
            .first { it.getAttribute("android:id") == "@+id/$id" }
    }

    private val layout by lazy {
        val resources = File(
            checkNotNull(System.getProperty("rouelibre.locales")) {
                "The resource directory was not handed to the test."
            },
        )
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(resources, "layout/sheet_station_detail.xml"))
    }

    private companion object {
        /** The constraints that centre a view in another instead of sizing it. */
        val HANGING = listOf(
            "app:layout_constraintTop_toTopOf",
            "app:layout_constraintBottom_toBottomOf",
        )
    }
}
