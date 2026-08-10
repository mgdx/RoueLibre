package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo

/**
 * The order the station list is shown in.
 *
 * Two orders, and the position decides between them. Someone standing in the
 * conurbation is looking for the station they are about to walk to, so the
 * nearest comes first. Someone consulting from elsewhere — planning a trip, or
 * simply out of the served area — has no nearest station, and an order drawn
 * from a position a hundred kilometres away would be arbitrary; the alphabet at
 * least lets them find a name they know.
 *
 * @param stations the stations to show, in alphabetical order.
 * @param around where the user is, or `null` if unknown or outside the city.
 * @return the same stations, nearest first, or untouched when there is no
 *   usable position.
 */
public fun orderStations(
    stations: List<StationWithAvailability>,
    around: Coordinates?,
): List<StationWithAvailability> {
    if (around == null) return stations
    return stations.sortedBy { it.station.position.distanceInMetresTo(around) }
}
