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
 */
public fun foldForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    for (character in decomposed) {
        when {
            // Diacritical marks vanish, their base letter having already been
            // separated out by the decomposition.
            character.isMarkCharacter() -> Unit
            character.isLetterOrDigit() -> builder.append(character.lowercaseChar())
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
internal fun stationMatches(entry: StationWithAvailability, foldedQuery: String): Boolean {
    if (foldedQuery.isEmpty()) return true
    val haystack = foldForSearch(
        listOfNotNull(entry.station.name, entry.station.postalCode).joinToString(" "),
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
 * @return the retained stations, in the same order. A query that is empty or
 *   made of punctuation alone returns the list untouched rather than empty:
 *   clearing a search field must bring everything back.
 */
public fun filterStations(
    stations: List<StationWithAvailability>,
    query: String,
): List<StationWithAvailability> {
    val folded = foldForSearch(query)
    if (folded.isEmpty()) return stations
    return stations.filter { stationMatches(it, folded) }
}
