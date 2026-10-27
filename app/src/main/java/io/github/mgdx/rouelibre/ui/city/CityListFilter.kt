package io.github.mgdx.rouelibre.ui.city

import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.config.filterCities

/**
 * What the user narrowed the city list by: the search typed and the country chosen.
 *
 * @property query the search field's text, raw.
 * @property country the code of the country chosen, `null` for every country.
 */
data class CityListFilter(val query: String, val country: String?)

/**
 * Whether the list must go back to its head once the new rows are shown.
 *
 * The list's differ keeps the row on show in place when rows are inserted
 * above it, so widening the filter left the list scrolled down to what used to
 * head it, with the installed cities — always first — out of sight. A new
 * filter is a new list to read from the top. Anything else that rebuilds the
 * rows — a download's progress, an installation, a catalogue refreshed —
 * leaves the filter as it was, and the place the user scrolled to with it.
 *
 * @param previous the filter the rows on show were drawn with, `null` before
 *   any: the first rows have no earlier place to leave, and a place restored
 *   after a rotation must be kept.
 */
fun shouldReturnToTop(previous: CityListFilter?, current: CityListFilter): Boolean =
    previous != null && previous != current

/**
 * The city in service as the rows on show were drawn: the row heading them.
 *
 * @property id the identifier of that city, `null` when none is in service.
 */
data class ActiveCity(val id: String?)

/**
 * Whether choosing another city must send the list back to its head.
 *
 * The city in service is sorted to the top, so choosing one lower down moves
 * its row above every other. The list's differ then keeps the row that headed
 * the list in place, and the city just chosen sat out of sight above it on the
 * way back from the storage screen. The new head is the row to show.
 *
 * @param previous the city in service as the rows on show were drawn, `null`
 *   before any: a screen rebuilt after a rotation has no earlier choice to
 *   compare with, and must keep the place restored.
 */
fun activeCityMoved(previous: ActiveCity?, current: ActiveCity): Boolean =
    previous != null && previous != current

/** Why the city list came up empty, which settles what the screen says and offers. */
enum class NoMatchReason {
    /** Nothing anywhere answers to the search: its spelling is what to look at. */
    NAME,

    /** Cities answer to the search, all in countries the filter leaves out. */
    COUNTRY,
}

/**
 * Why the list narrowed to nothing.
 *
 * Telling someone to check the spelling of "Lille" while it is the country
 * chosen that leaves Lille out sends them looking in the wrong place, and the
 * search they would clear was right. A blank search answers to every city, so
 * with one it can only be the country — not that the screen should ever get
 * there, a country being offered only for the cities it has.
 *
 * @param cities every city of the catalogue, before either filter.
 * @param country the code of the country chosen, `null` for every country.
 * @param letterFolds the search's own folding of letters, so that "elsewhere"
 *   is judged exactly as the list itself was.
 */
fun noMatchReason(
    cities: List<CityEntry>,
    query: String,
    country: String?,
    letterFolds: Map<Char, String>,
): NoMatchReason = if (country != null && filterCities(cities, query, letterFolds).isNotEmpty()) {
    NoMatchReason.COUNTRY
} else {
    NoMatchReason.NAME
}
