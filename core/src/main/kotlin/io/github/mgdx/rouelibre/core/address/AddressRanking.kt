package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import kotlin.math.floor

/**
 * Ranking of candidate streets (SPEC §4.3).
 *
 * Two criteria, in this order: **match quality first, proximity second**. At
 * equal match quality, the nearer street comes first.
 *
 * "Equal" is taken loosely, otherwise the proximity criterion would never come
 * into play: two matches differing only by the length of a prefix are, to the
 * user, the same match. Scores are therefore grouped into tiers, and distance
 * decides inside a tier — never across two tiers, where match quality keeps the
 * last word.
 */

/**
 * Width of a match tier.
 *
 * A twentieth separates genuinely different matches — a word found against a
 * word missing — without distinguishing what does not differ in practice, such
 * as two prefixes of neighbouring lengths.
 */
private const val QUALITY_TIER = 0.05

/**
 * Weight of each field in the match.
 *
 * The proper name identifies the street; the type — "rue", "avenue" — narrows
 * almost nothing, half the index being streets; the municipality narrows a
 * great deal, but does not designate the street itself.
 */
private const val PROPER_NAME_WEIGHT = 1.0
private const val STREET_TYPE_WEIGHT = 0.6
private const val CITY_WEIGHT = 0.8

/**
 * Weight of a weak query word: a stop word, or a very short fragment.
 *
 * "de", "la", "des" carry little meaning but are not noise: someone typing
 * "rue de la gare" wrote what they read on the street sign. They count,
 * therefore, but lightly.
 */
private const val WEAK_TERM_WEIGHT = 0.2

/**
 * Length below which a word carries no meaning of its own. Two letters is the
 * size of a stop word or of a truncated "rue".
 *
 * Such a word neither rules a street out nor picks one: a correction applied to
 * two letters changes half of them, which no longer repairs a word but writes
 * another.
 */
private const val SHORT_TERM_LENGTH = 2

/** A query word found exactly as typed. */
private const val EXACT_WORD_SCORE = 1.0

/**
 * A query word prefixing a word of the entry.
 *
 * The score rises with the share of the word covered: "gamb" against
 * "gambetta" is typing in progress, "gambett" is almost the whole word. The
 * floor of 0.72 stays above every typo fallback: an exact prefix is always a
 * better clue than a corrected letter.
 */
private const val PREFIX_FLOOR = 0.72
private const val PREFIX_RANGE = 0.28

/** A word found within one mistake, then within two. */
private const val ONE_MISTAKE_SCORE = 0.55
private const val TWO_MISTAKES_SCORE = 0.35

/**
 * The share of the score that rewards an entry for having **no** extra words.
 *
 * Without it, "Rue Gambetta" and "Rue Gambetta Prolongée" would tie for the
 * query "gambetta", when the first is what was asked for.
 */
private const val COVERAGE_WEIGHT = 0.15

/**
 * How far a query word may reach into an indexed one (SPEC §4.3, §7.8).
 *
 * Not a matter of strictness but of what the text is. A query typed into the
 * search box is unfinished by nature — the user is on the third letter of their
 * street — and the list it produces is a proposal they still have to choose
 * from. A text received from another application is finished, and nobody
 * chooses from anything: its first result becomes the journey.
 */
public enum class WordMatching {
    /** A word may stand for a longer one: "gamb" designates "Gambetta". */
    Prefixes,

    /**
     * A word is only itself, typos aside.
     *
     * What a finished text calls for: "on" is not "Onze Novembre", and taking
     * it for that would invent a destination out of a sentence that names none.
     */
    WholeWords,

    /**
     * Words that are only themselves, in a text that says more than an address.
     *
     * "Meet me here: 12 rue Nationale, Lille" is how an address is really
     * shared — almost nobody sends a bare one — and [WholeWords] refuses it
     * whole, one unknown word being enough to rule every street out. Here such
     * a word is no longer fatal — it lowers the street's score instead, so
     * that the street answering the most words of the query still comes first
     * — and what is left has to name a street by a word of its proper name,
     * written in full, so that a sentence naming no address comes back empty.
     *
     * It is only ever asked **after** [WholeWords] has answered nothing, and
     * what it brings back is a list to choose from and never a destination:
     * the words around the address are not read, so the street picked out of
     * them is a guess, and a guess is offered rather than followed (SPEC §7.8).
     */
    WholeWordsInSentence,
}

/**
 * Ranks the candidate streets for a query.
 *
 * @param candidates the streets to sort out, as the index returned them.
 * @param query the query taken apart by [parseQuery].
 * @param stopWords the stop words, which weigh less in the match.
 * @param origin reference point for proximity — the user's position, or the
 *   centre of the map. `null` if neither is known.
 * @param limit how many results to return.
 * @param matching whether the text is still being typed or finished.
 * @return the retained streets, best first.
 */
public fun rankStreets(
    candidates: Iterable<SearchableStreet>,
    query: AddressQuery,
    stopWords: Set<String>,
    origin: Coordinates?,
    limit: Int,
    matching: WordMatching,
): List<ScoredStreet> {
    if (query.isEmpty || limit <= 0) return emptyList()

    val scored = candidates.mapNotNull { street ->
        val quality = matchQualityOf(street, query.terms, stopWords, matching)
        if (quality <= 0.0) {
            null
        } else {
            ScoredStreet(
                street = street,
                matchQuality = quality,
                distanceInMetres = origin?.distanceInMetresTo(street.position),
            )
        }
    }

    return scored
        .sortedWith(
            compareByDescending<ScoredStreet> { qualityTierOf(it.matchQuality) }
                .thenBy { it.distanceInMetres ?: 0.0 }
                // Final tie-break, so that two runs over the same data always
                // return the same order: without it, the order of two
                // equivalent entries would depend on the index's own.
                .thenBy { it.street.id },
        )
        .take(limit)
}

/** The tier a match score belongs to. */
private fun qualityTierOf(quality: Double): Int = floor(quality / QUALITY_TIER).toInt()

/**
 * Scores the match between an entry and the words typed.
 *
 * @return a score from 0 to 1, or 0 if a meaningful word remains unmatched: a
 *   query one of whose words matches nothing does not describe this street.
 *   Read out of a sentence ([WordMatching.WholeWordsInSentence]), an unmatched
 *   word lowers the score instead of cancelling it — so that the street
 *   answering the most words of the query comes first — and the score is 0
 *   unless a word of the proper name was found in full.
 */
private fun matchQualityOf(
    street: SearchableStreet,
    terms: List<String>,
    stopWords: Set<String>,
    matching: WordMatching,
): Double {
    val nameWords = street.normalizedName.split(' ').filter { it.isNotEmpty() }
    val typeWords = street.normalizedType?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
    // The absorbed municipality counts as much as the current one: someone
    // living in Lomme writes "Lomme", not "Lille".
    val cityWords = listOfNotNull(street.normalizedCity, street.normalizedFormerCity)
        .flatMap { it.split(' ') }
        .filter { it.isNotEmpty() }

    var weightedScore = 0.0
    var totalWeight = 0.0
    val coveredNameWords = HashSet<String>()
    // In a sentence, a word that matches nothing may be one of the sentence's
    // own rather than a word the street fails to answer; it no longer rules the
    // street out. What is left must still name the street outright, or every
    // street of the index would answer a text that names none.
    val sentence = matching == WordMatching.WholeWordsInSentence
    var namedInFull = false

    for (term in terms) {
        // A stop word, or a two-letter fragment, does not carry enough meaning
        // to rule a street out on its own. Measured on the real index: a letter
        // lost from "rue" — "Re de la Paix" — left no result at all, while the
        // rest of the query designated the street unambiguously.
        //
        // The same lack of meaning bars it from picking one out: a correction
        // applied to two letters produces another word rather than the same one
        // — "on" for "Or", "20" for "2" — and a query reduced to that fragment
        // would designate a street nobody named (SPEC §7.8).
        val isWeak = term in stopWords || term.length <= SHORT_TERM_LENGTH
        val againstName = bestScoreAmong(term, nameWords, matching, isWeak)
        val best = maxOf(
            againstName.score * PROPER_NAME_WEIGHT,
            bestScoreAmong(term, typeWords, matching, isWeak).score * STREET_TYPE_WEIGHT,
            bestScoreAmong(term, cityWords, matching, isWeak).score * CITY_WEIGHT,
        )
        // A word found nowhere is a word the street does not answer to, and it
        // rules the street out — except in a sentence, where it may belong to
        // the phrase written around the address rather than to the address.
        //
        // **It still weighs, there and everywhere else**: the term is counted
        // in the total below with a score of zero, so a street that answers
        // fewer words of the query scores lower than one that answers more.
        // Passing over it instead made the opposite true, and that is what put
        // "Rue Nationale, Lille" out of the list for "Rendez-vous ici : 12 rue
        // Nationale, Lille": a landmark named "HEI Lille - Junia" answered the
        // single word "lille", scored a perfect 1 on the one term it was
        // charged for, and beat the street answering three words at the
        // unequal weights of a name, a type and a municipality.
        if (best <= 0.0 && !isWeak && !sentence) return 0.0
        if (!isWeak && againstName.score == EXACT_WORD_SCORE) namedInFull = true
        againstName.word?.let(coveredNameWords::add)

        val weight = if (isWeak) WEAK_TERM_WEIGHT else 1.0
        weightedScore += best * weight
        totalWeight += weight
    }
    if (totalWeight == 0.0) return 0.0
    // The whole word of a proper name, and nothing less: a municipality, a
    // street type or a corrected word designates no street on its own, and
    // those three are all a sentence naming no address ever leaves behind.
    if (sentence && !namedInFull) return 0.0

    val termScore = weightedScore / totalWeight
    // The share of the name's words the query actually asked for: it rewards
    // the name that stops where the query stops.
    val coverage = if (nameWords.isEmpty()) {
        1.0
    } else {
        coveredNameWords.size.toDouble() / nameWords.size
    }
    return (1 - COVERAGE_WEIGHT) * termScore + COVERAGE_WEIGHT * coverage
}

/** The best score of a query word among a field's words, and the word hit. */
private fun bestScoreAmong(
    term: String,
    words: List<String>,
    matching: WordMatching,
    isWeak: Boolean,
): WordMatch {
    var best = WordMatch(0.0, null)
    for (word in words) {
        val score = scoreWord(term, word, matching, isWeak)
        if (score > best.score) best = WordMatch(score, word)
    }
    return best
}

private data class WordMatch(val score: Double, val word: String?)

/**
 * Scores a query word against an indexed word.
 *
 * Three cases, from the surest to the least sure: the whole word, the prefix —
 * which covers typing in progress — then the edit-distance fallback, which only
 * comes in if the first two fail.
 *
 * The middle case is the one a finished text does without: it is what lets a
 * fragment stand for a longer word, and a fragment is only ever meant when
 * someone is still typing.
 *
 * The last is refused to a word too weak to designate anything ([isWeak]),
 * which is where a correction stops repairing a word and starts producing
 * another one.
 */
private fun scoreWord(
    term: String,
    word: String,
    matching: WordMatching,
    isWeak: Boolean,
): Double {
    if (term == word) return EXACT_WORD_SCORE
    if (matching == WordMatching.Prefixes && word.startsWith(term)) {
        return PREFIX_FLOOR + PREFIX_RANGE * (term.length.toDouble() / word.length)
    }
    if (isWeak) return 0.0

    val tolerance = toleratedMistakes(term)
    // Beyond the ceiling, the distance returned is not the real one: it only
    // says "further than we care about". Comparing it to the threshold is
    // therefore the only sound reading.
    val distance = boundedDamerauLevenshteinDistance(term, word, tolerance)
    return when {
        distance > tolerance -> 0.0
        distance <= 1 -> ONE_MISTAKE_SCORE
        else -> TWO_MISTAKES_SCORE
    }
}
