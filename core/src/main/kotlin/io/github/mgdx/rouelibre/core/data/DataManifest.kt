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
 *   under.
 * @property url where to get it.
 * @property sizeBytes the announced size, shown before asking for confirmation.
 * @property sha256 the digest, re-verified after download (SPEC §4.4).
 */
public data class ManifestFile(
    public val name: String,
    public val url: String,
    public val sizeBytes: Long,
    public val sha256: String,
)

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
