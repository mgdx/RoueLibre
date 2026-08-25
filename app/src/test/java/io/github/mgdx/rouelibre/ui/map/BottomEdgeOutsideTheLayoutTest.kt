package io.github.mgdx.rouelibre.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When the map's bottom edge is allowed to move (SPEC §7.1, SPEC §14).
 *
 * The whole bottom of the map screen hangs off the attribution, and how far it
 * has to rise is how tall the banner stands — a figure `MainActivity` reads
 * from the banner's own layout. That reading happens **during** the window's
 * layout pass, and the map's collector is resumed there and then: raising the
 * attribution at that moment made Android throw the pass away and run it
 * again, logging `requestLayout() improperly called ... running second layout
 * pass` for the attribution and for the screen's `ConstraintLayout` under it.
 *
 * Nothing was wrong with what was drawn — it is the moment that was wrong.
 * Waiting for the pass to finish costs the single frame the banner spends
 * sliding in.
 *
 * Read from the source, like `AboutAttributionsTest` reads it: a second layout
 * pass shows up in a log on a device, and there is no JVM test that can watch
 * for it (SPEC §14).
 */
class BottomEdgeOutsideTheLayoutTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val sources = File(resources.parentFile, "java/io/github/mgdx/rouelibre")

    private val holdTheBottomEdge by lazy {
        val fragment = File(sources, "ui/map/MapFragment.kt").readText()
        val declared = fragment.indexOf("private fun holdTheBottomEdge()")
        assertTrue("The map still holds its bottom edge", declared > 0)
        fragment.substring(declared, fragment.indexOf("\n    }", declared))
    }

    @Test
    fun `the bottom edge waits for the layout pass it is called during`() {
        assertTrue(
            "The margin is not touched while the window is being laid out",
            holdTheBottomEdge.contains("isInLayout"),
        )
        assertTrue(
            "What could not be done during the pass is done once it is over",
            holdTheBottomEdge.contains("post { holdTheBottomEdge() }"),
        )
    }

    @Test
    fun `the edge still rises by the bars and the banner together`() {
        assertTrue(
            "Nothing changes about where the edge ends up",
            holdTheBottomEdge.contains("bottomMargin = barsAtTheBottom + roomForTheBanner"),
        )
    }
}
