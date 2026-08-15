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
 * @property bikesByVehicleTypeAtDeparture how those bikes divided between the
 *   network's own vehicle type identifiers at that same instant, and empty
 *   where the feed publishes no breakdown. Carried raw, because turning
 *   identifiers into kinds takes the network's table, which this algorithm has
 *   no business knowing (SPEC §15): it is the interface that reads it, to say
 *   how many of the bikes waiting are electric (SPEC §7.4).
 * @property docksAtArrival free docks when the journey was computed.
 * @property walkToStation the access walk to the departure station.
 * @property ride the bike leg between the two stations.
 * @property walkToDestination the walk from the arrival station to the
 *   destination.
 * @property riskPenalty the reliability penalty, expressed in time. It serves
 *   to rank the options, never to be announced as a duration: the time shown to
 *   the user is [travelTime].
 */
public data class JourneyOption(
    public val departureStation: Station,
    public val arrivalStation: Station,
    public val bikesAtDeparture: Int,
    public val bikesByVehicleTypeAtDeparture: Map<String, Int> = emptyMap(),
    public val docksAtArrival: Int,
    public val walkToStation: RouteLeg,
    public val ride: RouteLeg,
    public val walkToDestination: RouteLeg,
    public val riskPenalty: Duration,
) {
    /** The duration actually expected, penalty excluded: the three legs, and nothing else. */
    public val travelTime: Duration
        get() = walkToStation.duration + ride.duration + walkToDestination.duration

    /** The duration used for ranking: the expected time, raised by the risk. */
    public val rankingTime: Duration
        get() = travelTime + riskPenalty

    /** The total distance covered, walking included. */
    public val distanceMetres: Int
        get() = walkToStation.distanceMetres + ride.distanceMetres +
            walkToDestination.distanceMetres

    /**
     * The metres climbed over the whole journey, the two walks included.
     *
     * The three legs add up rather than the ends being subtracted: what a
     * journey costs is every hill gone up, and a climb repaid by a descent
     * further on is still a climb. Each leg's figure is the routing engine's
     * filtered ascent, which already forgives dips of ten metres — so what is
     * summed here is real relief, not the sampling noise of the elevation
     * data.
     */
    public val climbMetres: Int
        get() = walkToStation.ascentMetres + ride.ascentMetres +
            walkToDestination.ascentMetres
}

/**
 * What the algorithm returns for a requested journey.
 */
public sealed interface JourneyPlan {

    /**
     * A bike journey was found, and it beats walking.
     *
     * One journey and one only: the pair the algorithm proved best (SPEC §6).
     * The runners-up are not carried, because they are not offered — a second
     * list of station pairs asked the user to arbitrate a choice the risk
     * penalty has already made for them.
     *
     * @property best the chosen option.
     */
    public data class Found(public val best: JourneyOption) : JourneyPlan

    /**
     * The journey offered runs on foot from end to end.
     *
     * Either no bike journey could be composed, or one could and lost to the
     * walk — in both cases walking is the answer, and [reason] says which of
     * the two brought us here.
     *
     * @property directWalk the walk from one end to the other.
     * @property reason why no bike is ridden.
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
 * say so, not propose an impossible journey. One of these causes is not a
 * failure at all — [WalkingIsQuicker] — but it ends the same way, on a walk.
 */
public sealed interface NoBikeJourney {

    /**
     * A bike journey exists, and the walk gets there sooner.
     *
     * The bike journey is then not carried: the user asked to reach a place,
     * not to fetch a bike, and offering a trip alongside a note saying the walk
     * beat it left them to arbitrate a comparison already settled.
     */
    public data object WalkingIsQuicker : NoBikeJourney

    /**
     * The network has no station in service holding a bike.
     *
     * Distance is not what brings us here — no station is ever too far to be
     * examined. This is the whole network being empty, or out of service, on
     * the lending side.
     */
    public data object NoBikeNearby : NoBikeJourney

    /** The network has no station in service with a free dock. */
    public data object NoDockNearby : NoBikeJourney

    /** Stations exist, but no route joins them. */
    public data object NoRouteBetweenStations : NoBikeJourney

    /** The routing graph is not installed. */
    public data object GraphMissing : NoBikeJourney

    /** One of the two points lies outside the covered area. */
    public data object OutsideCoverage : NoBikeJourney
}
