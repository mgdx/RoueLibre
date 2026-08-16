package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.BoundingBox

/**
 * Whether the installed data reaches a station at all (SPEC §4).
 *
 * The map tiles, the routing graph and the address index are all cut from one
 * reference box, and a feed is under no obligation to stay inside it. Fifteen
 * of the three hundred and thirty-two networks served publish at least one
 * station outside their own box, of two quite different kinds:
 *
 *  * producers' leftovers — a test entry at Hunedoara sitting in Bucharest
 *    290 km away, two of Santiago's 9 972 km away, two cars at
 *    sharedmobility.ch;
 *  * and real stations of the network, which is what makes this worth
 *    answering: Blue-bike serves Namur, Mons, De Panne and Libramont, 75 km
 *    from the box drawn around the rest.
 *
 * **They are shown, and said to be beyond the data.** Hiding them was refused:
 * six networks would silently lose real stations, and a rider standing at Namur
 * station with a Blue-bike in front of them would be told their network has
 * none there — while the count of bikes waiting, which comes live from the
 * feed, needs no local data to be true. Widening the box was refused too: it is
 * what every dataset is cut from, and stretching a 1.2 MB city to Bucharest, or
 * Santiago's to the far side of the world, costs orders of magnitude for one
 * entry — which is exactly why `tools/compute_bbox.py` sets those stations
 * aside when it draws the box.
 *
 * So the station stays, and what the application cannot deliver — a journey,
 * over a graph that stops long before it — is withdrawn before it is offered,
 * rather than after a computation that could only fail.
 *
 * @param area the reference box of the city in service, or `null` when none is
 *   known. Nothing is beyond a box that does not exist: with no box the
 *   application has no ground to call a station unreachable.
 */
public fun Station.isBeyondCoveredArea(area: BoundingBox?): Boolean {
    if (area == null || !area.isUsable) return false
    return position !in area
}
