package io.github.mgdx.rouelibre.ui.storage

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.data.datasets.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the storage screen shows of a transfer, from the press to its outcome
 * (SPEC §4.4, §14).
 *
 * Two silences are held here, and they are the same blind spot read at two
 * moments.
 *
 * **Before the first byte.** A press on "Download 9.1 MB" with no network
 * produced nothing at all: the line above the button only ever spoke of bytes
 * that had arrived, and where the connection is gone none ever does. The
 * request has ten seconds to give up, and the screen spent them looking exactly
 * as it had before the press — which reads as a press that was lost.
 *
 * **After the failure.** The row of a set whose transfer failed fell back to
 * "Not installed", word for word what a set nobody ever asked for says, once
 * the snackbar that had said otherwise was gone. Somebody left with a map and
 * without the two functions the welcome screen had just promised, and nothing
 * on the screen said so.
 */
class TransferStateTest {

    private val downloading = StorageUiState(isDownloading = true)

    @Test
    fun `an idle screen says nothing about a transfer`() {
        assertEquals(TransferLine.None, StorageUiState().transferLine())
    }

    /** The state that was missing: asked for, and not yet answered. */
    @Test
    fun `a transfer asked for is announced before its first byte`() {
        assertEquals(TransferLine.Starting, downloading.transferLine())
    }

    @Test
    fun `the first byte turns the announcement into a proportion`() {
        val state = downloading.copy(
            downloading = DownloadProgress("tiles.mbtiles", 1_000, 35_000),
        )

        assertEquals(TransferLine.UnderWay, state.transferLine())
    }

    /** The manifest check keeps the line it had, ahead of everything else. */
    @Test
    fun `reading the manifest is still said in its own words`() {
        assertEquals(
            TransferLine.Checking,
            downloading.copy(isChecking = true).transferLine(),
        )
    }

    /** So does the wait for a connection nobody is billed for (SPEC §7.6). */
    @Test
    fun `a transfer held back by a billed connection still says so`() {
        val state = StorageUiState(heldBackByMetering = true)

        assertEquals(TransferLine.WaitingForUnmetered, state.transferLine())
    }

    @Test
    fun `a failure is written on the row of the set that failed, and on no other`() {
        val state = StorageUiState().withFailure(DatasetKind.Routing, DataError.Offline)

        assertEquals(
            DataError.Offline,
            state.datasets.first { it.kind == DatasetKind.Routing }.failure,
        )
        assertNull(state.datasets.first { it.kind == DatasetKind.Tiles }.failure)
        assertNull(state.datasets.first { it.kind == DatasetKind.Addresses }.failure)
    }

    /**
     * A new attempt starts on a clean sheet: the row of a set being fetched
     * again must not go on showing why the previous attempt failed.
     */
    @Test
    fun `a new attempt forgets the failures of the previous one`() {
        val state = StorageUiState()
            .withFailure(DatasetKind.Routing, DataError.Offline)
            .withFailure(DatasetKind.Addresses, DataError.Timeout)
            .withoutFailures()

        assertEquals(emptyList<DataError>(), state.datasets.mapNotNull { it.failure })
    }
}
