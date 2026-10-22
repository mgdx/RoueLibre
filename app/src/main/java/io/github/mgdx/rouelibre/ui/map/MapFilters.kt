package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.WantedBikeKind

/**
 * The decisions behind the two filters laid over the main map: what is counted
 * — bikes or free docks — and which kind of bike (SPEC §7.1).
 *
 * Pure Kotlin, so that each can be read and tested without an Android runtime
 * (SPEC §14).
 */

/** The mode a press on the toggle passes to: bikes, then free docks, then bikes. */
internal fun AvailabilityMode.toggled(): AvailabilityMode = when (this) {
    AvailabilityMode.Bikes -> AvailabilityMode.Docks
    AvailabilityMode.Docks -> AvailabilityMode.Bikes
}

/**
 * The mode the markers count in, once the network has said whether its free
 * docks are worth showing (SPEC §7.1).
 *
 * Where they are not, the toggle is gone and the map counts bikes: a map left
 * counting free docks when the network crosses the line would be a column of ∞
 * with no button left to leave it by.
 */
internal fun AvailabilityMode.where(freeDocksShown: Boolean): AvailabilityMode =
    if (freeDocksShown) this else AvailabilityMode.Bikes

/**
 * The kind a press on the filter passes to: all bikes, then mechanical, then
 * electric, then all again.
 *
 * Three states on one button rather than a row of three: the map is scenery
 * for the stations (SPEC §7), and every control laid over it is taken from
 * them.
 */
internal fun WantedBikeKind?.nextOnTheMap(): WantedBikeKind? = when (this) {
    null -> WantedBikeKind.Mechanical
    WantedBikeKind.Mechanical -> WantedBikeKind.Electric
    WantedBikeKind.Electric -> null
}

/**
 * Whether the kind filter has something to filter.
 *
 * It follows the toggle beside it — gone without a base map, and gone while a
 * point is being designated — and needs two more things of its own: a network
 * that really lends both kinds (SPEC §15), since offering to sort bikes into
 * two piles where there is one would promise something nobody can collect,
 * and the bikes being what is counted, a kind having nothing to say about a
 * free dock.
 *
 * @param controlsUp whether the main screen's controls are up at all.
 * @param lent what the network lends, `null` until the reading lands.
 */
internal fun showsBikeKindFilter(
    controlsUp: Boolean,
    mode: AvailabilityMode,
    lent: FleetDescription?,
): Boolean = controlsUp && mode == AvailabilityMode.Bikes && lent?.isMixed == true

/**
 * The kind the markers count, or `null` — all of them.
 *
 * Silent wherever the filter itself is: a network lending one kind, or a
 * reading not landed yet, leaves the markers counting whole stations.
 */
internal fun bikeKindFilterOf(wanted: WantedBikeKind?, lent: FleetDescription?): BikeKindFilter? {
    val kind = wanted ?: return null
    val mixed = lent?.takeIf { it.isMixed } ?: return null
    return BikeKindFilter(kind, mixed.vehicleTypes)
}
