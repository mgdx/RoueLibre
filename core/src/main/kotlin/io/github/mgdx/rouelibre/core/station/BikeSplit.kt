package io.github.mgdx.rouelibre.core.station

/**
 * The kind of vehicle a station's count may be made of.
 *
 * Which identifier is which is a fact about the network, never about the
 * application (SPEC §15): producers name their types as they please — `346` and
 * `348` at nextbike, `mechanical` and `electrical` at Lyon, `bike` at Lille. The
 * table translating them is read from the network's own `vehicle_types` feed and
 * seeded by the city configuration (SPEC §4.1).
 *
 * @property wireName how the kind is written down, in the city configuration as
 *   in the settings where a reading is remembered. Stable from one release to
 *   the next, and lowercase, being the vocabulary the generation scripts write.
 */
public enum class VehicleKind(public val wireName: String) {
    /** A bicycle one pedals alone. */
    Mechanical("mechanical"),

    /** A bicycle a motor helps to pedal. */
    Electric("electric"),

    /**
     * A scooter, a moped — anything that is not a bicycle.
     *
     * Known and deliberately set aside rather than counted: a station's total
     * is a number of bikes, and a network lending scooters counts them in the
     * same breakdown. Knowing that an identifier is *not* a bike is what
     * distinguishes it from one that could not be read at all.
     */
    Other("other"),
    ;

    public companion object {
        /**
         * Reads a kind back from its [wireName].
         *
         * Anything unexpected reads as [Other] rather than being refused: a kind
         * this build does not know is certainly not a bike it can count, and one
         * unreadable word must not cost the user the whole city.
         */
        public fun ofWireName(name: String?): VehicleKind =
            entries.firstOrNull { it.wireName == name } ?: Other
    }
}

/**
 * How many bikes of each kind stand at a station.
 *
 * @property mechanical bikes one pedals alone.
 * @property electric bikes a motor helps to pedal.
 */
public data class BikeSplit(public val mechanical: Int, public val electric: Int)

/**
 * Splits a count of bikes into mechanical and electric, or gives up.
 *
 * Giving up is the point of this function. The breakdown published by a feed is
 * not always the same count as the total shown to the user, and a wrong split
 * is worse than no split at all — it sends someone to a station for a bike that
 * is not there. Three things therefore silence it:
 *
 * - **an unreadable identifier**: five of the networks served publish at their
 *   stations a vehicle type they never declared, and a bike of unknown
 *   propulsion belongs in neither column;
 * - **a sum that does not match the total**: the Beryl networks count their
 *   scooters in `num_bikes_available` but not their bikes, so at Norwich the
 *   breakdown accounts for a fraction of the number displayed;
 * - **nothing published at all**, which is the case of a station holding no
 *   bike, and of the feeds that publish no breakdown.
 *
 * In each case the caller shows the total alone, which is always true.
 *
 * It takes the breakdown apart from the total it must add up to, rather than a
 * station's state: a journey carries the two figures it was worked out on, away
 * from the station they were read at (SPEC §7.4.1), and that reading is split
 * by the same rules as the live one.
 *
 * @param bikesByVehicleType how many bikes stand there under each of the
 *   network's own vehicle type identifiers.
 * @param bikesAvailable the total the split has to account for.
 * @param vehicleTypes the kind of each vehicle type identifier of this network.
 * @return the split, or `null` if it cannot be trusted.
 */
public fun splitBikesByKind(
    bikesByVehicleType: Map<String, Int>,
    bikesAvailable: Int,
    vehicleTypes: Map<String, VehicleKind>,
): BikeSplit? {
    if (bikesByVehicleType.isEmpty() || vehicleTypes.isEmpty()) return null
    var mechanical = 0
    var electric = 0
    for ((identifier, count) in bikesByVehicleType) {
        when (vehicleTypes[identifier]) {
            VehicleKind.Mechanical -> mechanical += count
            VehicleKind.Electric -> electric += count
            VehicleKind.Other -> Unit
            null -> return null
        }
    }
    if (mechanical + electric != bikesAvailable) return null
    return BikeSplit(mechanical = mechanical, electric = electric)
}

/**
 * Splits a station's bikes as they stand right now, or gives up.
 *
 * @param vehicleTypes the kind of each vehicle type identifier of this network.
 * @return the split, or `null` if it cannot be trusted — see [splitBikesByKind]
 *   for what silences it.
 */
public fun StationAvailability.splitByKind(vehicleTypes: Map<String, VehicleKind>): BikeSplit? =
    splitBikesByKind(bikesByVehicleType, bikesAvailable, vehicleTypes)
