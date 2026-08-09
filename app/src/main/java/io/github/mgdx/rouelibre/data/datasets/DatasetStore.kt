package io.github.mgdx.rouelibre.data.datasets

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetRejection
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * Range, valide et supprime les jeux de données hors ligne (SPEC.md §4.4).
 *
 * Deux règles gouvernent tout ce fichier.
 *
 * **Une installation ne casse jamais l'existante.** Le fichier entrant est
 * écrit à côté, validé, et seulement ensuite mis à la place de l'ancien. Une
 * importation interrompue, un fichier tronqué ou un mauvais fichier laissent
 * l'application exactement dans l'état où elle était.
 *
 * **Un fichier refusé dit pourquoi.** Un jeu de données pèse des dizaines de
 * mégaoctets ; échouer sans expliquer laisserait l'utilisateur relancer la
 * même importation indéfiniment.
 *
 * @property context accès au stockage privé de l'application.
 * @property ioDispatcher contexte d'exécution des copies, qui portent sur
 *   plusieurs dizaines de mégaoctets.
 */
class DatasetStore(private val context: Context, private val ioDispatcher: CoroutineDispatcher) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /** Le répertoire d'un jeu de données, créé au besoin. */
    fun directoryOf(kind: DatasetKind): File = File(directory, kind.id).apply { mkdirs() }

    private val indexFile: File
        get() = File(directory, INDEX_FILE_NAME)

    private val mutableInstalled = MutableStateFlow(readIndex())

    /** Les jeux présents sur l'appareil, réémis à chaque changement. */
    val installed: StateFlow<Map<DatasetKind, InstalledDataset>> = mutableInstalled.asStateFlow()

    /**
     * Le fichier d'un jeu installé, ou `null` s'il ne l'est pas.
     *
     * Rendre le fichier plutôt que son contenu : MapLibre comme SQLite
     * ouvrent le leur eux-mêmes, et recopier trente-cinq mégaoctets en mémoire
     * n'aurait aucun sens.
     */
    fun fileOf(kind: DatasetKind): File? {
        val name = kind.fileName ?: return null
        return File(directoryOf(kind), name).takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Installe un fichier choisi par l'utilisateur.
     *
     * L'import manuel n'est pas un mode dégradé : le SPEC §4.4 en fait une
     * obligation, pour que quelqu'un qui génère ses propres fichiers puisse se
     * servir de l'application sans qu'elle n'émette la moindre requête.
     *
     * @param kind le jeu que ce fichier est censé être.
     * @param source document choisi dans le sélecteur du système.
     * @param expectedSha256 empreinte annoncée par un manifeste, si l'on en a
     *   un. À l'import manuel il n'y en a généralement pas : la validation se
     *   fait alors sur la structure du fichier et sa version de format.
     * @return le jeu installé, ou la raison du refus.
     */
    suspend fun importFrom(
        kind: DatasetKind,
        source: Uri,
        expectedSha256: String? = null,
    ): DatasetImportResult = withContext(ioDispatcher) {
        val targetName = kind.fileName ?: displayNameOf(source)
            ?: return@withContext DatasetImportResult.Rejected(
                DatasetRejection.TransferFailed("nom du fichier introuvable"),
            )
        val staged = File(directoryOf(kind), "$targetName$STAGING_SUFFIX")
        try {
            val digest = try {
                context.contentResolver.openInputStream(source)?.use { stream ->
                    copyAndDigest(stream, staged)
                } ?: return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed("fichier illisible"),
                )
            } catch (error: IOException) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed(error.message ?: "copie interrompue"),
                )
            } catch (error: SecurityException) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed(error.message ?: "accès refusé"),
                )
            }

            if (staged.length() == 0L) {
                return@withContext rejected(staged, DatasetRejection.Empty)
            }
            if (expectedSha256 != null && !digest.equals(expectedSha256, ignoreCase = true)) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.ChecksumMismatch(expectedSha256, digest),
                )
            }

            val inspected = try {
                inspect(kind, staged)
            } catch (error: RuntimeException) {
                // Dernier filet : une bibliothèque de lecture qui lèverait un
                // type inattendu ne doit pas faire tomber l'application au
                // milieu d'une importation.
                Inspection.Invalid(
                    DatasetRejection.WrongFormat(
                        error.message?.take(MAX_REJECTION_DETAIL) ?: "fichier illisible",
                    ),
                )
            }
            val formatVersion = when (val inspection = inspected) {
                is Inspection.Invalid -> return@withContext rejected(staged, inspection.reason)
                is Inspection.Valid -> inspection.formatVersion
            }

            // Le remplacement n'a lieu qu'ici, une fois tout vérifié.
            val target = File(directoryOf(kind), targetName)
            target.delete()
            if (!staged.renameTo(target)) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed("remplacement impossible"),
                )
            }

            val record = InstalledDataset(
                kind = kind,
                sizeBytes = directoryOf(kind).walkTopDown().filter { it.isFile }
                    .sumOf { it.length() },
                sha256 = digest,
                installedAt = Instant.now(),
                formatVersion = formatVersion,
            )
            writeIndex(mutableInstalled.value + (kind to record))
            DatasetImportResult.Installed(record)
        } finally {
            staged.delete()
        }
    }

    /** Supprime un jeu installé. L'utilisateur doit pouvoir reprendre la place. */
    suspend fun delete(kind: DatasetKind): Unit = withContext(ioDispatcher) {
        directoryOf(kind).deleteRecursively()
        writeIndex(mutableInstalled.value - kind)
    }

    /**
     * Nom du document choisi dans le sélecteur.
     *
     * Nécessaire pour les jeux dont les fichiers gardent leur nom d'origine :
     * le graphe de routage, dont BRouter déduit le nom des coordonnées.
     */
    private fun displayNameOf(source: Uri): String? =
        context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /** Somme occupée par les jeux installés, affichée dans l'écran stockage. */
    fun occupiedBytes(): Long = mutableInstalled.value.values.sumOf { it.sizeBytes }

    /**
     * Copie le flux vers [destination] en calculant son empreinte au passage.
     *
     * En une seule lecture : relire trente-cinq mégaoctets pour les hacher
     * doublerait le temps d'attente sans rien apporter.
     */
    private fun copyAndDigest(source: InputStream, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        destination.outputStream().use { sink ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                sink.write(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Vérifie qu'un fichier est bien ce qu'il prétend être.
     *
     * Sans manifeste, c'est la seule protection contre l'erreur la plus
     * probable de l'import manuel : désigner le mauvais fichier. Un fond de
     * carte importé comme index d'adresses ferait échouer la première
     * recherche, longtemps après, sans rapport visible avec la cause.
     *
     * @return la version de format lue dans le fichier, ou `null` s'il n'en
     *   porte pas.
     */
    private fun inspect(kind: DatasetKind, file: File): Inspection = when (kind) {
        DatasetKind.Tiles -> inspectTiles(file)
        DatasetKind.Addresses -> inspectAddresses(file)
        DatasetKind.Routing -> inspectRouting(file)
    }

    private fun inspectTiles(file: File): Inspection = readingSqlite(file) { database ->
        val format = database.rawQuery(
            "SELECT value FROM metadata WHERE name = ?",
            arrayOf("format"),
        ).use { if (it.moveToFirst()) it.getString(0) else null }

        if (format != EXPECTED_TILE_FORMAT) {
            return@readingSqlite Inspection.Invalid(
                DatasetRejection.WrongFormat(
                    "tuiles au format « ${format ?: "inconnu"} » au lieu de " +
                        "« $EXPECTED_TILE_FORMAT »",
                ),
            )
        }
        val tileCount = database.rawQuery("SELECT COUNT(*) FROM tiles", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (tileCount == 0L) {
            return@readingSqlite Inspection.Invalid(
                DatasetRejection.WrongFormat("le fichier ne contient aucune tuile"),
            )
        }
        Inspection.Valid(null)
    }

    private fun inspectAddresses(file: File): Inspection = readingSqlite(file) { database ->
        val version = database.rawQuery(
            "SELECT value FROM metadata WHERE key = ?",
            arrayOf("formatVersion"),
        ).use { if (it.moveToFirst()) it.getString(0)?.toIntOrNull() else null }
            ?: return@readingSqlite Inspection.Invalid(
                DatasetRejection.WrongFormat("index d'adresses sans version de format"),
            )

        if (version != SUPPORTED_ADDRESS_FORMAT_VERSION) {
            return@readingSqlite Inspection.Invalid(
                DatasetRejection.UnsupportedFormatVersion(
                    found = version,
                    supported = SUPPORTED_ADDRESS_FORMAT_VERSION,
                ),
            )
        }
        val streetCount = database.rawQuery("SELECT COUNT(*) FROM street", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (streetCount == 0L) {
            return@readingSqlite Inspection.Invalid(
                DatasetRejection.WrongFormat("l'index ne contient aucune voie"),
            )
        }
        Inspection.Valid(version)
    }

    /**
     * Le graphe de routage est un format binaire propre à BRouter, sans
     * en-tête reconnaissable à peu de frais. On se contente donc d'écarter
     * l'erreur la plus fréquente : avoir désigné l'un des deux autres jeux,
     * qui sont des bases SQLite.
     */
    private fun inspectRouting(file: File): Inspection {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = try {
            file.inputStream().use { it.read(header) }
        } catch (error: IOException) {
            return Inspection.Invalid(
                DatasetRejection.TransferFailed(error.message ?: "lecture impossible"),
            )
        }
        if (read == SQLITE_MAGIC.size && header.contentEquals(SQLITE_MAGIC)) {
            return Inspection.Invalid(
                DatasetRejection.WrongFormat(
                    "ce fichier est une base SQLite, pas un graphe de routage",
                ),
            )
        }
        return Inspection.Valid(null)
    }

    /**
     * Interroge un fichier comme une base SQLite, sans jamais lever.
     *
     * L'ouverture **et** les requêtes sont couvertes par le même filet. Ce
     * n'est pas de la prudence excessive : `openDatabase` réussit sur un
     * fichier qui n'est pas une base du tout, parce qu'elle n'en lit pas
     * l'en-tête, et c'est la première requête qui lève un
     * `SQLiteDatabaseCorruptException`. Un utilisateur désignant le graphe de
     * routage à la place du fond de carte faisait ainsi planter l'application.
     *
     * Le gestionnaire d'erreurs est neutralisé : celui d'Android **supprime**
     * le fichier qu'il juge corrompu. Sur un fichier en cours de validation
     * c'est sans conséquence, mais on ne veut pas de cette mécanique à portée
     * d'un jeu de données déjà installé.
     */
    private inline fun readingSqlite(
        file: File,
        block: (SQLiteDatabase) -> Inspection,
    ): Inspection = try {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            { /* ne rien supprimer : le fichier ne nous appartient pas encore */ },
        ).use { block(it) }
    } catch (error: RuntimeException) {
        // SQLiteException et ses sous-classes ne sont pas vérifiées ; elles
        // couvrent aussi bien un fichier corrompu qu'une table absente.
        Inspection.Invalid(
            DatasetRejection.WrongFormat(
                error.message?.take(MAX_REJECTION_DETAIL) ?: "fichier illisible",
            ),
        )
    }

    private fun rejected(staged: File, reason: DatasetRejection): DatasetImportResult {
        staged.delete()
        return DatasetImportResult.Rejected(reason)
    }

    /** Ce qu'une inspection de fichier apprend, avant toute mise en place. */
    private sealed interface Inspection {
        /** Le fichier est bien celui attendu ; il porte éventuellement une version. */
        data class Valid(val formatVersion: Int?) : Inspection

        /** Le fichier n'est pas exploitable, pour la raison donnée. */
        data class Invalid(val reason: DatasetRejection) : Inspection
    }

    // ------------------------------------------------------------- index --

    private fun readIndex(): Map<DatasetKind, InstalledDataset> {
        val file = indexFile
        if (!file.isFile) return emptyMap()
        return try {
            json.decodeFromString(IndexDocument.serializer(), file.readText())
                .entries
                .mapNotNull { it.toDomain() }
                // Un fichier disparu — vidage du cache, restauration partielle
                // — ne doit pas laisser une entrée fantôme dans l'écran.
                .filter { directoryOf(it.kind).listFiles()?.isNotEmpty() == true }
                .associateBy { it.kind }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeIndex(records: Map<DatasetKind, InstalledDataset>) {
        indexFile.writeText(
            json.encodeToString(
                IndexDocument.serializer(),
                IndexDocument(records.values.map(IndexEntry::fromDomain)),
            ),
        )
        mutableInstalled.value = records
    }

    private companion object {
        const val DIRECTORY_NAME = "datasets"
        const val INDEX_FILE_NAME = "installed.json"
        const val STAGING_SUFFIX = ".incoming"
        const val COPY_BUFFER_BYTES = 1 shl 16
        const val EXPECTED_TILE_FORMAT = "pbf"

        /** Longueur au-delà de laquelle un détail technique devient du bruit. */
        const val MAX_REJECTION_DETAIL = 200

        /**
         * Version de l'index d'adresses que cette version sait lire.
         *
         * Passée à 2 quand l'index s'est mis à porter le nom des communes
         * absorbées : l'application interroge cette colonne, un index plus
         * ancien n'est donc pas lisible et doit être refusé en le disant.
         */
        const val SUPPORTED_ADDRESS_FORMAT_VERSION = 2

        /** Les seize premiers octets de tout fichier SQLite. */
        val SQLITE_MAGIC: ByteArray = "SQLite format 3 ".toByteArray(Charsets.US_ASCII)
    }
}

@Serializable
private data class IndexDocument(val entries: List<IndexEntry> = emptyList())

@Serializable
private data class IndexEntry(
    val kind: String,
    val sizeBytes: Long,
    val sha256: String,
    val installedAtEpochSeconds: Long,
    val formatVersion: Int? = null,
) {
    fun toDomain(): InstalledDataset? {
        val resolved = DatasetKind.fromId(kind) ?: return null
        return InstalledDataset(
            kind = resolved,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            installedAt = Instant.ofEpochSecond(installedAtEpochSeconds),
            formatVersion = formatVersion,
        )
    }

    companion object {
        fun fromDomain(dataset: InstalledDataset) = IndexEntry(
            kind = dataset.kind.id,
            sizeBytes = dataset.sizeBytes,
            sha256 = dataset.sha256,
            installedAtEpochSeconds = dataset.installedAt.epochSecond,
            formatVersion = dataset.formatVersion,
        )
    }
}
