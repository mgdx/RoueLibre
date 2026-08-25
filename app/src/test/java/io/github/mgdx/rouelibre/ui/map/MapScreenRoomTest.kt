package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The room the map's controls give their labels (SPEC §7, SPEC §14).
 *
 * The mode toggle read "Free docks" with the bottom of its letters cut off by
 * its own outline, at the system text size of ×2.0 and there alone — which is
 * why it lived through every pass made at ×1.0. Its height was written as
 * 48 dp, a figure in `dp` capping a label written in `sp`: the text grew with
 * the system setting and the box did not.
 *
 * 48 dp is what a finger needs, not what a label needs, so it belongs in
 * `minHeight`, where it is a floor rather than a ceiling. The buttons carrying
 * only an icon keep their fixed square: nothing inside them grows.
 *
 * The geometry itself is measured on a device. What is held here is which
 * attribute carries the 48 dp, so that a hand putting the height back is told
 * what it is undoing rather than finding out on a phone nobody tests at ×2.0.
 * The file is read from the disk, as `WelcomeBodyRoomTest` reads it: no
 * Android runtime is involved (SPEC §14).
 */
class MapScreenRoomTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val mapLayout by lazy {
        File(resources, "layout/fragment_map.xml").readText()
    }

    /**
     * The attributes of the view declaring [identity].
     *
     * A view whose identity is gone from the layout is a failed assertion and
     * not an exception thrown while cutting the file up, so that what a run
     * reports is the thing that is missing.
     */
    private fun attributesOf(identity: String): String {
        val declared = mapLayout.indexOf("""android:id="@+id/$identity"""")
        assertTrue("The layout still declares $identity", declared > 0)
        return mapLayout.substring(declared, mapLayout.indexOf("/>", declared))
    }

    /** Every button of this screen that carries a label rather than an icon. */
    private val buttonsCarryingALabel = listOf(
        "mode_toggle",
        "bike_kind_filter",
        "missing_tiles_storage",
        "missing_tiles_list",
    )

    @Test
    fun `a button carrying a label is as tall as the label needs`() {
        for (button in buttonsCarryingALabel) {
            val attributes = attributesOf(button)
            assertFalse(
                "$button no longer caps its height at the touch target",
                attributes.contains("""android:layout_height="@dimen/touch_target_min""""),
            )
            assertTrue(
                "$button takes the height its label asks for",
                attributes.contains("""android:layout_height="wrap_content""""),
            )
        }
    }

    @Test
    fun `a button carrying a label is still a whole touch target`() {
        for (button in buttonsCarryingALabel) {
            assertTrue(
                "$button keeps the 48 dp a finger needs, as a floor",
                attributesOf(button).contains("""android:minHeight="@dimen/touch_target_min""""),
            )
        }
    }

    /**
     * The kind filter stands beside the toggle and is constrained on it, so a
     * toggle grown by the text size takes it along instead of leaving it
     * behind.
     */
    @Test
    fun `the kind filter follows the toggle it stands beside`() {
        val attributes = attributesOf("bike_kind_filter")
        for (constraint in listOf("Top_toTopOf", "Bottom_toBottomOf")) {
            assertTrue(
                "The kind filter is held to the toggle by $constraint",
                attributes.contains("""app:layout_constraint$constraint="@id/mode_toggle""""),
            )
        }
    }
}
