package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import kotlin.time.Duration

/**
 * Computes routes. Abstracted here so the journey algorithm stays in pure
 * Kotlin, testable without an engine or a graph (SPEC §14).
 */
public interface Router {

    /**
     * Traces a route between two points.
     *
     * @return the track, or the reason for the failure. Never throws.
     */
    public suspend fun route(from: Coordinates, to: Coordinates, mode: TravelMode): RouteResult
}

/**
 * A complete walk → bike → walk journey.
 *
 * @property departureStation the station where the bike is picked up.
 * @property arrivalStation the station where it is returned.
 * @property bikesAtDeparture bikes available when the journey was computed.
 *   Always shown, so the user can judge the risk for themselves (SPEC §6).
 * @property docksAtArrival free docks when the journey was computed.
 * @property walkToStation the access walk to the departure station.
 * @property ride the bike leg between the two stations.
 * @property walkToDestination the walk from the arrival station to the
 *   destination.
 * @property handlingTime the fixed pick-up and drop-off time.
 * @property riskPenalty the reliability penalty, expressed in time. It serves
 *   to rank the options, never to be announced as a duration: the time shown to
 *   the user is [travelTime].
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
    /** The duration actually expected, fixed handling included, penalty excluded. */
    public val travelTime: Duration
        get() = walkToStation.duration + ride.duration +
            walkToDestination.duration + handlingTime

    /** The duration used for ranking: the expected time, raised by the risk. */
    public val rankingTime: Duration
        get() = travelTime + riskPenalty

    /** The total distance covered, walking included. */
    public val distanceMetres: Int
        get() = walkToStation.distanceMetres + ride.distanceMetres +
            walkToDestination.distanceMetres
}

/**
 * What the algorithm returns for a requested journey.
 */
public sealed interface JourneyPlan {

    /**
     * A bike journey was found.
     *
     * @property best the best option.
     * @property alternatives up to three other station pairs, in order. SPEC §6
     *   requires them: the user must be able to prefer a better-stocked station
     *   to the fastest one.
     * @property directWalk the direct walk, when it could be computed.
     * @property walkingIsFaster true when walking all the way is quicker than
     *   the bike journey. SPEC §6 requires saying so.
     */
    public data class Found(
        public val best: JourneyOption,
        public val alternatives: List<JourneyOption>,
        public val directWalk: RouteLeg?,
        public val walkingIsFaster: Boolean,
    ) : JourneyPlan

    /**
     * No bike journey is possible, but the direct walk is.
     *
     * @property reason what was missing.
     */
    public data class WalkOnly(public val directWalk: RouteLeg, public val reason: NoBikeJourney) :
        JourneyPlan

    /** Nothing could be computed. */
    public data class Impossible(public val reason: NoBikeJourney) : JourneyPlan
}

/**
 * Why no bike journey was retained.
 *
 * SPEC §6 is explicit: when no nearby station has a bike, the application must
 * say so, not propose an impossible journey.
 */
public sealed interface NoBikeJourney {

    /** No station in service with at least one bike near the departure point. */
    public data object NoBikeNearby : NoBikeJourney

    /** No station in service with at least one dock near the arrival point. */
    public data object NoDockNearby : NoBikeJourney

    /** Stations exist, but no route joins them. */
    public data object NoRouteBetweenStations : NoBikeJourney

    /** The routing graph is not installed. */
    public data object GraphMissing : NoBikeJourney

    /** One of the two points lies outside the covered area. */
    public data object OutsideCoverage : NoBikeJourney
}
