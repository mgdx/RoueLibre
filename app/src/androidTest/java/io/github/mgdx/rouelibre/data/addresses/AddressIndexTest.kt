package io.github.mgdx.rouelibre.data.addresses

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressNormalizer
import io.github.mgdx.rouelibre.core.address.AddressNormalizerReader
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
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
 * Éprouve la recherche d'adresses sur le SQLite d'Android, pas sur celui de la
 * machine de développement.
 *
 * C'est tout l'intérêt de ce test : le SPEC §4.3 impose de vérifier que la
 * version de FTS retenue existe réellement sur un appareil à l'API 26, et que
 * le *tokenizer* choisi y est présent. Un index qui se construit très bien
 * sous Python peut être illisible sur un téléphone.
 *
 * L'index est fabriqué ici plutôt que copié depuis les ressources : le vrai
 * fichier pèse six mégaoctets et l'essentiel se prouve sur une douzaine de
 * lignes, tant que le schéma est celui du script de génération.
 */
@RunWith(AndroidJUnit4::class)
class AddressIndexTest {

    private lateinit var index: AddressIndex
    private lateinit var datasets: DatasetStore
    private lateinit var indexFile: File

    /** Le centre de Lille, point de référence du classement. */
    private val centre = Coordinates(50.6370, 3.0630)

    @Before
    fun buildIndex() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        datasets = DatasetStore(target, Dispatchers.IO)
        indexFile = datasets.directoryOf(DatasetKind.Addresses)
            .resolve(DatasetKind.Addresses.fileName)
        indexFile.delete()
        writeIndex(indexFile)
        index = AddressIndex(datasets, normalizer(target), Dispatchers.IO)
    }

    @After
    fun removeIndex() {
        index.close()
        indexFile.delete()
    }

    @Test
    fun trouve_une_voie_par_son_nom_propre() = runBlocking {
        val results = search("gambetta")

        assertEquals("Rue Gambetta", results.first().streetName)
        assertEquals("Lille", results.first().city)
    }

    @Test
    fun trouve_une_voie_pendant_la_frappe() = runBlocking {
        // La correspondance par préfixe est ce qui rend la liste vivante :
        // « nation » doit déjà désigner la rue Nationale.
        assertEquals("Rue Nationale", search("rue nation").first().streetName)
    }

    @Test
    fun rattrape_une_faute_de_frappe() = runBlocking {
        // Second étage : l'index plein texte ne rend rien, le parcours par
        // distance d'édition retrouve la rue (SPEC §4.3, critère 11).
        val results = search("gambeta")

        assertTrue(
            "la rue visée doit figurer dans les trois premiers",
            results.take(3).any { it.streetName == "Rue Gambetta" },
        )
    }

    @Test
    fun place_un_numero_present_dans_l_index() = runBlocking {
        val result = search("12 rue Nationale").first()

        assertEquals(12, result.houseNumber)
        assertEquals(PositionPrecision.Exact, result.precision)
        assertTrue(
            "le numéro doit être placé à sa position propre",
            result.position.distanceInMetresTo(NUMBER_12) < 5.0,
        )
    }

    @Test
    fun interpole_un_numero_absent_de_l_index() = runBlocking {
        // Le 16 n'est pas indexé : il doit tomber entre le 14 et le 18, et pas
        // au milieu de la rue.
        val result = search("16 rue Nationale").first()

        assertEquals(16, result.houseNumber)
        assertEquals(PositionPrecision.Interpolated, result.precision)
        assertTrue(
            "interpolation hors du segment 14–18",
            result.position.latitude > NUMBER_14.latitude &&
                result.position.latitude < NUMBER_18.latitude,
        )
    }

    @Test
    fun classe_par_proximite_a_correspondance_egale() = runBlocking {
        // Deux « rue Nationale » : celle de Lille est à quelques centaines de
        // mètres du point de référence, celle de Roubaix à une douzaine de
        // kilomètres.
        assertEquals("Lille", search("rue nationale").first().city)
    }

    @Test
    fun trouve_une_voie_par_le_nom_de_sa_commune_absorbee() = runBlocking {
        // La Base Adresse Nationale rattache Lomme et Hellemmes à Lille. Leurs
        // habitants, eux, tapent le nom de leur commune.
        val result = search("chemin de fer lomme").first()

        assertEquals("Rue du Chemin de Fer", result.streetName)
        // Et c'est ce nom-là qui s'affiche : le code postal est celui de Lomme.
        assertEquals("Lomme", result.city)
    }

    @Test
    fun ne_rend_rien_pour_une_saisie_vide() = runBlocking {
        assertEquals(emptyList<AddressResult>(), search("   "))
    }

    @Test
    fun signale_un_index_absent_au_lieu_d_echouer_en_silence() = runBlocking {
        index.close()
        indexFile.delete()

        val outcome = index.search("gambetta", centre)
        assertTrue("échec attendu, obtenu : $outcome", outcome is Outcome.Failure)
    }

    private suspend fun search(query: String): List<AddressResult> =
        when (val outcome = index.search(query, centre)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> throw AssertionError("recherche en échec : ${outcome.error}")
        }

    private fun normalizer(context: Context): AddressNormalizer {
        val document = context.assets.open("address_normalization.json")
            .bufferedReader()
            .use { it.readText() }
        return when (val outcome = AddressNormalizerReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> throw AssertionError("règles illisibles : ${outcome.error}")
        }
    }

    /**
     * Écrit un index minuscule, au schéma exact du script de génération.
     *
     * Toute divergence avec `tools/build_address_index.py` rendrait ce test
     * rassurant et faux : c'est le schéma qui est éprouvé ici autant que le
     * code qui le lit.
     */
    private fun writeIndex(file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use {
            it.execSQL(
                """
                CREATE TABLE street(
                    id INTEGER PRIMARY KEY, display_name TEXT NOT NULL,
                    normalized_name TEXT NOT NULL, normalized_type TEXT,
                    city TEXT NOT NULL, normalized_city TEXT NOT NULL,
                    former_city TEXT, normalized_former_city TEXT, postcode TEXT,
                    latitude REAL NOT NULL, longitude REAL NOT NULL, kind INTEGER NOT NULL)
                """.trimIndent(),
            )
            it.execSQL(
                "CREATE VIRTUAL TABLE street_search USING fts4(" +
                    "terms, content=\"\", tokenize=simple, prefix=\"2,3\")",
            )
            it.execSQL(
                """
                CREATE TABLE house_number(
                    street_id INTEGER NOT NULL, number INTEGER NOT NULL, suffix TEXT NOT NULL,
                    delta_lat INTEGER NOT NULL, delta_lon INTEGER NOT NULL,
                    PRIMARY KEY (street_id, number, suffix)) WITHOUT ROWID
                """.trimIndent(),
            )
            it.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            it.execSQL(
                "INSERT INTO metadata VALUES ('formatVersion','2'),('deltaScale','100000')",
            )

            addStreet(it, 1, "Rue Gambetta", "rue", "gambetta", "Lille", 50.6290, 3.0530)
            addStreet(it, 2, "Rue Nationale", "rue", "nationale", "Lille", 50.6355, 3.0555)
            addStreet(it, 3, "Rue Nationale", "rue", "nationale", "Roubaix", 50.6910, 3.1720)
            addStreet(
                it,
                4,
                "Boulevard de la Liberté",
                "boulevard",
                "de la liberte",
                "Lille",
                50.6330,
                3.0600,
            )
            // Une adresse que la Base Adresse Nationale rattache à Lille, mais
            // que son habitant situe à Lomme.
            addStreet(
                it,
                5,
                "Rue du Chemin de Fer",
                "rue",
                "du chemin de fer",
                "Lille",
                50.6400,
                3.0130,
                formerCity = "Lomme",
            )

            // Numéros de la rue Nationale de Lille : le 16 manque exprès.
            addNumber(it, 2, 12, NUMBER_12)
            addNumber(it, 2, 14, NUMBER_14)
            addNumber(it, 2, 18, NUMBER_18)
        }
    }

    private fun addStreet(
        database: SQLiteDatabase,
        id: Long,
        displayName: String,
        type: String,
        name: String,
        city: String,
        latitude: Double,
        longitude: Double,
        formerCity: String? = null,
    ) {
        database.execSQL(
            "INSERT INTO street VALUES (?,?,?,?,?,?,?,?,?,?,?,0)",
            arrayOf<Any?>(
                id, displayName, name, type, city, city.lowercase(),
                formerCity, formerCity?.lowercase(), "59000", latitude, longitude,
            ),
        )
        database.execSQL(
            "INSERT INTO street_search(docid, terms) VALUES (?,?)",
            arrayOf<Any>(
                id,
                "$type $name ${city.lowercase()} ${formerCity?.lowercase().orEmpty()}".trim(),
            ),
        )
    }

    private fun addNumber(
        database: SQLiteDatabase,
        streetId: Long,
        number: Int,
        position: Coordinates,
    ) {
        val street = STREET_POSITIONS.getValue(streetId)
        database.execSQL(
            "INSERT INTO house_number VALUES (?,?,'',?,?)",
            arrayOf<Any>(
                streetId,
                number,
                ((position.latitude - street.latitude) * DELTA_SCALE).toInt(),
                ((position.longitude - street.longitude) * DELTA_SCALE).toInt(),
            ),
        )
    }

    private companion object {
        const val DELTA_SCALE = 100_000.0

        val NUMBER_12 = Coordinates(50.6340, 3.0550)
        val NUMBER_14 = Coordinates(50.6345, 3.0552)
        val NUMBER_18 = Coordinates(50.6360, 3.0558)

        /** Points représentatifs des voies, pour calculer les deltas. */
        val STREET_POSITIONS = mapOf(2L to Coordinates(50.6355, 3.0555))
    }
}
