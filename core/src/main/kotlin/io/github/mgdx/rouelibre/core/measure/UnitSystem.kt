package io.github.mgdx.rouelibre.core.measure

/**
 * The units a distance is written in (SPEC §9).
 *
 * **Nothing is ever computed in these units.** The application measures in
 * metres from end to end — the routing graph, the algorithm of §6, the bounding
 * boxes, the GBFS feeds, the elevation profile — and a system of units is
 * consulted at one moment only: when a figure becomes a piece of text (see
 * [writeDistance]). That is what makes a rider in miles and a rider in
 * kilometres get the same journey, the same departure station and the same
 * announced time, written two ways.
 *
 * Three of them, because the imperial world is not one place: the United States
 * count short distances in feet, the United Kingdom in yards, and both switch
 * to miles further out.
 */
public enum class UnitSystem {
    /** Metres below a kilometre, kilometres above. */
    Metric,

    /** Feet below the threshold, miles above. */
    UnitedStates,

    /** Yards below the threshold, miles above. */
    UnitedKingdom,
}

/**
 * What the user asked for in the settings (SPEC §7.6).
 *
 * Four states rather than three: **following the device's region is a state of
 * its own**, and it is the one the application is in until somebody says
 * otherwise. It is not a synonym for metric — the same choice reads as miles in
 * Boston and as kilometres in Lyon — so it cannot be folded into [UnitSystem].
 *
 * @property id the value written to disk, stable from one release to the next.
 * @property system the units this choice imposes, or `null` where it imposes
 *   none and the region decides.
 */
public enum class UnitChoice(public val id: String, public val system: UnitSystem?) {
    /** Whatever the device's region measures in, and that is the default. */
    FollowSystem("follow_system", null),

    /** Metres and kilometres, wherever the device thinks it is. */
    Metric("metric", UnitSystem.Metric),

    /** Feet and miles. */
    UnitedStates("united_states", UnitSystem.UnitedStates),

    /** Yards and miles. */
    UnitedKingdom("united_kingdom", UnitSystem.UnitedKingdom),
    ;

    /**
     * The units to write in, given what the device's region measures in.
     *
     * @param region the system the device's formatting locale uses, which is
     *   the answer whenever no choice imposes another.
     */
    public fun resolve(region: UnitSystem): UnitSystem = system ?: region

    public companion object {
        /**
         * Reads a stored choice back; anything unknown follows the system.
         *
         * An absent value and an unreadable one give the same answer, and it is
         * the one that decides nothing: a word written by another version, or
         * by a hand, must not be read as a system in particular — the reader
         * would then be shown feet they never asked for.
         */
        public fun fromId(id: String?): UnitChoice = entries.firstOrNull { it.id == id }
            ?: FollowSystem
    }
}
