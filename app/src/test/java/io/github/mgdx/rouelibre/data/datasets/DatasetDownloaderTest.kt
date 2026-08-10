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
 * Tests du téléchargement des jeux de données (SPEC §4.4).
 *
 * Un vrai serveur HTTP local plutôt qu'une source simulée : ce qui compte ici
 * — la reprise par en-tête `Range`, le refus d'un fichier dont l'empreinte ne
 * correspond pas — se joue précisément dans les échanges HTTP, qu'une source
 * simulée ne reproduirait pas.
 */
class DatasetDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: DatasetDownloader
    private lateinit var workDirectory: File

    /** Le contenu servi, assez gros pour occuper plusieurs tampons. */
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
    fun `télécharge un jeu et vérifie son empreinte`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value ?: throw AssertionError("échec : $outcome")
        assertEquals(1, files.size)
        assertEquals(content.size.toLong(), files.first().length())
        assertTrue("le fichier partiel doit avoir disparu", partialFiles().isEmpty())
    }

    @Test
    fun `refuse un fichier dont l'empreinte ne correspond pas`() = runTest {
        // Le SPEC §4.4 l'exige : un fichier qui ne correspond pas au manifeste
        // est rejeté, et l'ancienne version conservée.
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = "00".repeat(32)), workDirectory)

        assertTrue("refus attendu, obtenu : $outcome", outcome is Outcome.Failure)
        assertTrue("rien ne doit rester à installer", workDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `reprend un transfert interrompu là où il s'est arrêté`() = runTest {
        // Une coupure au bout de trente mégaoctets ne doit pas obliger à tout
        // reprendre : la requête suivante demande la suite.
        val alreadyReceived = 120_000
        File(workDirectory, "$FILE_NAME.partiel").writeBytes(
            content.copyOfRange(0, alreadyReceived),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(okio.Buffer().write(content.copyOfRange(alreadyReceived, content.size)))
                .build(),
        )

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value ?: throw AssertionError("échec : $outcome")
        assertEquals(content.size.toLong(), files.first().length())
        assertEquals("bytes=$alreadyReceived-", server.takeRequest().headers["Range"])
    }

    @Test
    fun `repart de zéro si le serveur ignore la reprise`() = runTest {
        // Un serveur qui répond 200 renvoie le fichier entier : l'ajouter à la
        // suite de ce qu'on avait produirait un fichier corrompu.
        File(workDirectory, "$FILE_NAME.partiel").writeBytes(content.copyOfRange(0, 120_000))
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(content)).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        val files = (outcome as? Outcome.Success)?.value ?: throw AssertionError("échec : $outcome")
        assertEquals(content.size.toLong(), files.first().length())
    }

    @Test
    fun `un serveur qui refuse rend une erreur explicite`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        val outcome = downloader.download(datasetOf(sha256 = sha256Of(content)), workDirectory)

        assertTrue(outcome is Outcome.Failure)
    }

    @Test
    fun `lit le manifeste publié`() = runTest {
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
            ?: throw AssertionError("échec : $outcome")
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
        workDirectory.listFiles().orEmpty().filter { it.name.endsWith(".partiel") }

    private fun sha256Of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE_NAME = "tiles.mbtiles"
    }
}
