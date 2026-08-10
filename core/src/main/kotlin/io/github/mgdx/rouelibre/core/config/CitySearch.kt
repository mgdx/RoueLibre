package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.station.foldForSearch

/**
 * Filtering the catalogue by name.
 *
 * The catalogue lists every conurbation whose feed has been surveyed, and there
 * are enough of them that scrolling stopped being a way of finding one. The
 * search is the same as the station list's: a scan of a few hundred entries
 * already in memory, immediate on every keystroke, needing neither an index nor
 * a debounce.
 *
 * What is searched is what the row shows — the network's name and the
 * conurbation it runs in. Matching on a field the user cannot see would produce
 * results nothing on screen explains.
 */
public fun filterCities(cities: List<CityEntry>, query: String): List<CityEntry> {
    val folded = foldForSearch(query)
    if (folded.isEmpty()) return cities
    return cities.filter { cityMatches(it, folded) }
}

/**
 * Tells whether a city answers the query.
 *
 * Every word of the query must prefix the name from one of its words onwards,
 * in any order: "lille" finds "V'Lille — Lille", and "lyon velo" finds
 * "Vélo'v — Lyon". Prefix matching covers typing in progress, during which the
 * last word is always incomplete.
 *
 * The match runs over the following words joined together, and not over each
 * word separately, because **networks write the punctuation their users do not
 * type**: "V'Lille" and "Vélo'v" are searched as "vlille" and "velov", which
 * match nothing once the apostrophe has split them in two.
 */
internal fun cityMatches(entry: CityEntry, foldedQuery: String): Boolean {
    if (foldedQuery.isEmpty()) return true
    val words = foldForSearch(
        listOfNotNull(entry.displayName, entry.mainCity).joinToString(" "),
    ).split(' ')
    return foldedQuery.split(' ').all { term ->
        words.indices.any { start ->
            words.subList(start, words.size).joinToString("").startsWith(term)
        }
    }
}
