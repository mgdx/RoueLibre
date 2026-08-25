package io.github.mgdx.rouelibre.core.data

/**
 * What a file's first bytes say it is, before anything opens it (SPEC §4.4).
 *
 * Manual import hands us whatever the system chooser returned, and the likeliest
 * mistake is picking the wrong file. Recognising it here rather than at the
 * first query is what lets the refusal be named: handing a screenshot to SQLite
 * only yields "file is not a database", a sentence written for a developer,
 * which the screen then has nothing to say about.
 */
public enum class DatasetFileSignature {
    /** Nothing at all: the file has no byte. */
    Empty,

    /** A SQLite database: the base map and the address index are both one. */
    SqliteDatabase,

    /** A BRouter routing segment, in rd5 format. */
    RoutingGraph,

    /** None of the above: this file is not data this application reads. */
    Unrecognised,
}

/**
 * How many bytes [datasetFileSignature] needs from the head of a file.
 *
 * The rd5 header is the longer of the two shapes recognised, and it is exactly
 * this size: twenty-five eight-byte entries.
 */
public const val DATASET_HEADER_BYTES: Int = 200

/**
 * The first sixteen bytes of every SQLite file.
 *
 * The terminating NUL is written as an escape rather than as a raw byte: it
 * used to be an invisible character in the source, which no review could see
 * and any reformatting could have eaten.
 */
private val SQLITE_MAGIC: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** How many entries the rd5 header holds: a five-by-five grid of sub-indexes. */
private const val ROUTING_INDEX_ENTRIES = 25

/** The low bits of an rd5 index entry, which carry a position in the file. */
private const val ROUTING_POSITION_MASK = 0xFFFF_FFFF_FFFFL

/** How far an rd5 index entry's high bits are shifted: its format version. */
private const val ROUTING_VERSION_SHIFT = 48

/**
 * The largest format version an rd5 file may plausibly announce.
 *
 * BRouter's has been 10 then 11 for the whole life of the format, and the minor
 * version sits in the same field of the second entry, at 2. The bound is here to
 * reject foreign files, whose first bytes read as versions in the tens of
 * thousands — a PNG announces 35152 — not to pin a version we support.
 */
private const val MAX_ROUTING_VERSION = 255L

/**
 * Recognises a file from its head and its size.
 *
 * Pure, and deliberately so: this is the one decision of the import that can be
 * proven on the JVM, and it is the decision that settles what the user is told.
 *
 * @param header the first [DATASET_HEADER_BYTES] bytes of the file, or fewer if
 *   the file is shorter than that.
 * @param fileLength the file's total size, which the rd5 shape is checked
 *   against: its index holds positions, and a position past the end of the file
 *   is the surest sign that these bytes are not an index at all.
 */
public fun datasetFileSignature(header: ByteArray, fileLength: Long): DatasetFileSignature = when {
    fileLength <= 0L -> DatasetFileSignature.Empty
    startsWithSqliteMagic(header) -> DatasetFileSignature.SqliteDatabase
    looksLikeRoutingGraph(header, fileLength) -> DatasetFileSignature.RoutingGraph
    else -> DatasetFileSignature.Unrecognised
}

private fun startsWithSqliteMagic(header: ByteArray): Boolean = header.size >= SQLITE_MAGIC.size &&
    SQLITE_MAGIC.indices.all { header[it] == SQLITE_MAGIC[it] }

/**
 * Whether these bytes are an rd5 index.
 *
 * The format carries no magic number, so what is checked is its one structural
 * invariant: the header is twenty-five entries, each pairing a small format
 * version with the position, in the file, where a sub-index ends. Those
 * positions start after the header, never go backwards and never run past the
 * end of the file. A screenshot, an archive or a text file breaks that on its
 * very first entry, whose bytes read as a position of several terabytes.
 */
private fun looksLikeRoutingGraph(header: ByteArray, fileLength: Long): Boolean {
    if (header.size < DATASET_HEADER_BYTES || fileLength < DATASET_HEADER_BYTES) return false
    var previous = DATASET_HEADER_BYTES.toLong()
    for (entry in 0 until ROUTING_INDEX_ENTRIES) {
        val value = bigEndianLongAt(header, entry * Long.SIZE_BYTES)
        val version = value ushr ROUTING_VERSION_SHIFT
        if (version < 1L || version > MAX_ROUTING_VERSION) return false
        val position = value and ROUTING_POSITION_MASK
        if (position < previous || position > fileLength) return false
        previous = position
    }
    return true
}

private fun bigEndianLongAt(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (index in offset until offset + Long.SIZE_BYTES) {
        value = (value shl 8) or (bytes[index].toLong() and 0xFF)
    }
    return value
}
