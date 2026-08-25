package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When the station list lifts its controls over the banner, not by how much.
 *
 * `MainActivity` measures the room the banner takes from the banner's own
 * layout listener, so the value lands in the middle of the window's layout
 * pass, and the collector below resumes there — the two views share one
 * window. A margin and a padding set on the spot request a layout while one is
 * running, which Android answers by starting the whole pass again:
 * "requestLayout() improperly called ... running second layout pass", once for
 * the button and once for the list, on every banner raised. Nothing shows on
 * screen, which is exactly why only a guard like this one keeps it from coming
 * back.
 *
 * The source is read as `DataSourcesSearchTest` reads its fragment: no Android
 * runtime is involved (SPEC §14).
 */
class BannerRoomLayoutPassTest {

    @Test
    fun `the banner's room is applied once the layout pass is over`() {
        val collector = fragment
            .substringAfter("host.roomTakenByTheBanner.collect")
            .substringBefore("private fun makeRoomForTheBanner")
        listOf("updateLayoutParams", "updatePadding").forEach { applied ->
            assertFalse(
                "The banner's room is applied straight from the collector, " +
                    "which runs inside the window's layout pass ($applied).",
                collector.contains(applied),
            )
        }
        assertTrue(
            "The banner's room no longer waits for the pass to finish.",
            collector.contains("post { makeRoomForTheBanner(room) }"),
        )
    }

    /** `app/src/main/java`, the sibling of the resources the build hands over. */
    private val fragment by lazy {
        val resources = File(
            checkNotNull(System.getProperty("rouelibre.locales")) {
                "The resource directory was not handed to the test."
            },
        )
        File(
            resources.parentFile,
            "java/io/github/mgdx/rouelibre/ui/stations/StationListFragment.kt",
        ).readText()
    }
}
