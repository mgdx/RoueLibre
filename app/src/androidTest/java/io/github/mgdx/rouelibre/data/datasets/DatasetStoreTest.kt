package io.github.mgdx.rouelibre.data.datasets

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.data.DATASET_HEADER_BYTES
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the installation of the datasets (SPEC §4.4).
 *
 * The case that justifies this test on its own: **the routing graph must keep
 * its original name.** BRouter derives the segment's name from the coordinates
 * it is looking for — `E0_N50.rd5` for Lille — then opens it directly. A graph
 * renamed at installation stays on disk without ever being read, and the engine
 * answers "no route" with nothing to point at the cause.
 *
 * And the one opposite to it: **the base map must NOT keep its name.** MapLibre
 * opens the MBTiles itself and keeps it open for as long as the process lives,
 * indexed on the path it was handed, so a new map installed at the old path is
 * never read — Washington, updated to gain the district it was missing, went on
 * drawing the version with the hole in it until the application was killed. The
 * name therefore carries the digest of what the file holds, which is what makes
 * the path change with the content.
 */
@RunWith(AndroidJUnit4::class)
class DatasetStoreTest {

    private lateinit var store: DatasetStore
    private lateinit var incoming: File

    @Before
    fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        store = DatasetStore(target, Dispatchers.IO)
        // The sets are stored per city: without a city in service there is no
        // directory to write into.
        store.useCity(TEST_CITY)
        incoming = File(target.cacheDir, "incoming").apply { mkdirs() }
    }

    @After
    fun clean() = runBlocking {
        store.deleteCity(TEST_CITY)
        incoming.deleteRecursively()
        Unit
    }

    @Test
    fun the_routing_graph_keeps_the_name_brouter_will_look_for() = runBlocking {
        val source = routingGraphNamed("E0_N50.rd5")

        val result = store.importFrom(DatasetKind.Routing, Uri.fromFile(source))

        assertTrue("import refused: $result", result is DatasetImportResult.Installed)
        val installed = store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty()
        assertEquals(
            listOf("E0_N50.rd5"),
            installed.map { it.name },
        )
    }

    @Test
    fun a_sqlite_file_offered_as_a_graph_is_refused() = runBlocking {
        // The likeliest mistake of a manual import: picking the wrong file. A
        // base map taken for a graph has to be said at once, not discovered at
        // the first journey.
        val source = File(incoming, "tiles.mbtiles").apply {
            writeBytes(SQLITE_HEADER + "et la suite".toByteArray())
        }

        val result = store.importFrom(DatasetKind.Routing, Uri.fromFile(source))

        assertTrue("expected a refusal, got: $result", result is DatasetImportResult.Rejected)
        assertTrue(store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun one_city_s_data_does_not_appear_in_another() = runBlocking {
        // Two cities coexist on the device: moving from one to the other must
        // neither mix their files nor suggest the second is installed because
        // the first is (SPEC §11.9).
        // The graph is looked for in its directory rather than through
        // `fileOf`: it is the one set with no canonical name, so `fileOf`
        // returns null for it by design.
        store.importFrom(DatasetKind.Routing, Uri.fromFile(routingGraphNamed("E0_N50.rd5")))
        assertEquals(listOf("E0_N50.rd5"), installedSegments())

        store.useCity(OTHER_TEST_CITY)
        try {
            assertEquals(emptyList<String>(), installedSegments())
            assertTrue(store.installed.value.isEmpty())

            store.useCity(TEST_CITY)
            assertEquals(
                "the original city lost its data",
                listOf("E0_N50.rd5"),
                installedSegments(),
            )
        } finally {
            store.deleteCity(OTHER_TEST_CITY)
        }
    }

    @Test
    fun deleting_a_city_reclaims_all_its_space() = runBlocking {
        store.importFrom(DatasetKind.Routing, Uri.fromFile(routingGraphNamed("E0_N50.rd5")))
        assertTrue(store.occupiedBytesOf(TEST_CITY) > 0)

        store.deleteCity(TEST_CITY)

        assertEquals(0L, store.occupiedBytesOf(TEST_CITY))
        assertEquals(emptyList<String>(), installedSegments())
        assertTrue(store.installed.value.isEmpty())
    }

    @Test
    fun the_base_map_takes_another_path_when_its_content_changes() = runBlocking {
        store.install(DatasetKind.Tiles, listOf(baseMapNamed("tiles.mbtiles", "old")), FIRST_DIGEST)
        val first = store.fileOf(DatasetKind.Tiles)
        assertTrue("the first map was not installed", first != null)

        store.install(DatasetKind.Tiles, listOf(baseMapNamed("tiles.mbtiles", "new")), OTHER_DIGEST)
        val second = store.fileOf(DatasetKind.Tiles)

        assertTrue("the second map was not installed", second != null)
        assertTrue(
            "the map kept its path, and MapLibre would keep the file it opened: " +
                "${second?.name}",
            second?.path != first?.path,
        )
        assertEquals(
            "a map replaced must leave none of its predecessor behind",
            listOf(second?.name),
            store.directoryOf(DatasetKind.Tiles)?.listFiles().orEmpty().map { it.name },
        )
        assertTrue(
            "the name says nothing of what the file holds: ${second?.name}",
            second?.name?.contains(OTHER_DIGEST.take(8)) == true,
        )
    }

    @Test
    fun a_base_map_imported_by_hand_also_takes_another_path() = runBlocking {
        // The manual import has its own way of putting a file into place
        // (SPEC §4.4), and it must not be the one that leaves the path alone.
        store.importFrom(DatasetKind.Tiles, Uri.fromFile(baseMapNamed("first.mbtiles", "old")))
        val first = store.fileOf(DatasetKind.Tiles)

        store.importFrom(DatasetKind.Tiles, Uri.fromFile(baseMapNamed("second.mbtiles", "new")))
        val second = store.fileOf(DatasetKind.Tiles)

        assertTrue("the second map was not installed", second != null)
        assertTrue("the map kept its path: ${second?.name}", second?.path != first?.path)
        assertEquals(
            listOf(second?.name),
            store.directoryOf(DatasetKind.Tiles)?.listFiles().orEmpty().map { it.name },
        )
    }

    @Test
    fun a_base_map_installed_under_the_plain_name_is_still_read() = runBlocking {
        // What an earlier version wrote. It has to go on being read until the
        // next update renames it, or an installation in service would be
        // declared empty by a build that only knows the new name.
        val directory = store.directoryOf(DatasetKind.Tiles)
        assertTrue("no directory for the base map", directory != null)
        baseMapNamed("tiles.mbtiles", "as an earlier version left it")
            .copyTo(File(directory, "tiles.mbtiles"))

        assertEquals("tiles.mbtiles", store.fileOf(DatasetKind.Tiles)?.name)
    }

    /**
     * A base map holding one tile, which is all the inspection reads of it.
     *
     * Written here rather than copied from `data/out`: a real one weighs tens
     * of megabytes, and what is being tested is where the file lands, not what
     * it draws.
     */
    private fun baseMapNamed(name: String, tile: String): File {
        val file = File(incoming, name)
        file.delete()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("INSERT INTO metadata VALUES ('format', 'pbf')")
            database.execSQL(
                "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, " +
                    "tile_row INTEGER, tile_data BLOB)",
            )
            database.execSQL(
                "INSERT INTO tiles VALUES (0, 0, 0, ?)",
                arrayOf<Any>(tile.toByteArray()),
            )
        } finally {
            database.close()
        }
        return file
    }

    /** The routing segments of the city in service, by name. */
    private fun installedSegments(): List<String> =
        store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty().map { it.name }.sorted()

    /**
     * A routing graph as the installation reads one.
     *
     * The rd5 format carries no magic number, so what is checked of it is its
     * one structural invariant: twenty-five entries pairing a format version
     * with the position where a sub-index ends, never going backwards and never
     * running past the end of the file. A text file breaks that on its first
     * entry — which is what these tests used to hand it, and why three of them
     * had been failing since the check was introduced.
     */
    private fun routingGraphNamed(name: String): File {
        val length = DATASET_HEADER_BYTES + 1
        val header = ByteArray(length)
        for (entry in 0 until ROUTING_INDEX_ENTRIES) {
            // Version 1, and a sub-index ending at the last byte: the
            // positions must not go backwards, and all of them may be equal.
            val value = (1L shl ROUTING_VERSION_SHIFT) or length.toLong()
            for (byte in 0 until Long.SIZE_BYTES) {
                header[entry * Long.SIZE_BYTES + byte] =
                    (value ushr ((Long.SIZE_BYTES - 1 - byte) * Byte.SIZE_BITS)).toByte()
            }
        }
        return File(incoming, name).apply { writeBytes(header) }
    }

    private companion object {
        /** Network identifiers of the test's own, so as to erase nothing installed. */
        const val TEST_CITY = "reseau-de-test"
        const val OTHER_TEST_CITY = "autre-reseau-de-test"

        /**
         * Digests of a whole set, as a manifest announces them. Their value
         * matters no more than a name's does; that the two differ is the whole
         * point.
         */
        /** Shape of an rd5 header, as `datasetFileSignature` reads it. */
        const val ROUTING_INDEX_ENTRIES = 25
        const val ROUTING_VERSION_SHIFT = 48

        const val FIRST_DIGEST = "1111111111111111111111111111111111111111111111111111111111111111"
        const val OTHER_DIGEST = "2222222222222222222222222222222222222222222222222222222222222222"

        /**
         * The first sixteen bytes of every SQLite file, terminating NUL
         * included. The header is written by hand rather than by a real
         * database: it is all the validation reads.
         */
        val SQLITE_HEADER: ByteArray =
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
