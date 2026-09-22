package io.github.mgdx.rouelibre.ui.map

/**
 * Whether the point designated on the map still belongs to the city served.
 *
 * A designated place is an address of one conurbation: the search that found
 * it read that city's index, and its coordinates only mean something inside
 * that city's data. Serving another one therefore leaves it pointing at ground
 * the telephone has not got — an address of Washington was seen still named at
 * the bottom of the map of Lille, with its marker on it.
 *
 * The decision is written here, apart from the screen, because it is the whole
 * of the rule and could not be tested inside the fragment. It is asked once,
 * where a city is applied to the map, and that single question covers the city
 * changed under a map that is up as well as the state a screen is rebuilt from
 * after the process has been killed.
 *
 * Pure Kotlin, no Android (SPEC §14).
 *
 * @param cityOfTheDesignatedPlace the network identifier of the city the point
 *   was designated in, or `null` when no point is designated — there is then
 *   nothing to throw away — and when the state bundle a point came from was
 *   written before this identifier was recorded. Not knowing is no reason to
 *   destroy what the user designated, so the point stands.
 * @param cityNowServed the network identifier of the city being served, or
 *   `null` when none is chosen.
 */
internal fun designatedPlaceSurvives(
    cityOfTheDesignatedPlace: String?,
    cityNowServed: String?,
): Boolean = cityOfTheDesignatedPlace == null || cityOfTheDesignatedPlace == cityNowServed
