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
 * Ce qui se passe pendant un téléchargement, pour que l'écran le montre.
 *
 * @property fileName le fichier en cours.
 * @property downloadedBytes ce qui est déjà sur l'appareil, reprise comprise.
 * @property totalBytes la taille annoncée par le manifeste.
 */
data class DownloadProgress(val fileName: String, val downloadedBytes: Long, val totalBytes: Long)

/**
 * Télécharge les jeux de données publiés (SPEC §4.4).
 *
 * Trois exigences gouvernent ce fichier.
 *
 * **La reprise.** Un jeu pèse des dizaines de mégaoctets ; une coupure de
 * réseau au bout de trente ne doit pas obliger à tout reprendre. Le fichier
 * partiel est conservé et la requête suivante demande la suite par un en-tête
 * `Range`.
 *
 * **La vérification.** L'empreinte annoncée par le manifeste est recalculée
 * sur ce qui a été reçu. Un fichier qui ne correspond pas est rejeté, et
 * l'installation précédente reste intacte.
 *
 * **Rien d'automatique.** Cette classe n'est appelée que sur action explicite
 * de l'utilisateur. Une vérification périodique dessinerait un profil d'usage,
 * ce que la contrainte C3 exclut.
 *
 * @property client le client HTTP partagé de l'application.
 * @property userAgent identifie l'application et sa version, sans aucun
 *   identifiant propre à l'utilisateur ou à l'appareil.
 * @property ioDispatcher contexte d'exécution : ces transferts portent sur
 *   des dizaines de mégaoctets.
 */
class DatasetDownloader(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Récupère et lit le manifeste de publication.
     *
     * @param url adresse du manifeste, celle de la configuration de ville ou
     *   celle qu'a choisie l'utilisateur.
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
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "manifeste illisible"))
    } catch (error: IllegalArgumentException) {
        // Une URL inexploitable, saisie dans les réglages.
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "adresse invalide"))
    }

    /**
     * Télécharge les fichiers d'un jeu dans un répertoire de travail.
     *
     * Rien n'est installé ici : les fichiers reçus et vérifiés sont laissés à
     * l'appelant, qui les met en place. Le SPEC §4.4 impose d'écrire à côté,
     * de valider, puis de remplacer — une mise à jour interrompue ne doit
     * jamais laisser l'application inutilisable.
     *
     * @param dataset le jeu à prendre.
     * @param workDirectory où déposer les fichiers reçus.
     * @param onProgress appelé au fil du transfert, sur le fil d'appel.
     * @return les fichiers reçus et vérifiés, ou la raison de l'échec.
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
        val partial = File(workDirectory, "${file.name}$PARTIAL_SUFFIX")
        val alreadyReceived = if (partial.isFile) partial.length() else 0L

        // Un fichier partiel plus gros que ce qui est annoncé n'est pas une
        // reprise possible : c'est le reste d'un autre téléchargement.
        if (alreadyReceived > file.sizeBytes && file.sizeBytes > 0) {
            partial.delete()
        }

        val outcome = fetchInto(file, partial, onProgress)
        if (outcome is Outcome.Failure) return outcome

        val digest = sha256Of(partial)
        if (file.sha256.isNotEmpty() && !digest.equals(file.sha256, ignoreCase = true)) {
            // Le fichier reçu n'est pas celui annoncé : le garder pour une
            // reprise ne ferait que répéter l'erreur.
            partial.delete()
            return Outcome.Failure(
                DataError.MalformedResponse(
                    "empreinte inattendue pour ${file.name} : $digest",
                ),
            )
        }

        val complete = File(workDirectory, file.name)
        complete.delete()
        if (!partial.renameTo(complete)) {
            return Outcome.Failure(
                DataError.LocalStorageFailure("impossible de ranger ${file.name}"),
            )
        }
        return Outcome.Success(complete)
    }

    /**
     * Reçoit le fichier, en reprenant là où un transfert précédent s'est
     * arrêté.
     *
     * Un serveur qui ignore l'en-tête `Range` répond 200 avec le fichier
     * entier ; il faut alors repartir de zéro plutôt que d'ajouter le début à
     * la suite de ce qu'on avait — ce qui produirait un fichier corrompu que
     * seule l'empreinte rattraperait.
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
        // Interruption volontaire ou expiration : le fichier partiel reste,
        // c'est précisément ce qui permettra de reprendre.
        Outcome.Failure(DataError.Timeout)
    } catch (_: UnknownHostException) {
        Outcome.Failure(DataError.Offline)
    } catch (error: IOException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "transfert interrompu"))
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
        const val PARTIAL_SUFFIX = ".partiel"
        const val COPY_BUFFER_BYTES = 1 shl 16
        const val HTTP_PARTIAL_CONTENT = 206
    }
}
