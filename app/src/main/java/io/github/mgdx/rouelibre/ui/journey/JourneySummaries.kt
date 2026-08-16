package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.journey.JourneyMinutes
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.shownMinutes
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.station.BikeSplit
import io.github.mgdx.rouelibre.core.station.splitBikesByKind
import io.github.mgdx.rouelibre.ui.formatClimb
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatMinutes

/**
 * The line under the total time, written once for the two screens that show it.
 *
 * The result screen and its detail describe the same journey (SPEC §7.4), and a
 * summary worded differently on the second would read as a second journey.
 */

/**
 * What makes the total up: the walking, the riding, the distance, the climb,
 * and what is waiting at the station one sets off from.
 *
 * Each part joins the line behind the separator the rest of the interface uses,
 * and only where it has something to say: a journey that gains nothing has
 * nothing to say about hills, and "0 m of climb" is a figure the reader has to
 * weigh before discarding.
 *
 * The two figures of time are the journey's own, apportioned once for the whole
 * screen: this line sits beside the total and above the band of legs, and the
 * three have to add up (see [shownMinutes]).
 *
 * @param minutes the journey's legs as they are shown, rounded together.
 * @param atDeparture the bikes standing at the departure station, already
 *   worded by [bikesAtDeparture], or `null` where the city lends one kind or
 *   the breakdown cannot be trusted.
 */
fun Context.journeySummary(
    option: JourneyOption,
    minutes: JourneyMinutes,
    atDeparture: String? = null,
): String {
    val summary = getString(
        R.string.journey_summary,
        formatMinutes(minutes.walking),
        formatMinutes(minutes.ride),
        formatDistance(option.distanceMetres.toDouble()),
    )
    val climb = formatClimb(option.climbMetres, option.distanceMetres)
        ?.let { getString(R.string.journey_climb, it) }
    return listOfNotNull(summary, climb, atDeparture)
        .reduce { read, next -> getString(R.string.address_detail, read, next) }
}

/**
 * How the bikes at the departure station divided when the journey was worked
 * out, where saying so means something.
 *
 * The counts are the frozen ones the journey was decided on, never the station
 * as it stands now: they sit in a sentence about that journey, beside a total
 * time computed at the same instant (SPEC §7.4.1). Silent unless the city lends
 * both kinds in numbers that make an offer — elsewhere the split announces a
 * shortage that does not exist (SPEC §7.2) — and silent again wherever the
 * feed's own breakdown cannot be trusted.
 */
fun JourneyOption.bikeSplitAtDeparture(fleet: FleetDescription?): BikeSplit? {
    if (fleet == null || !fleet.isMixed) return null
    return splitBikesByKind(bikesByVehicleTypeAtDeparture, bikesAtDeparture, fleet.vehicleTypes)
}

/**
 * Both kinds waiting at the departure station, as the station's own sheet
 * writes them (SPEC §7.2).
 *
 * The same wording on the two screens that show the summary: the reader is
 * deciding which bike they are walking towards, and the two counts answer that
 * together — the electric ones alone left the reader working out the rest by
 * subtraction from a total said elsewhere.
 */
fun Context.bikesAtDeparture(split: BikeSplit?): String? = split?.let {
    getString(
        R.string.journey_bikes_at_departure,
        getString(
            R.string.station_bikes_split,
            resources.getQuantityString(R.plurals.bikes_mechanical, it.mechanical, it.mechanical),
            resources.getQuantityString(R.plurals.bikes_electric, it.electric, it.electric),
        ),
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
