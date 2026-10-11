package io.github.mgdx.rouelibre.ui.storage

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetUpdate
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * When the storage screen hands over to the map on its own (SPEC §7.9).
 *
 * At the end of the welcome sequence the user had downloaded their city and was
 * left before three "Up to date" rows, with nothing to press but Back to reach
 * the map they had come for. Elsewhere the screen is where they meant to be.
 */
class WelcomeHandOverTest {

    private val manifest = DataManifest(
        formatVersion = 2,
        releaseTag = "data-2026-08-gb",
        generatedAt = "2026-08-23T07:53:44Z",
        network = "nextbike-stirling",
        boundingBox = null,
        datasets = emptyList(),
    )

    private fun installed(kind: DatasetKind) = InstalledDataset(
        kind = kind,
        sizeBytes = 1_000,
        sha256 = "3bc421bf3a29e988e724e6370f46612b998cd4bb319fb6f28a8512e0fa578526",
        installedAt = Instant.parse("2026-10-05T08:00:00Z"),
        formatVersion = 2,
    )

    private fun rows(update: (DatasetKind) -> DatasetUpdate) = DatasetKind.entries.map { kind ->
        val state = update(kind)
        DatasetRow(
            kind = kind,
            installed = installed(kind).takeIf { state != DatasetUpdate.Missing },
            update = state,
        )
    }

    private val allInstalled = StorageUiState(
        manifest = manifest,
        datasets = rows { DatasetUpdate.UpToDate },
    )

    @Test
    fun `the welcome sequence ends on the map once every set is installed`() {
        assertTrue(allInstalled.handsOverToTheMap(openedFromWelcome = true))
    }

    /** Another city added, an update fetched: the screen stays. */
    @Test
    fun `a download started from the storage screen stays on it`() {
        assertFalse(allInstalled.handsOverToTheMap(openedFromWelcome = false))
    }

    /** The first set in is not the end of the transfer. */
    @Test
    fun `a set still to come keeps the screen`() {
        val state = allInstalled.copy(
            datasets = rows {
                if (it == DatasetKind.Routing) DatasetUpdate.Missing else DatasetUpdate.UpToDate
            },
            isDownloading = true,
        )

        assertFalse(state.handsOverToTheMap(openedFromWelcome = true))
    }

    /** The files all in, the transfer not yet said to be over. */
    @Test
    fun `a transfer still running keeps the screen`() {
        assertFalse(
            allInstalled.copy(isDownloading = true).handsOverToTheMap(openedFromWelcome = true),
        )
    }

    /** The screen stays to say why, and to offer the resumption. */
    @Test
    fun `a set that failed keeps the screen`() {
        val state = allInstalled.copy(
            datasets = rows {
                if (it == DatasetKind.Addresses) DatasetUpdate.Missing else DatasetUpdate.UpToDate
            },
        ).withFailure(DatasetKind.Addresses, DataError.Offline)

        assertFalse(state.handsOverToTheMap(openedFromWelcome = true))
    }

    /** Before the manifest, nothing says what "every set" means. */
    @Test
    fun `a screen that has read no manifest stays`() {
        val state =
            StorageUiState(datasets = DatasetKind.entries.map { DatasetRow(it, installed(it)) })

        assertFalse(state.handsOverToTheMap(openedFromWelcome = true))
    }

    @Test
    fun `a transfer held back by a billed connection keeps the screen`() {
        val state = allInstalled.copy(
            datasets = rows {
                if (it == DatasetKind.Tiles) DatasetUpdate.Missing else DatasetUpdate.UpToDate
            },
            heldBackByMetering = true,
        )

        assertFalse(state.handsOverToTheMap(openedFromWelcome = true))
    }
}
