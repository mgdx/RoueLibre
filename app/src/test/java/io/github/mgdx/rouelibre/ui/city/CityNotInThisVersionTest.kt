package io.github.mgdx.rouelibre.ui.city

import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the city list does with a city it is told about and cannot serve.
 *
 * The catalogue is refreshed over the network and each city's configuration
 * ships in the APK (SPEC §15), so the two can fall out of step in both
 * directions. This is the direction that was silently broken: a catalogue
 * published after the release names cities the build carries nothing for, and
 * choosing one used to leave the application with no configuration and the
 * screen with nothing to say. Wrocław, Bogotá and four others were added to the
 * catalogue on 31 August 2026, which is exactly when the case stopped being
 * hypothetical.
 */
class CityNotInThisVersionTest {

    @Test
    fun `a city the build ships is served`() {
        assertTrue(isCitySupported("wrm-nextbike-poland", KNOWN))
    }

    @Test
    fun `a city only the catalogue knows is refused`() {
        assertFalse(isCitySupported("a-network-added-after-this-release", KNOWN))
    }

    @Test
    fun `no readable configuration at all refuses nobody`() {
        // An asset directory that cannot be listed is a manufacturing defect.
        // Reading it as "this build serves no city" would turn that defect into
        // an application which refuses every one of its own cities.
        assertTrue(isCitySupported("wrm-nextbike-poland", emptySet()))
        assertTrue(isCitySupported("a-network-added-after-this-release", emptySet()))
    }

    @Test
    fun `the refused cities are shown last, under the ones that work`() {
        val rows = listOf(
            row("later-still", supported = false),
            row("added-after-this-release", supported = false),
            row("aachen"),
            row("zaragoza", installedBytes = 42_000_000),
        ).sortedWith(cityDisplayOrder())

        assertEquals(
            listOf("zaragoza", "aachen", "added-after-this-release", "later-still"),
            rows.map { it.entry.id },
        )
    }

    @Test
    fun `the city in service stays at the head even where one is refused`() {
        val rows = listOf(
            row("added-after-this-release", supported = false),
            row("aachen"),
            row("lille", active = true),
        ).sortedWith(cityDisplayOrder())

        assertEquals("lille", rows.first().entry.id)
    }

    private fun row(
        id: String,
        active: Boolean = false,
        installedBytes: Long = 0,
        supported: Boolean = true,
    ) = CityRow(
        entry = CityEntry(
            id = id,
            displayName = id,
            operator = "operator",
            mainCity = id,
            country = "FR",
            stationCount = 100,
            stationSamples = listOf(Coordinates(50.6, 3.06)),
            boundingBox = BoundingBox(
                south = 50.5,
                west = 2.9,
                north = 50.7,
                east = 3.2,
            ),
            centre = Coordinates(50.6, 3.06),
            dataSizeBytes = 1_000_000,
            releaseTag = "data-2026-08-fr",
            manifestUrl = "https://example.invalid/manifest.json",
            gbfsDiscoveryUrl = "https://example.invalid/gbfs.json",
        ),
        isActive = active,
        installedBytes = installedBytes,
        isSupported = supported,
    )

    private companion object {
        val KNOWN = setOf("aachen", "lille", "wrm-nextbike-poland", "zaragoza")
    }
}
