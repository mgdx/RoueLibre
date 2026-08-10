package io.github.mgdx.rouelibre.core.journey

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Settings of the journey algorithm (SPEC.md §6).
 *
 * The default values are those of the specification. Each is justified on its
 * own property: they are choices, not numbers found by trial and error, and
 * whoever changes one should know what they are moving.
 *
 * @property departureCandidates how many stations are examined at the departure
 *   end. Five: beyond that, the stations kept are so far from the departure
 *   point that the access walk eats the whole benefit, and every extra
 *   candidate multiplies the number of pairs to evaluate.
 * @property arrivalCandidates how many stations are examined at the arrival
 *   end, for the same reasons.
 * @property maxWalkToStationMetres the distance beyond which a station stops
 *   being a candidate. Twelve hundred metres, about a quarter of an hour on
 *   foot: past that, the access walk and the three minutes of fixed handling
 *   swallow everything the bike could have saved. Without this bound the
 *   algorithm serenely proposes walking four kilometres to fetch a bike, for
 *   want of anything better.
 * @property maxRideEvaluations the maximum number of bike legs actually
 *   computed. This is what BOUNDS the response time required by SPEC §6:
 *   pruning by lower bound does most of the work, but it depends on the
 *   geometry and guarantees nothing on its own. Since pairs are examined from
 *   the most promising to the least, stopping at the sixth almost never costs
 *   the optimum.
 * @property directWalkThresholdMetres the straight-line distance beyond which
 *   the direct walk is no longer computed up front. Three kilometres: on foot
 *   that is already three quarters of an hour, where the same trip by bike
 *   takes twenty minutes, fixed handling included. Walking can no longer win,
 *   and computing it cost a fifth of the time budget by itself. It is still
 *   computed, whatever the distance, when no bike journey is possible: it is
 *   then the only answer to give.
 * @property pickupTime fixed time to unlock and pull out a bike.
 * @property dropoffTime fixed time to rack and lock a bike.
 * @property fallbackPenalty the time lost if the chosen station turns out to be
 *   unusable on arrival: one has to reach the next one on foot. It converts a
 *   risk into minutes, and so makes it comparable to a detour.
 * @property bikeTurnoverPerMinute the rate at which a station empties or fills,
 *   in bikes per minute. About one bike every eight minutes during busy hours;
 *   it is what gives the risk penalty its scale.
 */
public data class JourneySettings(
    public val departureCandidates: Int = 5,
    public val arrivalCandidates: Int = 5,
    public val maxWalkToStationMetres: Double = 1_200.0,
    public val maxRideEvaluations: Int = 6,
    public val directWalkThresholdMetres: Double = 3_000.0,
    public val pickupTime: Duration = 2.minutes,
    public val dropoffTime: Duration = 1.minutes,
    public val fallbackPenalty: Duration = 6.minutes,
    public val bikeTurnoverPerMinute: Double = 0.12,
) {
    init {
        require(departureCandidates > 0) { "at least one departure station is needed" }
        require(arrivalCandidates > 0) { "at least one arrival station is needed" }
        require(maxWalkToStationMetres > 0) { "the walking distance must be positive" }
        require(maxRideEvaluations > 0) { "at least one ride must be evaluated" }
        require(bikeTurnoverPerMinute >= 0) { "a turnover rate cannot be negative" }
    }

    /** Total fixed handling time, at both ends of the bike leg. */
    public val handlingTime: Duration
        get() = pickupTime + dropoffTime
}

/**
 * Turns low availability into minutes of penalty (SPEC.md §6).
 *
 * The specification asks that a station with a single bike be less attractive
 * than a station with eight, even slightly further away. That requires being
 * able to compare a risk with a detour: the penalty is therefore expressed in
 * time, the same unit as the rest of the computation.
 *
 * The reasoning fits in one sentence: while we walk towards the station, other
 * people are helping themselves; if they exhaust the stock before we arrive, we
 * have to reach the next station, which costs
 * [JourneySettings.fallbackPenalty]. The risk therefore grows with the exposure
 * time and shrinks with the stock.
 *
 * This is not a probabilistic model: it is a monotonic heuristic, accepted as
 * such, whose two constants are configurable.
 *
 * @param count bikes available at the departure end, or free docks at the
 *   arrival end.
 * @param exposure the time that elapses before we reach that station.
 */
public fun availabilityRiskPenalty(
    count: Int,
    exposure: Duration,
    settings: JourneySettings,
): Duration {
    if (count <= 0) return settings.fallbackPenalty
    val expectedTurnover = exposure.inWholeSeconds / 60.0 * settings.bikeTurnoverPerMinute
    val risk = (expectedTurnover / count).coerceIn(0.0, 1.0)
    return settings.fallbackPenalty * risk
}
