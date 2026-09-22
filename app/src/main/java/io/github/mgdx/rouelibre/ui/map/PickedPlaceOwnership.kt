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
 * The decision is written here, apart from the screen, because both halves of
 * the rule need it and neither could be tested inside the fragment: the change
 * of city while the map is up, and the state an instance is rebuilt from after
 * the process has been killed on the other side of that change.
 *
 * Pure Kotlin, no Android (SPEC §14).
 *
 * @param cityItWasPickedIn the network identifier of the city the point was
 *   designated in, or `null` when that is not known — the first time a
 *   configuration is applied to a screen, where no city has been served yet
 *   and nothing has changed, and a state bundle written before this identifier
 *   was recorded. Not knowing is not a reason to throw away what the user
 *   designated, so the point stands.
 * @param cityNowServed the network identifier of the active city, or `null`
 *   when none is chosen.
 */
internal fun designatedPlaceSurvives(cityItWasPickedIn: String?, cityNowServed: String?): Boolean =
    cityItWasPickedIn == null || cityItWasPickedIn == cityNowServed
