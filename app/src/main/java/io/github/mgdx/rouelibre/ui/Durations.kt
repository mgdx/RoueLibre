package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import kotlin.time.Duration

/**
 * Puts a journey duration into words.
 *
 * Rounded to the minute: a route estimate is worth no better than that, and
 * showing seconds would promise an exactness the computation does not have. One
 * minute at least, even for fifty metres — "0 min" would read as a fault.
 *
 * @return a duration ready to show, "14 min" or "1 h 05" for instance.
 */
fun Context.formatDuration(duration: Duration): String {
    val totalMinutes = duration.inWholeSeconds
        .let { (it + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE }
        .coerceAtLeast(1)
    if (totalMinutes < MINUTES_PER_HOUR) {
        return getString(R.string.duration_minutes, totalMinutes.toInt())
    }
    return getString(
        R.string.duration_hours_minutes,
        (totalMinutes / MINUTES_PER_HOUR).toInt(),
        (totalMinutes % MINUTES_PER_HOUR).toInt(),
    )
}

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
