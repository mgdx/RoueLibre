package io.github.mgdx.rouelibre.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * Opens a point in whichever application answers `geo:` — but this one.
 *
 * The application computes journeys, it does not guide along them: turn by turn
 * navigation is out of scope (SPEC §13), and the applications that do it are
 * already installed. What is handed over is therefore a single point, in the
 * `geo:` URI every mapping application on Android understands — the handover a
 * station's sheet offers (SPEC §7.2), a journey's leg (SPEC §7.4) and a place
 * found on the map alike.
 *
 * The label travels with the coordinates so the receiving application shows a
 * named place rather than an anonymous point.
 *
 * **This application answers `geo:` itself** (SPEC §7.8), and on a phone where
 * it is the only one to, or the one kept as the default, handing a point over
 * reopened Roue Libre and started the journey again — the press looked like it
 * had done nothing. It is therefore taken out of the choice: what is offered is
 * the applications that guide, and when there is none the caller says so rather
 * than looping back on itself.
 *
 * The choice itself stays Android's chooser. The application picks no
 * navigation application for the user, here no more than anywhere else.
 *
 * @param place what the point is called, which is what travels with the
 *   coordinates. The receiving application shows a place, and a place is called
 *   "Roubaix Mairie" — it is not called "ride to Roubaix Mairie", which is what
 *   OsmAnd was handed and displayed on a first try.
 * @param position where it stands.
 * @param onNoNavigationApp told, with the sentence to show, when nothing on the
 *   device can take the point. Each screen says it its own way — a snackbar on
 *   its own root — so the message is handed back rather than shown from here.
 */
fun Fragment.handOverToNavigation(
    place: String,
    position: Coordinates,
    onNoNavigationApp: (String) -> Unit,
) {
    val context = requireContext()
    val point = "${position.latitude},${position.longitude}"
    // A place's name holds spaces, and sometimes an ampersand: encoded, or the
    // receiving application reads a truncated label.
    val label = Uri.encode(place)
    val target = Intent(Intent.ACTION_VIEW, "geo:$point?q=$point($label)".toUri())

    val guides = context.packageManager.queryIntentActivities(target, 0)
        .any { it.activityInfo.packageName != context.packageName }
    if (!guides) {
        onNoNavigationApp(getString(R.string.station_no_navigation_app))
        return
    }

    val chooser = Intent.createChooser(target, getString(R.string.journey_navigate))
        .putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context, MainActivity::class.java)),
        )
    try {
        startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // The chooser itself can be missing on a stripped-down system.
        onNoNavigationApp(getString(R.string.station_no_navigation_app))
    }
}
