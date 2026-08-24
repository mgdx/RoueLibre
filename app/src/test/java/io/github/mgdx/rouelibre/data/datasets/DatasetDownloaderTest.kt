package io.github.mgdx.rouelibre.data.datasets

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.ManifestFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.net.ssl.SSLHandshakeException

/**
 * Tests of dataset downloading (SPEC §4.4).
 *
 * A real local HTTP server rather than a fake source: what matters here — the
 * resumption through a `Range` header, the refusal of a file whose digest does
 * not match — happens precisely in the HTTP exchanges, which a fake source
 * would not reproduce.
 */
class DatasetDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: DatasetDownloader
    private lateinit var workDirectory: File

    /** The content served, large enough to fill several buffers. */
    private val content = ByteArray(200_000) { (it % 251).toByte() }

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        downloader = DatasetDownloader(
            client = OkHttpClient(),
            userAgent = "RoueLibre/test",
            ioDispatcher = Dispatchers.IO,
        )
        workDirectory = Files.createTempDirectory("telechargement").toFile()
    }

    @After
    fun stop() {
        server.close()
        workDirectory.deleteRecursively()
    }

    @Test
    fun `downloads a set and verifies its digest`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value
            ?: throw AssertionError("failure: $outcome")
        assertEquals(1, files.size)
        assertEquals(content.size.toLong(), files.first().length())
        assertTrue("the partial file must be gone", partialFiles().isEmpty())
    }

    @Test
    fun `names a manifest host that cannot be trusted for what it is`() = runTest {
        // Not a manifest that cannot be read: nothing was received. Told
        // otherwise, the reader looks for a fault in a file that never came
        // down. The failure is raised by an interceptor rather than by a real
        // handshake — what is under test is the name given to it.
        val refused = DatasetDownloader(
            client = OkHttpClient.Builder()
                .addInterceptor { throw SSLHandshakeException("certificate expired") }
                .build(),
            userAgent = "RoueLibre/test",
            ioDispatcher = Dispatchers.IO,
        )

        val outcome = refused.fetchManifest("https://example.invalid/manifest.json")

        assertTrue(outcome is Outcome.Failure)
        assertTrue(
            "expected UntrustedServer, got $outcome",
            (outcome as Outcome.Failure).error is DataError.UntrustedServer,
        )
    }

    @Test
    fun `refuses a file whose digest does not match`() = runTest {
        // SPEC §4.4 requires it: a file that does not match the manifest is
        // rejected, and the previous version kept.
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = "00".repeat(32)), workDirectory)

        assertTrue("expected a refusal, got: $outcome", outcome is Outcome.Failure)
        assertTrue("nothing must be left to install", workDirectory.listFiles().orEmpty().isEmpty())
        // The counterpart of the cut below, and the reason the two cannot share
        // one wording: here the whole file came down from a host that answered
        // perfectly, and what it contains is not what was announced. Calling
        // that a lost connection would send the reader to their Wi-Fi settings
        // for a file the producer published wrong.
        assertTrue(
            "expected MalformedResponse, got $outcome",
            (outcome as Outcome.Failure).error is DataError.MalformedResponse,
        )
    }

    @Test
    fun `a transfer cut in the middle is a lost connection, not an unreadable file`() = runTest {
        // The Wi-Fi dropping two seconds into a 44 MB download: the socket dies
        // halfway through the body, and OkHttp reports `unexpected end of
        // stream` — an error of the same family as a response that makes no
        // sense. Read as such, it told the user their map file was unreadable.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(okio.Buffer().write(content))
                .onResponseBody(SocketEffect.CloseSocket())
                .build(),
        )

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        assertTrue("expected a failure, got: $outcome", outcome is Outcome.Failure)
        assertEquals(DataError.Offline, (outcome as Outcome.Failure).error)
        // And the promise that goes with the wording: what arrived is kept, so
        // the download picks up where it stopped.
        assertTrue("nothing was kept of the transfer", partialFiles().single().length() > 0)
    }

    @Test
    fun `resumes an interrupted transfer where it stopped`() = runTest {
        // A cut after thirty megabytes must not force starting over: the next
        // request asks for the remainder.
        val alreadyReceived = 120_000
        File(workDirectory, "$FILE_NAME.partial").writeBytes(
            content.copyOfRange(0, alreadyReceived),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(okio.Buffer().write(content.copyOfRange(alreadyReceived, content.size)))
                .build(),
        )

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value
            ?: throw AssertionError("failure: $outcome")
        assertEquals(content.size.toLong(), files.first().length())
        assertEquals("bytes=$alreadyReceived-", server.takeRequest().headers["Range"])
    }

    @Test
    fun `a transfer given up stops where it is and starts again from there`() = runTest {
        // The whole promise of SPEC §4.4 when a connection starts billing in
        // the middle of a gigabyte: stop, keep what arrived, and ask for the
        // rest — never for the whole file again.
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())
        val scope = CoroutineScope(Dispatchers.IO)

        // Cancelled from inside the transfer, which is what a connection
        // turning billed does to it.
        scope.launch {
            downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory) {
                scope.cancel()
            }
        }.join()

        val partial = File(workDirectory, "$FILE_NAME.partial")
        val kept = partial.length()
        assertTrue("nothing was kept of the transfer", kept > 0)
        assertTrue("the transfer ran to its end anyway", kept < content.size)
        assertTrue("nothing must be installed", File(workDirectory, FILE_NAME).length() == 0L)

        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(okio.Buffer().write(content.copyOfRange(kept.toInt(), content.size)))
                .build(),
        )

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value
            ?: throw AssertionError("failure: $outcome")
        assertEquals(content.size.toLong(), files.first().length())
        server.takeRequest()
        assertEquals("bytes=$kept-", server.takeRequest().headers["Range"])
    }

    @Test
    fun `starts over if the server ignores the resumption`() = runTest {
        // A server answering 200 returns the whole file: appending it to what
        // we already had would produce a corrupted file.
        File(workDirectory, "$FILE_NAME.partial").writeBytes(content.copyOfRange(0, 120_000))
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value
            ?: throw AssertionError("failure: $outcome")
        assertEquals(content.size.toLong(), files.first().length())
    }

    @Test
    fun `a server that refuses returns an explicit error`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        assertTrue(outcome is Outcome.Failure)
    }

    @Test
    fun `reads the published manifest`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {"formatVersion":2,"releaseTag":"data-2026-08","network":"vlille",
                     "datasets":[{"id":"tiles","files":[
                       {"name":"tiles.mbtiles","url":"https://example.org/t","sizeBytes":10,
                        "sha256":"${"ab".repeat(32)}"}]}]}
                    """.trimIndent(),
                )
                .build(),
        )

        val outcome = downloader.fetchManifest(server.url("/manifest.json").toString())

        val manifest = (outcome as? Outcome.Success)?.value
            ?: throw AssertionError("failure: $outcome")
        assertEquals("data-2026-08", manifest.releaseTag)
        assertEquals(1, manifest.datasets.size)
    }

    @Test
    fun `a manifest cut in the middle is a lost connection`() = runTest {
        // The same cut as during a transfer, on the other gesture: pressing
        // "Check for updates" as the Wi-Fi drops. What the host publishes is
        // beyond reproach — the document below is the one the reader accepts —
        // so answering that its manifest is unreadable sends the reader after a
        // fault nobody made.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {"formatVersion":2,"releaseTag":"data-2026-08","network":"vlille",
                     "datasets":[{"id":"tiles","files":[
                       {"name":"tiles.mbtiles","url":"https://example.org/t","sizeBytes":10,
                        "sha256":"${"ab".repeat(32)}"}]}]}
                    """.trimIndent(),
                )
                .onResponseBody(SocketEffect.CloseSocket())
                .build(),
        )

        val outcome = downloader.fetchManifest(server.url("/manifest.json").toString())

        assertTrue("expected a failure, got: $outcome", outcome is Outcome.Failure)
        assertEquals(DataError.Offline, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `a manifest that will not parse stays an unreadable manifest`() = runTest {
        // The counterpart of the cut above, and the reason the two cannot share
        // one wording: the whole document came down from a host that answered
        // perfectly, and it is not a manifest. That one is the publisher's to
        // put right, not the reader's network.
        server.enqueue(MockResponse.Builder().code(200).body("""{"formatVersion":""").build())

        val outcome = downloader.fetchManifest(server.url("/manifest.json").toString())

        assertTrue("expected a failure, got: $outcome", outcome is Outcome.Failure)
        assertTrue(
            "expected MalformedResponse, got $outcome",
            (outcome as Outcome.Failure).error is DataError.MalformedResponse,
        )
    }

    private fun datasetOf(sha256: String) = ManifestDataset(
        kind = DatasetKind.Tiles,
        description = "Fond de carte",
        files = listOf(
            ManifestFile(
                name = FILE_NAME,
                url = server.url("/$FILE_NAME").toString(),
                sizeBytes = content.size.toLong(),
                sha256 = sha256,
            ),
        ),
    )

    private fun partialFiles() =
        workDirectory.listFiles().orEmpty().filter { it.name.endsWith(".partial") }

    private fun sha256Of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE_NAME = "tiles.mbtiles"
    }
}
