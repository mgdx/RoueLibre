package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests de la normalisation des noms de voies.
 *
 * Le plus important est celui qui rejoue les cas de référence produits par le
 * script d'indexation : le script Python et l'application appliquent le même
 * fichier de règles, mais rien ne garantirait qu'ils l'appliquent de la même
 * façon. Une divergence rendrait des rues introuvables — « boulevard » indexé
 * d'un côté, « bd » cherché de l'autre — sans qu'aucun autre test ne s'en
 * aperçoive.
 */
class AddressNormalizerTest {

    private val normalizer = testNormalizer()

    @Test
    fun `les cas de reference du script d'indexation sont reproduits`() {
        val files = fixtureFiles()
        assertTrue("aucun jeu de cas de référence trouvé", files.isNotEmpty())

        var checked = 0
        files.forEach { file ->
            val fixtures = json.decodeFromString(FixtureFile.serializer(), file.readText())
            assertTrue("cas de référence vides dans ${file.name}", fixtures.cases.isNotEmpty())
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
        println("cas de référence rejoués : $checked, sur ${files.size} réseaux")
    }

    @Test
    fun `les accents et la casse disparaissent`() {
        assertEquals(
            "rue de l hopital militaire",
            normalizer.normalize("Rue de l'Hôpital Militaire"),
        )
        assertEquals("faubourg de roubaix", normalizer.normalize("FAUBOURG DE ROUBAIX"))
    }

    @Test
    fun `les abreviations courantes sont developpees`() {
        assertEquals("boulevard victor hugo", normalizer.normalize("Bd Victor Hugo"))
        assertEquals("avenue des flandres", normalizer.normalize("Av. des Flandres"))
        assertEquals("saint andre", normalizer.normalize("St-André"))
    }

    @Test
    fun `une abreviation d'une lettre ne se developpe qu'en tete`() {
        // Sinon « Jean R Dupont » deviendrait « Jean rue Dupont ».
        assertEquals("rue nationale", normalizer.normalize("R. Nationale"))
        assertEquals("place jean r dupont", normalizer.normalize("Place Jean R Dupont"))
    }

    @Test
    fun `le type de voie est detache du nom propre`() {
        val split = normalizer.analyse("Rue Gambetta")
        assertEquals("rue", split.streetType)
        assertEquals("gambetta", split.properName)
    }

    @Test
    fun `un type n'est reconnu qu'en tete de nom`() {
        // Dans « rue de la Place », « place » fait partie du nom.
        val split = normalizer.analyse("Rue de la Place")
        assertEquals("rue", split.streetType)
        assertEquals("de la place", split.properName)
    }

    @Test
    fun `le type le plus long l'emporte`() {
        assertEquals("rond point", normalizer.analyse("Rond-Point de l'Europe").streetType)
    }

    @Test
    fun `un nom reduit a son type le garde pour nom propre`() {
        // « Grand Place » ne doit pas devenir introuvable faute de nom propre.
        val split = normalizer.analyse("Grand Place")
        assertEquals(null, split.streetType)
        assertEquals("grand place", split.properName)
    }

    @Test
    fun `un fichier de regles illisible rend un echec, pas une exception`() {
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
         * Les jeux de cas, un par réseau généré.
         *
         * Le répertoire est désigné par le build : chaque ville produite
         * ajoute son fichier, et le test les rejoue tous. La preuve
         * s'étend donc à chaque nouveau producteur, dont les noms de voies
         * ne s'écrivent pas comme ceux du précédent.
         */
        fun fixtureFiles(): List<File> {
            val path = checkNotNull(System.getProperty("rouelibre.normalizationFixtures")) {
                "répertoire des cas de référence non fourni par le build"
            }
            return File(path).listFiles().orEmpty()
                .filter { it.isFile && it.extension == "json" }
                .sortedBy { it.name }
        }
    }
}
