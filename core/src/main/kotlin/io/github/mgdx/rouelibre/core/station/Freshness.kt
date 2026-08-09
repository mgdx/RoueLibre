package io.github.mgdx.rouelibre.core.station

import java.time.Duration
import java.time.Instant

/**
 * Âge de la donnée affichée, en tranches lisibles (SPEC §4.1).
 *
 * Le SPEC impose d'afficher cet âge — « il y a 12 s ». Le découpage est fait
 * ici, en Kotlin pur et donc testable ; la mise en mots revient à la couche
 * interface, seule autorisée à contenir du français (SPEC §9).
 */
public sealed interface Freshness {

    /** Trop récent pour qu'un décompte ait du sens. */
    public data object JustNow : Freshness

    /** Moins d'une minute. */
    public data class Seconds(public val value: Int) : Freshness

    /** Moins d'une heure. */
    public data class Minutes(public val value: Int) : Freshness

    /** Moins d'un jour. */
    public data class Hours(public val value: Int) : Freshness

    /** Au-delà d'un jour, le décompte exact n'apprend plus rien. */
    public data object LongAgo : Freshness

    /** Aucune donnée n'a jamais été reçue. */
    public data object Never : Freshness

    /**
     * Vrai quand l'état affiché ne peut plus passer pour courant et doit être
     * signalé comme figé (SPEC §4.1).
     *
     * Cinq minutes : le flux est produit toutes les minutes, donc au-delà de
     * cinq c'est qu'au moins quatre rafraîchissements ont été manqués — on
     * n'est plus devant un retard, mais devant une photographie. En deçà, le
     * dire alarmerait pour un simple rafraîchissement raté.
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

/** Ancienneté, en minutes, au-delà de laquelle l'état est dit figé. */
private const val STALE_AFTER_MINUTES = 5

/** En deçà, afficher un décompte en secondes serait du bruit. */
private const val JUST_NOW_SECONDS = 5

/**
 * Calcule l'âge d'une donnée.
 *
 * @param fetchedAt date de récupération, ou `null` si rien n'a jamais été reçu.
 * @param now instant de référence, injecté pour rendre le calcul testable.
 */
public fun freshnessOf(fetchedAt: Instant?, now: Instant): Freshness {
    if (fetchedAt == null) return Freshness.Never
    val elapsed = Duration.between(fetchedAt, now)
    // Une horloge qui recule — changement de fuseau, correction NTP — ne doit
    // pas produire « il y a -3 secondes ».
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
