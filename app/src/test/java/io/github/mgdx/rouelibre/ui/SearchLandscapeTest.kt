package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The two screens one types into, lying down (SPEC §4.3, §7.2).
 *
 * Stacked as in portrait, both spend their height on things that cost the same
 * dp whichever way the phone is held — a bar, a title, a freshness line, a
 * field, a toggle — and a sideways Fairphone 3 with Gboard open stops at
 * 397 px. Measured there, the address search drew none of its answers while
 * they were being typed for, and the station list drew a row and a half; the
 * "nearest station first" button, hanging from a bottom edge the keyboard had
 * pushed up into the header, ended over the search field and half covered the
 * cross that clears it.
 *
 * Both sideways arrangements answer the same way: the header and the list take
 * a column each, and the header's column scrolls. What is held here is the
 * reasoning they rest on — that they are still the same screens, same views
 * and same identifiers, that the answers own the whole height of the window,
 * and that a text size makes the header taller rather than making its letters
 * fewer. The geometry itself is measured on a device; what a file read from
 * the disk can say is that the arrangement has not been quietly stacked, or
 * banded, or pinned to a height again. No Android runtime is involved
 * (SPEC §14).
 */
class SearchLandscapeTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private fun arrangement(folder: String, screen: String) =
        File(resources, "$folder/$screen").readText()

    /** Every view the arrangement in [folder] names, `@+id/` and all. */
    private fun identifiersOf(folder: String, screen: String) = """@\+id/(\w+)""".toRegex()
        .findAll(arrangement(folder, screen))
        .map { it.groupValues[1] }
        .toSet()

    /**
     * The two arrangements of each screen name the same views.
     *
     * This is not tidiness. A view binding whose identifier is missing from one
     * configuration is generated nullable, so the day an identifier is dropped
     * here the fragment stops compiling — and a view the restored page looks
     * for and does not find is a page that comes back from a rotation with
     * nothing on it, which is the one thing SPEC §7.6 asks of these two: the
     * typed query survives the screen turning over, and so does the place
     * chosen afterwards. The sideways arrangements move the views around and
     * nest them; they add no identifier and lose none.
     */
    @Test
    fun `lying down and standing up name the same views`() {
        SCREENS.forEach { screen ->
            assertEquals(
                "The two arrangements of $screen name the same views",
                identifiersOf("layout", screen),
                identifiersOf("layout-land", screen),
            )
        }
    }

    /**
     * Neither field lets the keyboard take the window for a copy of itself.
     *
     * `SearchKeyboardTest` asks this of the arrangements one stands up with;
     * these are the ones the defect was found on, and an `imeOptions` dropped
     * while moving a field into a column would put the screen back exactly
     * where it started.
     */
    @Test
    fun `both fields refuse the full-screen keyboard`() {
        SCREENS.forEach { screen ->
            val flags = viewOf("layout-land", screen, "search_input")
                .getAttribute("android:imeOptions")
                .split("|")
            assertTrue(
                "$screen, lying down: the keyboard may cover the screen.",
                NO_EXTRACT in flags,
            )
            assertTrue(
                "$screen, lying down: the search action key is gone.",
                "actionSearch" in flags,
            )
        }
    }

    /**
     * The answers take half the width, and take it in a way that mirrors.
     *
     * Half because half a sideways Fairphone 3 is the width the portrait
     * screen gives the same rows. Anchored by one edge and sized by a
     * percentage, and never by a bias: a bias is measured from the left
     * whichever way the language runs, and both screens are read right to left
     * in Arabic. One anchor and one half is what mirrors with the language.
     */
    @Test
    fun `the answers take a mirrored half of the width`() {
        SCREENS.forEach { screen ->
            val list = viewOf("layout-land", screen, LIST_OF.getValue(screen))
            assertEquals(
                "$screen: the answers no longer take half the width",
                "0.5",
                list.getAttribute("app:layout_constraintWidth_percent"),
            )
            assertEquals(
                "$screen: the answers' half is not anchored to the end edge",
                "parent",
                list.getAttribute("app:layout_constraintEnd_toEndOf"),
            )
            assertTrue(
                "$screen: a bias is measured from the left whichever way the language runs",
                "layout_constraintHorizontal_bias" !in arrangement("layout-land", screen),
            )
        }
    }

    /**
     * The list is given the whole height of the window, top and bottom.
     *
     * That is the whole of the answer to a keyboard: nothing above the list
     * costs height any more, so the rows take whatever the window is left with
     * instead of what a stack of headers has not already spent. On the address
     * search the panel that speaks for the whole screen still stands above the
     * list rather than over it, exactly as in portrait — so it is the panel
     * that holds the top, and the list follows it.
     */
    @Test
    fun `the answers run from the top of the window to the bottom`() {
        SCREENS.forEach { screen ->
            val list = viewOf("layout-land", screen, LIST_OF.getValue(screen))
            assertEquals(
                "$screen: the list stops short of the bottom edge",
                "parent",
                list.getAttribute("app:layout_constraintBottom_toBottomOf"),
            )
            assertEquals(
                "$screen: the list is measured against its own content again",
                "0dp",
                list.getAttribute("android:layout_height"),
            )
            assertEquals(
                "$screen: something is still taking the top of the answers' column",
                "parent",
                viewOf("layout-land", screen, TOP_OF.getValue(screen))
                    .getAttribute("app:layout_constraintTop_toTopOf"),
            )
        }
        assertEquals(
            "The panel no longer stands above the answers it speaks for",
            "@id/empty_state",
            viewOf("layout-land", "fragment_address_search.xml", "results")
                .getAttribute("app:layout_constraintTop_toBottomOf"),
        )
    }

    /**
     * Nothing of the header bands the screen.
     *
     * A bar, or a title and a freshness line, kept across the top costs the
     * rows the same height it costs in portrait — 140 px of a 302 px window on
     * a sideways Fairphone 3 with Gboard open, which is a row and a truncated
     * second — and it leaves the floating button hanging from a column that
     * short, high enough to bite into the first station's name. Whatever the
     * header is made of, it either sits inside the column or, where it is a
     * child of this layout in its own right, takes no more than its half.
     */
    @Test
    fun `nothing of the header reaches across the screen`() {
        SCREENS.forEach { screen ->
            HEADER_OF.getValue(screen).forEach { id ->
                val view = viewOf("layout-land", screen, id)
                val isAChildOfTheScreen = view.parentNode.nodeName
                    .startsWith("androidx.constraintlayout")
                val takesAHalf = view.getAttribute("app:layout_constraintWidth_percent") == "0.5"
                assertTrue(
                    "$screen: $id bands the screen instead of taking its column",
                    !isAChildOfTheScreen || takesAHalf,
                )
            }
        }
    }

    /**
     * The header's column gives way in height, never in letters.
     *
     * A text size of ×2.0 makes every part of that column taller — the title,
     * the data's age, the field itself — and a sideways window with a keyboard
     * in it has 151 dp. There is no arrangement that fits 36 dp of inset, a
     * 64 dp row and a 72 dp field into that, so the choice is between letters
     * lost and a column one scrolls; SPEC §7 settles it, and the comment beside
     * `mode_toggle` in `fragment_map.xml` records the same defect being fixed
     * the same way on the map. Scrolled, the field is also brought back into
     * sight by the scroll view when it takes the focus and the keyboard opens.
     */
    @Test
    fun `the header column scrolls rather than losing its letters`() {
        SCREENS.forEach { screen ->
            assertTrue(
                "$screen: the header's column cannot grow, so a large text size is cut off it",
                viewOf("layout-land", screen, "search_field")
                    .ancestors()
                    .any { it.tagName.endsWith("NestedScrollView") },
            )
        }
    }

    /**
     * The data's age is never centred inside a height it cannot have.
     *
     * Seated inside the row of icons — 48 dp of touch target — it was centred
     * there, and at ×2.0 what did not fit was lost off the top of the screen:
     * "Aktualisiert vor 59 Sekunden" measured `[457,0][670,320]`, its first
     * word cut above the edge, in French as much as in German. It keeps the
     * title's line, which is what buys the field the 53 px a sideways column
     * has not got to spare, but it keeps it in a row that wraps its tallest
     * child instead of one pinned to the icons beside it.
     */
    @Test
    fun `the data's age gives way in height rather than in letters`() {
        val freshness = viewOf("layout-land", "fragment_station_list.xml", "freshness")
        VERTICAL_PINS.forEach { attribute ->
            assertEquals(
                "The data's age is pinned again by $attribute, which is a height it cannot refuse",
                "",
                freshness.getAttribute(attribute),
            )
        }
        assertEquals(
            "The data's age is measured by something other than its own lines",
            "wrap_content",
            freshness.getAttribute("android:layout_height"),
        )
        assertEquals(
            "The row the data's age is on cannot grow under it",
            "wrap_content",
            (freshness.parentNode as Element).getAttribute("android:layout_height"),
        )
    }

    /**
     * The line of reassurance steps aside sideways.
     *
     * It is the one thing on the address search that explains rather than
     * answers, and a sideways screen has no height to explain in. It keeps its
     * identifier, gone rather than absent, for the reason the first test gives.
     */
    @Test
    fun `the address search folds away what only explains`() {
        assertEquals(
            "The privacy note is still taking height on a sideways screen",
            "gone",
            viewOf("layout-land", "fragment_address_search.xml", "privacy_note")
                .getAttribute("android:visibility"),
        )
    }

    /**
     * "Nearest station first" hangs from the list's column, not from a corner
     * the header can reach.
     *
     * Sideways, the screen's own bottom corner is a place the header reaches
     * the moment the keyboard takes the rest of the window — which is how the
     * button came to sit over the search field and half cover the cross that
     * clears it. Anchored to the column the rows are in, there is nothing
     * under it but rows, however far the bottom edge is pushed up.
     */
    @Test
    fun `the ordering button floats over the rows and over nothing else`() {
        val button = viewOf("layout-land", "fragment_station_list.xml", "locate_me")
        assertEquals(
            "locate_me hangs from the window again rather than from the list",
            "@id/swipe_refresh",
            button.getAttribute("app:layout_constraintBottom_toBottomOf"),
        )
        assertEquals(
            "locate_me is back over the column the search field is in",
            "@id/swipe_refresh",
            button.getAttribute("app:layout_constraintEnd_toEndOf"),
        )
        // The room under the last row rises with the button, and the fragment
        // raises both again by the height of a banner: without it the last
        // station of the network sits under the control for good.
        assertEquals(
            "@dimen/list_room_under_the_last_row",
            viewOf("layout-land", "fragment_station_list.xml", "stations")
                .getAttribute("android:paddingBottom"),
        )
    }

    /** The view called [id] in the arrangement of [screen] under [folder]. */
    private fun viewOf(folder: String, screen: String, id: String): Element {
        val views = root(File(resources, "$folder/$screen"))
            .descendants()
            .mapNotNull { view ->
                view.getAttribute("android:id")
                    .removePrefix("@+id/")
                    .ifEmpty { null }
                    ?.let { it to view }
            }
            .toMap()
        return checkNotNull(views[id]) { "$id is not in $folder/$screen" }
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

    /** Every element this one is nested in, nearest first. */
    private fun Element.ancestors(): List<Element> = generateSequence(parentNode) { it.parentNode }
        .filter { it.nodeType == Node.ELEMENT_NODE }
        .filterIsInstance<Element>()
        .toList()

    private companion object {
        const val NO_EXTRACT = "flagNoExtractUi"

        /** The two screens that filter a list from a field and are turned over. */
        val SCREENS = listOf("fragment_address_search.xml", "fragment_station_list.xml")

        /** What each of them calls the list the field filters. */
        val LIST_OF = mapOf(
            "fragment_address_search.xml" to "results",
            "fragment_station_list.xml" to "swipe_refresh",
        )

        /** What holds the top of the answers' column on each of them. */
        val TOP_OF = mapOf(
            "fragment_address_search.xml" to "empty_state",
            "fragment_station_list.xml" to "swipe_refresh",
        )

        /** Everything that is header rather than answer on each of them. */
        val HEADER_OF = mapOf(
            "fragment_address_search.xml" to listOf("toolbar", "search_field", "privacy_note"),
            "fragment_station_list.xml" to listOf(
                "title",
                "freshness",
                "open_settings",
                "open_favourites",
                "open_map",
                "search_field",
                "mode_toggle",
            ),
        )

        /** The two ways a view is held to a height decided somewhere else. */
        val VERTICAL_PINS = listOf(
            "app:layout_constraintTop_toTopOf",
            "app:layout_constraintBottom_toBottomOf",
        )
    }
}
