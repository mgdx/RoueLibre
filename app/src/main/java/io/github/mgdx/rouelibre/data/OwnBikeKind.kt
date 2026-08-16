package io.github.mgdx.rouelibre.data

/**
 * What the user says their **own** bike is (SPEC §7.3, §7.6).
 *
 * **Not to be confused with `WantedBikeKind`, and the two are deliberately not
 * named alike.** That one is a question about the **network**: which of the
 * bikes standing at a station one wants to be sent to, so it exists only where
 * the network lends both kinds, it is asked on the journey screen, and it
 * narrows the stations the algorithm of §6 may choose. This one is a question
 * about the rider: their own bike belongs to nobody's fleet, it is what it is in
 * Lille as in Lyon, so it is offered **everywhere**, `FleetDescription.isMixed`
 * being none of its business, and it is asked once in the settings.
 *
 * **It changes nothing but what is drawn and said.** No speed, no coefficient,
 * no profile: a pedal-assist bike is quicker in the real world, but §6 announces
 * only what the routing engine traced, and the ride is traced over the same
 * graph with the same profile whatever kind is declared. That is a decision and
 * not an omission — a figure invented for the motor is a figure nobody measured
 * — and it is why this type lives here, in the application's settings, and not
 * in the `core` module where the algorithm lives: nothing there can reach it.
 *
 * `null` — the type is used as a nullable throughout — is "not specified", the
 * state at installation and after any reset, and it reproduces exactly the
 * drawings and the sentences of the version before this choice existed.
 *
 * @property id the value written to disk, stable from one release to the next.
 */
enum class OwnBikeKind(val id: String) {
    /** A bike one pedals alone. Drawn plain, like an unspecified one. */
    Mechanical("mechanical"),

    /** A pedal-assist bike. Drawn bearing the bolt. */
    Electric("electric"),
    ;

    companion object {
        /**
         * Reads a stored word back, or `null` for "not specified".
         *
         * A word this build cannot read — written by another version, or by a
         * hand — is read as nothing said, never as a kind: standing in for it
         * with a guess would put a bolt on somebody's bike who never claimed
         * one.
         */
        fun fromId(id: String?): OwnBikeKind? = entries.firstOrNull { it.id == id }
    }
}
