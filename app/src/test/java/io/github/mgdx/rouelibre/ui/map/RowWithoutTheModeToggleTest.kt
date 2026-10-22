package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The map's bottom row holds without its Bikes / Free docks toggle (SPEC §7.1).
 *
 * The toggle is gone in a network whose free docks are not worth showing, and
 * the kind filter may then be the only button of the row. A gone view is laid
 * out as a point, its margins dropped: centred on the gone toggle the filter
 * would sit astride the attribution and start a few dp off the edge every other
 * control keeps, and the searched point's pill, hung on the toggle, would fall
 * onto the filter. Each of the three is held by an attribute, read here from
 * the layout as `MapToggleBackgroundTest` reads it: no Android runtime is
 * involved (SPEC §14).
 */
class RowWithoutTheModeToggleTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val mapLayout by lazy {
        File(resources, "layout/fragment_map.xml").readText()
    }

    /** The attributes of the view declaring [identity]. */
    private fun attributesOf(identity: String): String {
        val declared = mapLayout.indexOf("""android:id="@+id/$identity"""")
        assertTrue("The layout still declares $identity", declared > 0)
        return mapLayout.substring(declared, mapLayout.indexOf("/>", declared))
    }

    @Test
    fun `the kind filter is no longer centred on the toggle`() {
        assertFalse(
            "The kind filter is centred on the toggle again, which may be gone",
            attributesOf("bike_kind_filter")
                .contains("""app:layout_constraintTop_toTopOf="@id/mode_toggle""""),
        )
    }

    @Test
    fun `the kind filter takes the edge margin when the toggle is gone`() {
        assertTrue(
            "The kind filter starts off the edge the other controls keep",
            attributesOf("bike_kind_filter")
                .contains("""app:layout_goneMarginStart="@dimen/space_m""""),
        )
    }

    @Test
    fun `the searched point stands on whichever of the row is up`() {
        assertTrue(
            "The pill no longer hangs from the row's barrier",
            attributesOf("picked_place")
                .contains("""app:layout_constraintBottom_toTopOf="@id/filters_top""""),
        )
        val barrier = attributesOf("filters_top")
        assertTrue(
            "The barrier counts a gone button, and follows it down",
            barrier.contains("""app:barrierAllowsGoneWidgets="false""""),
        )
        assertTrue(
            "The barrier no longer covers the toggle, the kind filter and the attribution",
            barrier.contains(
                """app:constraint_referenced_ids="mode_toggle,bike_kind_filter,attribution"""",
            ),
        )
    }
}
