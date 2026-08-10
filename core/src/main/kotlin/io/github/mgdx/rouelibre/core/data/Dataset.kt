package io.github.mgdx.rouelibre.core.data

import java.time.Instant

/**
 * The three offline datasets (SPEC.md §4.4).
 *
 * They are published together and versioned together, but installed
 * separately: refreshing the address index must never force the tens of
 * megabytes of base map to come down again.
 *
 * @property id the identifier as it appears in the manifest.
 * @property fileName the name the file is stored under on the device,
 *   independent of the one it bore at the source. `null` for a set whose
 *   original name carries information we are not allowed to erase.
 */
public enum class DatasetKind(public val id: String, public val fileName: String?) {
    /** The vector base map, in MBTiles format. */
    Tiles("tiles", "tiles.mbtiles"),

    /**
     * The routing graph, in BRouter's rd5 format.
     *
     * **The file keeps its original name**, and that is indispensable: BRouter
     * derives the segment's name from the coordinates it is looking for —
     * `E0_N50.rd5` for Lille — then opens it directly in the segment directory.
     * A renamed graph would be invisible to the engine, which would answer "no
     * route" with nothing to point at the cause.
     */
    Routing("routing", null),

    /** The address index, a SQLite database. */
    Addresses("addresses", "addresses.sqlite"),

    ;

    public companion object {
        /** Finds a set by its manifest identifier, or `null`. */
        public fun fromId(id: String): DatasetKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A dataset present on the device.
 *
 * @property kind which of the three.
 * @property sizeBytes the size of the installed file.
 * @property sha256 the digest of the installed file. It decides, faced with a
 *   manifest, whether re-downloading is called for (SPEC §4.4).
 * @property installedAt when it was installed, shown in the storage screen.
 * @property formatVersion the format version read from the file itself, when it
 *   carries one.
 */
public data class InstalledDataset(
    public val kind: DatasetKind,
    public val sizeBytes: Long,
    public val sha256: String,
    public val installedAt: Instant,
    public val formatVersion: Int?,
)

/**
 * Why a file offered for installation was refused.
 *
 * None of these cases is a breakdown: they are situations the user can put
 * right, provided they are told which one (SPEC §14).
 */
public sealed interface DatasetRejection {

    /** The file does not have the shape expected for this dataset. */
    public data class WrongFormat(public val detail: String) : DatasetRejection

    /**
     * The file is of a format version this version of the application cannot
     * read. It has to be said, with an invitation to update the application,
     * not fail when opening it (SPEC §4.4).
     *
     * @property found the version found in the file.
     * @property supported the version the application can read.
     */
    public data class UnsupportedFormatVersion(public val found: Int, public val supported: Int) :
        DatasetRejection

    /** The digest does not match the one announced by the manifest. */
    public data class ChecksumMismatch(public val expected: String, public val actual: String) :
        DatasetRejection

    /** The file could not be read or written. */
    public data class TransferFailed(public val detail: String) : DatasetRejection

    /** The file is empty. */
    public data object Empty : DatasetRejection
}

/**
 * The outcome of an installation attempt.
 *
 * A separate type rather than the `DataError` of network failures: the causes
 * have nothing in common, and merging them would force every screen to handle
 * cases that do not concern it.
 */
public sealed interface DatasetImportResult {

    /** The file was validated and put in place. */
    public data class Installed(public val dataset: InstalledDataset) : DatasetImportResult

    /** The file was refused; the previous installation is untouched. */
    public data class Rejected(public val reason: DatasetRejection) : DatasetImportResult
}
