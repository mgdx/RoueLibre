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
 * Length below which a word cannot, on its own, rule a street out. Two letters
 * is the size of a stop word or of a truncated "rue".
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
 * Ranks the candidate streets for a query.
 *
 * @param candidates the streets to sort out, as the index returned them.
 * @param query the query taken apart by [parseQuery].
 * @param stopWords the stop words, which weigh less in the match.
 * @param origin reference point for proximity — the user's position, or the
 *   centre of the map. `null` if neither is known.
 * @param limit how many results to return.
 * @return the retained streets, best first.
 */
public fun rankStreets(
    candidates: Iterable<SearchableStreet>,
    query: AddressQuery,
    stopWords: Set<String>,
    origin: Coordinates?,
    limit: Int,
): List<ScoredStreet> {
    if (query.isEmpty || limit <= 0) return emptyList()

    val scored = candidates.mapNotNull { street ->
        val quality = matchQualityOf(street, query.terms, stopWords)
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
 */
private fun matchQualityOf(
    street: SearchableStreet,
    terms: List<String>,
    stopWords: Set<String>,
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

    for (term in terms) {
        val againstName = bestScoreAmong(term, nameWords)
        val best = maxOf(
            againstName.score * PROPER_NAME_WEIGHT,
            bestScoreAmong(term, typeWords).score * STREET_TYPE_WEIGHT,
            bestScoreAmong(term, cityWords).score * CITY_WEIGHT,
        )
        // A stop word, or a two-letter fragment, does not carry enough meaning
        // to rule a street out on its own. Measured on the real index: a letter
        // lost from "rue" — "Re de la Paix" — left no result at all, while the
        // rest of the query designated the street unambiguously.
        val isWeak = term in stopWords || term.length <= SHORT_TERM_LENGTH
        if (best <= 0.0 && !isWeak) return 0.0
        againstName.word?.let(coveredNameWords::add)

        val weight = if (isWeak) WEAK_TERM_WEIGHT else 1.0
        weightedScore += best * weight
        totalWeight += weight
    }
    if (totalWeight == 0.0) return 0.0

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
private fun bestScoreAmong(term: String, words: List<String>): WordMatch {
    var best = WordMatch(0.0, null)
    for (word in words) {
        val score = scoreWord(term, word)
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
 */
private fun scoreWord(term: String, word: String): Double {
    if (term == word) return EXACT_WORD_SCORE
    if (word.startsWith(term)) {
        return PREFIX_FLOOR + PREFIX_RANGE * (term.length.toDouble() / word.length)
    }
    // Falling back on a single-letter word makes no sense: within one mistake
    // it matches any other letter. From two letters on, though, the fallback
    // earns its keep — "ed" for "de", "re" for "rue" — and the low weight given
    // to such fragments contains the noise that follows.
    if (term.length < MINIMUM_FUZZY_LENGTH) return 0.0

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

/** Below two letters, any correction brings anything closer. */
private const val MINIMUM_FUZZY_LENGTH = 2
