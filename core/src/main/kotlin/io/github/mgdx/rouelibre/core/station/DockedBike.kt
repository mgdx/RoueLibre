package io.github.mgdx.rouelibre.core.station

/**
 * A bike the network reports standing at one of its stations (SPEC §4.1, §7.2).
 *
 * The same feed entry as a [StreetBike], read the other way round: what is
 * kept here is a bike **with** a `station_id`, whatever its state, because the
 * station's sheet has a use for a bike that cannot be taken — "4 bikes" with
 * one out of service explains a rack one walked to for nothing. It lives in
 * memory for the session and reaches no disk (SPEC §8), and it is read only
 * while the setting of SPEC §7.6 is on.
 *
 * No position and no identifier: a docked bike stands where its station
 * stands, and the standard rotates its identifier after every rental, so
 * neither says anything the station does not already say.
 *
 * @property stationId the station the feed puts the bike at, as the producer
 *   writes it — the same identifier `station_status` counts under.
 * @property vehicleTypeId the producer's own type identifier, which says
 *   nothing by itself; see [chargesAtStation].
 * @property chargeRatio `current_fuel_percent`, kept within 0 and 1 as for a
 *   street bike.
 * @property rangeMetres `current_range_meters`, kept only when above zero.
 * @property isDisabled the network says the bike cannot be rented.
 * @property isReserved somebody has booked the bike and is on their way to it.
 */
public data class DockedBike(
    public val stationId: String,
    public val vehicleTypeId: String?,
    public val chargeRatio: Double?,
    public val rangeMetres: Int?,
    public val isDisabled: Boolean,
    public val isReserved: Boolean,
)

/**
 * What a station's sheet says of the bikes standing there, beyond their count
 * (SPEC §7.2).
 *
 * @property charges the charge of each electric bike one could take, the
 *   fullest first: the reader is choosing which bike to walk to, and the best
 *   one is the answer. A bike disabled or reserved is not among them — it is
 *   not on offer.
 * @property outOfService how many of the bikes standing there the network
 *   says cannot be rented.
 */
public data class StationBikesDetail(
    public val charges: List<BikeCharge>,
    public val outOfService: Int,
) {
    /** Whether there is anything at all to say. */
    public val isEmpty: Boolean
        get() = charges.isEmpty() && outOfService == 0
}

/**
 * Reads what may be said of the bikes at one station, or `null` when nothing
 * can (SPEC §7.2).
 *
 * The charges are those of the **electric** bikes, as the network's own table
 * reads them (SPEC §4.1), and of no other: Fifteen writes a
 * `current_range_meters` on every one of Helsinki's 3,947 mechanical bikes,
 * and a range on a bike with no battery is a figure about nothing. A type the
 * table does not know is left out for the same reason — a station's sheet
 * counts, and a count must not be padded with a guess — where a street bike's
 * sheet, describing one bike, may read an unknown type as mechanical.
 *
 * Each charge is read as a street bike's is, the percentage first and the
 * range only where it and the type's maximum both hold up ([bikeCharge]); a
 * bike whose figures cannot be believed is silently missing from the list,
 * never written as flat. Sorted fullest first, percentages before ranges when
 * a producer mixes the two — which none does, but the order must still be one.
 *
 * @param bikes the bikes the feed puts at this station, and at this station
 *   only.
 * @param vehicleTypes the kind of each vehicle type identifier of this
 *   network.
 * @param maxRangeMetresByType how far a full battery of each type goes, where
 *   the type declares it.
 */
public fun chargesAtStation(
    bikes: List<DockedBike>,
    vehicleTypes: Map<String, VehicleKind>,
    maxRangeMetresByType: Map<String, Int>,
): StationBikesDetail? {
    if (bikes.isEmpty()) return null
    val charges = bikes
        .filter { !it.isDisabled && !it.isReserved }
        .filter {
            it.vehicleTypeId != null && vehicleTypes[it.vehicleTypeId] == VehicleKind.Electric
        }
        .mapNotNull {
            bikeCharge(it.chargeRatio, it.rangeMetres, maxRangeMetresByType[it.vehicleTypeId])
        }
        .sortedWith(
            compareByDescending<BikeCharge> {
                it is BikeCharge.Ratio
            }.thenByDescending { it.fill },
        )
    val detail = StationBikesDetail(
        charges = charges,
        outOfService = bikes.count { it.isDisabled },
    )
    return detail.takeUnless { it.isEmpty }
}

/** The figure a charge is ordered on: the ratio, or the range in metres. */
private val BikeCharge.fill: Double
    get() = when (this) {
        is BikeCharge.Ratio -> value
        is BikeCharge.Range -> metres.toDouble()
    }
