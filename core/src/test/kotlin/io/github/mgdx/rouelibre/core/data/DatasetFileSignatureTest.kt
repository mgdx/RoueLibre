package io.github.mgdx.rouelibre.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Recognising an imported file before anything opens it (SPEC §4.4).
 *
 * Importing a screenshot as the base map used to say nothing at all: the file
 * went straight to SQLite, which answered `file is not a database` into the
 * log, and the storage screen had no word of its own to put on the screen. The
 * routing graph was worse — it was checked only for *not* being a SQLite file,
 * so the very same screenshot was accepted, installed, and only found out much
 * later, as a route that could not be computed.
 *
 * These cases are the whole decision: what the file is, told from its first
 * bytes, is what settles which sentence the user reads.
 */
class DatasetFileSignatureTest {

    @Test
    fun `a file with no byte in it is empty, not unrecognised`() {
        assertEquals(
            DatasetFileSignature.Empty,
            datasetFileSignature(ByteArray(0), fileLength = 0),
        )
    }

    @Test
    fun `a screenshot is not data this application reads`() {
        val png = bytesOf("89504e470d0a1a0a0000000d49484452") + ByteArray(1_000)
        assertEquals(
            DatasetFileSignature.Unrecognised,
            datasetFileSignature(png.copyOf(DATASET_HEADER_BYTES), fileLength = 1_016),
        )
    }

    @Test
    fun `a base map or an address index is seen as the database it is`() {
        val sqlite = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(184)
        assertEquals(
            DatasetFileSignature.SqliteDatabase,
            datasetFileSignature(sqlite, fileLength = 40_960),
        )
    }

    @Test
    fun `a routing segment is seen as one`() {
        assertEquals(
            DatasetFileSignature.RoutingGraph,
            datasetFileSignature(REAL_SEGMENT_HEADER, fileLength = REAL_SEGMENT_LENGTH),
        )
    }

    /**
     * The invariant that does the work: an index entry is a position inside the
     * file. A shorter file with the same head is not that graph, and a foreign
     * file read as an index lands its very first position in the terabytes.
     */
    @Test
    fun `a segment header whose index runs past the end of the file is refused`() {
        assertEquals(
            DatasetFileSignature.Unrecognised,
            datasetFileSignature(REAL_SEGMENT_HEADER, fileLength = 100_000),
        )
    }

    @Test
    fun `a file too short to hold a segment header is refused`() {
        assertEquals(
            DatasetFileSignature.Unrecognised,
            datasetFileSignature(REAL_SEGMENT_HEADER.copyOf(64), fileLength = 64),
        )
    }

    /**
     * A text file, which is what a badly named export usually turns out to be.
     * Its first eight bytes read as a format version of 18533, which no rd5
     * announces.
     */
    @Test
    fun `a text file is refused`() {
        val text = "Hello, this is not a dataset.\n".repeat(20).toByteArray(Charsets.UTF_8)
        assertEquals(
            DatasetFileSignature.Unrecognised,
            datasetFileSignature(
                text.copyOf(DATASET_HEADER_BYTES),
                fileLength = text.size.toLong(),
            ),
        )
    }

    private fun bytesOf(hexadecimal: String): ByteArray = ByteArray(hexadecimal.length / 2) {
        hexadecimal.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    private companion object {

        /**
         * The head of a segment the generation scripts really produced, and the
         * size of that file: twenty-five entries pairing the rd5 format version
         * with the position where a sub-index ends.
         */
        val REAL_SEGMENT_HEADER: ByteArray = (
            "000b0000000000c800020000000000c8000b0000000000c8000b0000000000c8" +
                "000b0000000000c8000b0000000000c8000b0000000000c8000b0000000000c8" +
                "000b0000000000c8000b0000000000c8000b00000003010c000b00000003010c" +
                "000b00000003010c000b00000003010c000b00000003010c000b00000019518d" +
                "000b00000019518d000b00000019518d000b00000019518d000b00000019518d" +
                "000b00000019518d000b00000019518d000b00000019518d000b00000019518d" +
                "000b00000019518d"
            ).let { hexadecimal ->
            ByteArray(hexadecimal.length / 2) {
                hexadecimal.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
        }

        const val REAL_SEGMENT_LENGTH = 1_659_390L
    }
}
