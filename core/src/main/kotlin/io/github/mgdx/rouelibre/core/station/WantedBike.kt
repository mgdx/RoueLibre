package io.github.mgdx.rouelibre.core.station

/**
 * The kind of bike somebody asked for (SPEC §7.1, §7.3).
 *
 * Two values and no third: **asking for nothing is `null`**, not a value of this
 * enum. That is what the application is at rest — it presumes no kind, exactly
 * as it presumes no city (SPEC §15) — and a type whose default value would be
 * "any" invites a caller to forget the distinction between a choice made and a
 * choice never made.
 *
 * A scooter is deliberately not askable: [VehicleKind.Other] describes what the
 * feed counts, and this describes what a rider can want to pedal.
 *
 * @property kind what the vehicle type table has to say of a bike for it to
 *   answer this request.
 */
public enum class WantedBikeKind(public val kind: VehicleKind) {
    /** A bicycle one pedals alone. */
    Mechanical(VehicleKind.Mechanical),

    /** A bicycle a motor helps to pedal. */
    Electric(VehicleKind.Electric),
    ;

    /**
     * How the choice is written down, in the settings that remember it.
     *
     * The vocabulary of the city configurations and of the generation scripts,
     * so one word means one thing across the whole project.
     */
    public val wireName: String get() = kind.wireName

    public companion object {
        /**
         * Reads a remembered choice back, or `null` for no choice at all.
         *
         * An absent value and an unreadable one give the same answer, and it is
         * the one that constrains nothing: a word this build does not know is
         * not a kind it can go and find at a station, and standing in for it
         * with a guess would send somebody towards a bike nobody promised.
         */
        public fun ofWireName(name: String?): WantedBikeKind? =
            entries.firstOrNull { it.wireName == name }
    }
}

/**
 * A wanted kind of bike, and the table needed to recognise one.
 *
 * The two travel together because neither answers anything alone: the kinds are
 * the rider's vocabulary, the identifiers are the producer's, and only the
 * network's own `vehicle_types` table joins them (SPEC §4.1, §15). Nothing here
 * knows a city.
 *
 * @property wanted the kind asked for.
 * @property vehicleTypes the kind of each vehicle type identifier of the network
 *   in service.
 */
public data class BikeKindFilter(
    public val wanted: WantedBikeKind,
    public val vehicleTypes: Map<String, VehicleKind>,
) {

    /**
     * How many bikes of the wanted kind stand at a station, or `null`.
     *
     * `null` is not zero, and the difference is the whole point: it means the
     * breakdown could not be read — an identifier the network never declared, a
     * sum that does not match the count displayed, or no breakdown published at
     * all (see [splitBikesByKind]). A station whose bikes could not be counted
     * by kind promises nothing about the kind wanted, and must not be drawn or
     * chosen as though it held none.
     */
    public fun bikesAt(availability: StationAvailability?): Int? {
        val split = availability?.splitByKind(vehicleTypes) ?: return null
        return when (wanted) {
            WantedBikeKind.Mechanical -> split.mechanical
            WantedBikeKind.Electric -> split.electric
        }
    }

    /**
     * Whether a station really holds a bike of the wanted kind.
     *
     * **A breakdown that cannot be read fails this test.** One cannot promise a
     * bike one has not managed to count: the strictness is deliberate, and it is
     * the same rule that silences a station's split rather than guessing at it
     * (SPEC §7.2).
     */
    public fun isSatisfiedBy(availability: StationAvailability?): Boolean =
        (bikesAt(availability) ?: 0) > 0
}
