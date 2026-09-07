package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * That a station is handed over by the shared path, and not by a hand-built
 * intent (SPEC §7.2, §7.8).
 *
 * The application answers `geo:` itself, so an intent built here and started
 * plainly offered Roue Libre among the applications that guide: on a phone
 * where it is the only one to answer, or the one kept as the default, the
 * button reopened the station one was leaving and the press looked lost.
 * `handOverToNavigation` is what takes this application out of the chooser,
 * and the guard is that the sheet goes through it rather than round it.
 *
 * The source is read as `BannerRoomLayoutPassTest` reads its fragment: the
 * decision is which call is made, and no Android runtime is needed to see it
 * (SPEC §14).
 */
class StationHandoverTest {

    @Test
    fun `the sheet hands the station over through the shared path`() {
        assertTrue(
            "The station's sheet no longer calls handOverToNavigation().",
            sheet.contains("handOverToNavigation(station.name, station.position)"),
        )
    }

    @Test
    fun `the sheet builds no navigation intent of its own`() {
        listOf("ACTION_VIEW", "startActivity", "Uri.encode").forEach { built ->
            assertFalse(
                "The station's sheet builds its own handover again ($built), " +
                    "which puts Roue Libre back in the chooser.",
                sheet.contains(built),
            )
        }
    }

    /** `app/src/main/java`, the sibling of the resources the build hands over. */
    private val sheet by lazy {
        val resources = File(
            checkNotNull(System.getProperty("rouelibre.locales")) {
                "The resource directory was not handed to the test."
            },
        )
        File(
            resources.parentFile,
            "java/io/github/mgdx/rouelibre/ui/stations/StationDetailSheet.kt",
        ).readText()
    }
}
