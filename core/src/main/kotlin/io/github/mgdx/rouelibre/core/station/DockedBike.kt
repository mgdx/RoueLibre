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
 * No position: a docked bike stands where its station stands. The identifier
 * is kept, for the list of SPEC §7.2 to name each bike by, and for nothing
 * else — the standard rotates it after every rental, so it is never
 * persisted and never compared across two reads.
 *
 * @property id the producer's identifier, as written: nextbike's carries the
 *   number painted on the bike, Fifteen's is an opaque UUID, and the
 *   application does not tell the two apart.
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
    public val id: String,
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
 * @property bikes every bicycle standing there, one line each, for the list
 *   the sheet unfolds on request: those on offer first, the electric ones by
 *   charge, then the reserved, then the disabled. The scooters a network
 *   parks at the same station are not among them.
 * @property vehiclesListed how many vehicles the feed puts at this station,
 *   the scooters included — [bikes] leaves them out, and a comparison with
 *   the station feed must count what that feed counts.
 * @property vehiclesOnOffer how many of those the feed says one could take,
 *   being neither disabled nor reserved.
 */
public data class StationBikesDetail(
    public val charges: List<BikeCharge>,
    public val outOfService: Int,
    public val bikes: List<DockedBikeLine>,
    public val vehiclesListed: Int,
    public val vehiclesOnOffer: Int,
) {
    /** Whether the summary line, above the list, has anything to say. */
    public val hasSummary: Boolean
        get() = charges.isNotEmpty() || outOfService > 0

    /**
     * Whether the count the station feed publishes cannot be reconciled with
     * the bikes this feed lists (SPEC §7.2).
     *
     * The two feeds are two files, written apart and refreshed apart, and
     * nothing obliges a producer to keep them in step. Measured on
     * 11 September 2026: of the 53 stations of Reims holding either a count
     * or a vehicle, 24 published a figure no reading of the list could
     * account for — 5 bikes announced and 3 listed, 0 announced and 3
     * listed — and Angoulême 30 of 54, which is a second producer. Of sixty
     * networks read that day, forty-seven could be compared at all, and
     * thirty-nine of those had not one station in that position. It is
     * therefore the fault of certain producers and never the ordinary state
     * of affairs.
     *
     * **The test is an interval and not an equality**, because the standard
     * leaves one thing genuinely open: whether a bike somebody has booked is
     * still "available" at the station. Reims counts it, others do not, and
     * both readings are defensible. The count is therefore accepted anywhere
     * between the bikes on offer and every vehicle standing there; only a
     * count outside that range says something no arrangement of the list can
     * support. Read strictly, Reims would have shown 39 stations of 53
     * rather than 24, and Berlin's nextbike 4 of 686 either way.
     *
     * @param bikesCounted the figure the station feed publishes, as the sheet
     *   shows it in its disc.
     */
    public fun disagreesWith(bikesCounted: Int): Boolean =
        bikesCounted !in vehiclesOnOffer..vehiclesListed
}

/**
 * One bike of the list a station's sheet unfolds (SPEC §7.2).
 *
 * @property id the producer's identifier, as the feed writes it.
 * @property label what the sheet names the bike by: the identifier, cut down
 *   past twenty characters, see [abbreviateIdentifier].
 * @property kind what the type table reads the bike as, or `null` where the
 *   type is undeclared or unknown to the table — the line then names no kind
 *   rather than guessing one.
 * @property charge what may be said of its charge, on the usual terms, or
 *   `null`.
 * @property isDisabled the network says it cannot be rented.
 * @property isReserved somebody has booked it.
 */
public data class DockedBikeLine(
    public val id: String,
    public val label: String,
    public val kind: VehicleKind?,
    public val charge: BikeCharge?,
    public val isDisabled: Boolean,
    public val isReserved: Boolean,
) {
    /** Neither disabled nor reserved: a bike one could walk to and take. */
    public val isOnOffer: Boolean
        get() = !isDisabled && !isReserved
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
 * bike whose figures cannot be believed is silently missing from the
 * charges, never written as flat. Sorted fullest first, percentages before
 * ranges when a producer mixes the two — which none does, but the order must
 * still be one.
 *
 * Every bicycle standing there is listed all the same, charge or not, kind
 * or not, on offer or not: the list is what the reader unfolds to see the
 * station bike by bike, and it hides only the scooters, which are not bikes.
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
    val lines = bikes
        .filter { it.vehicleTypeId == null || vehicleTypes[it.vehicleTypeId] != VehicleKind.Other }
        .map { bike ->
            val kind = bike.vehicleTypeId?.let { vehicleTypes[it] }
            DockedBikeLine(
                id = bike.id,
                label = abbreviateIdentifier(bike.id),
                kind = kind,
                // A charge is read on an electric bike alone, see above.
                charge = if (kind == VehicleKind.Electric) {
                    bikeCharge(
                        bike.chargeRatio,
                        bike.rangeMetres,
                        maxRangeMetresByType[bike.vehicleTypeId],
                    )
                } else {
                    null
                },
                isDisabled = bike.isDisabled,
                isReserved = bike.isReserved,
            )
        }
        .sortedWith(LINE_ORDER)
    if (lines.isEmpty()) return null
    return StationBikesDetail(
        charges = lines.filter { it.isOnOffer }.mapNotNull { it.charge }.sortedWith(CHARGE_ORDER),
        outOfService = lines.count { it.isDisabled },
        bikes = lines,
        // Counted before the scooters are dropped: the station feed counts
        // every vehicle it lends, and only like may be set against like.
        vehiclesListed = bikes.size,
        vehiclesOnOffer = bikes.count { !it.isDisabled && !it.isReserved },
    )
}

/**
 * An identifier short enough to read, or its two ends around an ellipsis.
 *
 * Twenty characters is the ceiling, and past it the first five and the last
 * five are kept: a UUID such as `3e279687-add4-458d-8d37-5a6386b5fbd2` is not
 * something one reads, it is something one matches against the sticker on
 * the bike, and the two ends are what one matches. Under the ceiling the
 * identifier is left whole: nextbike's `nextbike_bb_20911` carries the
 * number painted on the frame in its last five characters, and twenty is
 * what lets it through untouched where twelve would have cut the number.
 */
public fun abbreviateIdentifier(identifier: String): String {
    if (identifier.length <= IDENTIFIER_LENGTH_SHOWN_WHOLE) return identifier
    return identifier.take(IDENTIFIER_END_KEPT) + ELLIPSIS +
        identifier.takeLast(IDENTIFIER_END_KEPT)
}

/** Past this many characters an identifier is abbreviated. */
private const val IDENTIFIER_LENGTH_SHOWN_WHOLE = 20

/** How many characters each end of an abbreviated identifier keeps. */
private const val IDENTIFIER_END_KEPT = 5

/** One character, so the abbreviation costs no more than it saves. */
private const val ELLIPSIS = "…"

/** Percentages before ranges, then the fullest first. */
private val CHARGE_ORDER: Comparator<BikeCharge> =
    compareByDescending<BikeCharge> { it is BikeCharge.Ratio }.thenByDescending { it.fill }

/**
 * The bikes one could take first — the electric ones by charge, a bike with
 * a charge before one without — then the reserved, then the disabled: the
 * list answers "which one do I walk to", and what cannot be taken comes last.
 */
private val LINE_ORDER: Comparator<DockedBikeLine> =
    compareBy<DockedBikeLine> { it.offerRank }
        .thenBy { if (it.charge == null) 1 else 0 }
        .thenComparing({ it.charge }, nullsLast(CHARGE_ORDER))

/** On offer, then reserved, then disabled. */
private val DockedBikeLine.offerRank: Int
    get() = when {
        isDisabled -> 2
        isReserved -> 1
        else -> 0
    }

/** The figure a charge is ordered on: the ratio, or the range in metres. */
private val BikeCharge.fill: Double
    get() = when (this) {
        is BikeCharge.Ratio -> value
        is BikeCharge.Range -> metres.toDouble()
    }
