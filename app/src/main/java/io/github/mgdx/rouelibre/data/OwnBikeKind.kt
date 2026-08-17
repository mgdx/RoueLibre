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
 * **Since 17 August 2026 it reaches the ride as well as the drawing.** Until
 * then it changed nothing but what was drawn and said — no speed, no
 * coefficient, no dedicated profile — on the grounds that §6 announces only
 * what the routing engine traced. That decision fell: a pedal-assist bike is
 * genuinely quicker, and announcing the same minutes for both was the larger
 * error. What it was protecting is protected still, because what changed is the
 * profile the engine traces with — `RiddenBike.ElectricallyAssisted` — and not
 * a figure invented afterwards.
 *
 * **This type stays here all the same.** It is a preference of the application,
 * with a storage key and a wording of its own, and the `core` module has no
 * business knowing either; what crosses into it is `RiddenBike`, the bike a ride
 * is computed on, and `asRiddenBike` is the whole of the translation.
 *
 * `null` — the type is used as a nullable throughout — is "not specified", the
 * state at installation and after any reset. It rides exactly as [Mechanical]
 * does, to the track and to the minute, and it keeps the drawings and the
 * sentences of the version before this choice existed.
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
