package io.github.mgdx.rouelibre.data.datasets

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DataManifestReader
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.ManifestFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * What is happening during a download, so the screen can show it.
 *
 * @property fileName the file in progress.
 * @property downloadedBytes what is already on the device, resumption included.
 * @property totalBytes the size announced by the manifest.
 */
data class DownloadProgress(val fileName: String, val downloadedBytes: Long, val totalBytes: Long)

/**
 * Downloads the published datasets (SPEC §4.4).
 *
 * Three requirements govern this file.
 *
 * **Resumption.** A dataset weighs tens of megabytes; a network cut after
 * thirty of them must not force starting over. The partial file is kept and the
 * next request asks for the remainder through a `Range` header.
 *
 * **Verification.** The digest announced by the manifest is recomputed over
 * what was received. A file that does not match is rejected, and the previous
 * installation stays untouched. It says what the file **is**; it says nothing
 * about where it lands — see [fileInside], which answers that other question.
 *
 * **Nothing automatic.** This class is only called on an explicit user action.
 * A periodic check would draw a usage profile, which constraint C3 rules out.
 *
 * @property client the application's shared HTTP client.
 * @property userAgent identifies the application and its version, with no
 *   identifier specific to the user or the device.
 * @property ioDispatcher the execution context: these transfers carry tens of
 *   megabytes.
 */
class DatasetDownloader(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Fetches and reads the release manifest.
     *
     * @param url the manifest's address, either the city configuration's or the
     *   one the user chose.
     */
    suspend fun fetchManifest(url: String): Outcome<DataManifest> = withContext(ioDispatcher) {
        when (val body = get(url)) {
            is Outcome.Failure -> body
            is Outcome.Success -> DataManifestReader.read(body.value)
        }
    }

    private fun get(url: String): Outcome<String> = try {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Outcome.Failure(DataError.ServerRefused(response.code))
            } else {
                Outcome.Success(response.body.string())
            }
        }
    } catch (error: SocketTimeoutException) {
        Outcome.Failure(DataError.Timeout)
    } catch (_: UnknownHostException) {
        Outcome.Failure(DataError.Offline)
    } catch (error: IOException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "unreadable manifest"))
    } catch (error: IllegalArgumentException) {
        // An unusable URL, typed into the settings.
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "invalid address"))
    }

    /**
     * Downloads a dataset's files into a working directory.
     *
     * Nothing is installed here: the files received and verified are left to
     * the caller, who puts them in place. SPEC §4.4 requires writing beside,
     * validating, then replacing — an interrupted update must never leave the
     * application unusable.
     *
     * @param dataset the set to fetch.
     * @param workDirectory where to drop the files received.
     * @param onProgress called as the transfer goes, on the calling thread.
     * @return the files received and verified, or the reason for the failure.
     */
    suspend fun download(
        dataset: ManifestDataset,
        workDirectory: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ): Outcome<List<File>> = withContext(ioDispatcher) {
        workDirectory.mkdirs()
        val received = mutableListOf<File>()
        for (file in dataset.files) {
            coroutineContext.ensureActive()
            when (val outcome = downloadFile(file, workDirectory, onProgress)) {
                is Outcome.Failure -> return@withContext outcome
                is Outcome.Success -> received.add(outcome.value)
            }
        }
        Outcome.Success(received)
    }

    private fun downloadFile(
        file: ManifestFile,
        workDirectory: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome<File> {
        val complete = fileInside(workDirectory, file.name)
            ?: return Outcome.Failure(
                DataError.MalformedResponse("unusable file name: ${file.name}"),
            )
        val partial = File(complete.path + PARTIAL_SUFFIX)
        val alreadyReceived = if (partial.isFile) partial.length() else 0L

        // A partial file larger than what is announced is not a resumable one:
        // it is the leftover of another download.
        if (alreadyReceived > file.sizeBytes && file.sizeBytes > 0) {
            partial.delete()
        }

        val outcome = fetchInto(file, partial, onProgress)
        if (outcome is Outcome.Failure) return outcome

        val digest = sha256Of(partial)
        if (file.sha256.isNotEmpty() && !digest.equals(file.sha256, ignoreCase = true)) {
            // The file received is not the one announced: keeping it for a
            // resumption would only repeat the mistake.
            partial.delete()
            return Outcome.Failure(
                DataError.MalformedResponse(
                    "unexpected digest for ${file.name}: $digest",
                ),
            )
        }

        complete.delete()
        if (!partial.renameTo(complete)) {
            return Outcome.Failure(
                DataError.LocalStorageFailure("cannot put ${file.name} into place"),
            )
        }
        return Outcome.Success(complete)
    }

    /**
     * Receives the file, resuming where a previous transfer stopped.
     *
     * A server that ignores the `Range` header answers 200 with the whole file;
     * we must then start from scratch rather than append the beginning to what
     * we already had — which would produce a corrupted file that only the
     * digest would catch.
     */
    private fun fetchInto(
        file: ManifestFile,
        partial: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome<Unit> = try {
        val alreadyReceived = if (partial.isFile) partial.length() else 0L
        val request = Request.Builder()
            .url(file.url)
            .header("User-Agent", userAgent)
            .apply { if (alreadyReceived > 0) header("Range", "bytes=$alreadyReceived-") }
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_PARTIAL_CONTENT -> Unit
                response.isSuccessful -> partial.delete()
                else -> return Outcome.Failure(DataError.ServerRefused(response.code))
            }
            val resuming = response.code == HTTP_PARTIAL_CONTENT
            var written = if (resuming) alreadyReceived else 0L

            response.body.byteStream().use { source ->
                java.io.FileOutputStream(partial, resuming).use { sink ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress(DownloadProgress(file.name, written, file.sizeBytes))
                    }
                }
            }
        }
        Outcome.Success(Unit)
    } catch (_: InterruptedIOException) {
        // A deliberate interruption or an expiry: the partial file stays, which
        // is precisely what will allow resuming.
        Outcome.Failure(DataError.Timeout)
    } catch (_: UnknownHostException) {
        Outcome.Failure(DataError.Offline)
    } catch (error: IOException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "transfer interrupted"))
    }

    /**
     * The file [name] designates inside [directory], or `null` if it would land
     * anywhere else.
     *
     * [ManifestFile] already refuses a name that is not a plain file name, and
     * that refusal is the real barrier: a manifest naming such a file is
     * rejected whole, before a single request goes out. This second check states
     * the same guarantee where it actually has to hold — **nothing is written
     * outside the working directory** — so that it survives a caller that one
     * day hands over a name from somewhere else.
     *
     * The comparison is on canonical paths: only the file system knows what a
     * path resolves to, symbolic links included.
     */
    private fun fileInside(directory: File, name: String): File? {
        val target = File(directory, name)
        return try {
            val root = directory.canonicalPath + File.separator
            target.takeIf { it.canonicalPath.startsWith(root) }
        } catch (_: IOException) {
            null
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /**
         * Suffix of a download in progress.
         *
         * It names a file in the cache directory, never anything the user
         * reads: it follows the code into English rather than staying with the
         * interface.
         */
        const val PARTIAL_SUFFIX = ".partial"
        const val COPY_BUFFER_BYTES = 1 shl 16
        const val HTTP_PARTIAL_CONTENT = 206
    }
}
