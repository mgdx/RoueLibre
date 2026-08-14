package io.github.mgdx.rouelibre.core.station

/**
 * What a network lends, as counted from the bikes standing at its stations.
 *
 * @property vehicleTypes what each identifier the status feed counts by stands
 *   for. Empty for a network publishing no breakdown at all, which then shows
 *   one figure per station and no split.
 * @property hasElectricBikes whether pedal-assist bikes are in circulation,
 *   mixed fleets included. It is what puts the bolt on the bike glyph (SPEC §7).
 * @property isMixed whether both kinds are out, in numbers that make an offer.
 *   It is what allows a station's count to be split (SPEC §7.2).
 * @property bikesCounted how many bikes the reading rests on, both kinds
 *   together. Zero means nothing could be counted, and the reading is then the
 *   declaration's rather than the network's.
 */
public data class FleetReading(
    public val vehicleTypes: Map<String, VehicleKind>,
    public val hasElectricBikes: Boolean,
    public val isMixed: Boolean,
    public val bikesCounted: Int,
)

/**
 * Counts what a network really lends, from the state of its stations.
 *
 * Counted rather than declared, and that distinction is the whole point. A
 * survey of the three hundred and thirty-three networks served found that **a
 * third of those declaring a mixed fleet have not one bike of one of the two
 * kinds in circulation**: Madrid declares a mechanical type and puts out 5857
 * electric bikes and no mechanical one, Berlin declares an electric type and
 * puts out 1971 mechanical bikes and no electric one. The declaration says what
 * the operator may lend one day; the status feed says what is at the stations
 * now, and that is what the user walks to.
 *
 * The same reading is done offline by `tools/read_fleet.py`, which seeds the
 * city configuration so that a first launch has an answer before any feed has
 * been reached. This is its counterpart at runtime (SPEC §4.1), and the two
 * must agree — a change made here belongs in the script too.
 *
 * @param availabilities the state of every station, as `station_status` gives
 *   it.
 * @param declaredVehicleTypes what each identifier stands for, from the
 *   `vehicle_types` feed. Empty for a network on GBFS 1.0, which has no such
 *   feed: the kinds are then read from the names Vélib' publishes inline.
 * @param declaresElectricBikes whether that feed declares a pedal-assist
 *   bicycle. Used only when nothing can be counted.
 */
public fun countFleet(
    availabilities: List<StationAvailability>,
    declaredVehicleTypes: Map<String, VehicleKind>,
    declaresElectricBikes: Boolean,
): FleetReading {
    var mechanical = 0
    var electric = 0
    for (availability in availabilities) {
        for ((identifier, count) in availability.bikesByVehicleType) {
            // The declaration wins over the Vélib' names: a network declaring a
            // type of its own called "mechanical" means its own, not Vélib's.
            // An identifier in neither is ignored rather than guessed — five
            // networks publish at their stations a type they never declared,
            // and a bike of unknown propulsion belongs in neither column.
            when (declaredVehicleTypes[identifier] ?: VELIB_VEHICLE_TYPES[identifier]) {
                VehicleKind.Mechanical -> mechanical += count
                VehicleKind.Electric -> electric += count
                VehicleKind.Other, null -> Unit
            }
        }
    }

    val counted = mechanical + electric
    val vehicleTypes = when {
        declaredVehicleTypes.isNotEmpty() -> declaredVehicleTypes
        // The Vélib' names earn their place in the table only once seen in the
        // feed: writing them for every network would suggest a breakdown that
        // no other one publishes under those names.
        counted > 0 -> VELIB_VEHICLE_TYPES
        else -> emptyMap()
    }

    return FleetReading(
        vehicleTypes = vehicleTypes,
        // Nothing counted — a feed publishing no breakdown, or a network whose
        // every station is empty at that moment — falls back on the
        // declaration, which must not turn an electric city into a mechanical
        // one.
        hasElectricBikes = if (counted == 0) declaresElectricBikes else electric > 0,
        isMixed = isMixed(mechanical = mechanical, electric = electric),
        bikesCounted = counted,
    )
}

/**
 * Whether both kinds are out, in numbers that make an offer.
 *
 * Never true on a declaration alone: a split shown to the user is a promise
 * about what stands at the station, and only a count can make it.
 */
private fun isMixed(mechanical: Int, electric: Int): Boolean {
    if (mechanical == 0 || electric == 0) return false
    val total = mechanical + electric
    return minOf(mechanical, electric).toDouble() / total >= MINORITY_SHARE
}

/**
 * Below this share of the bikes counted, a kind is a residue rather than an
 * offer, and announcing a mixed fleet would promise something the user will
 * never find: Barcelona puts out 1922 electric bikes and 2 mechanical ones,
 * Mannheim 2258 mechanical and 10 electric. Two percent still keeps the
 * smallest genuine mixed fleets — one electric bike out of twenty counts.
 */
private const val MINORITY_SHARE = 0.02

/**
 * The kinds Vélib' Métropole names inline, for want of a `vehicle_types` feed.
 *
 * The network is on GBFS 1.0 and publishes its breakdown as
 * `[{"mechanical": 3}, {"ebike": 0}]`, the key being the kind's own name.
 * Naming the two here is what lets that feed be read through the very same
 * table as every other network, with no special case anywhere else — and
 * without it, the 7854 electric bikes of the largest network in France would be
 * invisible.
 */
private val VELIB_VEHICLE_TYPES: Map<String, VehicleKind> = mapOf(
    "mechanical" to VehicleKind.Mechanical,
    "ebike" to VehicleKind.Electric,
)
