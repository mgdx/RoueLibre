package io.github.mgdx.rouelibre.ui.journey

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.journey.DeparturePoint
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.BikeGlyphs

/**
 * The kind of bike a journey's ride is known to be made on, or `null` where
 * nothing settles it (SPEC §7.4).
 *
 * Two things settle it, and only two. A bike chosen outside the stations is one
 * bike, and the network's table named its kind (SPEC §7.2.1). A journey worked
 * out for a kind asked for on the search screen departs only from a station
 * holding that kind, and its ride is timed on it (SPEC §6, §7.3) — so the kind
 * must be the one the computation actually applied, not the one the switch
 * shows: a network unable to honour it computed the journey for any bike.
 *
 * The counts at the departure station are deliberately not read: a station
 * holding both kinds hands over whichever the rider takes, and guessing it
 * would draw a promise nobody made.
 *
 * @param computedFor the kind the journey was worked out for, `null` for any.
 */
internal fun rideKindOf(departure: DeparturePoint, computedFor: WantedBikeKind?): WantedBikeKind? =
    when (departure) {
        is DeparturePoint.AtStation -> computedFor
        is DeparturePoint.AtStreetBike -> bikeKindOf(departure.kind)
    }

/**
 * The kind of bike a computation sets out to ride, known before it has found
 * anything: the same two settlements as [rideKindOf], read off the question
 * rather than off the answer. A journey on one's own bike asks neither, and
 * draws the network's bike while it waits, as it always has.
 *
 * @param streetBikeKind what the table read the bike chosen outside the
 *   stations as, or `null` for a journey that sets off from none.
 * @param applied the kind the computation narrows the stations to, or `null`.
 */
internal fun rideKindAsked(
    streetBikeKind: VehicleKind?,
    applied: WantedBikeKind?,
): WantedBikeKind? = if (streetBikeKind != null) bikeKindOf(streetBikeKind) else applied

/** A vehicle type's kind as a bike one rides, `null` for what is no bike to name. */
private fun bikeKindOf(kind: VehicleKind): WantedBikeKind? = when (kind) {
    VehicleKind.Electric -> WantedBikeKind.Electric
    VehicleKind.Mechanical -> WantedBikeKind.Mechanical
    // A scooter, or a type the table could not read.
    VehicleKind.Other -> null
}

/**
 * The kind worth saying of the ride, or `null` to keep the network's own bike.
 *
 * Only a network lending both kinds has a choice to report. Where it lends one
 * kind, the bike it draws everywhere already says which (SPEC §15).
 */
internal fun shownRideKind(fleet: BikeFleet, rideKind: WantedBikeKind?): WantedBikeKind? =
    rideKind.takeIf { fleet == BikeFleet.Mixed }

/**
 * The bike drawn for a ride of a known kind: the bolt for a pedal-assist one,
 * the plain bike otherwise — never the cog, one bike being of one kind, as
 * the bike outside the stations and the rider's own are drawn.
 */
@DrawableRes
internal fun rideIconOf(kind: WantedBikeKind): Int = when (kind) {
    WantedBikeKind.Electric -> R.drawable.ic_bike_electric
    WantedBikeKind.Mechanical -> R.drawable.ic_bike
}

/**
 * The disc standing for a station of the journey's drawing: the bike taken
 * where [shownRideKind] names it, the network's own badge otherwise.
 */
@DrawableRes
internal fun stationMarkerOf(fleet: BikeFleet, rideKind: WantedBikeKind?): Int =
    when (shownRideKind(fleet, rideKind)) {
        WantedBikeKind.Electric -> BikeGlyphs.stationMarker(BikeFleet.Electric)
        WantedBikeKind.Mechanical -> BikeGlyphs.stationMarker(BikeFleet.Mechanical)
        null -> BikeGlyphs.stationMarker(fleet)
    }

/** What a screen reader says of a ride of a known kind, given its arrival station. */
@StringRes
internal fun rideDescriptionOf(kind: WantedBikeKind): Int = when (kind) {
    WantedBikeKind.Electric -> R.string.journey_step_ride_electric_description
    WantedBikeKind.Mechanical -> R.string.journey_step_ride_mechanical_description
}
