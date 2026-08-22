package io.github.mgdx.rouelibre.ui.journey

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.data.OwnBikeKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The bike drawn for a journey ridden on the rider's **own** bike
 * (SPEC §7.3, §7.6).
 *
 * The counterpart of `BikeGlyphs`, and deliberately not the same object: that
 * one draws what the **network** lends and is settled by counting the bikes at
 * its stations, this one draws what the rider said they own and is settled by
 * them alone. Their answers must never be read off one another — a mechanical
 * conurbation lends nothing that says anything about the electric bike in
 * somebody's hallway, and the reverse holds just as plainly.
 *
 * Two drawings, and the bolt is the one that has to be earned: everything that
 * is not a declared pedal-assist bike takes the plain glyph. The plain bike
 * promises the least, which is the rule `BikeGlyphs` already follows for a
 * fleet nothing is known about.
 */
object OwnBikeGlyphs {

    /**
     * The bike as an icon, beside the row of a ride on one's own bike.
     *
     * The counterpart of `BikeGlyphs.icon`, which the station journey's ride row
     * already uses: the row's icon follows the bike the row is about, and on
     * this journey that bike is the rider's.
     */
    @DrawableRes
    fun icon(kind: OwnBikeKind): Int = when (kind) {
        OwnBikeKind.Electric -> R.drawable.ic_bike_electric
        OwnBikeKind.Mechanical -> R.drawable.ic_bike
    }

    /** The disc standing at either end of a journey on one's own bike. */
    @DrawableRes
    fun endpointMarker(kind: OwnBikeKind): Int = when (kind) {
        OwnBikeKind.Electric -> R.drawable.marker_journey_station_electric
        OwnBikeKind.Mechanical -> R.drawable.marker_journey_station
    }

    /** The one-stroke illustration of the search screen. */
    @DrawableRes
    fun journeyShape(kind: OwnBikeKind): Int = when (kind) {
        OwnBikeKind.Electric -> R.drawable.illustration_journey_own_bike_electric
        OwnBikeKind.Mechanical -> R.drawable.illustration_journey_own_bike
    }
}

/**
 * Says what the rider's own bike is, and says it again when that changes.
 *
 * Read from disk, so the first answer arrives a beat after the screen: every
 * caller draws the plain bike until then, which is what a bike nobody has
 * declared is read as anyway. Followed rather than read once, so a kind
 * declared in the settings reaches a journey screen without the application
 * being restarted.
 *
 * @param apply what to redraw, run on the main thread while the view is started,
 *   and safe to run more than once.
 */
fun Fragment.withOwnBikeKind(apply: (kind: OwnBikeKind) -> Unit) {
    val container = (requireActivity().application as RoueLibreApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.preferences.ownBikeKind
                // Every write to the settings file reaches this flow, most of
                // them about something else: redrawing the map's marker images
                // on a theme being pressed would be work for nothing.
                .distinctUntilChanged()
                .collect(apply)
        }
    }
}
