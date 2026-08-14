package io.github.mgdx.rouelibre.ui

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.FleetDescription
import kotlinx.coroutines.launch

/**
 * What the city in service lends, as the interface draws it.
 *
 * Three offers, three drawings. The distinction is not a nuance: a network
 * lending only mechanical bikes, one lending only electric ones and one lending
 * both are three different things to somebody deciding whether to walk to a
 * station.
 */
enum class BikeFleet {
    /** Bikes one pedals alone. The plain bike is drawn. */
    Mechanical,

    /** Pedal-assist bikes. The bike bears a bolt. */
    Electric,

    /** Both kinds, side by side. The bike bears a bolt and a cog. */
    Mixed,
    ;

    companion object {
        /**
         * What a city's configuration says it lends (SPEC §15).
         *
         * A city not chosen yet, and one whose feeds let nothing be counted,
         * both give [Mechanical]: the plain bike promises the least.
         */
        fun of(fleet: FleetDescription?): BikeFleet = when {
            fleet == null -> Mechanical
            fleet.isMixed -> Mixed
            fleet.hasElectricBikes -> Electric
            else -> Mechanical
        }
    }
}

/**
 * The bike the interface draws, plain or bearing what the city lends.
 *
 * A network lending pedal-assist bikes is not the same offer as one lending
 * mechanical bikes, nor as one lending both, and the user has to know which one
 * they are walking to a station for. The bolt and the cog say it wherever a
 * bike is drawn — the journey button, the ride leg, the stations of a journey —
 * and nowhere else: it is a property of the fleet, not a decoration.
 *
 * Which of the three applies is read from the city configuration and from
 * nothing else (SPEC §15): the code knows no city, and the same build serves a
 * mechanical fleet in one conurbation and a mixed one in the next.
 *
 * The application's own identity is left alone — the launcher icon and the
 * welcome screens are the same whichever city is served, and one of them is
 * shown before any city has been chosen at all.
 */
object BikeGlyphs {

    /** The bike as an icon, on a button or beside a line of text. */
    @DrawableRes
    fun icon(fleet: BikeFleet): Int = when (fleet) {
        BikeFleet.Mechanical -> R.drawable.ic_bike
        BikeFleet.Electric -> R.drawable.ic_bike_electric
        BikeFleet.Mixed -> R.drawable.ic_bike_mixed
    }

    /** The disc bearing a bike that stands for a station on a journey. */
    @DrawableRes
    fun stationMarker(fleet: BikeFleet): Int = when (fleet) {
        BikeFleet.Mechanical -> R.drawable.marker_journey_station
        BikeFleet.Electric -> R.drawable.marker_journey_station_electric
        BikeFleet.Mixed -> R.drawable.marker_journey_station_mixed
    }

    /** The walk, ride, walk illustration of the search screen. */
    @DrawableRes
    fun journeyShape(fleet: BikeFleet): Int = when (fleet) {
        BikeFleet.Mechanical -> R.drawable.illustration_journey
        BikeFleet.Electric -> R.drawable.illustration_journey_electric
        BikeFleet.Mixed -> R.drawable.illustration_journey_mixed
    }
}

/**
 * Says what the city in service lends, once it is known.
 *
 * The configuration is read from disk, so the answer comes a moment after the
 * screen: [apply] therefore runs on a view already on screen, and every caller
 * draws the plain bike until then rather than an empty space.
 *
 * @param apply what to redraw, run on the main thread while the view lives.
 */
fun Fragment.withBikeFleet(apply: (fleet: BikeFleet) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        val container = (requireActivity().application as RoueLibreApplication).container
        apply(BikeFleet.of(container.activeCity()?.fleet))
    }
}
