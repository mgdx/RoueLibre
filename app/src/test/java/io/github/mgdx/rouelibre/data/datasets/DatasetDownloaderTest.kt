package io.github.mgdx.rouelibre.data.datasets

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.ManifestFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

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
    fun `refuses a file whose digest does not match`() = runTest {
        // SPEC §4.4 requires it: a file that does not match the manifest is
        // rejected, and the previous version kept.
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = "00".repeat(32)), workDirectory)

        assertTrue("expected a refusal, got: $outcome", outcome is Outcome.Failure)
        assertTrue("nothing must be left to install", workDirectory.listFiles().orEmpty().isEmpty())
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
                        "sha256":"ab"}]}]}
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
