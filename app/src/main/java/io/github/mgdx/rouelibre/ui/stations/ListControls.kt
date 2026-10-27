package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.ui.map.mapControls

/**
 * Whether the station list offers the journey search (SPEC §7.6).
 *
 * **The map's own rule, read from the map's own function**: the list carries
 * the journey button where the map keeps it, and shows it when the map shows
 * its — once the base map is on the device. A second notion of "the data is
 * installed" would sooner or later disagree with the first, and the same
 * button would come and go on one screen and not on the other.
 *
 * It is also the journey's own need, twice over: the journey is worked out on
 * the routing graph and shown on the base map (SPEC §5, §7.4), and the three
 * datasets travel together (SPEC §4.4). A list without them is what the
 * application is reduced to before the download, and offering a journey there
 * would lead to a screen that can only say none can be worked out.
 *
 * The other controls of the list's column need nothing downloaded and are
 * always there: the way to the map, whose panel says what is missing and
 * offers the download; the search, which reads the station feed; and
 * "nearest station first", which reads the position.
 *
 * @param hasBaseMap whether the tiles the map is drawn from are on the device.
 */
internal fun listOffersTheJourney(hasBaseMap: Boolean): Boolean =
    mapControls(hasBaseMap = hasBaseMap, isPicking = false).browsing
