package io.github.mgdx.rouelibre.ui.journey

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
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

    /** One leg's end: where it stops, and under what name. */
    private data class Target(val label: String, val position: Coordinates)

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
            1 -> handOver(targets.first())
            else -> ask(targets)
        }
    }

    private fun targetsOf(journey: ShownJourney): List<Target> = when (val plan = journey.plan) {
        is JourneyPlan.Found -> legsOf(plan.best, journey.destination)
        is JourneyPlan.WalkOnly -> listOf(
            Target(journey.destination.label, journey.destination.position),
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
            fragment.getString(R.string.journey_step_to_station, option.departureStation.name),
            option.departureStation.position,
        ),
        Target(
            fragment.getString(R.string.journey_step_ride, option.arrivalStation.name),
            option.arrivalStation.position,
        ),
        Target(
            fragment.getString(R.string.journey_step_to_destination),
            destination.position,
        ),
    )

    private fun ask(targets: List<Target>) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.journey_navigate_which)
            .setItems(targets.map { it.label }.toTypedArray()) { _, chosen ->
                handOver(targets[chosen])
            }
            .show()
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
    private fun handOver(target: Target) {
        val context = fragment.requireContext()
        val point = "${target.position.latitude},${target.position.longitude}"
        // A station's name holds spaces, and sometimes an ampersand: encoded,
        // or the receiving application reads a truncated label.
        val label = Uri.encode(target.label)
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
}
