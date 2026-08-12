package io.github.mgdx.rouelibre.data.addresses

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.Outcome
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
 * Exercises address search on Android's SQLite, not on the development
 * machine's.
 *
 * That is the whole point of this test: SPEC §4.3 requires checking that the
 * chosen FTS version really exists on an API 26 device, and that the chosen
 * tokenizer is present there. An index that builds perfectly well under Python
 * can be unreadable on a phone.
 *
 * The index is built here rather than copied from the resources: the real file
 * weighs six megabytes, and what matters is proven on a dozen rows, as long as
 * the schema is the generation script's.
 */
@RunWith(AndroidJUnit4::class)
class AddressIndexTest {

    private lateinit var index: AddressIndex
    private lateinit var datasets: DatasetStore
    private lateinit var indexFile: File

    /** The centre of Lille, the ranking's reference point. */
    private val centre = Coordinates(50.6370, 3.0630)

    @Before
    fun buildIndex() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        datasets = DatasetStore(target, Dispatchers.IO)
        // A city of its own: the sets being stored per network, the test
        // writes into a directory belonging to no real city. A test that erases
        // the data of whoever runs it is a bad neighbour.
        datasets.useCity(TEST_CITY)
        indexFile = checkNotNull(datasets.directoryOf(DatasetKind.Addresses))
            // The index has a canonical name, unlike the routing graph.
            .resolve(checkNotNull(DatasetKind.Addresses.fileName))
        indexFile.delete()
        writeIndex(indexFile)
        index = AddressIndex(datasets, AddressNormalizers(target), Dispatchers.IO)
    }

    @After
    fun removeIndex() = runBlocking {
        index.close()
        datasets.deleteCity(TEST_CITY)
    }

    @Test
    fun finds_a_street_by_its_proper_name() = runBlocking {
        val results = search("gambetta")

        assertEquals("Rue Gambetta", results.first().streetName)
        assertEquals("Lille", results.first().city)
    }

    @Test
    fun finds_a_street_while_typing() = runBlocking {
        // Prefix matching is what makes the list feel alive: "nation" must
        // already designate the rue Nationale.
        assertEquals("Rue Nationale", search("rue nation").first().streetName)
    }

    @Test
    fun recovers_from_a_typo() = runBlocking {
        // Second stage: the full-text index returns nothing, and the
        // edit-distance scan finds the street (SPEC §4.3, criterion 11).
        val results = search("gambeta")

        assertTrue(
            "the intended street must be among the first three",
            results.take(3).any { it.streetName == "Rue Gambetta" },
        )
    }

    @Test
    fun places_a_number_present_in_the_index() = runBlocking {
        val result = search("12 rue Nationale").first()

        assertEquals(12, result.houseNumber)
        assertEquals(PositionPrecision.Exact, result.precision)
        assertTrue(
            "the number must sit at its own position",
            result.position.distanceInMetresTo(NUMBER_12) < 5.0,
        )
    }

    @Test
    fun interpolates_a_number_absent_from_the_index() = runBlocking {
        // Number 16 is not indexed: it must land between 14 and 18, and not in
        // the middle of the street.
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
    fun ranks_by_proximity_at_equal_match() = runBlocking {
        // Two "rue Nationale": the one in Lille is a few hundred metres from
        // the reference point, the one in Roubaix a dozen kilometres away.
        assertEquals("Lille", search("rue nationale").first().city)
    }

    @Test
    fun finds_a_street_by_its_absorbed_municipality_name() = runBlocking {
        // The national address base attaches Lomme and Hellemmes to Lille.
        // Their residents, for their part, type their own municipality's name.
        val result = search("chemin de fer lomme").first()

        assertEquals("Rue du Chemin de Fer", result.streetName)
        // And that is the name shown: the postcode is Lomme's.
        assertEquals("Lomme", result.city)
    }

    @Test
    fun returns_nothing_for_an_empty_query() = runBlocking {
        assertEquals(emptyList<AddressResult>(), search("   "))
    }

    @Test
    fun reports_a_missing_index_instead_of_failing_silently() = runBlocking {
        index.close()
        indexFile.delete()

        val outcome = index.search("gambetta", centre)
        assertTrue("expected a failure, got: $outcome", outcome is Outcome.Failure)
    }

    private suspend fun search(query: String): List<AddressResult> =
        when (val outcome = index.search(query, centre)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> throw AssertionError("search failed: ${outcome.error}")
        }

    /**
     * Writes a tiny index, to the generation script's exact schema.
     *
     * Any divergence from `tools/build_address_index.py` would make this test
     * reassuring and wrong: it is the schema that is exercised here as much as
     * the code that reads it.
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
                "INSERT INTO metadata VALUES ('formatVersion','2'),('deltaScale','100000')," +
                    // The language the names below are written in: the index
                    // says it, and the search reads it back (SPEC §15.1).
                    "('normalizationLanguage','fr')",
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
            // An address the national address base attaches to Lille, but
            // which its resident places in Lomme.
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

            // Numbers of Lille's rue Nationale: 16 is missing on purpose.
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

        /** A network of the test's own, so as to erase no installed data. */
        const val TEST_CITY = "reseau-de-test"

        val NUMBER_12 = Coordinates(50.6340, 3.0550)
        val NUMBER_14 = Coordinates(50.6345, 3.0552)
        val NUMBER_18 = Coordinates(50.6360, 3.0558)

        /** The streets' representative points, used to compute the deltas. */
        val STREET_POSITIONS = mapOf(2L to Coordinates(50.6355, 3.0555))
    }
}
