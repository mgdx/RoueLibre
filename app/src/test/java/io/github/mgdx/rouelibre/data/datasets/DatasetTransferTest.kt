package io.github.mgdx.rouelibre.data.datasets

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.ManifestFile
import io.github.mgdx.rouelibre.data.network.ConnectionCost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * The dataset transfer outlives the screen that starts it, and never starts on
 * its own (SPEC §4.4).
 *
 * The defect this holds: on the storage screen, "Download 7.6 MB" then Back a
 * second later left a `tiles.mbtiles.partial` frozen where it stood and a city
 * in service with no map, no journeys and no addresses, without a word. The
 * transfer ran in the screen's own scope, and leaving the screen cancelled it.
 * Here the screen is a scope of its own, cancelled the way leaving it cancels
 * a view model's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatasetTransferTest {

    private val dispatcher = StandardTestDispatcher()

    /** The application's scope: nothing in these tests cancels it. */
    private val application = CoroutineScope(SupervisorJob() + dispatcher)

    /** The storage screen's scope, cancelled when the user presses Back. */
    private val screen = CoroutineScope(Job() + dispatcher)

    private val connection = FakeConnectionCost()
    private val unmeteredOnly = MutableStateFlow(true)

    /** What the server has been asked for, in order. */
    private val requested = mutableListOf<DatasetKind>()

    /** What has been put in place, in order. */
    private val installed = mutableListOf<DatasetKind>()

    /** Holds every download until the test lets the bytes arrive. */
    private val bytesArrive = CompletableDeferred<Unit>()

    private val transfer = DatasetTransfer(
        scope = application,
        download = { dataset, _, onProgress ->
            requested += dataset.kind
            onProgress(DownloadProgress(dataset.kind.id, 0, dataset.sizeBytes))
            bytesArrive.await()
            Outcome.Success(listOf(File(dataset.kind.id)))
        },
        install = { kind, _, fingerprint ->
            installed += kind
            DatasetImportResult.Installed(
                InstalledDataset(kind, 1L, fingerprint, Instant.EPOCH, null),
            )
        },
        workDirectory = File("downloads"),
        connectionCost = connection,
        unmeteredOnly = unmeteredOnly,
    )

    /** The press on "Download 7.6 MB", made from the screen. */
    private fun TestScope.pressDownload() {
        screen.launch { transfer.start(manifest, DatasetKind.entries) }
        advanceUntilIdle()
    }

    @Test
    fun `a transfer carries on after the screen that started it has gone`() = runTest(dispatcher) {
        pressDownload()
        assertEquals(listOf(DatasetKind.Tiles), requested)

        screen.cancel()
        bytesArrive.complete(Unit)
        advanceUntilIdle()

        assertEquals(DatasetKind.entries, installed)
        assertFalse(transfer.state.value.isRunning)
    }

    @Test
    fun `a screen opened during the transfer finds it under way`() = runTest(dispatcher) {
        pressDownload()
        screen.cancel()
        advanceUntilIdle()

        val state = transfer.state.value
        assertTrue(state.isRunning)
        assertEquals(DatasetKind.Tiles.id, state.progress?.fileName)
        assertEquals(manifest, state.manifest)
    }

    @Test
    fun `nothing starts without a press, whatever the connection does`() = runTest(dispatcher) {
        connection.connected.value = false
        advanceUntilIdle()
        connection.connected.value = true
        connection.meteredNow.value = true
        advanceUntilIdle()
        connection.meteredNow.value = false
        unmeteredOnly.value = false
        advanceUntilIdle()

        assertTrue(requested.isEmpty())
        assertFalse(transfer.state.value.isRunning)
    }

    @Test
    fun `a connection that starts billing stops the transfer nobody is watching`() =
        runTest(dispatcher) {
            pressDownload()
            screen.cancel()

            connection.meteredNow.value = true
            advanceUntilIdle()
            bytesArrive.complete(Unit)
            advanceUntilIdle()

            assertTrue(installed.isEmpty())
            assertFalse(transfer.state.value.isRunning)
            assertTrue(transfer.state.value.heldBackByMetering)
        }

    @Test
    fun `a transfer stopped for billing does not start again when Wi-Fi returns`() =
        runTest(dispatcher) {
            pressDownload()
            screen.cancel()
            connection.meteredNow.value = true
            advanceUntilIdle()

            connection.meteredNow.value = false
            bytesArrive.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(DatasetKind.Tiles), requested)
            assertTrue(installed.isEmpty())
            assertFalse(transfer.state.value.isRunning)
        }

    @Test
    fun `changing city abandons the transfer before anything is installed`() = runTest(dispatcher) {
        pressDownload()
        screen.cancel()

        transfer.abandon()
        bytesArrive.complete(Unit)
        advanceUntilIdle()

        assertTrue(installed.isEmpty())
        assertEquals(TransferState(), transfer.state.value)
    }

    private class FakeConnectionCost : ConnectionCost {
        val meteredNow = MutableStateFlow(false)
        override val connected = MutableStateFlow(true)
        override fun isMetered(): Boolean = meteredNow.value
        override val metered = meteredNow
    }

    private companion object {
        val manifest = DataManifest(
            formatVersion = 1,
            releaseTag = "data-2026-10",
            generatedAt = "2026-10-06T00:00:00Z",
            network = "acces-velo-saguenay",
            boundingBox = null,
            datasets = DatasetKind.entries.map { kind ->
                ManifestDataset(
                    kind = kind,
                    description = kind.id,
                    files = listOf(
                        ManifestFile(
                            name = "${kind.id}.bin",
                            url = "https://example.invalid/${kind.id}.bin",
                            sizeBytes = 2_500_000,
                            sha256 = "0".repeat(64),
                        ),
                    ),
                )
            },
        )
    }
}
