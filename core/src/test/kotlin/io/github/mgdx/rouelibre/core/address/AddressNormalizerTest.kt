package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests of street-name normalisation.
 *
 * The most important is the one that replays the reference cases produced by
 * the indexing script: the Python script and the application apply the same
 * rules file, but nothing would guarantee they apply it the same way. A
 * divergence would make streets impossible to find — "boulevard" indexed on one
 * side, "bd" searched on the other — without any other test noticing.
 */
class AddressNormalizerTest {

    private val normalizer = testNormalizer()

    @Test
    fun `the indexing script's reference cases are reproduced`() {
        val files = fixtureFiles()
        assertTrue("no set of reference cases found", files.isNotEmpty())

        var checked = 0
        files.forEach { file ->
            val fixtures = json.decodeFromString(FixtureFile.serializer(), file.readText())
            assertTrue("empty reference cases in ${file.name}", fixtures.cases.isNotEmpty())
            fixtures.cases.forEach { case ->
                assertEquals(
                    "normalisation de « ${case.input} » (${file.name})",
                    case.normalized,
                    normalizer.normalize(case.input),
                )
                val split = normalizer.analyse(case.input)
                assertEquals(
                    "type de « ${case.input} » (${file.name})",
                    case.type,
                    split.streetType,
                )
                assertEquals(
                    "nom propre de « ${case.input} » (${file.name})",
                    case.name,
                    split.properName,
                )
                checked++
            }
        }
        println("reference cases replayed: $checked, across ${files.size} networks")
    }

    @Test
    fun `accents and case disappear`() {
        assertEquals(
            "rue de l hopital militaire",
            normalizer.normalize("Rue de l'Hôpital Militaire"),
        )
        assertEquals("faubourg de roubaix", normalizer.normalize("FAUBOURG DE ROUBAIX"))
    }

    @Test
    fun `common abbreviations are expanded`() {
        assertEquals("boulevard victor hugo", normalizer.normalize("Bd Victor Hugo"))
        assertEquals("avenue des flandres", normalizer.normalize("Av. des Flandres"))
        assertEquals("saint andre", normalizer.normalize("St-André"))
    }

    @Test
    fun `a single-letter abbreviation is only expanded in leading position`() {
        // Otherwise "Jean R Dupont" would become "Jean rue Dupont".
        assertEquals("rue nationale", normalizer.normalize("R. Nationale"))
        assertEquals("place jean r dupont", normalizer.normalize("Place Jean R Dupont"))
    }

    @Test
    fun `the street type is detached from the proper name`() {
        val split = normalizer.analyse("Rue Gambetta")
        assertEquals("rue", split.streetType)
        assertEquals("gambetta", split.properName)
    }

    @Test
    fun `a type is only recognised at the head of a name`() {
        // In "rue de la Place", "place" is part of the name.
        val split = normalizer.analyse("Rue de la Place")
        assertEquals("rue", split.streetType)
        assertEquals("de la place", split.properName)
    }

    @Test
    fun `the longest type wins`() {
        assertEquals("rond point", normalizer.analyse("Rond-Point de l'Europe").streetType)
    }

    @Test
    fun `a name reduced to its type keeps it as its proper name`() {
        // "Grand Place" must not become unfindable for want of a proper name.
        val split = normalizer.analyse("Grand Place")
        assertEquals(null, split.streetType)
        assertEquals("grand place", split.properName)
    }

    @Test
    fun `an unreadable rules file returns a failure, not an exception`() {
        val outcome = AddressNormalizerReader.read("{ ceci n'est pas du json")
        assertTrue(outcome is Outcome.Failure)
    }

    @Serializable
    private data class FixtureFile(val cases: List<FixtureCase> = emptyList())

    @Serializable
    private data class FixtureCase(
        val input: String,
        val normalized: String,
        val type: String? = null,
        val name: String,
    )

    private companion object {

        val json = Json { ignoreUnknownKeys = true }

        /**
         * The case sets, one per generated network.
         *
         * The directory is named by the build: every city produced adds its
         * file, and the test replays them all. The proof therefore extends to
         * each new producer, whose street names are not written like the
         * previous one's.
         */
        fun fixtureFiles(): List<File> {
            val path = checkNotNull(System.getProperty("rouelibre.normalizationFixtures")) {
                "reference-case directory not supplied by the build"
            }
            return File(path).listFiles().orEmpty()
                .filter { it.isFile && it.extension == "json" }
                .sortedBy { it.name }
        }
    }
}
