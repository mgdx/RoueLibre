package io.github.mgdx.rouelibre.core.journey

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How a journey's durations are rounded before they are shown (SPEC §7.4).
 *
 * Every screen that shows a journey shows it twice over: a total, and the parts
 * that make it up. Rounding each of them on its own is what made the three
 * figures of the result card disagree — "26 min" beside "3 min · 22 min ·
 * 3 min", which adds up to 28. Three roundings up of half a minute each cost
 * nearly two minutes the journey does not take, and the reader is left holding
 * an application that cannot add.
 *
 * So the rounding happens once, over the whole, and the remainder is shared out
 * among the legs: the parts shown are whole minutes that add up, exactly, to the
 * total shown. What is lost is that a leg's figure is no longer that leg's own
 * rounding — a leg may read one minute short of what it would read alone. What
 * is gained is a screen whose figures agree, which is what the reader checks
 * first.
 */

/** A minute, in seconds — the unit every journey duration is shown in. */
private const val SECONDS_PER_MINUTE = 60L

/**
 * A duration as its own screen shows it: whole minutes, never less than one.
 *
 * Rounded up, because a route estimate is worth no better than the minute and
 * announcing less time than the computation found would be the wrong way to be
 * wrong. One minute at least, even for fifty metres: "0 min" reads as a fault.
 */
public fun Duration.inShownMinutes(): Int {
    val seconds = inWholeSeconds.coerceAtLeast(0)
    return ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE)
        .coerceAtLeast(1)
        .toInt()
}

/**
 * Shares a whole out into whole minutes, so the parts add up to it.
 *
 * The total is the one [inShownMinutes] gives for the durations added together.
 * Each part starts at its own minutes rounded **down** — never below one, for
 * the same reason a duration is never shown as zero — and the minutes still to
 * place go to the parts that lost the most seconds to that rounding. Which is
 * the fairest place for them: they are the seconds those parts actually ran.
 *
 * Where the floors alone already exceed the rounded total — three legs of twenty
 * seconds each, each worth its minute — the total shown is what its parts add up
 * to. A total that contradicted the figures printed beneath it would be the very
 * fault this exists to prevent.
 *
 * @param durations the parts, in the order they are shown.
 * @return one whole-minute figure per part, in the same order.
 */
public fun apportionMinutes(durations: List<Duration>): List<Int> {
    if (durations.isEmpty()) return emptyList()
    val seconds = durations.map { it.inWholeSeconds.coerceAtLeast(0) }
    val apportioned = seconds
        .map { (it / SECONDS_PER_MINUTE).coerceAtLeast(1).toInt() }
        .toMutableList()
    val target = maxOf(seconds.sum().seconds.inShownMinutes(), apportioned.sum())
    // The parts whose discarded seconds weigh most, first: a leg cut of
    // fifty-nine seconds has a better claim to the spare minute than one cut of
    // one second. Ties fall to the earlier part, which keeps the answer the same
    // from one run to the next.
    val byDiscardedSeconds = seconds.indices.sortedByDescending { seconds[it] % SECONDS_PER_MINUTE }
    repeat(target - apportioned.sum()) { placed ->
        val part = byDiscardedSeconds[placed % byDiscardedSeconds.size]
        apportioned[part] += 1
    }
    return apportioned
}

/**
 * A journey's three legs, in the whole minutes every screen shows them in.
 *
 * @property walkToStation the walk to the departure station.
 * @property ride the bike leg.
 * @property walkToDestination the walk that ends the journey.
 */
public data class JourneyMinutes(
    public val walkToStation: Int,
    public val ride: Int,
    public val walkToDestination: Int,
) {
    /** The two walks together, as the summary line says them in one figure. */
    public val walking: Int
        get() = walkToStation + walkToDestination

    /** The total announced above them, which is their sum by construction. */
    public val total: Int
        get() = walkToStation + ride + walkToDestination
}

/**
 * The journey's legs as they are to be shown, total included (SPEC §7.4).
 *
 * The one place these figures are worked out, so the result card, the step band
 * and the detail screen cannot round the same journey differently.
 */
public fun JourneyOption.shownMinutes(): JourneyMinutes {
    val (toStation, ride, toDestination) = apportionMinutes(
        listOf(walkToStation.duration, this.ride.duration, walkToDestination.duration),
    )
    return JourneyMinutes(
        walkToStation = toStation,
        ride = ride,
        walkToDestination = toDestination,
    )
}
