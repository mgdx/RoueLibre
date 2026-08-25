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

    @Test
    fun `an address is found inside the sentence written around it`() {
        // The commonest shape of a real share: nobody sends a bare address
        // (SPEC §7.8). "Rendez-vous ici" names no street, and demanding every
        // word of the text ruled out the one it does name.
        val nationale = street("Rue Nationale")
        val others = listOf(street("Rue Gambetta"), street("Boulevard de la Liberté"))
        val sentence = "Rendez-vous ici : 12 rue Nationale, Lille"

        assertEquals(
            emptyList<Long>(),
            rank(sentence, others + nationale, matching = WordMatching.WholeWords),
        )
        assertEquals(
            listOf(nationale.id),
            rank(sentence, others + nationale, matching = WordMatching.WholeWordsInSentence),
        )
    }

    @Test
    fun `a sentence naming no address brings nothing back`() {
        // The counterpart, and the reason the sentence still has to name a
        // street outright: setting the unknown words aside must not leave an
        // empty query that every street answers.
        val streets = listOf(
            street("Rue Nationale"),
            street("Rue Gambetta"),
            street("Boulevard de la Liberté"),
            street("Place du Lion d'Or"),
        )

        for (text in listOf("coucou", "on se voit demain", "merci beaucoup")) {
            assertEquals(
                emptyList<Long>(),
                rank(text, streets, matching = WordMatching.WholeWordsInSentence),
            )
        }
    }

    @Test
    fun `a municipality alone does not designate a street`() {
        // What a sentence readily leaves behind once its own words are set
        // aside: a town, a street type, and no street.
        val streets = listOf(street("Rue Nationale"), street("Rue Gambetta"))

        assertEquals(
            emptyList<Long>(),
            rank("à demain à Lille", streets, matching = WordMatching.WholeWordsInSentence),
        )
        assertEquals(
            emptyList<Long>(),
            rank("dans la rue à Lille", streets, matching = WordMatching.WholeWordsInSentence),
        )
    }

    @Test
    fun `a word begun is not read as a street inside a sentence either`() {
        // The rule [WholeWords] exists for is kept: only the words the sentence
        // holds in full may pick a street out of it.
        val gambetta = street("Rue Gambetta")

        assertEquals(
            emptyList<Long>(),
            rank(
                "on passe par gamb",
                listOf(gambetta),
                matching = WordMatching.WholeWordsInSentence,
            ),
        )
    }

    @Test
    fun `inside a sentence, the street answering the most words comes first`() {
        // Measured on the real index of Lille, with this fallback's first
        // shape: the sentence of the report offered five landmarks named after
        // the town — "HEI Lille - Junia", "Supinfo Lille", "Port de Lille" —
        // and not the street it names. Each answered the single word "lille",
        // and a word passed over cost them nothing; "Rue Nationale" answered
        // three words at the unequal weights of a name, a type and a
        // municipality, and came out lower for it.
        //
        // The trap entries are created first, so that they hold the lower
        // identifiers: on an equal score they would come first, and only a
        // score of its own puts the street the sentence names at the top.
        val trap = listOf(
            street("HEI Lille - Junia"),
            street("Supinfo Lille"),
            street("Port de Lille"),
            street("ESME - Lille"),
            street("ISG Lille"),
        )
        val nationale = street("Rue Nationale")

        assertEquals(
            nationale.id,
            rank(
                "Rendez-vous ici : 12 rue Nationale, Lille",
                trap + nationale,
                matching = WordMatching.WholeWordsInSentence,
            ).first(),
        )
    }

    @Test
    fun `inside a sentence, the street type still tells two namesakes apart`() {
        // The same sentence without its municipality, the report's second
        // reading: "Route Nationale" answers "nationale" alone and answered it
        // perfectly, where "Rue Nationale" answers the type as well. The word
        // the second one has and the first one lacks must count for it.
        val route = street("Route Nationale")
        val pasteur = street("Rue Pasteur")
        val nationale = street("Rue Nationale")

        assertEquals(
            nationale.id,
            rank(
                "Rendez-vous ici : 12 rue Nationale",
                listOf(route, pasteur, nationale),
                matching = WordMatching.WholeWordsInSentence,
            ).first(),
        )
    }

    @Test
    fun `the sentence's own words weigh on every street alike`() {
        // Where the streets answer the same single word, the sentence around it
        // charges them all the same and decides nothing between them: the name
        // that stops where the query stops still comes first, and the others
        // follow in the order they were already in.
        val paul = street("Rue Paul")
        val lafargue = street("Rue Paul Lafargue")
        val ramadier = street("Rue Paul Ramadier")

        assertEquals(
            listOf(paul.id, lafargue.id, ramadier.id),
            rank(
                "Rendez-vous demain matin devant chez Paul",
                listOf(paul, lafargue, ramadier),
                matching = WordMatching.WholeWordsInSentence,
            ),
        )
    }
}
