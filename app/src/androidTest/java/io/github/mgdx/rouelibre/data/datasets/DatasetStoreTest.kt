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
 * Éprouve l'installation des jeux de données (SPEC §4.4).
 *
 * Le cas qui justifie ce test à lui seul : **le graphe de routage doit garder
 * son nom d'origine.** BRouter déduit le nom du segment des coordonnées
 * cherchées — `E0_N50.rd5` pour Lille — puis l'ouvre directement. Un graphe
 * renommé à l'installation reste sur le disque sans jamais être lu, et le
 * moteur répond « aucun itinéraire » sans que rien n'indique la cause.
 */
@RunWith(AndroidJUnit4::class)
class DatasetStoreTest {

    private lateinit var store: DatasetStore
    private lateinit var incoming: File

    @Before
    fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        store = DatasetStore(target, Dispatchers.IO)
        // Les jeux sont rangés par ville : sans ville en service, il n'y a
        // aucun répertoire où écrire.
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

        assertTrue("import refusé : $result", result is DatasetImportResult.Installed)
        val installed = store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty()
        assertEquals(
            listOf("E0_N50.rd5"),
            installed.map { it.name },
        )
    }

    @Test
    fun a_sqlite_file_offered_as_a_graph_is_refused() = runBlocking {
        // L'erreur la plus probable de l'import manuel : désigner le mauvais
        // fichier. Un fond de carte pris pour un graphe doit être dit tout de
        // suite, pas découvert au premier itinéraire.
        val source = File(incoming, "tiles.mbtiles").apply {
            writeBytes(SQLITE_HEADER + "et la suite".toByteArray())
        }

        val result = store.importFrom(DatasetKind.Routing, Uri.fromFile(source))

        assertTrue("expected a refusal, got: $result", result is DatasetImportResult.Rejected)
        assertTrue(store.directoryOf(DatasetKind.Routing)?.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun one_city_s_data_does_not_appear_in_another() = runBlocking {
        // Deux villes cohabitent sur l'appareil : passer de l'une à l'autre ne
        // doit ni mélanger leurs fichiers, ni faire croire que la seconde est
        // installée parce que la première l'est (SPEC §11.9).
        store.importFrom(DatasetKind.Routing, Uri.fromFile(fileNamed("E0_N50.rd5", "segment")))
        assertTrue(store.fileOf(DatasetKind.Routing) != null)

        store.useCity(OTHER_TEST_CITY)
        try {
            assertEquals(null, store.fileOf(DatasetKind.Routing))
            assertTrue(store.installed.value.isEmpty())

            store.useCity(TEST_CITY)
            assertTrue(
                "la ville d'origine a perdu ses données",
                store.fileOf(DatasetKind.Routing) != null,
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
        assertEquals(null, store.fileOf(DatasetKind.Routing))
        assertTrue(store.installed.value.isEmpty())
    }

    private fun fileNamed(name: String, content: String): File =
        File(incoming, name).apply { writeText(content) }

    private companion object {
        /** Identifiants de réseau propres au test, pour ne rien effacer d'installé. */
        const val TEST_CITY = "reseau-de-test"
        const val OTHER_TEST_CITY = "autre-reseau-de-test"

        /**
         * Les seize premiers octets de tout fichier SQLite, terminateur nul
         * compris. L'en-tête est écrit à la main plutôt que par une vraie
         * base : c'est tout ce que la validation lit.
         */
        val SQLITE_HEADER: ByteArray =
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
