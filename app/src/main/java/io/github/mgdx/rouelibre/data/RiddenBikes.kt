package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.journey.RiddenBike
import io.github.mgdx.rouelibre.core.station.WantedBikeKind

/**
 * Where the two bike questions meet, and the only place they do (SPEC §7.6).
 *
 * The algorithm of §6 needs one thing about the bike: which one the ride is
 * worked out on, which is what `RiddenBike` says. The rider is asked two
 * different questions instead — what they want the network to lend them
 * ([WantedBikeKind], on the journey screen, only where the network lends both)
 * and what their own bike is ([OwnBikeKind], once, in the settings). They stay
 * apart everywhere else: two names, two screens, two storage keys, and nothing
 * in `core` knows either of them.
 *
 * These two functions are the whole of the translation, and they belong to the
 * application layer because that is where the questions were asked. Neither
 * type moves into `core`: what the rider prefers and how it is written to disk
 * is none of the algorithm's business.
 */

/**
 * The bike the network was asked for, as the algorithm rides it (SPEC §6).
 *
 * **Asking for nothing is answered with the mechanical bike**, and that is the
 * pessimistic reading, chosen deliberately. "Any bike" is the default and by
 * far the commonest state of the question; the station may hand over either
 * kind, and somebody who asked for no assistance must not be shown minutes that
 * assumed one. It is the same side the reliability penalty errs on: over-state
 * the ride a little and the rider arrives early, under-state it and they were
 * promised a bike that was never there.
 *
 * Read from the filter the algorithm actually received, never from the raw
 * preference: a kind the network cannot honour is dropped before it gets here
 * (see `JourneyViewModel.bikeKindFilter`), and a journey that was not narrowed
 * by a kind must not be timed as though it had been.
 */
fun WantedBikeKind?.asRiddenBike(): RiddenBike = when (this) {
    WantedBikeKind.Electric -> RiddenBike.ElectricallyAssisted
    WantedBikeKind.Mechanical, null -> RiddenBike.Mechanical
}

/**
 * The rider's own bike, as the algorithm rides it (SPEC §7.6).
 *
 * **Not specified and mechanical give the same ride**, to the track and to the
 * minute: a bike declared mechanical and a bike nobody declared are the same
 * plain bike, which is already the rule that governs the bolt on the drawing
 * and the wording of the summary. Only a declared pedal-assist bike changes
 * anything, and it changes it because the rider said so.
 */
fun OwnBikeKind?.asRiddenBike(): RiddenBike = when (this) {
    OwnBikeKind.Electric -> RiddenBike.ElectricallyAssisted
    OwnBikeKind.Mechanical, null -> RiddenBike.Mechanical
}
