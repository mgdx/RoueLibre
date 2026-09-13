package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import java.text.Collator

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
 * **The alphabet is applied here and nowhere else.** It used to be the cache's
 * `ORDER BY name`, and SQLite compares the bytes: "ÉPINETTES" landed after
 * "TINQUEUX" because U+00C9 is past "Z", and `COLLATE NOCASE` would not have
 * helped since it only knows ASCII. A [Collator] reads "É" as the letter it is
 * drawn over, and it is the only thing that reads "ı" and "İ" as Turkish writes
 * them rather than as accidents of "i".
 *
 * @param stations the stations to show, in any order.
 * @param around where the user is, or `null` if unknown or outside the city.
 * @param byName how names are compared, built by the caller from the language
 *   the interface speaks: the reader scans this list with their own alphabet in
 *   mind, and it is the one language this module cannot guess. A collator keeps
 *   state while it compares, so the caller hands over one of its own rather than
 *   one shared across threads.
 * @return the same stations, nearest first, or in alphabetical order when there
 *   is no usable position.
 */
public fun orderStations(
    stations: List<StationWithAvailability>,
    around: Coordinates?,
    byName: Collator,
): List<StationWithAvailability> {
    if (around != null) {
        return stations.sortedBy { it.station.position.distanceInMetresTo(around) }
    }
    // One collation key per station rather than a collator call per comparison:
    // a network has several hundred stations, the list is rebuilt on every
    // availability refresh and on every keystroke of the search field, and a key
    // compares as a byte string where the collator walks its rules again.
    return stations
        .map { byName.getCollationKey(it.station.name) to it }
        .sortedBy { it.first }
        .map { it.second }
}
