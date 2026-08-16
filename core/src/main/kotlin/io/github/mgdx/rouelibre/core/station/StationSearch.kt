package io.github.mgdx.rouelibre.core.station

import java.text.Normalizer

/**
 * Filtering of the station list by name.
 *
 * Not to be confused with the address search of SPEC §4.3, which queries an
 * offline index of hundreds of thousands of house numbers. Here it is only a
 * matter of finding a station among the few hundred already in memory, which
 * needs neither an index nor debouncing: the scan is immediate and can happen
 * on every keystroke.
 */

/**
 * Reduces a text to its comparable form.
 *
 * The Lille network's feed publishes its names without accents — "Metropole
 * Europeenne de Lille" — while the user types them. Without this folding,
 * searching for "théâtre" would not find "Theatre".
 *
 * Decomposition alone only reaches letters built from a base and a mark. It
 * leaves "ł", "ß" and "ø" whole, because they are letters in their own right,
 * and a name written with one of them would answer to no ordinary keyboard:
 * "bialystok" would not find "Białystok". Those are what [letterFolds] carries.
 *
 * @param letterFolds the letters accent removal cannot reach, from
 *   [io.github.mgdx.rouelibre.core.address.searchLetterFolds]. Empty folds
 *   nothing beyond the marks, which is what a caller with no rule set at hand
 *   gets.
 */
public fun foldForSearch(text: String, letterFolds: Map<Char, String>): String {
    // The order is [AddressNormalizer.normalize]'s, and for its reasons: the
    // folded letters are written in lower case, and a fold may yield two of
    // them — "ß" gives "ss" — whose own accents the decomposition below still
    // has to reach.
    val lowered = text.lowercase()
    val replaced = if (letterFolds.isEmpty()) {
        lowered
    } else {
        val substituted = StringBuilder(lowered.length)
        for (character in lowered) {
            val replacement = letterFolds[character]
            if (replacement == null) {
                substituted.append(character)
            } else {
                substituted.append(replacement)
            }
        }
        substituted.toString()
    }

    val decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    for (character in decomposed) {
        when {
            // Diacritical marks vanish, their base letter having already been
            // separated out by the decomposition.
            character.isMarkCharacter() -> Unit
            character.isLetterOrDigit() -> builder.append(character)
            // Hyphens, apostrophes and dots become word separators, so that
            // "Saint-André" can just as well be searched as two words.
            else -> builder.append(' ')
        }
    }
    return builder.toString().trim().replace(WHITESPACE_RUN, " ")
}

private val WHITESPACE_RUN = Regex("\\s+")

private fun Char.isMarkCharacter(): Boolean = Character.getType(this).let {
    it == Character.NON_SPACING_MARK.toInt() ||
        it == Character.COMBINING_SPACING_MARK.toInt() ||
        it == Character.ENCLOSING_MARK.toInt()
}

/**
 * Tells whether a station answers the query.
 *
 * Every word of the query must prefix a word of the text searched, in any
 * order: "gare lille" finds "Gare Lille Flandres", and so does "flandres gare".
 * Prefix matching covers typing in progress, during which the last word is
 * always incomplete.
 *
 * The postcode is part of the text searched: it is shown on the row, so the
 * user may legitimately expect to be able to type it.
 */
internal fun stationMatches(
    entry: StationWithAvailability,
    foldedQuery: String,
    letterFolds: Map<Char, String>,
): Boolean {
    if (foldedQuery.isEmpty()) return true
    val haystack = foldForSearch(
        listOfNotNull(entry.station.name, entry.station.postalCode).joinToString(" "),
        letterFolds,
    )
    val words = haystack.split(' ')
    return foldedQuery.split(' ').all { term ->
        words.any { it.startsWith(term) }
    }
}

/**
 * Keeps only the stations that answer the query.
 *
 * @param stations the known stations, in their display order.
 * @param query what the user typed, raw.
 * @param letterFolds the letters accent removal cannot reach (see
 *   [foldForSearch]): a network names its stops in its own language, and its
 *   rider types on whatever keyboard they have.
 * @return the retained stations, in the same order. A query that is empty or
 *   made of punctuation alone returns the list untouched rather than empty:
 *   clearing a search field must bring everything back.
 */
public fun filterStations(
    stations: List<StationWithAvailability>,
    query: String,
    letterFolds: Map<Char, String>,
): List<StationWithAvailability> {
    val folded = foldForSearch(query, letterFolds)
    if (folded.isEmpty()) return stations
    return stations.filter { stationMatches(it, folded, letterFolds) }
}
