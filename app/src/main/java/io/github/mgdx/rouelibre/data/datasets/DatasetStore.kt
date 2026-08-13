package io.github.mgdx.rouelibre.data.datasets

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mgdx.rouelibre.core.config.isUsableCityId
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
 * Stores, validates and deletes the offline datasets (SPEC.md §4.4).
 *
 * Two rules govern this whole file.
 *
 * **An installation never breaks the existing one.** The incoming file is
 * written beside the old one, validated, and only then put in its place. An
 * interrupted import, a truncated file or the wrong file leave the application
 * exactly as it was.
 *
 * **A refused file says why.** A dataset weighs tens of megabytes; failing
 * without explaining would leave the user retrying the same import for ever.
 *
 * @property context access to the application's private storage.
 * @property ioDispatcher the execution context for the copies, which carry
 *   several tens of megabytes.
 */
class DatasetStore(private val context: Context, private val ioDispatcher: CoroutineDispatcher) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * The city whose datasets are in service.
     *
     * `null` as long as none is chosen: there is then neither a file to read
     * nor a directory to write into. `@Volatile` because the read comes from
     * the main thread and the change from the IO dispatcher.
     */
    @Volatile
    private var cityId: String? = null

    private val root: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /**
     * The directory of the city in service.
     *
     * Every city has its own: keeping several cities' data side by side avoids
     * downloading everything again on each trip between two, and it is what
     * makes deleting a single one possible (SPEC §11.9).
     */
    private val directory: File?
        get() = cityId?.let { File(root, it).apply { mkdirs() } }

    /** A dataset's directory, created as needed, or `null` without a city. */
    fun directoryOf(kind: DatasetKind): File? =
        directory?.let { File(it, kind.id).apply { mkdirs() } }

    private val indexFile: File?
        get() = directory?.let { File(it, INDEX_FILE_NAME) }

    private val mutableInstalled = MutableStateFlow(readIndex())

    /**
     * Puts the data of city [id] into service, or none if `null`.
     *
     * It re-reads the inventory in the same breath: the screens observing it
     * therefore switch to the new city without having to bother.
     *
     * An identifier that could not name a directory serves none. The reader of
     * the catalogue drops such an entry well before this point, so nothing in
     * the interface can offer one; what arrives here is what a **previous**
     * version wrote into the settings, and it must not be acted on.
     */
    fun useCity(id: String?) {
        val served = id?.takeIf(::isUsableCityId)
        if (served == cityId) return
        cityId = served
        mutableInstalled.value = readIndex()
    }

    /**
     * The weight of a city's data, including a city not in service: it is what
     * the storage screen announces before offering to delete it.
     */
    fun occupiedBytesOf(id: String): Long = directoryOfCity(id)
        ?.walkTopDown()
        ?.filter { it.isFile }
        ?.sumOf { it.length() }
        ?: 0L

    /**
     * Deletes all of a city's data (SPEC §11.9).
     *
     * Including that of the city in service: somebody who moves house must be
     * able to reclaim the space without having to pick another city first.
     */
    suspend fun deleteCity(id: String): Unit = withContext(ioDispatcher) {
        directoryOfCity(id)?.deleteRecursively()
        if (id == cityId) mutableInstalled.value = emptyMap()
    }

    /**
     * The directory of the city [id], or `null` if that is not an identifier.
     *
     * The single door to a city's storage, so that no caller can walk in with a
     * name that designates something else. `deleteCity` recursively deletes what
     * this returns: it is the one place where the question has to be settled.
     */
    private fun directoryOfCity(id: String): File? =
        if (isUsableCityId(id)) File(root, id) else null

    /** The sets present on the device, re-emitted on every change. */
    val installed: StateFlow<Map<DatasetKind, InstalledDataset>> = mutableInstalled.asStateFlow()

    /**
     * The file of an installed set, or `null` if it is not installed.
     *
     * Returning the file rather than its contents: MapLibre and SQLite both
     * open theirs themselves, and copying thirty-five megabytes into memory
     * would make no sense.
     */
    fun fileOf(kind: DatasetKind): File? {
        val name = kind.fileName ?: return null
        val directory = directoryOf(kind) ?: return null
        return File(directory, name).takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Installs a file chosen by the user.
     *
     * Manual import is not a degraded mode: SPEC §4.4 makes it an obligation,
     * so that somebody who generates their own files can use the application
     * without it issuing a single request.
     *
     * @param kind the set this file is supposed to be.
     * @param source the document picked from the system chooser.
     * @param expectedSha256 the digest announced by a manifest, if we have one.
     *   On a manual import there usually is none: validation then rests on the
     *   file's structure and its format version.
     * @return the set installed, or the reason for the refusal.
     */
    suspend fun importFrom(
        kind: DatasetKind,
        source: Uri,
        expectedSha256: String? = null,
    ): DatasetImportResult = withContext(ioDispatcher) {
        val destination = directoryOf(kind)
            ?: return@withContext DatasetImportResult.Rejected(NO_CITY_REJECTION)
        val targetName = kind.fileName ?: displayNameOf(source)
            ?: return@withContext DatasetImportResult.Rejected(
                DatasetRejection.TransferFailed("file name not found"),
            )
        val staged = File(destination, "$targetName$STAGING_SUFFIX")
        try {
            val digest = try {
                context.contentResolver.openInputStream(source)?.use { stream ->
                    copyAndDigest(stream, staged)
                } ?: return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed("unreadable file"),
                )
            } catch (error: IOException) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed(error.message ?: "copy interrupted"),
                )
            } catch (error: SecurityException) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed(error.message ?: "access denied"),
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
                // Last net: a reading library throwing an unexpected type must
                // not bring the application down in the middle of an import.
                Inspection.Invalid(
                    DatasetRejection.WrongFormat(
                        error.message?.take(MAX_REJECTION_DETAIL) ?: "unreadable file",
                    ),
                )
            }
            val formatVersion = when (val inspection = inspected) {
                is Inspection.Invalid -> return@withContext rejected(staged, inspection.reason)
                is Inspection.Valid -> inspection.formatVersion
            }

            // The replacement happens only here, once everything is checked.
            val target = File(destination, targetName)
            target.delete()
            if (!staged.renameTo(target)) {
                return@withContext rejected(
                    staged,
                    DatasetRejection.TransferFailed("replacement impossible"),
                )
            }

            val record = InstalledDataset(
                kind = kind,
                sizeBytes = destination.walkTopDown().filter { it.isFile }
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

    /**
     * Puts already downloaded and verified files into place (SPEC §4.4).
     *
     * The order is the one the specification imposes: the files are checked
     * first, and the old version is only removed afterwards. An interrupted or
     * corrupted update must never leave the application unusable.
     *
     * @param kind the set concerned.
     * @param files the files received, whose digests [DatasetDownloader] has
     *   already put against the manifest.
     * @param fingerprint the digest of the set as a whole, as the manifest
     *   describes it. It is what will be compared against the next release to
     *   decide whether re-downloading is called for.
     * @return the set installed, or the reason for the refusal.
     */
    suspend fun install(
        kind: DatasetKind,
        files: List<File>,
        fingerprint: String,
    ): DatasetImportResult = withContext(ioDispatcher) {
        if (files.isEmpty()) {
            return@withContext DatasetImportResult.Rejected(DatasetRejection.Empty)
        }
        val destination = directoryOf(kind)
            ?: return@withContext DatasetImportResult.Rejected(NO_CITY_REJECTION)

        // The check bears on the files received, before anything at all is
        // replaced.
        var formatVersion: Int? = null
        for (file in files) {
            val inspected = try {
                inspect(kind, file)
            } catch (error: RuntimeException) {
                Inspection.Invalid(
                    DatasetRejection.WrongFormat(
                        error.message?.take(MAX_REJECTION_DETAIL) ?: "fichier illisible",
                    ),
                )
            }
            when (inspected) {
                is Inspection.Invalid -> return@withContext DatasetImportResult.Rejected(
                    inspected.reason,
                )

                is Inspection.Valid -> formatVersion = inspected.formatVersion ?: formatVersion
            }
        }

        // The old version's files go: a routing segment left in place after
        // becoming obsolete would still be read by the engine.
        destination.listFiles()?.forEach { it.delete() }
        for (file in files) {
            val target = File(destination, file.name)
            if (!file.renameTo(target) && !copyInto(file, target)) {
                return@withContext DatasetImportResult.Rejected(
                    DatasetRejection.TransferFailed("cannot put ${file.name} into place"),
                )
            }
        }

        val record = InstalledDataset(
            kind = kind,
            sizeBytes = destination.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            sha256 = fingerprint,
            installedAt = Instant.now(),
            formatVersion = formatVersion,
        )
        writeIndex(mutableInstalled.value + (kind to record))
        DatasetImportResult.Installed(record)
    }

    /**
     * Moves a file when a plain rename does not suffice.
     *
     * The working directory and the installation directory can sit on two
     * different volumes — the cache is sometimes mounted apart — and `renameTo`
     * then fails without saying anything.
     */
    private fun copyInto(source: File, target: File): Boolean = try {
        source.copyTo(target, overwrite = true)
        source.delete()
        true
    } catch (_: IOException) {
        false
    }

    /** Deletes an installed set. The user must be able to reclaim the space. */
    suspend fun delete(kind: DatasetKind): Unit = withContext(ioDispatcher) {
        directoryOf(kind)?.deleteRecursively()
        writeIndex(mutableInstalled.value - kind)
    }

    /**
     * The name of the document picked from the chooser.
     *
     * Needed for the sets whose files keep their original name: the routing
     * graph, whose name BRouter derives from the coordinates.
     *
     * The fallback on the URI's last segment is not decorative: not every
     * document provider publishes `DISPLAY_NAME`, and a file designated by a
     * `file:` URI has no provider at all.
     */
    private fun displayNameOf(source: Uri): String? =
        (queriedDisplayNameOf(source) ?: source.lastPathSegment)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }

    private fun queriedDisplayNameOf(source: Uri): String? =
        context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }

    /** The total the installed sets occupy, shown in the storage screen. */
    fun occupiedBytes(): Long = mutableInstalled.value.values.sumOf { it.sizeBytes }

    /**
     * Copies the stream into [destination], computing its digest on the way.
     *
     * In a single read: reading thirty-five megabytes again just to hash them
     * would double the wait for nothing.
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
     * Checks that a file really is what it claims to be.
     *
     * Without a manifest this is the only protection against the likeliest
     * mistake of a manual import: picking the wrong file. A base map imported
     * as an address index would fail the first search, long afterwards, with no
     * visible connection to the cause.
     *
     * @return the format version read from the file, or `null` if it carries
     *   none.
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
                    "tiles in \"${format ?: "unknown"}\" format instead of " +
                        "\"$EXPECTED_TILE_FORMAT\"",
                ),
            )
        }
        val tileCount = database.rawQuery("SELECT COUNT(*) FROM tiles", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (tileCount == 0L) {
            return@readingSqlite Inspection.Invalid(
                DatasetRejection.WrongFormat("the file contains no tile at all"),
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
                DatasetRejection.WrongFormat("address index without a format version"),
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
                DatasetRejection.WrongFormat("the index contains no street at all"),
            )
        }
        Inspection.Valid(version)
    }

    /**
     * The routing graph is a binary format of BRouter's own, with no header
     * recognisable at low cost. We therefore settle for ruling out the
     * commonest mistake: having picked one of the other two sets, which are
     * SQLite databases.
     */
    private fun inspectRouting(file: File): Inspection {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = try {
            file.inputStream().use { it.read(header) }
        } catch (error: IOException) {
            return Inspection.Invalid(
                DatasetRejection.TransferFailed(error.message ?: "cannot read"),
            )
        }
        if (read == SQLITE_MAGIC.size && header.contentEquals(SQLITE_MAGIC)) {
            return Inspection.Invalid(
                DatasetRejection.WrongFormat(
                    "this file is a SQLite database, not a routing graph",
                ),
            )
        }
        return Inspection.Valid(null)
    }

    /**
     * Queries a file as a SQLite database, without ever throwing.
     *
     * The opening **and** the queries are covered by the same net. This is not
     * excessive caution: `openDatabase` succeeds on a file that is not a
     * database at all, because it does not read its header, and it is the first
     * query that throws a `SQLiteDatabaseCorruptException`. A user picking the
     * routing graph instead of the base map crashed the application that way.
     *
     * The error handler is neutralised: Android's own **deletes** the file it
     * judges corrupted. On a file being validated that is harmless, but we do
     * not want that machinery within reach of an already installed dataset.
     */
    private inline fun readingSqlite(
        file: File,
        block: (SQLiteDatabase) -> Inspection,
    ): Inspection = try {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            { /* delete nothing: the file is not ours yet */ },
        ).use { block(it) }
    } catch (error: RuntimeException) {
        // SQLiteException and its subclasses are unchecked; they cover a
        // corrupted file as well as a missing table.
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

    /** What inspecting a file teaches, before anything is put into place. */
    private sealed interface Inspection {
        /** The file is the expected one; it may carry a version. */
        data class Valid(val formatVersion: Int?) : Inspection

        /** The file is unusable, for the reason given. */
        data class Invalid(val reason: DatasetRejection) : Inspection
    }

    // ------------------------------------------------------------- index --

    private fun readIndex(): Map<DatasetKind, InstalledDataset> {
        val file = indexFile ?: return emptyMap()
        if (!file.isFile) return emptyMap()
        return try {
            json.decodeFromString(IndexDocument.serializer(), file.readText())
                .entries
                .mapNotNull { it.toDomain() }
                // A vanished file — a cleared cache, a partial restore — must
                // not leave a ghost entry on the screen.
                .filter { directoryOf(it.kind)?.listFiles()?.isNotEmpty() == true }
                .associateBy { it.kind }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeIndex(records: Map<DatasetKind, InstalledDataset>) {
        val file = indexFile ?: return
        file.writeText(
            json.encodeToString(
                IndexDocument.serializer(),
                IndexDocument(records.values.map(IndexEntry::fromDomain)),
            ),
        )
        mutableInstalled.value = records
    }

    private companion object {
        const val DIRECTORY_NAME = "datasets"

        /**
         * The refusal opposed to an installation without an active city.
         *
         * It can only come from a screen that should have been closed: nothing
         * in the interface offers installing data before the city it describes
         * has been chosen.
         */
        val NO_CITY_REJECTION = DatasetRejection.TransferFailed("no active city")
        const val INDEX_FILE_NAME = "installed.json"
        const val STAGING_SUFFIX = ".incoming"
        const val COPY_BUFFER_BYTES = 1 shl 16
        const val EXPECTED_TILE_FORMAT = "pbf"

        /** The length past which a technical detail becomes noise. */
        const val MAX_REJECTION_DETAIL = 200

        /**
         * The address index version this build can read.
         *
         * Raised to 2 when the index started carrying the names of absorbed
         * municipalities: the application queries that column, so an older
         * index is not readable and must be refused with a word about why.
         */
        const val SUPPORTED_ADDRESS_FORMAT_VERSION = 2

        /**
         * The first sixteen bytes of every SQLite file.
         *
         * The terminating NUL is written as an escape rather than as a raw
         * byte: it used to be an invisible character in the source, which no
         * review could see and any reformatting could have eaten.
         */
        val SQLITE_MAGIC: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
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
