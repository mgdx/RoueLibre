package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The two screens one types into, lying down (SPEC §4.3, §7.2).
 *
 * Stacked as in portrait, both spend their height on things that cost the same
 * dp whichever way the phone is held — a bar, a title, a freshness line, a
 * field, a toggle — and a sideways Fairphone 3 with the keyboard open has some
 * two hundred dp of window to spend. Measured there, the address search drew
 * none of its answers while they were being typed for, and the station list
 * drew a row and a half; the "nearest station first" button, hanging from a
 * bottom edge the keyboard had pushed up into the header, ended over the
 * search field and half covered the cross that clears it.
 *
 * Both sideways arrangements answer the same way: the header and the list
 * share the width instead of the height. What is held here is the reasoning
 * they rest on — that they are still the same screens, same views and same
 * identifiers, and that they still do the things the height they give back is
 * bought with. The geometry itself is measured on a device; what a file read
 * from the disk can say is that the arrangement has not been quietly stacked
 * again. No Android runtime is involved (SPEC §14).
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
     * chosen afterwards. The sideways arrangements move the views around; they
     * add none and lose none.
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
     * On each screen the field and the list share the width, as a chain.
     *
     * A chain rather than a guideline for two reasons, both written in
     * `layout-land/fragment_welcome.xml`: it mirrors on its own, so the split
     * stays honest in a right-to-left language, and it adds no identifier that
     * portrait has not got. Two weights of one are what makes the halves
     * halves.
     */
    @Test
    fun `the field and the list share the width rather than the height`() {
        SCREENS.forEach { screen ->
            val land = arrangement("layout-land", screen)
            val list = LIST_OF.getValue(screen)
            assertTrue(
                "$screen: the field is constrained across to the list",
                """app:layout_constraintEnd_toStartOf="@id/$list"""" in land,
            )
            assertTrue(
                "$screen: the list is constrained back across to the field, which makes a chain",
                """app:layout_constraintStart_toEndOf="@id/search_field"""" in land,
            )
            assertEquals(
                "$screen: the two halves are halves, and a half is its own mirror",
                2,
                """app:layout_constraintHorizontal_weight="1"""".toRegex().findAll(land).count(),
            )
        }
    }

    /**
     * The list is given the whole height of its column.
     *
     * That is the whole of the answer to a keyboard: nothing above the list
     * costs height any more, so the rows take whatever the window is left
     * with instead of what a stack of headers has not already spent.
     */
    @Test
    fun `the list reaches the bottom of the window on both screens`() {
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
        }
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

    private companion object {
        const val NO_EXTRACT = "flagNoExtractUi"

        /** The two screens that filter a list from a field and are turned over. */
        val SCREENS = listOf("fragment_address_search.xml", "fragment_station_list.xml")

        /** What each of them calls the list the field filters. */
        val LIST_OF = mapOf(
            "fragment_address_search.xml" to "results",
            "fragment_station_list.xml" to "swipe_refresh",
        )
    }
}
