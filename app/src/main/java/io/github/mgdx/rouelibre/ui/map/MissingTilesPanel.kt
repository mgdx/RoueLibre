package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.config.ActiveCity

/**
 * Why the map has no base map to draw, as the panel covering it says
 * (SPEC §4.4, §15.1).
 */
internal enum class MissingTilesReason {
    /**
     * The city chosen went away. Not the first launch's question: this user
     * chose, and what they chose went away. Asking "Which city?" over their own
     * tiles read as the application having forgotten them (SPEC §15.1).
     */
    CityGone,

    /**
     * No city is chosen. It is not data that is missing but the choice of
     * conurbation: offering to install tiles would make no sense while we do
     * not know whose tiles they would be.
     */
    NoCity,

    /**
     * The city's data is on its way: "needs its offline tiles" over a download
     * already under way read as a city that was broken.
     */
    Downloading,

    /** The city is chosen and its base map is not installed. */
    NoTiles,
}

/**
 * What the panel covering a map without tiles says, and which of its parts
 * stand.
 *
 * @property showsMessage whether the sentence under the title is shown. The
 *   message about missing tiles ends by sending the reader to the station
 *   list, which is no answer to somebody designating an end of a journey: in
 *   the picker the title says what is missing and the buttons say what to do
 *   about it. The message about the city, itself, holds in both roles.
 * @property showsWayOut whether the second button is shown. It leads to the
 *   station list, which leads nowhere once the network is gone: there is
 *   nothing left to list. In the picker the same button is the way back, and
 *   it stays.
 */
internal data class MissingTilesPanelContent(
    val reason: MissingTilesReason,
    val showsMessage: Boolean,
    val showsWayOut: Boolean,
)

/**
 * Settles what the panel says for the city the map was given.
 *
 * Held apart from the screen so that the rule can be read, and tested, without
 * an Android runtime (SPEC §14). The panel is filled whether or not it is then
 * shown: it only covers the map when there are no tiles.
 *
 * @param transferRunning whether a download of datasets is under way.
 * @param isPicking whether the map was opened to designate a point.
 */
internal fun missingTilesPanel(
    city: ActiveCity,
    transferRunning: Boolean,
    isPicking: Boolean,
): MissingTilesPanelContent {
    val hasCity = city is ActiveCity.Served
    val reason = when {
        city is ActiveCity.NoLongerServed -> MissingTilesReason.CityGone
        !hasCity -> MissingTilesReason.NoCity
        transferRunning -> MissingTilesReason.Downloading
        else -> MissingTilesReason.NoTiles
    }
    return MissingTilesPanelContent(
        reason = reason,
        showsMessage = !hasCity || !isPicking,
        showsWayOut = isPicking || city !is ActiveCity.NoLongerServed,
    )
}
