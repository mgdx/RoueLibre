package io.github.mgdx.rouelibre.core.journey

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Réglages de l'algorithme de trajet (SPEC.md §6).
 *
 * Les valeurs par défaut sont celles du cahier des charges. Chacune est
 * justifiée sur sa propriété : ce sont des choix, pas des nombres trouvés par
 * tâtonnement, et quiconque les modifie doit savoir ce qu'il déplace.
 *
 * @property departureCandidates nombre de stations examinées au départ. Cinq :
 *   au-delà, les stations retenues sont si éloignées du point de départ que la
 *   marche d'accès mange tout le bénéfice, et chaque candidate supplémentaire
 *   multiplie le nombre de couples à évaluer.
 * @property arrivalCandidates nombre de stations examinées à l'arrivée, pour
 *   les mêmes raisons.
 * @property maxWalkToStationMetres distance au-delà de laquelle une station
 *   cesse d'être une candidate. Douze cents mètres, soit un quart d'heure de
 *   marche : au-delà, la marche d'accès et les quatre minutes de forfait
 *   engloutissent tout ce que le vélo pouvait faire gagner. Sans cette borne,
 *   l'algorithme propose sereinement de marcher quatre kilomètres pour aller
 *   chercher un vélo, faute de mieux.
 * @property maxRideEvaluations nombre maximal de trajets à vélo réellement
 *   calculés. C'est ce qui BORNE le temps de réponse, exigé par le SPEC §6 :
 *   l'élagage par borne inférieure fait l'essentiel du travail, mais il dépend
 *   de la géométrie et ne garantit rien à lui seul. Les couples étant examinés
 *   du plus prometteur au moins prometteur, s'arrêter au sixième ne coûte à peu
 *   près jamais l'optimum.
 * @property directWalkThresholdMetres distance à vol d'oiseau au-delà de
 *   laquelle la marche directe n'est plus calculée d'emblée. Trois kilomètres :
 *   à pied c'est déjà trois quarts d'heure, quand le même trajet à vélo en
 *   demande vingt, forfaits compris. La marche ne peut plus gagner, et la
 *   calculer coûtait à elle seule un cinquième du budget de temps. Elle reste
 *   calculée, quelle que soit la distance, lorsqu'aucun trajet à vélo n'est
 *   possible : c'est alors la seule réponse à donner.
 * @property pickupTime temps forfaitaire pour déverrouiller et sortir un vélo.
 * @property dropoffTime temps forfaitaire pour ranger et verrouiller un vélo.
 * @property fallbackPenalty temps perdu si la station retenue s'avère
 *   inutilisable à l'arrivée : il faut rejoindre la suivante à pied. Sert à
 *   convertir un risque en minutes, donc à le rendre comparable à un détour.
 * @property bikeTurnoverPerMinute vitesse à laquelle une station se vide ou se
 *   remplit, en vélos par minute. Un vélo toutes les huit minutes environ aux
 *   heures actives ; c'est ce qui donne son échelle à la pénalité de risque.
 */
public data class JourneySettings(
    public val departureCandidates: Int = 5,
    public val arrivalCandidates: Int = 5,
    public val maxWalkToStationMetres: Double = 1_200.0,
    public val maxRideEvaluations: Int = 6,
    public val directWalkThresholdMetres: Double = 3_000.0,
    public val pickupTime: Duration = 2.minutes,
    public val dropoffTime: Duration = 2.minutes,
    public val fallbackPenalty: Duration = 6.minutes,
    public val bikeTurnoverPerMinute: Double = 0.12,
) {
    init {
        require(departureCandidates > 0) { "il faut au moins une station de départ" }
        require(arrivalCandidates > 0) { "il faut au moins une station d'arrivée" }
        require(maxWalkToStationMetres > 0) { "la distance de marche doit être positive" }
        require(maxRideEvaluations > 0) { "il faut évaluer au moins un trajet" }
        require(bikeTurnoverPerMinute >= 0) { "une rotation ne peut pas être négative" }
    }

    /** Temps forfaitaire total, aux deux extrémités du trajet à vélo. */
    public val handlingTime: Duration
        get() = pickupTime + dropoffTime
}

/**
 * Convertit une disponibilité faible en minutes de pénalité (SPEC.md §6).
 *
 * Le cahier des charges demande qu'une station à un seul vélo soit moins
 * attractive qu'une station à huit, même un peu plus loin. Encore faut-il
 * pouvoir comparer un risque à un détour : la pénalité est donc exprimée en
 * temps, la même unité que le reste du calcul.
 *
 * Le raisonnement tient en une phrase : pendant qu'on marche vers la station,
 * d'autres personnes s'y servent ; si elles épuisent le stock avant l'arrivée,
 * il faut rejoindre la station suivante, ce qui coûte [JourneySettings.fallbackPenalty].
 * Le risque croît donc avec le temps d'exposition et décroît avec le stock.
 *
 * Ce n'est pas un modèle probabiliste : c'est une heuristique monotone,
 * assumée comme telle, dont les deux constantes sont réglables.
 *
 * @param count vélos disponibles au départ, ou places libres à l'arrivée.
 * @param exposure temps qui s'écoule avant qu'on n'atteigne cette station.
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
