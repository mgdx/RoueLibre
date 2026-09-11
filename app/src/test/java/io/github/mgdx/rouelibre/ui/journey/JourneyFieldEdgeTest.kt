package io.github.mgdx.rouelibre.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The margin the two address fields keep when the swap button is not there.
 *
 * A journey departing from a bike outside stations locks its origin and hides
 * the swap button (SPEC §7.3), and both journey screens do it the same way.
 * The origin field is constrained to that button's start, so hiding it leaves
 * the field constrained to a point: `ConstraintLayout` reduces a `GONE` view to
 * a point and drops its margins with it, and the field ran to the physical edge
 * of the screen while the sentence and the button below it kept their sixteen.
 * `layout_goneMarginEnd` is what takes the hidden button's margin over, and the
 * destination field, whose end is the origin's, follows along.
 *
 * Written as `SearchKeyboardTest` is written: the layouts are read from the
 * files the build ships, so no Android runtime is involved (SPEC §14). That the
 * two edges then look alike is checked on a telephone.
 */
class JourneyFieldEdgeTest {

    @Test
    fun `the origin field keeps its end margin once the swap button is gone`() {
        LAYOUTS.forEach { layout ->
            val views = root(resource(layout))
                .descendants()
                .associateBy { it.getAttribute("android:id") }
            val origin = checkNotNull(views["@+id/origin"]) { "$layout: no origin field." }
            val swap = checkNotNull(views["@+id/swap"]) { "$layout: no swap button." }

            // The defect only exists while the field hangs off the button; were
            // that constraint ever to change, this test would be reassuring
            // about something that no longer happens.
            assertEquals(
                "$layout: the origin field is no longer constrained to the swap button.",
                "@id/swap",
                origin.getAttribute("app:layout_constraintEnd_toStartOf"),
            )
            assertEquals(
                "$layout: the origin field loses its end margin when the swap button is hidden.",
                swap.getAttribute("android:layout_marginEnd"),
                origin.getAttribute("app:layout_goneMarginEnd"),
            )
        }
    }

    @Test
    fun `the destination field takes its edges from the origin`() {
        LAYOUTS.forEach { layout ->
            val destination = root(resource(layout)).descendants()
                .single { it.getAttribute("android:id") == "@+id/destination" }
            assertEquals(
                "$layout: the destination field no longer shares the origin's end.",
                "@id/origin",
                destination.getAttribute("app:layout_constraintEnd_toEndOf"),
            )
        }
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
        /** The three layouts holding the pair of fields and their swap button. */
        val LAYOUTS = listOf(
            "layout/fragment_journey_search.xml",
            "layout/fragment_journey_result.xml",
            "layout-land/fragment_journey_result.xml",
        )
    }
}
