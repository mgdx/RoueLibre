package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import kotlin.time.Duration

/**
 * Calcule des itinéraires. Abstrait ici pour que l'algorithme de trajet reste
 * en Kotlin pur, testable sans moteur ni graphe (SPEC §14).
 */
public interface Router {

    /**
     * Trace un itinéraire entre deux points.
     *
     * @return le tracé, ou la raison de l'échec. Ne lève jamais.
     */
    public suspend fun route(from: Coordinates, to: Coordinates, mode: TravelMode): RouteResult
}

/**
 * Un trajet complet marche → vélo → marche.
 *
 * @property departureStation station où l'on prend le vélo.
 * @property arrivalStation station où on le rend.
 * @property bikesAtDeparture vélos disponibles au moment du calcul. Toujours
 *   affiché, pour que l'utilisateur juge lui-même du risque (SPEC §6).
 * @property docksAtArrival places libres au moment du calcul.
 * @property walkToStation marche d'accès à la station de départ.
 * @property ride trajet à vélo entre les deux stations.
 * @property walkToDestination marche de la station d'arrivée à la destination.
 * @property handlingTime temps forfaitaire de prise et de dépose.
 * @property riskPenalty pénalité de fiabilité, exprimée en temps. Elle sert à
 *   classer les propositions, jamais à être annoncée comme une durée : le
 *   temps affiché à l'utilisateur est [travelTime].
 */
public data class JourneyOption(
    public val departureStation: Station,
    public val arrivalStation: Station,
    public val bikesAtDeparture: Int,
    public val docksAtArrival: Int,
    public val walkToStation: RouteLeg,
    public val ride: RouteLeg,
    public val walkToDestination: RouteLeg,
    public val handlingTime: Duration,
    public val riskPenalty: Duration,
) {
    /** Durée réellement attendue, forfaits compris et pénalité exclue. */
    public val travelTime: Duration
        get() = walkToStation.duration + ride.duration +
            walkToDestination.duration + handlingTime

    /** Durée servant au classement : le temps attendu, majoré du risque. */
    public val rankingTime: Duration
        get() = travelTime + riskPenalty

    /** Distance totale parcourue, marche comprise. */
    public val distanceMetres: Int
        get() = walkToStation.distanceMetres + ride.distanceMetres +
            walkToDestination.distanceMetres
}

/**
 * Ce que l'algorithme rend pour un trajet demandé.
 */
public sealed interface JourneyPlan {

    /**
     * Un trajet à vélo a été trouvé.
     *
     * @property best la meilleure proposition.
     * @property alternatives jusqu'à trois autres couples de stations, dans
     *   l'ordre. Le SPEC §6 les exige : l'utilisateur doit pouvoir préférer
     *   une station mieux fournie à la plus rapide.
     * @property directWalk marche directe, quand elle a pu être calculée.
     * @property walkingIsFaster vrai quand marcher tout du long va plus vite
     *   que le trajet à vélo. Le SPEC §6 impose de le dire.
     */
    public data class Found(
        public val best: JourneyOption,
        public val alternatives: List<JourneyOption>,
        public val directWalk: RouteLeg?,
        public val walkingIsFaster: Boolean,
    ) : JourneyPlan

    /**
     * Aucun trajet à vélo n'est possible, mais la marche directe l'est.
     *
     * @property reason ce qui a manqué.
     */
    public data class WalkOnly(public val directWalk: RouteLeg, public val reason: NoBikeJourney) :
        JourneyPlan

    /** Rien n'a pu être calculé. */
    public data class Impossible(public val reason: NoBikeJourney) : JourneyPlan
}

/**
 * Pourquoi aucun trajet à vélo n'a été retenu.
 *
 * Le SPEC §6 est explicite : quand aucune station proche n'a de vélo,
 * l'application doit le dire, pas proposer un trajet impossible.
 */
public sealed interface NoBikeJourney {

    /** Aucune station en service avec au moins un vélo près du départ. */
    public data object NoBikeNearby : NoBikeJourney

    /** Aucune station en service avec au moins une place près de l'arrivée. */
    public data object NoDockNearby : NoBikeJourney

    /** Des stations existent, mais aucun itinéraire ne les relie. */
    public data object NoRouteBetweenStations : NoBikeJourney

    /** Le graphe de routage n'est pas installé. */
    public data object GraphMissing : NoBikeJourney

    /** Un des deux points est hors de l'emprise couverte. */
    public data object OutsideCoverage : NoBikeJourney
}
