package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.ui.formatClimb
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration

/**
 * The line under the total time, written once for the two screens that show it.
 *
 * The result screen and its detail describe the same journey (SPEC §7.4), and a
 * summary worded differently on the second would read as a second journey.
 */

/**
 * What makes the total up: the walking, the riding, the distance, the climb.
 *
 * The climb joins the line behind the separator the rest of the interface uses,
 * and only where there is one to name: a journey that gains nothing has nothing
 * to say about hills, and "0 m of climb" is a figure the reader has to weigh
 * before discarding.
 */
fun Context.journeySummary(option: JourneyOption): String {
    val summary = getString(
        R.string.journey_summary,
        formatDuration(option.walkToStation.duration + option.walkToDestination.duration),
        formatDuration(option.ride.duration),
        formatDistance(option.distanceMetres.toDouble()),
    )
    val climb = formatClimb(option.climbMetres, option.distanceMetres) ?: return summary
    return getString(
        R.string.address_detail,
        summary,
        getString(R.string.journey_climb, climb),
    )
}

/**
 * The summary of a journey made on foot from end to end.
 *
 * It says why there is no bike in it: because there was none to be had, or
 * because walking gets there sooner (SPEC §6). The climb, where there is one,
 * is written into the sentence rather than hung after it: this summary ends on
 * a full stop and a reason, and a clause added behind that would read as an
 * afterthought to a finished sentence.
 *
 * @param isQuickerThanTheBike the walk won the comparison, rather than being
 *   all that was left.
 */
fun Context.walkSummary(walk: RouteLeg, isQuickerThanTheBike: Boolean): String {
    val distance = formatDistance(walk.distanceMetres.toDouble())
    val climb = formatClimb(walk.ascentMetres, walk.distanceMetres)
        ?: return getString(
            if (isQuickerThanTheBike) {
                R.string.journey_walk_is_quicker
            } else {
                R.string.journey_walk_only
            },
            distance,
        )
    return getString(
        if (isQuickerThanTheBike) {
            R.string.journey_walk_is_quicker_climb
        } else {
            R.string.journey_walk_only_climb
        },
        distance,
        climb,
    )
}
