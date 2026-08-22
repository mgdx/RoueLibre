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
 * **[Mechanical] is the default**, at installation, after any reset and for a
 * word this build cannot read. A third state, "not specified", stood beside the
 * two until 19 August 2026 and was withdrawn because it settled nothing: it
 * rode, drew and read exactly as [Mechanical] did, so it offered a choice
 * between two labels for one journey. Nothing has to be migrated, and that
 * follows from the same fact — an installation left unspecified wrote no word
 * at all, and the absence is now read as mechanical rather than as nothing
 * said, which is the ride it was already getting.
 *
 * @property id the value written to disk, stable from one release to the next.
 */
enum class OwnBikeKind(val id: String) {
    /** A bike one pedals alone. Drawn plain, and what an installation says until asked. */
    Mechanical("mechanical"),

    /** A pedal-assist bike. Drawn bearing the bolt. */
    Electric("electric"),
    ;

    companion object {
        /**
         * Reads a stored word back, [Mechanical] for a word this build cannot read.
         *
         * Written by another version, or by a hand, such a word falls on the
         * bike that promises the least and never on the assisted one: guessing
         * the other way would put a bolt on the bike of somebody who never
         * claimed one, and time their journey as though it had a motor.
         */
        fun fromId(id: String?): OwnBikeKind = entries.firstOrNull { it.id == id } ?: Mechanical
    }
}
