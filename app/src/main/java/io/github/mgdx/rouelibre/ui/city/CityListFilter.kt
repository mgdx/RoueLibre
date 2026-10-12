package io.github.mgdx.rouelibre.ui.city

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
