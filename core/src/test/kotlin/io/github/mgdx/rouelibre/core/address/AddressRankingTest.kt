package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du classement des voies candidates.
 *
 * Le critère d'acceptation 11 du SPEC est vérifié ici : une recherche
 * comportant une faute de frappe ou une lettre manquante retrouve la rue visée
 * dans les trois premiers résultats.
 */
class AddressRankingTest {

    private val normalizer = testNormalizer()

    /** Le centre de Lille, qui sert de point de référence aux tests. */
    private val centre = Coordinates(50.6370, 3.0630)

    private var nextId = 1L

    private fun street(
        name: String,
        city: String = "Lille",
        position: Coordinates = centre,
        formerCity: String? = null,
    ): SearchableStreet {
        val split = normalizer.analyse(name)
        return SearchableStreet(
            id = nextId++,
            normalizedType = split.streetType,
            normalizedName = split.properName,
            normalizedCity = normalizer.normalize(city),
            position = position,
            normalizedFormerCity = formerCity?.let(normalizer::normalize),
        )
    }

    private fun rank(
        query: String,
        candidates: List<SearchableStreet>,
        origin: Coordinates? = null,
        limit: Int = 5,
    ): List<Long> = rankStreets(
        candidates,
        normalizer.parseQuery(query),
        normalizer.stopWords,
        origin,
        limit,
    ).map { it.street.id }

    @Test
    fun `the proper name is enough to find the street`() {
        val gambetta = street("Rue Gambetta")
        val autres = listOf(street("Rue Nationale"), street("Boulevard de la Liberté"))

        assertEquals(listOf(gambetta.id), rank("gambetta", autres + gambetta))
    }

    @Test
    fun `a prefix covers typing in progress`() {
        val gambetta = street("Rue Gambetta")
        assertEquals(listOf(gambetta.id), rank("gamb", listOf(gambetta)))
    }

    @Test
    fun `the name that stops where the query stops comes first`() {
        val exacte = street("Rue Gambetta")
        val prolongee = street("Rue Gambetta Prolongée")

        assertEquals(exacte.id, rank("rue gambetta", listOf(prolongee, exacte)).first())
    }

    @Test
    fun `a typo still finds the street in the first three results`() {
        // Critère d'acceptation 11 du SPEC.
        val visee = street("Rue Nationale")
        val bruit = listOf(
            street("Rue Nationale", city = "Roubaix"),
            street("Rue Nicolas Leblanc"),
            street("Rue de Turenne"),
            street("Rue Notre-Dame"),
            street("Avenue Nationale", city = "Tourcoing"),
        )

        val fautes = listOf(
            "rue natinale", // lettre manquante
            "rue natioanle", // deux lettres interverties
            "rue nationnale", // lettre en trop
        )
        fautes.forEach { faute ->
            val classement = rank(faute, bruit + visee)
            assertTrue(
                "« $faute » : la rue visée doit figurer dans les trois premiers",
                visee.id in classement.take(3),
            )
        }
    }

    @Test
    fun `word order carries no penalty`() {
        // Le type de voie étant stocké à part, « gare de la rue » et « rue de
        // la gare » atteignent la même entrée (SPEC §4.3).
        val gare = street("Rue de la Gare")
        assertEquals(listOf(gare.id), rank("gare rue", listOf(gare)))
    }

    @Test
    fun `at equal match quality, the nearer street comes first`() {
        val loin = street("Rue Nationale", position = Coordinates(50.6900, 3.1700))
        val proche = street("Rue Nationale", position = Coordinates(50.6375, 3.0625))

        assertEquals(
            listOf(proche.id, loin.id),
            rank("rue nationale", listOf(loin, proche), origin = centre),
        )
    }

    @Test
    fun `a better match wins over proximity`() {
        // La proximité départage à l'intérieur d'un palier, jamais entre deux.
        val voisineMaisAutre = street("Rue Nicolas Leblanc", position = centre)
        val exacteMaisLoin = street(
            "Rue Gambetta",
            position = Coordinates(50.7200, 3.1800),
        )

        assertEquals(
            exacteMaisLoin.id,
            rank("gambetta", listOf(voisineMaisAutre, exacteMaisLoin), origin = centre).first(),
        )
    }

    @Test
    fun `the municipality is part of what is searched`() {
        val lille = street("Rue Nationale", city = "Lille")
        val roubaix = street("Rue Nationale", city = "Roubaix")

        assertEquals(listOf(roubaix.id), rank("rue nationale roubaix", listOf(lille, roubaix)))
    }

    @Test
    fun `the absorbed municipality is searched like the current one`() {
        // La Base Adresse Nationale rattache Lomme à Lille ; son habitant, lui,
        // tape « Lomme ».
        val lomme = street("Rue du Chemin de Fer", city = "Lille", formerCity = "Lomme")
        val lille = street("Rue du Chemin de Fer", city = "Lille")

        assertEquals(listOf(lomme.id), rank("chemin de fer lomme", listOf(lille, lomme)))
    }

    @Test
    fun `a word matching nothing rules the street out`() {
        val gambetta = street("Rue Gambetta")
        assertTrue(rank("gambetta roubaix", listOf(gambetta)).isEmpty())
    }

    @Test
    fun `a mistake in a two-letter word does not make the street vanish`() {
        // Mesuré sur l'index réel : « Re de la Paix » et « Rue ed la Paix » ne
        // rendaient AUCUN résultat, le fragment de deux lettres écartant à lui
        // seul toutes les voies. C'est pourtant la faute la plus banale, et le
        // reste de la saisie désignait la voie sans ambiguïté.
        val paix = street("Rue de la Paix")

        assertEquals(listOf(paix.id), rank("re de la paix", listOf(paix)))
        assertEquals(listOf(paix.id), rank("rue ed la paix", listOf(paix)))
    }

    @Test
    fun `a stop word alone is not enough to bring a street up`() {
        // « de » ne doit pas ramener la moitié de l'index.
        val liberte = street("Boulevard de la Liberté")
        val gambetta = street("Rue Gambetta")

        assertEquals(listOf(liberte.id), rank("de la liberte", listOf(gambetta, liberte)))
    }

    @Test
    fun `the ranking is stable from one run to the next`() {
        val jumelles = listOf(street("Rue Nationale"), street("Rue Nationale"))
        assertEquals(
            rank("rue nationale", jumelles),
            rank("rue nationale", jumelles.reversed()),
        )
    }
}
