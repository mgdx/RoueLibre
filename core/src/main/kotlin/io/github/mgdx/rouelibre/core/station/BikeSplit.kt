package io.github.mgdx.rouelibre.core.station

/**
 * The kind of vehicle a station's count may be made of.
 *
 * Which identifier is which is a fact about the network, read from the city
 * configuration and from nowhere else (SPEC §15): producers name their types as
 * they please — `346` and `348` at nextbike, `mechanical` and `electrical` at
 * Lyon, `bike` at Lille.
 */
public enum class VehicleKind {
    /** A bicycle one pedals alone. */
    Mechanical,

    /** A bicycle a motor helps to pedal. */
    Electric,

    /**
     * A scooter, a moped — anything that is not a bicycle.
     *
     * Known and deliberately set aside rather than counted: a station's total
     * is a number of bikes, and a network lending scooters counts them in the
     * same breakdown. Knowing that an identifier is *not* a bike is what
     * distinguishes it from one that could not be read at all.
     */
    Other,
}

/**
 * How many bikes of each kind stand at a station.
 *
 * @property mechanical bikes one pedals alone.
 * @property electric bikes a motor helps to pedal.
 */
public data class BikeSplit(public val mechanical: Int, public val electric: Int)

/**
 * Splits a station's bikes into mechanical and electric, or gives up.
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
 * @param vehicleTypes the kind of each vehicle type identifier of this network.
 * @return the split, or `null` if it cannot be trusted.
 */
public fun StationAvailability.splitByKind(vehicleTypes: Map<String, VehicleKind>): BikeSplit? {
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
