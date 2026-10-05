package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the buttons for home and work stand, standing up and lying down
 * (SPEC §7.1).
 *
 * They were chained up the right-hand column from the journey button, in both
 * orientations. A Fairphone 3 on its side has some 410dp of height, the column
 * of seven buttons does not fit in it, and the work button came to lie over
 * the settings, which a press then never reached: it brought the map onto
 * work instead.
 *
 * Lying down they now stand in a second column beside the right-hand one. One
 * layout serves both orientations, the two arrangements differing only by two
 * distances that `values-land` overrides; what is held here is that the
 * buttons hang from the journey button by those distances, and that the
 * distances still say "head of the column" standing up and "beside it" lying
 * down. The geometry itself is measured on a device. No Android runtime is
 * involved (SPEC §14).
 */
class SavedPlaceButtonsLandscapeTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val mapLayout by lazy {
        File(resources, "layout/fragment_map.xml").readText()
    }

    /** The attributes of the view declaring [identity], up to its end. */
    private fun attributesOf(identity: String): String {
        val declared = mapLayout.indexOf("""android:id="@+id/$identity"""")
        assertTrue("The layout still declares $identity", declared > 0)
        return mapLayout.substring(declared, mapLayout.indexOf("/>", declared))
    }

    /** Every dimension in `dp` the file [folder]/dimens.xml declares. */
    private fun dimensionsIn(folder: String): Map<String, Int> =
        """<dimen name="(\w+)">(\d+)dp</dimen>""".toRegex()
            .findAll(File(resources, "$folder/dimens.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

    private val standingUp by lazy { dimensionsIn("values") }
    private val lyingDown by lazy { dimensionsIn("values-land") }

    /** One button and the gap between two of them: a place in the column. */
    private val onePlace by lazy {
        checkNotNull(standingUp["touch_target_min"]) + checkNotNull(standingUp["space_s"])
    }

    @Test
    fun `neither button is chained up the column from the journey button`() {
        for (button in listOf("centre_on_home", "centre_on_work")) {
            assertFalse(
                "$button climbs the right-hand column in every orientation again",
                attributesOf(
                    button,
                ).contains("""app:layout_constraintBottom_toTopOf="@id/open_journey""""),
            )
        }
    }

    @Test
    fun `home hangs from the journey button by the two distances`() {
        val home = attributesOf("centre_on_home")
        listOf(
            """app:layout_constraintEnd_toEndOf="@id/open_journey"""",
            """app:layout_constraintBottom_toBottomOf="@id/open_journey"""",
            """android:layout_marginEnd="@dimen/saved_place_button_shift"""",
            """android:layout_marginBottom="@dimen/saved_place_button_lift"""",
        ).forEach { expected ->
            assertTrue("Home is placed by $expected", home.contains(expected))
        }
    }

    /**
     * Work stands above home, and takes its place when home is hidden — the
     * gone margins are what the hidden home's own margins would have been.
     */
    @Test
    fun `work stands above home and takes its place when home is hidden`() {
        val work = attributesOf("centre_on_work")
        listOf(
            """app:layout_constraintEnd_toEndOf="@id/centre_on_home"""",
            """app:layout_constraintBottom_toTopOf="@id/centre_on_home"""",
            """app:layout_goneMarginEnd="@dimen/saved_place_button_shift"""",
            """app:layout_goneMarginBottom="@dimen/saved_place_button_lift"""",
        ).forEach { expected ->
            assertTrue("Work is placed by $expected", work.contains(expected))
        }
    }

    @Test
    fun `standing up, the buttons head the right-hand column`() {
        assertEquals(0, standingUp["saved_place_button_shift"])
        assertEquals(onePlace, standingUp["saved_place_button_lift"])
    }

    @Test
    fun `lying down, the buttons stand in a column beside it`() {
        assertEquals(onePlace, lyingDown["saved_place_button_shift"])
        assertEquals(0, lyingDown["saved_place_button_lift"])
    }
}
