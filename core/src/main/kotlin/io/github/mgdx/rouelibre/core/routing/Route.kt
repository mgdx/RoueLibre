package io.github.mgdx.rouelibre.core.routing

import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlin.time.Duration

/**
 * Les deux modes de déplacement d'un trajet porte-à-porte (SPEC.md §5).
 *
 * @property profileName nom du fichier de profil BRouter, sans extension.
 */
public enum class TravelMode(public val profileName: String) {
    /** Trajets d'accès, à pied. */
    Walking("urban-walk"),

    /** Trajet principal, sur un vélo de libre-service. */
    Cycling("city-bike"),
}

/**
 * Un segment d'itinéraire calculé.
 *
 * @property mode à pied ou à vélo.
 * @property distanceMetres longueur du tracé.
 * @property duration durée estimée par le moteur pour ce mode.
 * @property ascentMetres dénivelé positif cumulé.
 * @property geometry le tracé, du départ à l'arrivée.
 */
public data class RouteLeg(
    public val mode: TravelMode,
    public val distanceMetres: Int,
    public val duration: Duration,
    public val ascentMetres: Int,
    public val geometry: List<Coordinates>,
)

/**
 * Pourquoi un calcul d'itinéraire n'a pas abouti.
 *
 * Chaque cas appelle une conduite différente, et donc un message distinct :
 * c'est ce qui a guidé ce découpage, pas la nature technique de l'échec
 * (SPEC §14).
 */
public sealed interface RoutingFailure {

    /** Le graphe de routage n'est pas installé sur l'appareil. */
    public data object GraphMissing : RoutingFailure

    /**
     * Un des points est hors de l'emprise couverte par le graphe.
     *
     * Le SPEC §4 l'exige : hors emprise, l'application doit le dire clairement
     * et jamais échouer en silence.
     */
    public data object OutsideCoverage : RoutingFailure

    /**
     * Aucun chemin praticable entre les deux points pour ce mode.
     *
     * Arrive légitimement : une station de l'autre côté d'un canal sans pont
     * proche, ou une zone piétonne fermée à la circulation cycliste.
     */
    public data object NoRouteFound : RoutingFailure

    /** Le calcul a dépassé le temps imparti. */
    public data object Timeout : RoutingFailure

    /**
     * Le moteur a échoué pour une raison qui lui est propre.
     *
     * @property detail message du moteur, destiné au journal et au rapport de
     *   bogue, jamais à l'écran.
     */
    public data class EngineFailure(public val detail: String) : RoutingFailure
}

/** Issue d'un calcul d'itinéraire. */
public sealed interface RouteResult {

    /** Le tracé demandé. */
    public data class Success(public val leg: RouteLeg) : RouteResult

    /** Le calcul n'a pas abouti. */
    public data class Failure(public val reason: RoutingFailure) : RouteResult
}
