package io.github.mgdx.rouelibre.ui.storage

import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * When opening the storage screen is itself the manifest check (SPEC §4.4).
 *
 * A city with none of its sets was shown three "Not installed" and a button
 * reading "Check for updates" whenever the screen was reached by any other way
 * than choosing the city — "the map needs its offline tiles", the settings, a
 * search — and nothing said that the update was the way to the download. The
 * offer only came after a press that named something else.
 */
class OpeningCheckTest {

    private val nothing = emptyMap<DatasetKind, InstalledDataset>()

    private val tilesOnly = mapOf(
        DatasetKind.Tiles to InstalledDataset(
            kind = DatasetKind.Tiles,
            sizeBytes = 3_174_400,
            sha256 = "3bc421bf3a29e988e724e6370f46612b998cd4bb319fb6f28a8512e0fa578526",
            installedAt = Instant.parse("2026-10-05T08:00:00Z"),
            formatVersion = 2,
        ),
    )

    @Test
    fun `a city with nothing installed is checked as the screen opens`() {
        assertTrue(OpeningCheck().calledFor(nothing))
    }

    @Test
    fun `a city with one set installed waits for a press`() {
        assertFalse(OpeningCheck().calledFor(tilesOnly))
    }

    /** A rotation, a dialogue answered: the store hands its inventory again. */
    @Test
    fun `the inventory read again asks for no second check`() {
        val rule = OpeningCheck()
        assertTrue(rule.calledFor(nothing))
        assertFalse(rule.calledFor(nothing))
        assertFalse(rule.calledFor(nothing))
    }

    /** Deleting the last set on the screen is not opening it. */
    @Test
    fun `emptying the city on the screen asks for no check`() {
        val rule = OpeningCheck()
        assertFalse(rule.calledFor(tilesOnly))
        assertFalse(rule.calledFor(nothing))
    }

    /** From the welcome screen or the city choice, the check is already asked for. */
    @Test
    fun `a check asked by whoever opened the screen is not doubled`() {
        val rule = OpeningCheck()
        rule.checkAsked()
        assertFalse(rule.calledFor(nothing))
    }
}
