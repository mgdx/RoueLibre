package io.github.mgdx.rouelibre.core.address

import kotlin.math.abs
import kotlin.math.min

/**
 * How many mistakes are allowed for a word of this length (SPEC §4.3).
 *
 * One mistake below eight characters, two beyond: on a short word, two mistakes
 * change it so much that the fallback would bring back more noise than service
 * — "gare" is two mistakes away from "gard", "care", "gaz", and from a good
 * part of the dictionary.
 */
public fun toleratedMistakes(word: String): Int = if (word.length < 8) 1 else 2

/**
 * The edit distance between two words, capped.
 *
 * A variant of **Damerau-Levenshtein**: on top of insertions, deletions and
 * substitutions it adds the **transposition of two neighbouring letters**, the
 * commonest mistake on a touch keyboard, which plain Levenshtein would count as
 * two.
 *
 * This is the so-called "optimal string alignment" variant: it does not allow a
 * letter already transposed to undergo another edit. That case is pathological
 * on street names, and ruling it out keeps the algorithm to two rows of a table
 * instead of a full matrix and a dictionary of last occurrences.
 *
 * The cap is not a comfort: it allows abandoning a comparison as soon as the
 * current row exceeds the threshold, which rules out the overwhelming majority
 * of the index's twenty thousand entries within a few characters.
 *
 * @param source the first word, already normalised.
 * @param target the second word, already normalised.
 * @param maximum the distance beyond which the result no longer matters.
 * @return the exact distance if it is at most [maximum], otherwise a value
 *   strictly greater than [maximum], of which only that property is guaranteed.
 */
public fun boundedDamerauLevenshteinDistance(source: String, target: String, maximum: Int): Int {
    val beyond = maximum + 1
    if (abs(source.length - target.length) > maximum) return beyond
    if (source == target) return 0
    if (source.isEmpty()) return target.length.coerceAtMost(beyond)
    if (target.isEmpty()) return source.length.coerceAtMost(beyond)

    // Three rows are enough: the current one, the previous one, and the one
    // before that — the only one transposition needs.
    var beforePrevious = IntArray(target.length + 1)
    var previous = IntArray(target.length + 1) { it }
    var current = IntArray(target.length + 1)

    for (sourceIndex in 1..source.length) {
        current[0] = sourceIndex
        var rowMinimum = current[0]
        for (targetIndex in 1..target.length) {
            val substitutionCost =
                if (source[sourceIndex - 1] == target[targetIndex - 1]) 0 else 1
            var best = min(
                current[targetIndex - 1] + 1, // insertion
                previous[targetIndex] + 1, // deletion
            )
            best = min(best, previous[targetIndex - 1] + substitutionCost)
            val isTransposition = sourceIndex > 1 &&
                targetIndex > 1 &&
                source[sourceIndex - 1] == target[targetIndex - 2] &&
                source[sourceIndex - 2] == target[targetIndex - 1]
            if (isTransposition) {
                best = min(best, beforePrevious[targetIndex - 2] + 1)
            }
            current[targetIndex] = best
            rowMinimum = min(rowMinimum, best)
        }
        // A row entirely above the cap can never come back down: the rows that
        // follow only grow.
        if (rowMinimum > maximum) return beyond

        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
    }
    return previous[target.length]
}
