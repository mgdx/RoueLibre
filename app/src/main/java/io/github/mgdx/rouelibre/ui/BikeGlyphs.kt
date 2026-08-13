package io.github.mgdx.rouelibre.ui

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import kotlinx.coroutines.launch

/**
 * The bike the interface draws, plain or bearing a bolt.
 *
 * A network lending pedal-assist bikes is not the same offer as one lending
 * mechanical bikes, and the user has to know which one they are walking to a
 * station for. The bolt says it wherever a bike is drawn — the journey button,
 * the ride leg, the stations of a journey — and nowhere else: it is a property
 * of the fleet, not a decoration.
 *
 * Which of the two applies is read from the city configuration and from
 * nothing else (SPEC §15): the code knows no city, and the same build serves a
 * mechanical fleet in one conurbation and an electric one in the next.
 *
 * The application's own identity is left alone — the launcher icon and the
 * welcome screens are the same whichever city is served, and one of them is
 * shown before any city has been chosen at all.
 */
object BikeGlyphs {

    /** The bike as an icon, on a button or beside a line of text. */
    @DrawableRes
    fun icon(electricBikes: Boolean): Int =
        if (electricBikes) R.drawable.ic_bike_electric else R.drawable.ic_bike

    /** The disc bearing a bike that stands for a station on a journey. */
    @DrawableRes
    fun stationMarker(electricBikes: Boolean): Int = if (electricBikes) {
        R.drawable.marker_journey_station_electric
    } else {
        R.drawable.marker_journey_station
    }

    /** The walk, ride, walk illustration of the search screen. */
    @DrawableRes
    fun journeyShape(electricBikes: Boolean): Int = if (electricBikes) {
        R.drawable.illustration_journey_electric
    } else {
        R.drawable.illustration_journey
    }
}

/**
 * Says whether the city in service lends pedal-assist bikes, once it is known.
 *
 * The configuration is read from disk, so the answer comes a moment after the
 * screen: [apply] therefore runs on a view already on screen, and every caller
 * draws the plain bike until then rather than an empty space. A city that is
 * not electric, one not chosen yet and one whose feed declares no vehicle type
 * all give the same answer, which is the same drawing.
 *
 * @param apply what to redraw, run on the main thread while the view lives.
 */
fun Fragment.withBikeFleet(apply: (electricBikes: Boolean) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        val container = (requireActivity().application as RoueLibreApplication).container
        apply(container.activeCity()?.fleet?.hasElectricBikes == true)
    }
}
