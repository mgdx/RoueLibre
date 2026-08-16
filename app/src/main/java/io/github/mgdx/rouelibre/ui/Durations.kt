package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.journey.inShownMinutes
import kotlin.time.Duration

/**
 * Puts a journey duration into words.
 *
 * Rounded to the minute: a route estimate is worth no better than that, and
 * showing seconds would promise an exactness the computation does not have. One
 * minute at least, even for fifty metres — "0 min" would read as a fault. The
 * rounding itself belongs to the business module, which is also where the legs
 * of one journey are rounded together so their figures agree.
 *
 * @return a duration ready to show, "14 min" or "1 h 05" for instance.
 */
fun Context.formatDuration(duration: Duration): String = formatMinutes(duration.inShownMinutes())

/**
 * Puts an already rounded count of minutes into words.
 *
 * What a journey's legs are shown with: they are rounded as a group, so that
 * the parts add up to the total, and rounding them a second time here would
 * undo that.
 */
fun Context.formatMinutes(minutes: Int): String {
    if (minutes < MINUTES_PER_HOUR) {
        return getString(R.string.duration_minutes, minutes)
    }
    return getString(
        R.string.duration_hours_minutes,
        minutes / MINUTES_PER_HOUR,
        minutes % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR = 60
