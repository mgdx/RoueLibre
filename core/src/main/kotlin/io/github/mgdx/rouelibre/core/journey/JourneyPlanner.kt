package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Chooses the best pair of stations for a door-to-door journey.
 *
 * This is the application's business core (SPEC.md §6). The principle fits in
 * one sentence: **never settle for the nearest station**. The station nearest
 * the departure point may hold a single bike, or force a detour at the far end;
 * it is the whole pair that must be optimised.
 *
 * ## Keeping the wait bearable
 *
 * Five departure stations and five arrival stations make twenty-five bike legs
 * to evaluate. Measured on the Lille graph, a ten-kilometre leg takes four
 * tenths of a second: computing them all would take ten seconds, and a leg
 * across the Paris conurbation costs several times that. SPEC §6 sets no
 * deadline on the answer, but it does require the number of computations to
 * stay bounded — nobody watches a bike go round for two minutes.
 *
 * The algorithm goes about it in two steps.
 *
 * It first **ranks** the pairs by a lower bound on the total time — the
 * straight-line distance covered at a speed nobody reaches in town. No real
 * journey can beat that, so the pairs at the top of that ranking are the only
 * ones that can win. The first [JourneySettings.maxRideEvaluations] are
 * computed for real; a pair further down is only computed when its bound still
 * beats the best journey found, or when a failed leg left options missing —
 * within the hard extra budget of [JourneySettings.extraRideEvaluations]. In
 * the usual case the answer is thus a proven optimum, for a fraction of the
 * twenty-five pairs' cost.
 *
 * It then computes them **in parallel**. Those six legs are independent, and a
 * phone has several cores; chaining them would only add up their durations —
 * measured on the emulator, chaining six short legs already took 2.4 s, and
 * long ones would turn that into a minute.
 *
 * @property router access to the routing engine.
 * @property settings the algorithm's settings.
 */
public class JourneyPlanner(
    private val router: Router,
    private val settings: JourneySettings = JourneySettings(),
) {

    /**
     * Composes the best journey between two points.
     *
     * @param origin departure point.
     * @param destination arrival point.
     * @param stations the known stations and their last state.
     * @return the chosen journey, or what prevented it from being composed.
     */
    public suspend fun plan(
        origin: Coordinates,
        destination: Coordinates,
        stations: List<StationWithAvailability>,
    ): JourneyPlan {
        val departures = candidates(
            stations = stations,
            near = origin,
            limit = settings.departureCandidates,
            countOf = { it.availability?.takeIf { state -> state.canLendBike }?.bikesAvailable },
        )
        val arrivals = candidates(
            stations = stations,
            near = destination,
            limit = settings.arrivalCandidates,
            countOf = { it.availability?.takeIf { state -> state.canAcceptBike }?.docksAvailable },
        )

        if (departures.isEmpty()) return giveUp(origin, destination, NoBikeJourney.NoBikeNearby)
        if (arrivals.isEmpty()) return giveUp(origin, destination, NoBikeJourney.NoDockNearby)

        // The direct walk is only computed if it stands a chance of winning.
        // Over three kilometres it cannot, and computing it would cost a fifth
        // of the time budget for a result we knew we would discard.
        val couldWalkAllTheWay = origin.distanceInMetresTo(destination) <=
            settings.directWalkThresholdMetres

        // The access walks are short and few; every pair uses them, so all of
        // them are computed. A single scope carries them all, the direct walk
        // included: these legs are independent, and every one kept out of the
        // scope would add its full duration to the critical path.
        val (directWalk, walksToStation, walksToDestination) = coroutineScope {
            val direct = async {
                if (couldWalkAllTheWay) {
                    legOrNull(origin, destination, TravelMode.Walking)
                } else {
                    null
                }
            }
            val toStation = departures.associateWith { candidate ->
                async { legOrNull(origin, candidate.station.position, TravelMode.Walking) }
            }
            val toDestination = arrivals.associateWith { candidate ->
                async { legOrNull(candidate.station.position, destination, TravelMode.Walking) }
            }
            Triple(
                direct.await(),
                toStation.mapValues { entry -> entry.value.await() },
                toDestination.mapValues { entry -> entry.value.await() },
            )
        }

        val pairs = buildPairs(departures, arrivals, walksToStation, walksToDestination)
        if (pairs.isEmpty()) {
            return giveUp(origin, destination, NoBikeJourney.NoRouteBetweenStations)
        }

        val options = evaluate(pairs)
        if (options.isEmpty()) {
            return giveUp(origin, destination, NoBikeJourney.NoRouteBetweenStations)
        }

        val best = options.minBy { it.rankingTime }
        // A bike journey the walk beats is not offered at all: the walk is
        // (SPEC §6). Announcing the ride with a note saying one would get there
        // sooner on foot handed the user back a comparison already made, and
        // made them ask for the walk they had just been told to take.
        if (directWalk != null && directWalk.duration < best.travelTime) {
            return JourneyPlan.WalkOnly(directWalk, NoBikeJourney.WalkingIsQuicker)
        }
        return JourneyPlan.Found(best)
    }

    /**
     * Keeps the nearest stations that actually provide the service.
     *
     * A station out of service, or empty on the side we need, is not a
     * candidate: proposing a journey that leans on it would be proposing an
     * impossible journey.
     *
     * Nearness is judged as the crow flies. A station across a river can
     * therefore edge out one that is nearer on foot — accepted: measuring the
     * real walk to every station in town would cost more route computations
     * than the journey itself.
     */
    private fun candidates(
        stations: List<StationWithAvailability>,
        near: Coordinates,
        limit: Int,
        countOf: (StationWithAvailability) -> Int?,
    ): List<Candidate> = stations
        .mapNotNull { entry ->
            val count = countOf(entry)?.takeIf { it > 0 } ?: return@mapNotNull null
            Candidate(
                station = entry.station,
                count = count,
                straightLineMetres = near.distanceInMetresTo(entry.station.position),
            )
        }
        // A station out of walking range is not a candidate, even if there is
        // no other: announcing that no station is usable beats proposing three
        // kilometres on foot to go and fetch a bike.
        .filter { it.straightLineMetres <= settings.maxWalkToStationMetres }
        .sortedBy { it.straightLineMetres }
        .take(limit)

    /**
     * Prepares the usable pairs, each with its lower bound.
     *
     * A pair whose walking legs could not both be computed is dropped: without
     * one of them, the complete journey cannot be composed.
     */
    private fun buildPairs(
        departures: List<Candidate>,
        arrivals: List<Candidate>,
        walksToStation: Map<Candidate, RouteLeg?>,
        walksToDestination: Map<Candidate, RouteLeg?>,
    ): List<Pair> = departures.flatMap { departure ->
        val walkTo = walksToStation[departure] ?: return@flatMap emptyList()
        arrivals.mapNotNull { arrival ->
            if (arrival.station.id == departure.station.id) return@mapNotNull null
            val walkFrom = walksToDestination[arrival] ?: return@mapNotNull null
            Pair(
                departure = departure,
                arrival = arrival,
                walkToStation = walkTo,
                walkToDestination = walkFrom,
                lowerBound = lowerBoundOf(departure, arrival, walkTo, walkFrom),
            )
        }
    }.sortedBy { it.lowerBound }

    /**
     * Lower bound on a pair's ranking time.
     *
     * It must be **optimistic**: were it to overestimate, we might discard the
     * best journey without ever computing it. The bike leg is therefore assumed
     * to be a straight line ridden at a speed nobody really reaches in town.
     */
    private fun lowerBoundOf(
        departure: Candidate,
        arrival: Candidate,
        walkToStation: RouteLeg,
        walkToDestination: RouteLeg,
    ): Duration {
        val asTheCrowFlies = departure.station.position
            .distanceInMetresTo(arrival.station.position)
        val fastestRide = (asTheCrowFlies / OPTIMISTIC_CYCLING_METRES_PER_SECOND).seconds
        val travel = walkToStation.duration + fastestRide + walkToDestination.duration
        return travel + riskOf(departure, arrival, walkToStation.duration, fastestRide)
    }

    /**
     * Computes the most promising pairs for real.
     *
     * Pairs arrive sorted by lower bound. A first wave computes the
     * [JourneySettings.maxRideEvaluations] best-ranked ones concurrently: six
     * independent legs on a device with several cores have no reason to wait
     * for one another.
     *
     * The first wave does not always settle the matter. Every one of its legs
     * can fail — two stations can be separated by an obstacle a bike does not
     * cross — leaving no journey at all; and the ranking is only a bound: a
     * pair left uncomputed whose bound beats the best journey found could
     * still be the true optimum. Further waves therefore compute exactly the
     * pairs that keep one of those two possibilities alive, within the extra
     * budget of [JourneySettings.extraRideEvaluations]. When no computable
     * pair remains — the usual outcome — the option returned is provably the
     * best of all the pairs, at a fraction of their cost.
     */
    private suspend fun evaluate(pairs: List<Pair>): List<JourneyOption> {
        val waiting = ArrayDeque(pairs)
        val options = mutableListOf<JourneyOption>()
        var budget = settings.maxRideEvaluations + settings.extraRideEvaluations
        var waveLimit = settings.maxRideEvaluations

        while (budget > 0 && waiting.isNotEmpty()) {
            val wave = mutableListOf<Pair>()
            while (
                wave.size < minOf(waveLimit, budget) &&
                waiting.isNotEmpty() &&
                deservesComputing(waiting.first(), options)
            ) {
                wave += waiting.removeFirst()
            }
            if (wave.isEmpty()) break
            budget -= wave.size
            options += computeWave(wave)
            // The later waves may spend whatever budget remains: they only
            // ever hold pairs that can still change the answer.
            waveLimit = budget
        }
        return options
    }

    /**
     * Whether a pair's bike leg is still worth computing.
     *
     * As long as no option exists, every pair is worth computing: failed legs
     * would otherwise leave the user without a journey while computable pairs
     * waited. Once one is found, a pair earns its computation by beating it on
     * the lower bound — discarding it unseen could discard the optimum.
     *
     * Pairs arrive in bound order, so the first refusal ends the wave: nothing
     * behind it can do better.
     */
    private fun deservesComputing(pair: Pair, options: List<JourneyOption>): Boolean {
        val best = options.minOfOrNull { it.rankingTime } ?: return true
        return pair.lowerBound < best
    }

    /** Computes one wave of bike legs concurrently, dropping the failures. */
    private suspend fun computeWave(wave: List<Pair>): List<JourneyOption> = coroutineScope {
        wave.map { pair ->
            async {
                val ride = legOrNull(
                    pair.departure.station.position,
                    pair.arrival.station.position,
                    TravelMode.Cycling,
                ) ?: return@async null

                JourneyOption(
                    departureStation = pair.departure.station,
                    arrivalStation = pair.arrival.station,
                    bikesAtDeparture = pair.departure.count,
                    docksAtArrival = pair.arrival.count,
                    walkToStation = pair.walkToStation,
                    ride = ride,
                    walkToDestination = pair.walkToDestination,
                    riskPenalty = riskOf(
                        pair.departure,
                        pair.arrival,
                        pair.walkToStation.duration,
                        ride.duration,
                    ),
                )
            }
        }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * The pair's reliability penalty (SPEC §6).
     *
     * At the departure end, the risk is finding the station empty after the
     * access walk. At the arrival end, finding it full — and the exposure is
     * longer, since one gets there after the walk and the ride.
     */
    private fun riskOf(
        departure: Candidate,
        arrival: Candidate,
        walkToStation: Duration,
        ride: Duration,
    ): Duration {
        val departureRisk = availabilityRiskPenalty(
            count = departure.count,
            exposure = walkToStation,
            settings = settings,
        )
        val arrivalRisk = availabilityRiskPenalty(
            count = arrival.count,
            exposure = walkToStation + ride,
            settings = settings,
        )
        return departureRisk + arrivalRisk
    }

    private suspend fun legOrNull(
        from: Coordinates,
        to: Coordinates,
        mode: TravelMode,
    ): RouteLeg? = when (val result = router.route(from, to, mode)) {
        is RouteResult.Success -> result.leg
        is RouteResult.Failure -> null
    }

    /**
     * Returns the direct walk when no bike journey is possible.
     *
     * It is computed here even if the distance made it useless for comparison:
     * without a bike it is the only answer we can give, and giving it is worth
     * the time it costs.
     */
    private suspend fun giveUp(
        origin: Coordinates,
        destination: Coordinates,
        reason: NoBikeJourney,
    ): JourneyPlan {
        coroutineContext.ensureActive()
        val walk = legOrNull(origin, destination, TravelMode.Walking)
        return if (walk != null) {
            JourneyPlan.WalkOnly(walk, reason)
        } else {
            JourneyPlan.Impossible(reason)
        }
    }

    /** A retained station, with what qualifies it. */
    private data class Candidate(
        val station: Station,
        val count: Int,
        val straightLineMetres: Double,
    )

    /** A pair of stations, with its walking legs and its lower bound. */
    private data class Pair(
        val departure: Candidate,
        val arrival: Candidate,
        val walkToStation: RouteLeg,
        val walkToDestination: RouteLeg,
        val lowerBound: Duration,
    )

    private companion object {
        /**
         * The speed used for the lower bound, in metres per second —
         * twenty-five kilometres an hour.
         *
         * Deliberately unreachable on a share bike in town, where the average
         * hovers around thirteen. The bound must stay optimistic: a realistic
         * speed would risk discarding the best pair before ever computing it.
         */
        const val OPTIMISTIC_CYCLING_METRES_PER_SECOND = 7.0
    }
}

/**
 * Translates an engine failure into a cause the algorithm can use.
 *
 * Used by the calling layer when no route could be traced: a missing graph and
 * a destination outside the covered area do not call for the same message.
 */
public fun RoutingFailure.toNoBikeJourney(): NoBikeJourney = when (this) {
    RoutingFailure.GraphMissing -> NoBikeJourney.GraphMissing
    RoutingFailure.OutsideCoverage -> NoBikeJourney.OutsideCoverage
    else -> NoBikeJourney.NoRouteBetweenStations
}
