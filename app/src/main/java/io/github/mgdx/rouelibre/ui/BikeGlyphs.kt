package io.github.mgdx.rouelibre.ui

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.config.FleetDescription
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
         * What the city in service is known to lend (SPEC §7, §15).
         *
         * A city not chosen yet, one whose configuration has not been read from
         * disk yet, and one whose feeds let nothing be counted all give
         * [Mechanical]: the plain bike promises the least, and the bolt is what
         * has to be earned.
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
 * Which of the three applies is never decided here (SPEC §15): the code knows
 * no city, and the same build serves a mechanical fleet in one conurbation and a
 * mixed one in the next. It is counted from the network's own feeds, seeded by
 * the city configuration for the launches that reach no network (SPEC §4.1).
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
 * Says what the city in service lends, and says it again when that changes.
 *
 * Two moments, and both come after the screen is up. The configuration is read
 * from disk, so the first answer arrives a beat late — every caller draws the
 * plain bike until then rather than an empty space. Then the stations refresh,
 * the bikes standing at them are counted (SPEC §4.1), and a network that turns
 * out to lend more than the configuration was seeded with says so on the spot.
 * A reading only ever adds (see `FleetRepository`), so this fires rarely and
 * never flickers back.
 *
 * [apply] must therefore be safe to run more than once: it redraws, it does not
 * append.
 *
 * @param apply what to redraw, run on the main thread while the view is
 *   started.
 */
fun Fragment.withBikeFleet(apply: (fleet: BikeFleet) -> Unit) {
    val container = (requireActivity().application as RoueLibreApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.fleetRepository.fleet
                .map { BikeFleet.of(it) }
                // Several readings draw the same bike — a vehicle type added to
                // the table changes nothing here. Redrawing on each would put
                // the markers back into the map style for nothing.
                .distinctUntilChanged()
                .collect(apply)
        }
    }
}

/**
 * Says what the city lends down to the vehicle types it counts its bikes by.
 *
 * The reading itself, where [withBikeFleet] gives only the bike to draw: it
 * takes the identifier table to say how many of the bikes at a station are
 * electric (SPEC §7.2), and that table grows as the network is read (SPEC
 * §4.1). Nothing is deduplicated here, since a table that has gained an
 * identifier is a reading that changes what the caller says.
 *
 * @param apply what to redraw, run on the main thread while the view is
 *   started, and safe to run more than once.
 */
fun Fragment.withFleet(apply: (fleet: FleetDescription?) -> Unit) {
    val container = (requireActivity().application as RoueLibreApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.fleetRepository.fleet.collect(apply)
        }
    }
}
