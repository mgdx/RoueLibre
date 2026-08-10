package io.github.mgdx.rouelibre.core.station

import java.time.Duration
import java.time.Instant

/**
 * The age of the displayed data, in readable bands (SPEC §4.1).
 *
 * The specification requires showing that age — "12 s ago". The banding happens
 * here, in pure Kotlin and therefore testable; putting it into words falls to
 * the interface layer, the only one allowed to hold French (SPEC §9).
 */
public sealed interface Freshness {

    /** Too recent for a countdown to mean anything. */
    public data object JustNow : Freshness

    /** Under a minute. */
    public data class Seconds(public val value: Int) : Freshness

    /** Under an hour. */
    public data class Minutes(public val value: Int) : Freshness

    /** Under a day. */
    public data class Hours(public val value: Int) : Freshness

    /** Beyond a day, the exact count teaches nothing more. */
    public data object LongAgo : Freshness

    /** No data has ever been received. */
    public data object Never : Freshness

    /**
     * True when the displayed state can no longer pass for current and must be
     * flagged as frozen (SPEC §4.1).
     *
     * Five minutes: the feed is produced every minute, so beyond five at least
     * four refreshes have been missed — we are no longer looking at a delay but
     * at a photograph. Below that, saying so would alarm over a single missed
     * refresh.
     */
    public val isStale: Boolean
        get() = when (this) {
            JustNow -> false
            is Seconds -> false
            is Minutes -> value >= STALE_AFTER_MINUTES
            is Hours -> true
            LongAgo -> true
            Never -> true
        }
}

/** The age, in minutes, beyond which the state is called frozen. */
private const val STALE_AFTER_MINUTES = 5

/** Below this, showing a count in seconds would be noise. */
private const val JUST_NOW_SECONDS = 5

/**
 * Computes the age of a piece of data.
 *
 * @param fetchedAt when it was fetched, or `null` if nothing was ever received.
 * @param now the reference instant, injected to keep the computation testable.
 */
public fun freshnessOf(fetchedAt: Instant?, now: Instant): Freshness {
    if (fetchedAt == null) return Freshness.Never
    val elapsed = Duration.between(fetchedAt, now)
    // A clock that goes backwards — a time-zone change, an NTP correction —
    // must not produce "-3 seconds ago".
    if (elapsed.isNegative) return Freshness.JustNow

    val seconds = elapsed.seconds
    return when {
        seconds < JUST_NOW_SECONDS -> Freshness.JustNow
        seconds < 60 -> Freshness.Seconds(seconds.toInt())
        seconds < 3_600 -> Freshness.Minutes((seconds / 60).toInt())
        seconds < 86_400 -> Freshness.Hours((seconds / 3_600).toInt())
        else -> Freshness.LongAgo
    }
}
