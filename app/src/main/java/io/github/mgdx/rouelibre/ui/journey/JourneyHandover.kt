package io.github.mgdx.rouelibre.ui.journey

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.ui.ChoiceDialogFragment
import io.github.mgdx.rouelibre.ui.MainActivity

/**
 * Hands a journey over to a navigation application (SPEC §7.4).
 *
 * The application computes the journey, it does not guide you along it: turn by
 * turn navigation is out of scope (SPEC §13), and the applications that do it
 * are already installed. What is handed over is therefore a destination, in the
 * `geo:` URI every mapping application on Android understands — the same
 * handover a station's sheet offers (SPEC §7.2).
 *
 * **One leg at a time, because a `geo:` URI carries one point.** No standard
 * scheme carries a route with waypoints: the ones that exist belong to a single
 * application each, and picking one would tie the button to that application.
 * So the press asks which part of the journey is being set off on — walk to the
 * station, ride to the other, walk to the destination — and hands that leg's end
 * over. It is also how a journey is actually lived: one leg at a time, the
 * question asked again at each station.
 */
class JourneyHandover(private val fragment: Fragment, private val onMessage: (String) -> Unit) {

    /**
     * One leg's end, named twice.
     *
     * @property leg how the leg reads in the menu, where the question is which
     *   part of the journey one is setting off on: "ride to Roubaix Mairie".
     * @property place what the point is called, which is what travels with the
     *   coordinates. The application receiving it shows a place, and a place is
     *   called "Roubaix Mairie" — it is not called "ride to Roubaix Mairie",
     *   which is what OsmAnd was handed and displayed on a first try.
     * @property position where it stands.
     */
    private data class Target(val leg: String, val place: String, val position: Coordinates)

    /**
     * Offers the journey's legs, and hands the chosen one over.
     *
     * A journey with a single leg — the walk that replaces a bike trip — asks
     * nothing: a menu of one is a press spent on a choice that does not exist.
     */
    fun offer(journey: ShownJourney) {
        val targets = targetsOf(journey)
        when (targets.size) {
            0 -> return
            1 -> targets.first().let { handOver(it.place, it.position) }
            else -> ask(targets)
        }
    }

    private fun targetsOf(journey: ShownJourney): List<Target> = when (val plan = journey.plan) {
        is JourneyPlan.Found -> legsOf(plan.best, journey.destination)
        is JourneyPlan.WalkOnly -> listOf(
            Target(
                leg = fragment.getString(R.string.journey_step_walk_all),
                place = journey.destination.label,
                position = journey.destination.position,
            ),
        )

        // One leg, so no question is asked: the ride ends where the journey
        // does, and a menu of one is a press spent on a choice that does not
        // exist.
        is JourneyPlan.OwnBike -> listOf(
            Target(
                leg = fragment.getString(R.string.journey_step_ride_all),
                place = journey.destination.label,
                position = journey.destination.position,
            ),
        )

        is JourneyPlan.Impossible -> emptyList()
    }

    /**
     * The three legs, named by where each one ends.
     *
     * The wording is the step list's own: the same journey, described the same
     * way from one screen to the next.
     */
    private fun legsOf(option: JourneyOption, destination: JourneyEndpoint) = listOf(
        Target(
            leg = fragment.getString(
                R.string.journey_step_to_station,
                option.departureStation.name,
            ),
            place = option.departureStation.name,
            position = option.departureStation.position,
        ),
        Target(
            leg = fragment.getString(R.string.journey_step_ride, option.arrivalStation.name),
            place = option.arrivalStation.name,
            position = option.arrivalStation.position,
        ),
        Target(
            leg = fragment.getString(R.string.journey_step_to_destination),
            place = destination.label,
            position = destination.position,
        ),
    )

    private fun ask(targets: List<Target>) {
        ChoiceDialogFragment.ask(
            manager = fragment.childFragmentManager,
            requestKey = LEG_ANSWER,
            title = R.string.journey_navigate_which,
            labels = targets.map { it.leg },
            // The menu is left by the back gesture, as it always was: a
            // "cancel" row under three legs would be a fourth thing to read.
            dismiss = ChoiceDialogFragment.NO_BUTTON,
            // The ends themselves travel with the question. The journey they
            // were read from is recomputed when the screen is rebuilt, and a
            // recomputation is a fresh reading of the availability: it can
            // come back with other stations, and the index would then name a
            // leg nobody chose.
            payload = Bundle().apply {
                putStringArray(PLACES, targets.map { it.place }.toTypedArray())
                putDoubleArray(LATITUDES, targets.map { it.position.latitude }.toDoubleArray())
                putDoubleArray(LONGITUDES, targets.map { it.position.longitude }.toDoubleArray())
            },
        )
    }

    /**
     * Collects the leg chosen from the menu.
     *
     * Called where the screen is built rather than where the menu is put up:
     * the menu is back on its own after a rotation, and its answer would
     * otherwise arrive with nobody listening for it. That rotation used to
     * take the menu away without a word.
     */
    fun listenForTheChosenLeg() {
        ChoiceDialogFragment.onAnswer(
            fragment.childFragmentManager,
            fragment.viewLifecycleOwner,
            LEG_ANSWER,
        ) { chosen, payload ->
            val places = payload.getStringArray(PLACES) ?: return@onAnswer
            val latitudes = payload.getDoubleArray(LATITUDES) ?: return@onAnswer
            val longitudes = payload.getDoubleArray(LONGITUDES) ?: return@onAnswer
            if (chosen !in places.indices) return@onAnswer
            handOver(places[chosen], Coordinates(latitudes[chosen], longitudes[chosen]))
        }
    }

    /**
     * Opens the point in whichever application answers `geo:` — but this one.
     *
     * The label travels with the coordinates so the receiving application shows
     * a named place rather than an anonymous point.
     *
     * **This application answers `geo:` itself** (SPEC §7.8), and on a phone
     * where it is the only one to, or the one kept as the default, handing a
     * leg over reopened Roue Libre and started the journey again — the press
     * looked like it had done nothing. It is therefore taken out of the
     * choice: what is offered is the applications that guide, and when there
     * is none the screen says so rather than looping back on itself.
     *
     * The choice itself stays Android's chooser. The application picks no
     * navigation application for the user, here no more than anywhere else.
     */
    private fun handOver(place: String, position: Coordinates) {
        val context = fragment.requireContext()
        val point = "${position.latitude},${position.longitude}"
        // A station's name holds spaces, and sometimes an ampersand: encoded,
        // or the receiving application reads a truncated label.
        val label = Uri.encode(place)
        val place = Intent(Intent.ACTION_VIEW, "geo:$point?q=$point($label)".toUri())

        val guides = context.packageManager.queryIntentActivities(place, 0)
            .any { it.activityInfo.packageName != context.packageName }
        if (!guides) {
            onMessage(fragment.getString(R.string.station_no_navigation_app))
            return
        }

        val chooser = Intent.createChooser(place, fragment.getString(R.string.journey_navigate))
            .putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, MainActivity::class.java)),
            )
        try {
            fragment.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // The chooser itself can be missing on a stripped-down system.
            onMessage(fragment.getString(R.string.station_no_navigation_app))
        }
    }

    private companion object {
        /** The key the leg chosen from the menu is answered under. */
        const val LEG_ANSWER = "journey-leg-handover"

        /** The ends the menu was put up with, carried across a rebuild. */
        const val PLACES = "places"
        const val LATITUDES = "latitudes"
        const val LONGITUDES = "longitudes"
    }
}
