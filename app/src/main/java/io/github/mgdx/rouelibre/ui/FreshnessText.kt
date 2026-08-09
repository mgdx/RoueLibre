package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.Freshness

/**
 * Met en mots l'âge de la donnée affichée (SPEC §4.1).
 *
 * @return une expression du type « il y a 12 secondes », sans phrase autour.
 */
fun Freshness.toRelativeText(context: Context): String = when (this) {
    Freshness.JustNow -> context.getString(R.string.freshness_just_now)
    is Freshness.Seconds ->
        context.resources.getQuantityString(R.plurals.freshness_seconds, value, value)
    is Freshness.Minutes ->
        context.resources.getQuantityString(R.plurals.freshness_minutes, value, value)
    is Freshness.Hours ->
        context.resources.getQuantityString(R.plurals.freshness_hours, value, value)
    Freshness.LongAgo -> context.getString(R.string.freshness_long_ago)
    Freshness.Never -> context.getString(R.string.freshness_never)
}

/**
 * Compose la ligne d'âge affichée sous le titre de l'écran.
 *
 * Hors ligne ou après un rafraîchissement manqué, la donnée doit être
 * explicitement marquée comme figée : présenter un état vieux d'une heure
 * comme s'il était courant serait un mensonge (SPEC §4.1).
 *
 * @param isStale vrai quand l'état est trop vieux pour passer pour courant.
 */
fun Freshness.toStatusLine(context: Context, isStale: Boolean): String = when {
    this is Freshness.Never -> context.getString(R.string.freshness_never)
    isStale -> context.getString(R.string.freshness_stale, toRelativeText(context))
    else -> context.getString(R.string.freshness_fresh, toRelativeText(context))
}
