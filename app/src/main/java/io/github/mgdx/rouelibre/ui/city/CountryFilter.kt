package io.github.mgdx.rouelibre.ui.city

import io.github.mgdx.rouelibre.core.config.CityEntry
import java.text.Collator
import java.util.Locale

/**
 * A country the city list can be narrowed to.
 *
 * @property code the ISO 3166-1 alpha-2 code, upper case, as the filter keeps it.
 * @property name the country's name in the language the interface speaks.
 */
data class CountryChoice(val code: String, val name: String)

/**
 * The countries the city list offers to narrow itself to.
 *
 * Taken from the catalogue itself and from nothing else: a country the
 * catalogue adds tomorrow is offered without a release, and none is offered
 * that would narrow the list to nothing.
 *
 * @param locale the language the interface speaks, which names and orders the
 *   countries: somebody looking for "Allemagne" looks among the "A"s.
 * @return the countries ordered by their name, or nothing when the catalogue
 *   spans fewer than two — a filter with a single choice narrows nothing, and
 *   would only push the list down.
 */
fun countriesOffered(entries: List<CityEntry>, locale: Locale): List<CountryChoice> {
    val codes = entries.map { countryCode(it.country) }.filter { it.isNotEmpty() }.distinct()
    if (codes.size < 2) return emptyList()
    val byName = Collator.getInstance(locale)
    return codes
        .map { CountryChoice(it, countryName(it, locale)) }
        .sortedWith(compareBy(byName) { it.name })
}

/**
 * The cities of one country, in the order they were given.
 *
 * @param country the code of the country chosen, or `null` for every country.
 */
fun filterByCountry(entries: List<CityEntry>, country: String?): List<CityEntry> =
    if (country == null) entries else entries.filter { countryCode(it.country) == country }

/**
 * The country a list kept from before is still to be narrowed to, if any.
 *
 * The catalogue is downloaded again each time the screen opens: a country
 * remembered across a rotation or the death of the process may no longer be
 * offered, and narrowing the list to a country nobody can see chosen would
 * leave it inexplicably short.
 */
fun countryStillOffered(saved: String?, offered: List<CountryChoice>): String? =
    saved?.takeIf { code -> offered.any { it.code == code } }

/**
 * A country's name, falling back on its code when the platform has none.
 *
 * Built through a language tag rather than `Locale("", code)`, whose
 * constructors are deprecated; a code that is no region at all leaves the tag's
 * region empty, and the code itself is then the most honest name to show.
 */
internal fun countryName(code: String, locale: Locale): String =
    Locale.forLanguageTag("und-$code").getDisplayCountry(locale).ifBlank { code }

/** The catalogue's code as the filter compares it: its hand is not ours. */
private fun countryCode(country: String): String = country.trim().uppercase(Locale.ROOT)
