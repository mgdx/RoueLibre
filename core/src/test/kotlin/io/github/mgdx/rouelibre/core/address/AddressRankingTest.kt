package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of candidate street ranking.
 *
 * Acceptance criterion 11 of the specification is verified here: a search
 * containing a typo or a missing letter finds the intended street in the first
 * three results.
 */
class AddressRankingTest {

    private val normalizer = testNormalizer()

    /** The centre of Lille, which serves as the tests' reference point. */
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
        matching: WordMatching = WordMatching.Prefixes,
    ): List<Long> = rankStreets(
        candidates,
        normalizer.parseQuery(query),
        normalizer.stopWords,
        origin,
        limit,
        matching,
    ).map { it.street.id }

    @Test
    fun `the proper name is enough to find the street`() {
        val gambetta = street("Rue Gambetta")
        val others = listOf(street("Rue Nationale"), street("Boulevard de la Liberté"))

        assertEquals(listOf(gambetta.id), rank("gambetta", others + gambetta))
    }

    @Test
    fun `a prefix covers typing in progress`() {
        val gambetta = street("Rue Gambetta")
        assertEquals(listOf(gambetta.id), rank("gamb", listOf(gambetta)))
    }

    @Test
    fun `the name that stops where the query stops comes first`() {
        val exact = street("Rue Gambetta")
        val extended = street("Rue Gambetta Prolongée")

        assertEquals(exact.id, rank("rue gambetta", listOf(extended, exact)).first())
    }

    @Test
    fun `a typo still finds the street in the first three results`() {
        // Acceptance criterion 11 of the specification.
        val target = street("Rue Nationale")
        val noise = listOf(
            street("Rue Nationale", city = "Roubaix"),
            street("Rue Nicolas Leblanc"),
            street("Rue de Turenne"),
            street("Rue Notre-Dame"),
            street("Avenue Nationale", city = "Tourcoing"),
        )

        val typos = listOf(
            "rue natinale", // a missing letter
            "rue natioanle", // two letters transposed
            "rue nationnale", // a letter too many
        )
        typos.forEach { typo ->
            val ranking = rank(typo, noise + target)
            assertTrue(
                "\"$typo\": the intended street must be in the first three",
                target.id in ranking.take(3),
            )
        }
    }

    @Test
    fun `word order carries no penalty`() {
        // The street type being stored separately, "gare de la rue" and "rue
        // de la gare" reach the same entry (SPEC §4.3).
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
        // Proximity decides inside a tier, never across two.
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
        // The national address base attaches Lomme to Lille; its resident, for
        // their part, types "Lomme".
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
        // Measured on the real index: "Re de la Paix" and "Rue ed la Paix"
        // returned NO result at all, the two-letter fragment ruling out every
        // street on its own. That is the most ordinary mistake there is, and
        // the rest of the query designated the street unambiguously.
        val paix = street("Rue de la Paix")

        assertEquals(listOf(paix.id), rank("re de la paix", listOf(paix)))
        assertEquals(listOf(paix.id), rank("rue ed la paix", listOf(paix)))
    }

    @Test
    fun `a two-letter fragment does not pick a street out on a correction`() {
        // The counterpart of the test above: too weak to rule a street out, it
        // is just as weak at singling one out. "on" is one mistake from "Or",
        // from "En", from "Un" — the correction writes another word instead of
        // repairing one, and there is nothing here for it to lean on.
        val lion = street("Place du Lion d'Or")

        assertEquals(emptyList<Long>(), rank("on", listOf(lion)))
        // Begun rather than mistyped, it still designates the street it opens:
        // that is typing in progress, and the list is only a proposal.
        assertEquals(listOf(lion.id), rank("li", listOf(lion)))
    }

    @Test
    fun `a finished text is not read as a word begun`() {
        // What a text received from another application calls for: its first
        // result becomes a journey without anyone choosing it (SPEC §7.8).
        val gambetta = street("Rue Gambetta")

        assertEquals(listOf(gambetta.id), rank("gamb", listOf(gambetta)))
        assertEquals(
            emptyList<Long>(),
            rank("gamb", listOf(gambetta), matching = WordMatching.WholeWords),
        )
        // The whole word, though, is the same street on either path.
        assertEquals(
            listOf(gambetta.id),
            rank("rue gambetta", listOf(gambetta), matching = WordMatching.WholeWords),
        )
    }

    @Test
    fun `a stop word alone is not enough to bring a street up`() {
        // "de" must not bring back half the index.
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
