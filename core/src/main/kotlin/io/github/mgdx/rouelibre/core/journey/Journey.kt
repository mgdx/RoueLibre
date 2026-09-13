package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
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
 * Where a journey's bike is taken (SPEC §6, §7.4).
 *
 * Two answers, and the algorithm only ever chooses the first of them. The
 * ordinary search departs from a station and never from a bike standing in the
 * street: nothing in the feed says that such a bike may be taken (SPEC §4.1),
 * so the application does not pick one for anybody. The second is the journey
 * somebody asked for themselves, from a bike they chose on the map
 * (SPEC §7.2.1).
 *
 * The other end is a [Station] and stays one, whichever of these the journey
 * begins at: a bike has to be handed back, and a free dock is the only place
 * that takes it (SPEC §6).
 */
public sealed interface DeparturePoint {

    /** Where the bike stands, which is where the journey begins. */
    public val position: Coordinates

    /**
     * A station of the network, the ordinary way a journey begins.
     *
     * @property station the station the bike is picked up at.
     */
    public data class AtStation(public val station: Station) : DeparturePoint {
        override val position: Coordinates
            get() = station.position
    }

    /**
     * A bike the network reports away from its stations (SPEC §7.2.1).
     *
     * @property bike the bike chosen on the map, as the feed reported it.
     * @property kind what the network's vehicle type table reads that bike as
     *   (SPEC §4.1). Carried beside the bike rather than read from it here:
     *   the table is the network's and this algorithm knows no network
     *   (SPEC §15). It is what the ride is traced on.
     */
    public data class AtStreetBike(public val bike: StreetBike, public val kind: VehicleKind) :
        DeparturePoint {
        override val position: Coordinates
            get() = bike.position
    }
}

/**
 * A complete walk → bike → walk journey.
 *
 * @property departure where the bike is picked up: a station, or a bike the
 *   rider chose outside them (SPEC §7.2.1).
 * @property arrivalStation the station where it is returned.
 * @property bikesAtDeparture bikes available when the journey was computed.
 *   Always shown, so the user can judge the risk for themselves (SPEC §6).
 *   One, on a journey beginning at a bike outside stations: a bike is one
 *   bike, and nothing is counted on it (SPEC §7.2.1).
 * @property bikesByVehicleTypeAtDeparture how those bikes divided between the
 *   network's own vehicle type identifiers at that same instant, and empty
 *   where the feed publishes no breakdown. Carried raw, because turning
 *   identifiers into kinds takes the network's table, which this algorithm has
 *   no business knowing (SPEC §15): it is the interface that reads it, to say
 *   how many of the bikes waiting are electric (SPEC §7.4).
 * @property docksAtArrival free docks when the journey was computed.
 * @property walkToStation the access walk to the departure station, and
 *   `null` on a journey beginning at a bike outside stations: the rider is
 *   standing in front of the bike they chose, so there is no walk to it and
 *   none is computed (SPEC §6).
 * @property ride the bike leg, from wherever the bike stands to the arrival
 *   station.
 * @property walkToDestination the walk from the arrival station to the
 *   destination, and `null` where there is no ground between them: a journey
 *   whose destination **is** the arrival station ends when the bike is handed
 *   back, and a leg of no length is not a leg (SPEC §7.4.1). The mirror of
 *   [walkToStation] above, and silent for the same reason — a leg nobody walks
 *   would still be shown as a minute, since no duration is ever shown as less
 *   than one.
 * @property riskPenalty the reliability penalty, expressed in time. It serves
 *   to rank the options, never to be announced as a duration: the time shown to
 *   the user is [travelTime].
 */
public data class JourneyOption(
    public val departure: DeparturePoint,
    public val arrivalStation: Station,
    public val bikesAtDeparture: Int,
    public val bikesByVehicleTypeAtDeparture: Map<String, Int> = emptyMap(),
    public val docksAtArrival: Int,
    public val walkToStation: RouteLeg?,
    public val ride: RouteLeg,
    public val walkToDestination: RouteLeg?,
    public val riskPenalty: Duration,
) {
    /** The duration actually expected, penalty excluded: the legs, and nothing else. */
    public val travelTime: Duration
        get() = (walkToStation?.duration ?: Duration.ZERO) + ride.duration +
            (walkToDestination?.duration ?: Duration.ZERO)

    /** The duration used for ranking: the expected time, raised by the risk. */
    public val rankingTime: Duration
        get() = travelTime + riskPenalty

    /** The total distance covered, walking included. */
    public val distanceMetres: Int
        get() = (walkToStation?.distanceMetres ?: 0) + ride.distanceMetres +
            (walkToDestination?.distanceMetres ?: 0)

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
        get() = (walkToStation?.ascentMetres ?: 0) + ride.ascentMetres +
            (walkToDestination?.ascentMetres ?: 0)
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
     * It is also what a journey from a bike outside stations comes back as
     * (SPEC §7.2.1): a ride, a station and a walk, with no access walk in
     * front of it — see [JourneyOption.departure].
     *
     * @property best the chosen option.
     */
    public data class Found(public val best: JourneyOption) : JourneyPlan

    /**
     * The journey is ridden from end to end, on the rider's own bike.
     *
     * No station stands in it and none was looked for: the bike is already
     * there, so there is nothing to fetch and nothing to return (SPEC §7.3).
     * That is why it carries one leg where [Found] carries three, and why no
     * count of bikes or docks comes with it — the availability feed has nothing
     * to say about a bike that is not the network's.
     *
     * @property ride the ride from one end to the other.
     */
    public data class OwnBike(public val ride: RouteLeg) : JourneyPlan

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

    /**
     * No station in service holds a bike of the kind asked for (SPEC §7.3).
     *
     * Distinct from [NoBikeNearby] because the two call for different things to
     * be done about them: one waits for a bike, the other may simply take the
     * other kind. The kind is carried so the message can name it — "no station
     * nearby has an electric bike right now" is an answer, "no bike found" is
     * not.
     *
     * A station whose breakdown cannot be read counts as not holding the kind:
     * a bike nobody managed to count is not a bike to walk to (see
     * `BikeKindFilter`).
     *
     * @property wanted the kind that was asked for.
     */
    public data class NoWantedBikeNearby(public val wanted: WantedBikeKind) : NoBikeJourney

    /** The network has no station in service with a free dock. */
    public data object NoDockNearby : NoBikeJourney

    /** Stations exist, but no route joins them. */
    public data object NoRouteBetweenStations : NoBikeJourney

    /** The routing graph is not installed. */
    public data object GraphMissing : NoBikeJourney

    /**
     * An end of the journey lies outside the covered area.
     *
     * **Which end is carried, because the two are not the same news.** The
     * departure point is the one that usually fails, and it is the one the
     * user did not choose: a journey prepared before setting off starts from
     * the device's position, still at home, while the destination just picked
     * is perfectly serviceable. A refusal naming neither end is read against
     * the end the user was looking at — the arrival — and sends them to
     * correct the one point that was right.
     *
     * @property uncovered the end the installed data cannot serve, or `null`
     *   when the refusal came back from the routing engine, which answers for
     *   a leg rather than for one of its two points and cannot say which of
     *   them it stumbled on.
     */
    public data class OutsideCoverage(public val uncovered: UncoveredEnds? = null) : NoBikeJourney
}

/**
 * Which ends of a journey the installed data does not cover.
 *
 * There is no case for "neither": that is a journey rather than a refusal, and
 * it comes back as one.
 */
public enum class UncoveredEnds {

    /** The departure point alone. */
    Origin,

    /** The arrival point alone. */
    Destination,

    /** Both of them. */
    BothEnds,
}
