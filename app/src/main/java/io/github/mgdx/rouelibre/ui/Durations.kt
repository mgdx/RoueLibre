package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import kotlin.time.Duration

/**
 * Met une durée de trajet en mots.
 *
 * Arrondie à la minute : une estimation d'itinéraire ne vaut pas mieux que
 * cela, et afficher des secondes promettrait une exactitude que le calcul n'a
 * pas. Une minute au moins, même pour cinquante mètres — « 0 min » se lirait
 * comme une erreur.
 *
 * @return une durée prête à afficher, par exemple « 14 min » ou « 1 h 05 ».
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
