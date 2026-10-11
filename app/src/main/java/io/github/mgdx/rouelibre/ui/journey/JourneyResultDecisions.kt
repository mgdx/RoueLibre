package io.github.mgdx.rouelibre.ui.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.routing.RouteLeg

/**
 * The decisions the journey result takes about what it shows, apart from the
 * views that show it (SPEC §7.4).
 *
 * Pure Kotlin, so that each can be read and tested without an Android runtime
 * (SPEC §14).
 */

/**
 * The track the map is framed on: the whole journey, its legs end to end.
 *
 * A journey with no station runs in one leg, which is the track — the direct
 * walk, or the ride on one's own bike. A journey that could not be composed has
 * no track at all.
 */
internal fun trackOf(plan: JourneyPlan?): List<Coordinates> = when (plan) {
    is JourneyPlan.Found -> plan.best.walkToStation?.geometry.orEmpty() +
        plan.best.ride.geometry +
        plan.best.walkToDestination?.geometry.orEmpty()

    is JourneyPlan.WalkOnly -> plan.directWalk.geometry
    is JourneyPlan.OwnBike -> plan.ride.geometry
    is JourneyPlan.Impossible, null -> emptyList()
}

/**
 * Whether a track has an extent the map can be framed on.
 *
 * A journey of fewer than two points has none — an impossible one, most often.
 * The framing is then dropped rather than kept from the previous answer, and
 * the button offering to come back to it goes with it: there is nothing left
 * to come back to.
 */
internal fun canBeFramed(track: List<Coordinates>): Boolean = track.size >= 2

/**
 * The leg of a journey that runs from one end to the other with no station in
 * it — the walk, or the ride on one's own bike — or `null` for any other plan.
 */
internal fun soleLegOf(plan: JourneyPlan?): RouteLeg? = when (plan) {
    is JourneyPlan.OwnBike -> plan.ride
    is JourneyPlan.WalkOnly -> plan.directWalk
    else -> null
}

/**
 * Whether the screen offers its two ways on — the detail and the handover to a
 * navigation application.
 *
 * An impossible journey has neither a detail to open nor a point to hand over.
 */
internal fun offersWaysOn(plan: JourneyPlan?): Boolean =
    plan is JourneyPlan.Found || plan is JourneyPlan.WalkOnly || plan is JourneyPlan.OwnBike

/**
 * The margin left around the track, in pixels.
 *
 * Wide enough for the markers, which are drawn centred on their point and would
 * otherwise be cut in half at the edge — and it never eats more than a quarter
 * of the map: on a short one it gives way rather than leaving the framing
 * nothing to work with.
 *
 * @param wantedPixels the margin asked for, already converted from dp.
 */
internal fun frameMarginPixels(wantedPixels: Int, width: Int, height: Int): Int =
    wantedPixels.coerceAtMost(minOf(width, height) / 4)
