package io.github.mgdx.rouelibre.data.datasets

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        val source = fileNamed("E0_N50.rd5", "ceci n'est pas une base SQLite")

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
        store.importFrom(DatasetKind.Routing, Uri.fromFile(fileNamed("E0_N50.rd5", "segment")))
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
        store.importFrom(DatasetKind.Routing, Uri.fromFile(fileNamed("E0_N50.rd5", "segment")))
        assertTrue(store.occupiedBytesOf(TEST_CITY) > 0)

        store.deleteCity(TEST_CITY)

        assertEquals(0L, store.occupiedBytesOf(TEST_CITY))
        assertEquals(emptyList<String>(), installedSegments())
        assertTrue(store.installed.value.isEmpty())
    }

    /** The routing segments of the city in service, by name. */
    private fun installedSegments(): List<String> =
        store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty().map { it.name }.sorted()

    private fun fileNamed(name: String, content: String): File =
        File(incoming, name).apply { writeText(content) }

    private companion object {
        /** Network identifiers of the test's own, so as to erase nothing installed. */
        const val TEST_CITY = "reseau-de-test"
        const val OTHER_TEST_CITY = "autre-reseau-de-test"

        /**
         * The first sixteen bytes of every SQLite file, terminating NUL
         * included. The header is written by hand rather than by a real
         * database: it is all the validation reads.
         */
        val SQLITE_HEADER: ByteArray =
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
