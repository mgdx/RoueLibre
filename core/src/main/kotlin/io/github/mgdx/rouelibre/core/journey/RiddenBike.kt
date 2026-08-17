package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.routing.TravelMode

/**
 * The bike a ride is worked out on (SPEC §6, §7.3, §7.6).
 *
 * Until 17 August 2026 there was no such notion: one profile served every bike
 * leg, and the kind of bike — asked of the network or declared as one's own —
 * reached neither the track nor the minutes. That decision fell on that date.
 * A pedal-assist bike really is quicker and really does climb without effort,
 * and announcing the same minutes for both was a larger error than the one it
 * guarded against.
 *
 * **What it guarded against is guarded still.** SPEC §6 announces no time the
 * application did not compute, and no speed is added here for the motor: the
 * assisted ride is traced by the routing engine with [TravelMode.ElectricCycling],
 * a profile that describes that bike — its motor's power, its extra five kilos,
 * a hill it no longer needs to go round. The engine computes, as before.
 *
 * ## Why a factor as well as a profile
 *
 * The profile gives the engine's free-running estimate, and that estimate is
 * unobstructed: no lights, no traffic, no getting going again. Measured over
 * eleven legs of the Lille graph on 17 August 2026 — 331 m to 15.2 km — the two
 * profiles trace the **same tracks** and the assisted one takes 0.829 to 0.891
 * of the mechanical one's time, 0.840 over the whole set. What is left to the
 * factor is only the gap between that and the ratio actually observed in town,
 * where a share bike averages some 15 km/h against 18 to 19 for an assisted one.
 * **The two do not stack blindly**: the factor carries what the profile does not,
 * and nothing more.
 *
 * ## Where it enters, and why there
 *
 * Exactly where [WalkingPace] enters — in `JourneyPlanner.atThePacesAsked`,
 * before any pair of stations is compared. It is not a matter of presentation:
 * the ride is one of the three legs the pairs are weighed on, so a quicker bike
 * genuinely earns a station further off, at the price of a shorter walk. A
 * factor that only reached the figures on the screen would announce minutes the
 * choice of stations had not been made on.
 *
 * ## Two questions, one answer
 *
 * This is the notion the `core` module owns. Whether the rider asked the network
 * for an electric bike (§7.3) or declared their own to be one (§7.6) are two
 * different questions, asked in two places, written under two keys, and they
 * stay apart in the application layer — which is where they are translated into
 * this. Nothing here knows which of the two was asked.
 *
 * @property travelMode the routing profile the ride is traced with.
 * @property durationFactor what the ride's duration is multiplied by, on top of
 *   what the profile already gives. Only the ride: no walking leg is touched by
 *   this, a motor saying nothing about how one walks.
 */
public enum class RiddenBike(
    public val travelMode: TravelMode,
    public val durationFactor: Double,
) {

    /**
     * A bike one pedals alone, and the default.
     *
     * The mechanical profile and a factor of exactly one: the engine's estimate
     * passed on untouched, which is what makes this the value a journey worked
     * out before this notion existed comes back identical under.
     *
     * **It is also what "any bike" gets**, and that is the pessimistic choice,
     * made deliberately (SPEC §6): somebody who asked for nothing may well be
     * lent a mechanical bike, and must not be promised minutes that assumed an
     * assistance the station may not hand them.
     */
    Mechanical(TravelMode.Cycling, 1.00),

    /**
     * A pedal-assist bike — about twenty per cent quicker, all told.
     *
     * **The factor is what the profile does not already give, and no more.**
     * Measured over eleven legs of the Lille graph on 17 August 2026, 331 m to
     * 15.2 km, [TravelMode.ElectricCycling] alone takes ×0.8406 of the
     * mechanical profile's time; 0.95 on top of it brings the whole to ×0.80,
     * which is the ratio observed in town between a share bike at some 15 km/h
     * and an assisted one at 18 to 19.
     *
     * Rounded to two figures, like [WalkingPace]'s: a third would claim a
     * precision the measurement behind it does not have.
     */
    ElectricallyAssisted(TravelMode.ElectricCycling, 0.95),
}
