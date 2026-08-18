package io.github.mgdx.rouelibre.ui.map

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
