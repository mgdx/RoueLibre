package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.covers

/**
 * The controls the map screen lays over its map, in the role it is serving.
 *
 * The screen serves two: the main screen one browses availability from, and
 * the picker one aims a journey's end with (SPEC §7.1, §7.3). Each has its own
 * controls, and neither has any while the base map is missing — the panel that
 * says so covers the whole screen, and a control left visible under it is
 * invisible without ceasing to be clickable.
 *
 * @property browsing the controls of the main screen: the settings, the
 *   station list, the address search, the journey and the availability mode.
 * @property picking the crosshair and the button that confirms the point aimed
 *   at.
 */
internal data class MapControls(val browsing: Boolean, val picking: Boolean) {

    /** Serves both roles, and neither of them without a map to look at. */
    val locateMe: Boolean get() = browsing || picking

    /**
     * The compass, which serves both roles too: a map one can turn is a map
     * one can be lost on, whether one is browsing it or aiming at a point.
     *
     * This says the button is allowed on the screen, not that it is on it: it
     * only ever appears once the map is off north (see [compassNeedle]).
     */
    val compass: Boolean get() = browsing || picking
}

/**
 * What the map screen shows over its map, given what it has and what it is for.
 *
 * Held here rather than in the fragment so that the rule can be read, and
 * tested, without an Android runtime (SPEC §14).
 *
 * @param hasBaseMap whether the tiles the map is drawn from are on the device.
 * @param isPicking whether the screen was opened to designate a point.
 */
internal fun mapControls(hasBaseMap: Boolean, isPicking: Boolean): MapControls = MapControls(
    browsing = hasBaseMap && !isPicking,
    picking = hasBaseMap && isPicking,
)

/**
 * Whether the map offers a button that brings the camera onto a named place
 * (SPEC §7.1).
 *
 * Only among the main screen's controls, only for a place the user has
 * named, and only while the setting asks for it. **A place the city in service
 * does not cover has no button**: the camera is penned inside that city
 * (see [ServedAreaCamera]), so the press would stop at the nearest edge and
 * pass that off as home — the reading "locate me" already refuses for a
 * position off the map. The place itself is kept, and its button comes back
 * with its city.
 *
 * @param place the place named, or `null` when none is.
 * @param wanted whether the settings ask for the buttons.
 * @param browsing whether the main screen's controls are up at all.
 * @param servedArea the box of the city in service, `null` when it declares
 *   none — and nothing is then outside anything.
 */
internal fun offersCentringOn(
    place: Coordinates?,
    wanted: Boolean,
    browsing: Boolean,
    servedArea: BoundingBox?,
): Boolean = place != null && wanted && browsing && servedArea.covers(place)
