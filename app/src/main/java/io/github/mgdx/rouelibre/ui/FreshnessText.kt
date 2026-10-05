package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.Freshness
import io.github.mgdx.rouelibre.core.station.isLoadingFirstAvailability

/**
 * Puts the displayed data's age into words (SPEC §4.1).
 *
 * @return an expression of the form "12 seconds ago", with no sentence around
 *   it.
 */
fun Freshness.toRelativeText(context: Context): String = when (this) {
    Freshness.JustNow -> context.getString(R.string.freshness_just_now)
    is Freshness.Seconds ->
        context.resources.getQuantityString(R.plurals.freshness_seconds, value, value)
    is Freshness.Minutes ->
        context.resources.getQuantityString(R.plurals.freshness_minutes, value, value)
    is Freshness.Hours ->
        context.resources.getQuantityString(R.plurals.freshness_hours, value, value)
    is Freshness.Days ->
        context.resources.getQuantityString(R.plurals.freshness_days, value, value)
    is Freshness.Months ->
        context.resources.getQuantityString(R.plurals.freshness_months, value, value)
    Freshness.Never -> context.getString(R.string.freshness_never)
}

/**
 * Composes the age line shown under the screen's title.
 *
 * Offline, or after a missed refresh, the data must be explicitly marked as
 * frozen: presenting an hour-old state as though it were current would be a lie
 * (SPEC §4.1).
 *
 * @param isStale true when the state is too old to pass for current.
 * @param isRefreshing true while a refresh is under way; it changes the line
 *   only before any availability has arrived — see
 *   [isLoadingFirstAvailability].
 */
fun Freshness.toStatusLine(
    context: Context,
    isStale: Boolean,
    isRefreshing: Boolean = false,
): String = when {
    isLoadingFirstAvailability(this, isRefreshing) -> context.getString(R.string.freshness_loading)
    this is Freshness.Never -> context.getString(R.string.freshness_never)
    isStale -> context.getString(R.string.freshness_stale, toRelativeText(context))
    else -> context.getString(R.string.freshness_fresh, toRelativeText(context))
}
