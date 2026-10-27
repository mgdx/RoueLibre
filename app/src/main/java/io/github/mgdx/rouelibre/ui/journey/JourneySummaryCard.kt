package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import androidx.core.view.isVisible
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.journey.JourneyMinutes
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.journey.inShownMinutes
import io.github.mgdx.rouelibre.core.journey.shownMinutes
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.databinding.ViewJourneyResultDetailBinding
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.formatDuration
import io.github.mgdx.rouelibre.ui.formatMinutes
import io.github.mgdx.rouelibre.ui.toUserMessage

/**
 * The card under the journey result's map: how long, what makes it up, and
 * the journey's shape seen at once (SPEC §7.4).
 *
 * One first looks at where one is going, then reads how long it takes and
 * through which stations: this is the reading. It says nothing of the map,
 * which [JourneyTrackOnMap] draws.
 *
 * @param detail the card's views, while they exist.
 * @param lentFleet what the network lends, `null` until the reading lands.
 * @param ownBikeKind what the rider said their own bike is.
 * @param freeDocksShown whether the network's free docks are worth showing,
 *   which words the reason a journey could not end (SPEC §7.1).
 */
internal class JourneySummaryCard(
    private val detail: () -> ViewJourneyResultDetailBinding?,
    private val lentFleet: () -> FleetDescription?,
    private val ownBikeKind: () -> OwnBikeKind,
    private val freeDocksShown: () -> Boolean,
) {

    /** Writes the card for a journey worked out. */
    fun show(state: JourneyUiState) {
        showWaysOn(state)
        val option = state.chosen
        if (option == null) {
            showJourneyWithoutStations(state)
            return
        }

        // Rounded once for the three figures the card carries: the total, the
        // sentence beside it and the band underneath describe the same journey,
        // and each rounding its own legs made them disagree by a minute or two
        // (see JourneyMinutes).
        val minutes = option.shownMinutes()
        val views = detail() ?: return
        showTotal(views.root.context.formatMinutes(minutes.total))
        showSummary(option)
        showShape(option, minutes, state.rideKind)
    }

    /**
     * What makes the total up, and what waits at the departure station.
     *
     * The two kinds are counted apart where the city lends both: it is what
     * decides whether the walk to the station is the one wanted (SPEC §7.4),
     * and the counts are the frozen ones the journey was worked out on, like
     * the total time beside them.
     */
    fun showSummary(option: JourneyOption) {
        val views = detail() ?: return
        val context = views.root.context
        views.summary.text = context.journeySummary(
            option,
            minutes = option.shownMinutes(),
            atDeparture = context.bikesAtDeparture(option.bikeSplitAtDeparture(lentFleet())),
        )
    }

    /**
     * Writes the sentence under the total again for the rider's own bike, once
     * its kind is known (SPEC §7.3, §7.6) — on a journey ridden on it, and on
     * no other.
     */
    fun showOwnBikeSentence(plan: JourneyPlan?) {
        val ride = (plan as? JourneyPlan.OwnBike)?.ride ?: return
        val views = detail() ?: return
        views.summary.text = views.root.context.ownBikeSummary(ride, ownBikeKind())
    }

    /**
     * Writes what stands where the total time goes.
     *
     * **A figure is one line, whatever digits the locale counts it in.** On a
     * device set to `ar` the total read "٦ min" — an Arabic-Indic six, then a
     * Latin word, two runs of opposite direction — and broke in two inside a
     * view measured at 143 px, which is the width that very text asks for: the
     * line's runs need a hair more than the width the measurement had promised,
     * and the wrap follows. The block then stood twice as tall, with the
     * chevron — centred on this view — beside the middle of the summary rather
     * than beside the figure, and the map lost the height. One line is also
     * what a figure is: it is read at a glance, and a duration split across two
     * lines is not read at all.
     *
     * A sentence is another matter and may take the lines it needs: "No
     * journey" is the one thing this view holds that is not a figure, and at
     * the largest text sizes it needs them.
     *
     * `setSingleLine` rather than `maxLines`, and the difference is the whole
     * repair: a maximum of one line still breaks the text and then shows the
     * first line alone — "٦" without its unit — where a single line is laid out
     * as one run and kept whole.
     *
     * @param figure true when the view is being handed a duration.
     */
    private fun showTotal(text: CharSequence, figure: Boolean = true) {
        val views = detail() ?: return
        views.totalTime.setSingleLine(figure)
        views.totalTime.text = text
    }

    /**
     * The two ways on the screen offers, shown only when there is a journey —
     * see [offersWaysOn].
     *
     * The summary block opens the journey in full, and the button hands a leg
     * of it to a navigation application. Without a journey the chevron and the
     * button go, and the block stops answering to a press rather than opening
     * an empty screen.
     */
    private fun showWaysOn(state: JourneyUiState) {
        val views = detail() ?: return
        val hasJourney = offersWaysOn(state.plan)
        views.summaryBlock.isClickable = hasJourney
        views.summaryBlock.isFocusable = hasJourney
        views.detailChevron.isVisible = hasJourney
        views.navigate.isVisible = hasJourney
    }

    /**
     * Draws the journey's shape, with how long each leg takes and how far it
     * runs.
     *
     * The step list reads one line at a time, and is folded away besides; this
     * is the whole journey seen at once, in the drawing the search screen uses
     * for it.
     *
     * @param computedFor the kind of bike the journey was worked out for, which
     *   the ride's stroke then names (SPEC §7.4).
     */
    private fun showShape(
        option: JourneyOption,
        minutes: JourneyMinutes,
        computedFor: WantedBikeKind?,
    ) {
        val views = detail() ?: return
        val context = views.root.context
        views.shape.rideKind = rideKindOf(option.departure, computedFor)
        // Either walk may be missing, and the drawing then holds two strokes
        // and three discs. No access walk on a journey begun at a bike outside
        // the stations — the bike, the arrival station, the destination
        // (SPEC §7.4); no final walk where the destination is the arrival
        // station itself, where the last disc is an end of the journey and is
        // drawn as one (SPEC §7.4.1).
        views.shape.departureMarker = JourneyMarkers.departureMarkerOf(option.departure)
        views.shape.legs = listOfNotNull(
            option.walkToStation?.let {
                context.legOf(it, minutes.walkToStation, isRide = false)
            },
            context.legOf(option.ride, minutes.ride, isRide = true),
            option.walkToDestination?.let {
                context.legOf(it, minutes.walkToDestination, isRide = false)
            },
        )
    }

    private fun Context.legOf(leg: RouteLeg, minutes: Int, isRide: Boolean) = JourneyShapeView.Leg(
        isRide = isRide,
        duration = formatMinutes(minutes),
        distance = formatDistance(leg.distanceMetres.toDouble()),
    )

    /**
     * Shows a journey with no station in it, or says what is missing.
     *
     * Two of them run from one end to the other in a single leg: the walk that
     * SPEC §6 requires when no nearby station has a bike — or when walking is
     * quicker than fetching one — and the ride of somebody on their own bike,
     * which never had a station in it to begin with (SPEC §7.3). What is left
     * is a journey that could not be composed at all, which says why.
     */
    private fun showJourneyWithoutStations(state: JourneyUiState) {
        val views = detail() ?: return
        val context = views.root.context
        val plan = state.plan
        val ownBike = plan as? JourneyPlan.OwnBike
        val walkOnly = plan as? JourneyPlan.WalkOnly
        val soleLeg = soleLegOf(plan)
        val total = soleLeg?.let { context.formatDuration(it.duration) }
        showTotal(total ?: context.getString(R.string.journey_none_title), figure = total != null)
        views.summary.text = when {
            ownBike != null -> context.ownBikeSummary(ownBike.ride, ownBikeKind())
            !state.hasStations -> context.getString(R.string.journey_no_stations)
            walkOnly != null -> context.walkSummary(
                walkOnly.directWalk,
                isQuickerThanTheBike = walkOnly.reason == NoBikeJourney.WalkingIsQuicker,
            )
            else -> context.reasonOf(plan)
        }
        // One stroke between two ends — dotted for the walk, unbroken for the
        // ride: the journey there is, with no station on the way. A single leg
        // is its own total, so there is nothing to apportion here.
        views.shape.departureMarker = null
        views.shape.rideKind = null
        views.shape.legs = soleLeg
            ?.let {
                listOf(context.legOf(it, it.duration.inShownMinutes(), isRide = ownBike != null))
            }
            .orEmpty()
    }

    /**
     * Why there is no journey, in words.
     *
     * The wording lives with the other user-facing failures (`ErrorMessages`),
     * where SPEC §14 wants it: a plan with no reason at all — which does not
     * happen — falls back on the plainest of them.
     */
    private fun Context.reasonOf(plan: JourneyPlan?): String {
        val reason = when (plan) {
            is JourneyPlan.Impossible -> plan.reason
            is JourneyPlan.WalkOnly -> plan.reason
            else -> null
        }
        return reason?.toUserMessage(this, freeDocksShown()) ?: getString(R.string.journey_no_route)
    }
}
