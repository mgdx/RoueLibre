package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
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
 * ## Asking for one kind of bike
 *
 * [wantedBike] narrows the departure end to the stations that really hold the
 * kind asked for, and does nothing else (SPEC §6). It is a **strict filter, not
 * a weighting**: a station without that kind is not a candidate at all, and when
 * none is left the answer says so rather than proposing a walk towards a bike
 * that is not there. There is no penalty coefficient for the wrong kind, because
 * there is nothing to measure — the question is not "how likely am I to find
 * one" but "does this station lend one".
 *
 * One thing it deliberately leaves alone: the **arrival end**, where a free dock
 * is a free dock whatever one returns to it.
 *
 * One thing it does reach: the **reliability penalty**, which then weighs the
 * bikes of that kind alone rather than the station's whole rack — see
 * [countAtRisk]. What the journey **carries and shows** is the whole count all
 * the same (SPEC §7.4).
 *
 * The **time announced** moves with it too, since 17 August 2026, and only when
 * an assisted bike was asked for: that is [JourneySettings.riddenBike]'s doing
 * rather than this filter's, and the application layer is what joins the two.
 *
 * ## At the paces asked for
 *
 * [JourneySettings.walkingPace] and [JourneySettings.riddenBike] scale the
 * duration of every leg before anything is compared — see [atThePacesAsked].
 * Neither is a matter of presentation: the two walks and the ride are the three
 * legs being weighed against one another, so a slower walker is genuinely owed a
 * nearer departure station even at the cost of a longer ride, and a quicker bike
 * genuinely earns a station further off. At [WalkingPace.Normal] and
 * [RiddenBike.Mechanical] both factors are one and this class behaves exactly as
 * it did before either existed.
 *
 * @property router access to the routing engine.
 * @property settings the algorithm's settings.
 * @property wantedBike the kind of bike asked for, with the network's table to
 *   recognise it by, or `null` — the default — to ask for nothing. Built without
 *   it, this class behaves exactly as it did before the choice existed.
 */
public class JourneyPlanner(
    private val router: Router,
    private val settings: JourneySettings = JourneySettings(),
    private val wantedBike: BikeKindFilter? = null,
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
        val tripMetres = origin.distanceInMetresTo(destination)

        val departures = candidates(
            stations = stations,
            near = origin,
            limit = settings.departureCandidates,
            countOf = { entry ->
                entry.availability
                    ?.takeIf { state -> state.canLendBike && lendsTheWantedBike(state) }
                    ?.bikesAvailable
            },
            atRisk = ::countAtRisk,
        )
        // The arrival end ignores the kind asked for: a free dock is a free dock
        // whatever bike is returned to it (SPEC §6).
        val arrivals = candidates(
            stations = stations,
            near = destination,
            limit = settings.arrivalCandidates,
            countOf = { it.availability?.takeIf { state -> state.canAcceptBike }?.docksAvailable },
        )

        if (departures.isEmpty()) return giveUp(origin, destination, noBikeToLend())
        if (arrivals.isEmpty()) return giveUp(origin, destination, NoBikeJourney.NoDockNearby)

        // Below three kilometres the direct walk is computed here, in the same
        // breath as the access walks: it often wins, and in this scope it costs
        // a core rather than a wait. Beyond, it is left to the end of the
        // method, where it is only computed if it can still change the answer.
        val couldWalkAllTheWay = tripMetres <= settings.directWalkThresholdMetres

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
        val walk = directWalk ?: directWalkIfItCouldWin(origin, destination, tripMetres, best)
        if (walk != null && walk.duration < best.travelTime) {
            return JourneyPlan.WalkOnly(walk, NoBikeJourney.WalkingIsQuicker)
        }
        return JourneyPlan.Found(best)
    }

    /**
     * Composes the journey of somebody riding their own bike (SPEC §7.3).
     *
     * One leg, from the door one leaves to the door one reaches. No station is
     * looked at — not the nearest, not the best stocked: the bike is already
     * downstairs, so the whole of what [plan] does, which is to pay two walks
     * and a reliability risk for the right to borrow one, has nothing left to
     * buy.
     *
     * **The direct walk is not computed, and not compared.** In [plan] it
     * guards against a journey where fetching a bike costs more than it saves;
     * here nothing is fetched, and answering "you would get there sooner on
     * foot" to somebody who has said they are on their bike would be answering
     * a question they did not ask.
     *
     * **The ride is traced with the profile of the bike the rider declared**
     * ([JourneySettings.riddenBike]), exactly as a share bike's leg is traced
     * with the profile of the bike the network was asked for. The streets a
     * bicycle may take do not depend on whose bicycle it is, and neither profile
     * lets it anywhere the other would not; what depends on the bike is how fast
     * it covers them and how much a hill costs it. A bike declared mechanical
     * and a bike nobody declared are the same plain bike and come back with the
     * ride of before this was modelled (SPEC §7.6).
     *
     * @param origin departure point.
     * @param destination arrival point.
     * @return the ride, or what prevented it from being traced.
     */
    public suspend fun planWithOwnBike(
        origin: Coordinates,
        destination: Coordinates,
    ): JourneyPlan = when (
        val result = router.route(origin, destination, settings.riddenBike.travelMode)
    ) {
        is RouteResult.Success -> JourneyPlan.OwnBike(result.leg.atThePacesAsked())
        // The engine's reason is kept rather than flattened into "no route":
        // a graph that is not installed and a point outside the covered area
        // call for two different things to be done about them (SPEC §7.4).
        is RouteResult.Failure -> JourneyPlan.Impossible(result.reason.toNoBikeJourney())
    }

    /**
     * Keeps the nearest stations that actually provide the service.
     *
     * A station out of service, or empty on the side we need, is not a
     * candidate: proposing a journey that leans on it would be proposing an
     * impossible journey. At the departure end, a kind asked for narrows it
     * further, by the same reasoning — see [lendsTheWantedBike].
     *
     * **Distance disqualifies nothing.** A station is examined however far it
     * stands: on a long trip, twenty minutes of access walk buy hours, and a
     * cut-off — whatever its value — answered a fifteen-kilometre journey with
     * four hours of walking rather than the ride that was there for the taking.
     * What guards against an access walk that costs more than it saves is not a
     * threshold but the comparison with the direct walk, which decides on the
     * real times rather than on a distance believed to stand for them.
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
        atRisk: (StationWithAvailability, Int) -> Int = { _, count -> count },
    ): List<Candidate> = stations
        .mapNotNull { entry ->
            val count = countOf(entry)?.takeIf { it > 0 } ?: return@mapNotNull null
            Candidate(
                station = entry.station,
                count = count,
                countAtRisk = atRisk(entry, count),
                bikesByVehicleType = entry.availability?.bikesByVehicleType.orEmpty(),
                straightLineMetres = near.distanceInMetresTo(entry.station.position),
            )
        }
        .sortedBy { it.straightLineMetres }
        .take(limit)

    /**
     * The stock the reliability penalty is measured on.
     *
     * The whole count, except at the departure end when a kind was asked for:
     * there it is the bikes **of that kind alone**. The seven mechanical bikes
     * standing beside the single electric one do not serve somebody who asked
     * for an electric bike — if that one goes while they walk, they reach the
     * next station just as surely as if the whole rack had emptied, and that is
     * exactly the cost `fallbackPenalty` stands for.
     *
     * The turnover rate is left alone, and no constant is added: what changes is
     * the base it divides, not the figure itself. That does carry an assumption
     * — that any bike leaving the station could be one of the kind wanted, which
     * is the pessimistic reading of a rate measured over the whole stock. It is
     * the deliberate side to err on: under-stating this risk sends somebody to a
     * bike that will not be there, while over-stating it sends them a little
     * further to one that will.
     *
     * At the arrival end nothing changes, a free dock taking back any bike.
     */
    private fun countAtRisk(entry: StationWithAvailability, count: Int): Int =
        wantedBike?.bikesAt(entry.availability) ?: count

    /**
     * Whether a station holds a bike of the kind asked for.
     *
     * True for everybody when nothing was asked for, which is the application at
     * rest: no kind is presumed.
     *
     * **A station whose breakdown cannot be read is left out.** The feed may
     * count bikes under a vehicle type the network never declared, or publish a
     * breakdown that does not add up to the total, or publish none at all
     * (see `splitBikesByKind`): in all three the station may well hold what is
     * wanted, and nothing here can say so. Walking somebody to a bike we failed
     * to count would be promising what we cannot deliver, which is the same
     * reason a station's sheet shows its total alone rather than a guess
     * (SPEC §7.2).
     */
    private fun lendsTheWantedBike(availability: StationAvailability): Boolean =
        wantedBike?.isSatisfiedBy(availability) ?: true

    /**
     * Why no station can lend, in the terms the question was put in.
     *
     * Somebody who asked for a kind is told about that kind: "no station nearby
     * has an electric bike right now" leaves them something to do — take the
     * other kind, or wait — where a bare "no bike found" would have them look
     * for another address.
     */
    private fun noBikeToLend(): NoBikeJourney = wantedBike
        ?.let { NoBikeJourney.NoWantedBikeNearby(it.wanted) }
        ?: NoBikeJourney.NoBikeNearby

    /**
     * The direct walk, computed late and only when it can still win.
     *
     * Since no distance disqualifies a station, the walk is what keeps an
     * absurd journey off the screen — two hours on foot to fetch a bike is
     * refused here, by measurement, not by a rule of thumb. But computing a
     * twenty-kilometre walk to discard it costs as much as the ride itself, so
     * it is worth asking first whether it could win at all: the walk cannot
     * take less than the straight line covered at a pace nobody holds. A
     * journey quicker than *that* beats every real walk, and none is computed.
     *
     * The bound is optimistic on purpose, in the walker's favour: were it
     * generous the other way, we would skip a walk that would have won.
     */
    private suspend fun directWalkIfItCouldWin(
        origin: Coordinates,
        destination: Coordinates,
        tripMetres: Double,
        best: JourneyOption,
    ): RouteLeg? {
        val fastestWalk = (tripMetres / optimisticWalkingMetresPerSecond).seconds
        if (best.travelTime <= fastestWalk) return null
        return legOrNull(origin, destination, TravelMode.Walking)
    }

    /**
     * The pace no walker beats, for the walker this journey is being worked out
     * for.
     *
     * The bound of [OPTIMISTIC_WALKING_METRES_PER_SECOND] is stated against the
     * engine's own pace, so it has to be moved with it: a slow walker's real
     * walk is 1.4 times longer, and a bound that ignored that would keep sending
     * a twenty-kilometre walk to be computed that never had a chance. Divided by
     * the same factor the walk is multiplied by, the bound stays exactly as
     * optimistic as it was — never generous the other way, which is the only
     * error that would matter, since it would skip a walk that would have won.
     */
    private val optimisticWalkingMetresPerSecond: Double
        get() = OPTIMISTIC_WALKING_METRES_PER_SECOND / settings.walkingPace.durationFactor

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
                // Traced with the profile of the bike this journey is for: an
                // assisted one is not merely quicker over the same track, it
                // has no reason to go round a hill (SPEC §6).
                val ride = legOrNull(
                    pair.departure.station.position,
                    pair.arrival.station.position,
                    settings.riddenBike.travelMode,
                ) ?: return@async null

                JourneyOption(
                    departureStation = pair.departure.station,
                    arrivalStation = pair.arrival.station,
                    bikesAtDeparture = pair.departure.count,
                    bikesByVehicleTypeAtDeparture = pair.departure.bikesByVehicleType,
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
     * At the departure end, the risk is finding the station empty **of what one
     * asked for** after the access walk — see [countAtRisk]. At the arrival end,
     * finding it full — and the exposure is longer, since one gets there after
     * the walk and the ride.
     */
    private fun riskOf(
        departure: Candidate,
        arrival: Candidate,
        walkToStation: Duration,
        ride: Duration,
    ): Duration {
        val departureRisk = availabilityRiskPenalty(
            count = departure.countAtRisk,
            exposure = walkToStation,
            settings = settings,
        )
        val arrivalRisk = availabilityRiskPenalty(
            count = arrival.countAtRisk,
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
        is RouteResult.Success -> result.leg.atThePacesAsked()
        is RouteResult.Failure -> null
    }

    /**
     * A leg as the person asking for it actually covers it (SPEC §6).
     *
     * **The one place either pace enters, and it enters early on purpose.**
     * Every leg this class uses comes through [legOrNull], so scaling here
     * reaches the access walks, the direct walk, the walk offered when no bike
     * journey can be composed and the ride itself — all of them before a single
     * pair is compared, which is the whole point: a factor that only reached the
     * figures on the screen would announce different minutes for the same
     * stations, and leave a slow walker sent to the station a brisk one was owed,
     * or a rider on an assisted bike sent to the station a mechanical one was.
     *
     * **Two factors, two questions, and they never meet on the same leg.** The
     * walks carry [WalkingPace]'s alone — a motor says nothing about how one
     * walks — and the ride carries [RiddenBike]'s alone.
     *
     * **The geometry is not recomputed here, and there is no profile per pace.**
     * The same streets are walked whether one dawdles or hurries: what changes is
     * how long they take, so the track, its distance and its climb are the
     * engine's, untouched. The two bikes are the one place where the streets may
     * genuinely differ, and that is settled where it belongs — in the profile the
     * leg was traced with, chosen before this point — never by a correction
     * applied after the fact.
     */
    private fun RouteLeg.atThePacesAsked(): RouteLeg = when (mode) {
        TravelMode.Walking -> copy(duration = duration * settings.walkingPace.durationFactor)
        TravelMode.Cycling, TravelMode.ElectricCycling ->
            copy(duration = duration * settings.riddenBike.durationFactor)
    }

    /**
     * Returns the direct walk when no bike journey is possible.
     *
     * It is computed here even if the distance made it useless for comparison:
     * without a bike it is the only answer we can give, and giving it is worth
     * the time it costs.
     *
     * **That last walk is also where the engine's own reason is recovered.**
     * Every leg above comes back through [legOrNull], which keeps the track and
     * drops why it failed — a loss of no consequence while a single pair simply
     * cannot be joined, and a wrong answer when nothing could be traced at all.
     * The walk asked for here crosses the same ground with the same data, so its
     * failure carries the cause the whole computation stumbled on, and
     * [decisiveOver] says when that cause outweighs the one the station search
     * had reached.
     */
    private suspend fun giveUp(
        origin: Coordinates,
        destination: Coordinates,
        reason: NoBikeJourney,
    ): JourneyPlan {
        coroutineContext.ensureActive()
        return when (val walk = router.route(origin, destination, TravelMode.Walking)) {
            is RouteResult.Success -> JourneyPlan.WalkOnly(walk.leg.atThePacesAsked(), reason)
            is RouteResult.Failure -> JourneyPlan.Impossible(walk.reason.decisiveOver(reason))
        }
    }

    /**
     * A retained station, with what qualifies it.
     *
     * @property count what it holds of the side we need: bikes at the departure
     *   end, free docks at the arrival end. It is the figure the journey carries
     *   and the interface shows, and a kind asked for never narrows it
     *   (SPEC §7.4).
     * @property countAtRisk what the reliability penalty is measured on — see
     *   [countAtRisk]. The same figure as [count], except at the departure end
     *   when a kind was asked for.
     * @property bikesByVehicleType how its bikes divide between the network's
     *   own vehicle type identifiers. Read at both ends, since it comes with
     *   the state the count itself is read from, and only used at the departure
     *   end: what is returned at the arrival end is the bike one already has.
     */
    private data class Candidate(
        val station: Station,
        val count: Int,
        val countAtRisk: Int,
        val bikesByVehicleType: Map<String, Int>,
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

        /**
         * The speed used to tell whether the direct walk is worth computing,
         * in metres per second — six and a half kilometres an hour.
         *
         * Held by nobody over an hour, where the engine traces urban walking at
         * about 1.4 m/s and the fastest pace on offer — [WalkingPace.Brisk] —
         * brings that to 1.67. Same requirement as above and for the same
         * reason: no real walk can beat this, so a journey that beats it needs
         * no walk computed to prove it wins. It is stated against the engine's
         * own pace and scaled with it, see [optimisticWalkingMetresPerSecond];
         * whatever pace is asked for, this must stay above it.
         */
        const val OPTIMISTIC_WALKING_METRES_PER_SECOND = 1.8
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

/**
 * The cause to announce when the walk of last resort failed as well.
 *
 * Two engine failures answer for the whole computation rather than for one leg:
 * data that is not installed, and a point the data never covered. Neither is
 * about the streets, both say what the user can do about it, and both stop
 * every leg alike — so they replace [reason], which in their presence could
 * only ever have been "no route between the stations", over a graph that was
 * not being read.
 *
 * Every other failure leaves [reason] standing. It came from the state of the
 * network rather than from the engine — no bike nearby, no free dock — and it
 * is both truer and more useful than anything the engine's silence could add.
 */
private fun RoutingFailure.decisiveOver(reason: NoBikeJourney): NoBikeJourney = when (this) {
    RoutingFailure.GraphMissing, RoutingFailure.OutsideCoverage -> toNoBikeJourney()
    else -> reason
}
