package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * A bike the network reports away from its stations (SPEC §4.1, §7.2.1).
 *
 * What is kept from the feed is a bike **without** a `station_id`, not disabled
 * and not reserved: the rest is dropped at parse time and never reaches a
 * screen, a bike at a station being already counted by the station feed. What
 * remains lives in memory for the session and reaches no disk (SPEC §8).
 *
 * @property id the producer's identifier, which the standard requires it to
 *   rotate after every rental. Opaque, and **never persisted**: a favourite
 *   bike would survive its own first ride as a reference to nothing.
 * @property position where the feed puts it. Drawn there and never guessed
 *   back into a station: Vel'in Calais publishes its whole fleet without a
 *   `station_id`, every bike within thirty metres of a station, and a distance
 *   heuristic would be a coefficient nobody measured.
 * @property vehicleTypeId the producer's own type identifier, which says
 *   nothing by itself — it takes the network's table, see [kind]. Null on the
 *   feeds that define no vehicle types (GBFS 1.x and 2.0).
 * @property chargeRatio `current_fuel_percent`, a ratio from 0 to 1, kept only
 *   within that range: a producer writing 67 for 67 % gets null rather than a
 *   guess.
 * @property rangeMetres `current_range_meters`, kept only when above zero:
 *   nextbike writes a range of zero on every one of its electric bikes.
 */
public data class StreetBike(
    public val id: String,
    public val position: Coordinates,
    public val vehicleTypeId: String?,
    public val chargeRatio: Double?,
    public val rangeMetres: Int?,
)

/**
 * What the sheet says of a bike's charge, resolved against the type table.
 *
 * The percentage first, and the range only when it means something — see
 * [charge] for why the order is not a preference.
 */
public sealed interface BikeCharge {
    /** The charge as a ratio from 0.0 to 1.0. */
    public data class Ratio(public val value: Double) : BikeCharge

    /** The distance the bike can still cover, in metres. */
    public data class Range(public val metres: Int) : BikeCharge
}

/**
 * The charge of this bike, as the sheet may state it, or `null` when nothing
 * reliable can be said (SPEC §7.2.1).
 *
 * The ratio wins whenever it is published: nextbike writes
 * `current_fuel_percent` on its pedal-assist types and `current_range_meters`
 * as `0` on every one of them, with `max_range_meters: 0` in the type, so the
 * percentage is the figure that holds up. The range is stated only when both
 * it and the type's maximum are above zero: "0 km" would say the bike is flat
 * when it is not, and a range with no maximum behind it cannot be read as a
 * charge at all.
 *
 * @param maxRangeMetres the `max_range_meters` of the bike's declared type,
 *   or `null` when the type declares none.
 */
public fun StreetBike.charge(maxRangeMetres: Int?): BikeCharge? {
    chargeRatio?.let { return BikeCharge.Ratio(it) }
    val range = rangeMetres ?: return null
    val maximum = maxRangeMetres ?: return null
    if (range <= 0 || maximum <= 0) return null
    return BikeCharge.Range(range)
}

/**
 * What kind of bike this is, through the same table as a station's breakdown.
 *
 * A type the table does not know, or a bike declaring none, is read as a
 * mechanical bike rather than left unread: a bike outside stations is one
 * bike, drawn on its own and counted nowhere, so the caution [splitBikesByKind]
 * owes to a count — a wrong split sends somebody to a bike that is not there —
 * does not apply. The plain bike is the drawing that promises the least.
 *
 * @param vehicleTypes the kind of each vehicle type identifier of this
 *   network.
 */
public fun StreetBike.kind(vehicleTypes: Map<String, VehicleKind>): VehicleKind =
    vehicleTypeId?.let { vehicleTypes[it] } ?: VehicleKind.Mechanical

/**
 * The bikes the map may draw: those whose declared type is a bicycle, and those
 * declaring none.
 *
 * A network lending scooters lists them in the very same feed, and a scooter is
 * not what this application shows (SPEC §4.1). A bike whose type the table does
 * not know is kept, on the same reading as [kind]: the table is the operator's
 * declaration, and an identifier it omits is an identifier the operator added
 * since — not a scooter.
 *
 * @param vehicleTypes the kind of each vehicle type identifier of this
 *   network.
 */
public fun streetBikesShown(
    bikes: List<StreetBike>,
    vehicleTypes: Map<String, VehicleKind>,
): List<StreetBike> = bikes.filter { bike ->
    val typeId = bike.vehicleTypeId ?: return@filter true
    vehicleTypes[typeId] != VehicleKind.Other
}
