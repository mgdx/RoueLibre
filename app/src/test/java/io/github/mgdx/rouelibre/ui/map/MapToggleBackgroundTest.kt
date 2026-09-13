package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The toggles laid over the map are read against a ground of their own
 * (SPEC §7).
 *
 * `Widget.RoueLibre.Toggle` leaves its resting state transparent, which is
 * right on a settings screen — the button stands on the theme's surface
 * already. Over the map there is nothing behind the word but the map: a
 * station's disc crossed the middle of "Bikes", and streets and parks ran
 * through the rest of it. The five icon buttons of the same screen are filled,
 * and the freshness label carries `pill_surface`, so the pair at the bottom
 * left was the one thing on the map one had to read through.
 *
 * The colour is a token and not a value written into the layout, and it is
 * checked to exist in both themes: the map is very pale in one and very dark
 * in the other, and a ground defined for a single theme would be no ground at
 * all in the second.
 *
 * The files are read from the disk, as `MapScreenRoomTest` reads the same
 * layout: no Android runtime is involved (SPEC §14).
 */
class MapToggleBackgroundTest {

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

    /** The two controls of the map that are words rather than an icon. */
    private val togglesOverTheMap = listOf("mode_toggle", "bike_kind_filter")

    @Test
    fun `a toggle over the map fills its outline`() {
        for (toggle in togglesOverTheMap) {
            assertTrue(
                "$toggle lets the map through behind its label",
                attributesOf(toggle).contains("""app:backgroundTint="@color/surface""""),
            )
        }
    }

    @Test
    fun `the ground they are filled with is defined in both themes`() {
        for (theme in listOf("values", "values-night")) {
            assertTrue(
                "The $theme palette no longer defines the surface the map's toggles stand on",
                """<color name="surface">""" in File(resources, "$theme/colors.xml").readText(),
            )
        }
    }
}
