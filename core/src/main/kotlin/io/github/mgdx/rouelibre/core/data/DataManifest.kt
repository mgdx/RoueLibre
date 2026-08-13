package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What a data release announces (SPEC §4.4).
 *
 * The manifest weighs a few kilobytes and describes all three sets: their
 * version, their address, their size and their digest. It is what allows
 * re-downloading **only what changed** — refreshing the address index must never
 * force the thirty-five megabytes of tiles to come down again.
 *
 * @property formatVersion the format version of the files described. A version
 *   the application cannot read must produce an invitation to update, not a
 *   failure when opening a file.
 * @property releaseTag the release's tag, `data-2026-08` for instance.
 * @property generatedAt the generation date, as the script wrote it.
 * @property network the identifier of the network served, which must match the
 *   one in the city configuration.
 * @property boundingBox the area this data covers.
 * @property datasets the published sets.
 */
public data class DataManifest(
    public val formatVersion: Int,
    public val releaseTag: String,
    public val generatedAt: String,
    public val network: String,
    public val boundingBox: BoundingBox?,
    public val datasets: List<ManifestDataset>,
) {
    /** The total announced size, all sets taken together. */
    public val totalSizeBytes: Long
        get() = datasets.sumOf { it.sizeBytes }

    /** The set described for this category, if the manifest lists it. */
    public fun datasetFor(kind: DatasetKind): ManifestDataset? =
        datasets.firstOrNull { it.kind == kind }
}

/**
 * A published dataset.
 *
 * @property kind which of the three.
 * @property description what the producer says about it, in one line.
 * @property files the files it is made of. The routing graph may have several,
 *   the other two have only one.
 */
public data class ManifestDataset(
    public val kind: DatasetKind,
    public val description: String,
    public val files: List<ManifestFile>,
) {
    /** The announced size of this set. */
    public val sizeBytes: Long
        get() = files.sumOf { it.sizeBytes }

    /**
     * The digest of the set as a whole.
     *
     * The files' digests, in the order of their names: this is what the
     * application keeps after installing, and what it compares against the next
     * manifest to decide whether re-downloading is called for. A set of a single
     * file therefore has that file's digest, which keeps the comparison sound
     * even after a manual import.
     */
    public val fingerprint: String
        get() = files.sortedBy { it.name }.joinToString(separator = ",") { it.sha256 }
}

/**
 * A file to download.
 *
 * @property name the name it is published under, and the name it will be stored
 *   under. It must be a plain file name — see [requirePlainFileName].
 * @property url where to get it.
 * @property sizeBytes the announced size, shown before asking for confirmation.
 * @property sha256 the digest, re-verified after download (SPEC §4.4). It is
 *   required — see [requireDigest].
 * @throws IllegalArgumentException if [name] is not a plain file name, or if
 *   [sha256] is not a digest. The reader turns that into a refusal of the whole
 *   manifest.
 */
public data class ManifestFile(
    public val name: String,
    public val url: String,
    public val sizeBytes: Long,
    public val sha256: String,
) {
    init {
        requirePlainFileName(name)
        requireDigest(sha256, name)
    }
}

/**
 * Refuses a file name that would designate anything but a file in the directory
 * meant for it.
 *
 * This name arrives in a **downloaded** document and becomes a path component on
 * the device. A `..` in it would make the download land outside the directory
 * prepared for it — anywhere in the application's private storage — and the
 * digest announced beside it is no protection whatsoever: whoever writes the
 * manifest supplies the content *and* the digest it is checked against. The
 * verification says what the file is, never where it lands.
 *
 * The refusal happens here, at the reading, rather than at the moment of
 * writing: a manifest that names such a file is not a manifest to be patched up,
 * it is one to be rejected — and it must be rejected whole, unlike a dataset of
 * an unknown category, which a later release may legitimately describe.
 *
 * What is refused is a separator, the two names that designate a directory, and
 * the null character, which truncates a path in the layers underneath. Nothing
 * more: what matters is that the name designate a file *here*, not that it look
 * the way today's generation script writes it. A legitimate name may well hold a
 * space or an accent, and refusing those would be inventing a rule the property
 * to be held does not need.
 *
 * The backslash is refused as well as the slash. It separates nothing on
 * Android, but this module compiles and is tested on the JVM, and a rule that
 * depends on the platform it runs on is a rule one has to think about twice.
 */
private fun requirePlainFileName(name: String) {
    require(
        name.isNotEmpty() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name &&
            '\u0000' !in name,
    ) { "unusable file name in the manifest: \"$name\"" }
}

/**
 * Refuses a file announced without a usable digest.
 *
 * The digest is what SPEC §4.4 rests the whole download on: what arrives is
 * hashed and put against what was announced. Making it optional made that
 * guarantee optional too — and optional for **whoever writes the manifest**,
 * which is to say for the one party it protects against. A file with no digest
 * was installed on the strength of its shape alone, silently, with nothing to
 * distinguish it from a verified one.
 *
 * A SHA-256 is sixty-four hexadecimal characters, and refusing anything else
 * here rather than at the comparison is not pedantry: a truncated or mistyped
 * digest would otherwise be found out only after tens of megabytes have come
 * down, under a message about an unexpected digest that would point at the
 * server rather than at the manifest.
 *
 * The case is free, as it is in the comparison.
 *
 * @param name the file concerned, so the refusal says which one.
 */
private fun requireDigest(sha256: String, name: String) {
    require(sha256.length == SHA256_LENGTH && sha256.all { it.isHexadecimal() }) {
        "unusable digest for \"$name\": \"$sha256\""
    }
}

private const val SHA256_LENGTH = 64

private fun Char.isHexadecimal(): Boolean = this in '0'..'9' ||
    this in 'a'..'f' ||
    this in 'A'..'F'

/**
 * What is to be done with a set, faced with a manifest.
 */
public enum class DatasetUpdate {
    /** Absent from the device: it must be downloaded to be of any use. */
    Missing,

    /** Installed, but the manifest announces another version. */
    Outdated,

    /** Installed and current: re-download nothing. */
    UpToDate,
}

/**
 * Compares what is installed with what is published (SPEC §4.4).
 *
 * The comparison is on digests, never on dates: a more recent release date does
 * not say the content changed, and a file regenerated identically must not be
 * downloaded again.
 *
 * @param manifest the announced release.
 * @param installedFingerprints the digest of every set present on the device.
 * @return the state of each of the manifest's sets.
 */
public fun compareWithInstalled(
    manifest: DataManifest,
    installedFingerprints: Map<DatasetKind, String>,
): Map<DatasetKind, DatasetUpdate> = manifest.datasets.associate { dataset ->
    val installed = installedFingerprints[dataset.kind]
    dataset.kind to when {
        installed == null -> DatasetUpdate.Missing
        installed.equals(dataset.fingerprint, ignoreCase = true) -> DatasetUpdate.UpToDate
        else -> DatasetUpdate.Outdated
    }
}

/** Reads a data release manifest. */
public object DataManifestReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the contents of a manifest.
     *
     * @param document the raw contents of the `manifest.json` file.
     * @return the manifest, or the error preventing it from being read. A set
     *   whose identifier is unknown is ignored rather than fatal: a more recent
     *   release may describe others, and that must not stand in the way of
     *   updating the ones we do know.
     */
    public fun read(document: String): Outcome<DataManifest> = try {
        val parsed = json.decodeFromString(ManifestDocument.serializer(), document)
        Outcome.Success(parsed.toDomain())
    } catch (error: SerializationException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "unreadable manifest"))
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "inconsistent manifest"))
    }
}

@Serializable
private data class ManifestDocument(
    val formatVersion: Int = 1,
    val releaseTag: String = "",
    val generatedAt: String = "",
    val network: String = "",
    val boundingBox: BoundingBoxDocument = BoundingBoxDocument(),
    val datasets: List<DatasetDocument> = emptyList(),
) {
    fun toDomain(): DataManifest = DataManifest(
        formatVersion = formatVersion,
        releaseTag = releaseTag,
        generatedAt = generatedAt,
        network = network,
        boundingBox = boundingBox.toDomain(),
        datasets = datasets.mapNotNull { it.toDomain() },
    )
}

@Serializable
private data class DatasetDocument(
    val id: String,
    val description: String = "",
    val files: List<FileDocument> = emptyList(),
) {
    fun toDomain(): ManifestDataset? {
        val kind = DatasetKind.fromId(id) ?: return null
        if (files.isEmpty()) return null
        return ManifestDataset(
            kind = kind,
            description = description,
            files = files.map { it.toDomain() },
        )
    }
}

@Serializable
private data class FileDocument(
    val name: String,
    val url: String,
    val sizeBytes: Long = 0,
    val sha256: String = "",
) {
    fun toDomain(): ManifestFile = ManifestFile(name, url, sizeBytes, sha256)
}

@Serializable
private data class BoundingBoxDocument(
    val south: Double? = null,
    val west: Double? = null,
    val north: Double? = null,
    val east: Double? = null,
) {
    fun toDomain(): BoundingBox? {
        val southValue = south ?: return null
        val westValue = west ?: return null
        val northValue = north ?: return null
        val eastValue = east ?: return null
        return BoundingBox(southValue, westValue, northValue, eastValue)
    }
}
