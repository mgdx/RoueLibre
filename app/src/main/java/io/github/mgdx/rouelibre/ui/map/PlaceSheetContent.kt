package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.covers

/**
 * What a place's sheet shows, decided before anything is drawn (SPEC §7.2).
 *
 * @property title the line at the top of the sheet.
 * @property offersJourney whether the two journey buttons are live.
 */
internal data class PlaceSheetContent(val title: String, val offersJourney: Boolean)

/**
 * Puts a place's sheet into words and decides what it may offer.
 *
 * **The title is the place's own label, and "This place" only when there is
 * none.** A point comes to the map from three directions — an address found by
 * the search, a place received from another application (SPEC §7.8), the point
 * retrieved after the phone was turned — and only the first is guaranteed to
 * be named: a bare `geo:50.63,3.06` carries no label at all, and a heading left
 * blank would read as a sheet that failed to load rather than as a point nobody
 * named.
 *
 * **A place beyond the installed data is offered no journey.** The route is
 * computed over a graph cut from the city's box, so a point outside it has no
 * path to or from anywhere: offering the button and answering "no usable route"
 * after the computation tells the user they got something wrong, when it was
 * never on offer. It is the reading the station sheet already gives a station
 * beyond the data, and the one the application gives a place received from
 * outside the box, which is precisely how such a point reaches this map.
 * Handing the place to a navigation application stays: that one does not run on
 * our graph.
 *
 * @param label what the point is called, empty when nothing named it.
 * @param position where it stands.
 * @param coveredArea the reference box of the city in service, or `null` while
 *   it is unknown — nothing is beyond a box that does not exist.
 * @param whenUnnamed the heading for a point nobody named.
 */
internal fun placeSheetContent(
    label: String,
    position: Coordinates,
    coveredArea: BoundingBox?,
    whenUnnamed: String,
): PlaceSheetContent = PlaceSheetContent(
    title = label.trim().ifEmpty { whenUnnamed },
    offersJourney = coveredArea.covers(position),
)
