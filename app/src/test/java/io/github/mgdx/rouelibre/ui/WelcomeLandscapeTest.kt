package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The welcome sequence lying down (SPEC §7.9).
 *
 * Stacked as in portrait, the page spends its height on things that cost the
 * same dp whichever way the phone is held — a title, a step label and two
 * buttons one under the other — and on a Fairphone 3 turned sideways that left
 * the paragraph 318 px of the 1080 the screen has: two lines and a half, the
 * third cut through the middle. The sideways arrangement gives that height
 * back by making the two things that can give way do so — the paragraph and
 * the drawing share the width rather than the height, and the two buttons
 * share a row rather than stacking.
 *
 * What is held here is the reasoning that arrangement rests on. The geometry
 * itself is measured on a device by `TextSizeLayoutTest`; what a file read
 * from the disk can say is that the arrangement still does the things the
 * height it gives back is bought with, and that it is still the same screen —
 * same views, same identifiers, which is what the binding and the page
 * restored after a rotation depend on. No Android runtime is involved
 * (SPEC §14).
 */
class WelcomeLandscapeTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private fun arrangement(folder: String) =
        File(resources, "$folder/fragment_welcome.xml").readText()

    private val landscape by lazy { arrangement("layout-land") }

    /** Every view the arrangement in [folder] names, `@+id/` and all. */
    private fun identifiersOf(folder: String) = """@\+id/(\w+)""".toRegex()
        .findAll(arrangement(folder))
        .map { it.groupValues[1] }
        .toSet()

    /**
     * The two arrangements name the same views.
     *
     * This is not tidiness. A view binding whose identifier is missing from one
     * configuration is generated nullable, so the day an identifier is dropped
     * here the fragment stops compiling — and a view the restored page looks
     * for and does not find is a page that comes back from a rotation, or from
     * the death of the process, with nothing on it. The sideways arrangement
     * moves the views around; it adds none and loses none.
     */
    @Test
    fun `lying down and standing up name the same views`() {
        assertEquals(
            "The two arrangements of the welcome page name the same views",
            identifiersOf("layout"),
            identifiersOf("layout-land"),
        )
    }

    /**
     * The paragraph and the drawing share the width, as a chain.
     *
     * A chain rather than a guideline for two reasons. It mirrors on its own,
     * so the split stays honest in a right-to-left language; and the fleet
     * page, which hides the drawing, is then left holding a chain of one and
     * the paragraph takes the whole width — the answer portrait gives when the
     * same page breaks the same chain vertically.
     */
    @Test
    fun `the paragraph and the drawing share the width rather than the height`() {
        assertTrue(
            "The paragraph is constrained across to the drawing",
            """app:layout_constraintEnd_toStartOf="@id/illustration"""" in landscape,
        )
        assertTrue(
            "The drawing is constrained back across to the paragraph, which makes a chain",
            """app:layout_constraintStart_toEndOf="@id/body_container"""" in landscape,
        )
        assertEquals(
            "The two halves are halves, and a half is its own mirror",
            2,
            """app:layout_constraintHorizontal_weight="1"""".toRegex()
                .findAll(landscape).count(),
        )
    }

    /**
     * The paragraph is given the whole height of the room, not a share of it.
     *
     * Measured against the room rather than against its own content, it cannot
     * claim a height the room has not got, and what does not fit is scrolled
     * — which is what SPEC §14 asks of a page that no longer fits, and what
     * the portrait arrangement gets by capping a `wrap` container instead.
     */
    @Test
    fun `the paragraph scrolls inside the room instead of spilling out of it`() {
        val container = landscape.substring(
            landscape.indexOf("<androidx.core.widget.NestedScrollView"),
            landscape.indexOf(">", landscape.indexOf("""android:id="@+id/body_container"""")),
        )
        assertTrue(
            "The paragraph reaches the top of the room",
            """app:layout_constraintTop_toTopOf="parent"""" in container,
        )
        assertTrue(
            "The paragraph reaches the bottom of the room",
            """app:layout_constraintBottom_toBottomOf="parent"""" in container,
        )
        assertTrue(
            "Its height is the room's",
            """android:layout_height="0dp"""" in container,
        )
    }

    /**
     * The two buttons share the bottom row.
     *
     * That is the other half of the height given back, and the half a hand
     * tidying this file is most likely to undo by writing the buttons under
     * one another as portrait has them.
     */
    @Test
    fun `the two buttons share a row instead of stacking`() {
        assertTrue(
            "\"Skip\" sits beside \"Continue\" rather than under it",
            """app:layout_constraintEnd_toStartOf="@id/next"""" in landscape,
        )
        assertTrue(
            "The two buttons are set on one line",
            """app:layout_constraintTop_toTopOf="@id/next"""" in landscape,
        )
    }

    /**
     * A resized row may give up width; it may not give up the touch target.
     *
     * Both buttons are as wide as their words here rather than as wide as the
     * screen, and the minimum height is then the only thing left saying how
     * big the press has to be.
     */
    @Test
    fun `both buttons keep the minimum touch target`() {
        assertEquals(
            "Each button keeps the minimum height of a touch target",
            2,
            """android:minHeight="@dimen/touch_target_min"""".toRegex()
                .findAll(landscape).count(),
        )
    }
}
