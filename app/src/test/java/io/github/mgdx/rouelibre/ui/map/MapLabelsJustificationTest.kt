package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The short lines laid over the map are not justified (SPEC §7).
 *
 * The theme justifies every text view, which suits a paragraph and nothing
 * else. At twice the system text size the data's age took two lines, and the
 * first of them came out spread bank to bank — "Updated    3    seconds" —
 * which reads as three labels rather than one. `Widget.RoueLibre.Name` is the
 * style that refuses it for a line naming one thing; what is held here is that
 * the labels over the map still wear it, and that it still refuses.
 *
 * The files are read from the disk, as `MapScreenRoomTest` reads them: no
 * Android runtime is involved (SPEC §14).
 */
class MapLabelsJustificationTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val mapLayout by lazy {
        File(resources, "layout/fragment_map.xml").readText()
    }

    /** The whole opening tag of the view declaring [identity], style included. */
    private fun tagOf(identity: String): String {
        val declared = mapLayout.indexOf("""android:id="@+id/$identity"""")
        assertTrue("The layout still declares $identity", declared > 0)
        return mapLayout.substring(
            mapLayout.lastIndexOf("<", declared),
            mapLayout.indexOf(">", declared),
        )
    }

    @Test
    fun `the labels over the map are set as names, not as paragraphs`() {
        for (label in listOf("freshness", "picked_place", "attribution", "missing_tiles_title")) {
            assertTrue(
                "$label wears the style that refuses justification",
                tagOf(label).contains("""style="@style/Widget.RoueLibre.Name""""),
            )
        }
    }

    @Test
    fun `the style a name wears refuses justification`() {
        val type = File(resources, "values/type.xml").readText()
        val name = type
            .substringAfter("""<style name="Widget.RoueLibre.Name"""")
            .substringBefore("</style>")
        assertTrue(
            "Widget.RoueLibre.Name no longer refuses justification",
            name.contains("""<item name="android:justificationMode">none</item>"""),
        )
    }
}
