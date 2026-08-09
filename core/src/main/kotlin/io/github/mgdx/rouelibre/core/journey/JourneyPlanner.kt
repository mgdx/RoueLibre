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
 * Choisit le meilleur couple de stations pour un trajet porte-à-porte.
 *
 * C'est le cœur métier de l'application (SPEC.md §6). Le principe tient en une
 * phrase : **ne jamais se contenter de la station la plus proche**. La station
 * la plus proche du départ peut n'avoir qu'un vélo, ou obliger à un détour à
 * l'arrivée ; c'est le couple entier qu'il faut optimiser.
 *
 * ## Tenir les trois secondes
 *
 * Cinq stations au départ et cinq à l'arrivée font vingt-cinq trajets à vélo à
 * évaluer. Mesuré sur le graphe lillois, un trajet de dix kilomètres demande
 * quatre dixièmes de seconde : les calculer tous prendrait dix secondes, très
 * au-delà du budget du SPEC §6.
 *
 * L'algorithme s'y prend en deux temps.
 *
 * Il **classe** d'abord les couples par une borne inférieure du temps total —
 * la distance à vol d'oiseau parcourue à une vitesse que personne n'atteint en
 * ville. Aucun trajet réel ne peut faire mieux, si bien que les couples en tête
 * de ce classement sont les seuls à pouvoir gagner. Seuls les
 * [JourneySettings.maxRideEvaluations] premiers sont ensuite calculés pour de
 * bon : six au lieu de vingt-cinq.
 *
 * Il les calcule ensuite **en parallèle**. Ces six trajets sont indépendants,
 * et un téléphone a plusieurs cœurs ; les enchaîner ne servirait qu'à
 * additionner leurs durées. Mesuré sur l'émulateur, l'enchaînement demandait
 * 2,4 s là où le budget du SPEC §6 est de 3 s — une marge qui n'aurait pas
 * survécu à un appareil milieu de gamme.
 *
 * @property router accès au moteur d'itinéraire.
 * @property settings réglages de l'algorithme.
 */
public class JourneyPlanner(
    private val router: Router,
    private val settings: JourneySettings = JourneySettings(),
) {

    /**
     * Compose le meilleur trajet entre deux points.
     *
     * @param origin point de départ.
     * @param destination point d'arrivée.
     * @param stations les stations connues et leur dernier état.
     * @return le trajet retenu et ses alternatives, ou ce qui a empêché de le
     *   composer.
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

        // La marche directe n'est calculée d'emblée que si elle a une chance
        // de gagner. Sur un trajet de huit kilomètres, elle ne peut pas, et la
        // calculer coûtait un cinquième du budget de temps pour un résultat
        // qu'on savait d'avance ne pas retenir.
        val couldWalkAllTheWay = origin.distanceInMetresTo(destination) <=
            settings.directWalkThresholdMetres
        val directWalk = if (couldWalkAllTheWay) {
            legOrNull(origin, destination, TravelMode.Walking)
        } else {
            null
        }

        if (departures.isEmpty()) return giveUp(origin, destination, NoBikeJourney.NoBikeNearby)
        if (arrivals.isEmpty()) return giveUp(origin, destination, NoBikeJourney.NoDockNearby)

        // Les marches d'accès sont courtes et peu nombreuses ; elles servent à
        // chaque couple, donc on les calcule toutes — mais de front, pour la
        // même raison que les trajets à vélo.
        val walksToStation = coroutineScope {
            departures.map { candidate ->
                candidate to async {
                    legOrNull(origin, candidate.station.position, TravelMode.Walking)
                }
            }.associate { (candidate, deferred) -> candidate to deferred.await() }
        }
        val walksToDestination = coroutineScope {
            arrivals.map { candidate ->
                candidate to async {
                    legOrNull(candidate.station.position, destination, TravelMode.Walking)
                }
            }.associate { (candidate, deferred) -> candidate to deferred.await() }
        }

        val pairs = buildPairs(departures, arrivals, walksToStation, walksToDestination)
        if (pairs.isEmpty()) {
            return giveUp(origin, destination, NoBikeJourney.NoRouteBetweenStations)
        }

        val options = evaluate(pairs)
        if (options.isEmpty()) {
            return giveUp(origin, destination, NoBikeJourney.NoRouteBetweenStations)
        }

        val ranked = options.sortedBy { it.rankingTime }
        val best = ranked.first()
        return JourneyPlan.Found(
            best = best,
            // Trois alternatives, comme le demande le SPEC §6.
            alternatives = ranked.drop(1).take(ALTERNATIVE_COUNT),
            directWalk = directWalk,
            walkingIsFaster = directWalk != null && directWalk.duration < best.travelTime,
        )
    }

    /**
     * Retient les stations les plus proches qui rendent réellement le service.
     *
     * Une station hors service, ou vide du côté où on en a besoin, n'est pas
     * une candidate : proposer un trajet qui s'appuie dessus serait proposer
     * un trajet impossible.
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
        // Une station hors de portée de marche n'est pas une candidate, même
        // s'il n'y en a pas d'autre : mieux vaut annoncer qu'aucune station
        // n'est utilisable que proposer trois kilomètres à pied pour aller
        // chercher un vélo.
        .filter { it.straightLineMetres <= settings.maxWalkToStationMetres }
        .sortedBy { it.straightLineMetres }
        .take(limit)

    /**
     * Prépare les couples exploitables, chacun avec sa borne inférieure.
     *
     * Un couple dont une des marches n'a pas pu être calculée est écarté :
     * sans elle, on ne saurait pas composer le trajet complet.
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
     * Borne inférieure du temps de classement d'un couple.
     *
     * Elle doit être **optimiste** : si elle surestimait, on écarterait
     * peut-être le meilleur trajet sans jamais le calculer. Le trajet à vélo
     * est donc supposé rectiligne et parcouru à une vitesse que personne
     * n'atteint réellement en ville.
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
        val travel = walkToStation.duration + fastestRide +
            walkToDestination.duration + settings.handlingTime
        return travel + riskOf(departure, arrival, walkToStation.duration, fastestRide)
    }

    /**
     * Calcule pour de bon les couples les plus prometteurs.
     *
     * Les couples arrivent triés par borne inférieure ; seuls les premiers
     * peuvent gagner, et ce sont les seuls calculés. Ils le sont de front :
     * six trajets indépendants sur un appareil qui a plusieurs cœurs n'ont
     * aucune raison d'attendre les uns après les autres.
     *
     * Un couple dont le trajet à vélo échoue est simplement écarté — deux
     * stations peuvent être séparées par un obstacle que le vélo ne franchit
     * pas.
     */
    private suspend fun evaluate(pairs: List<Pair>): List<JourneyOption> = coroutineScope {
        pairs.take(settings.maxRideEvaluations)
            .map { pair ->
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
                        handlingTime = settings.handlingTime,
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
     * Pénalité de fiabilité du couple (SPEC §6).
     *
     * Au départ, le risque est celui de trouver la station vide après la
     * marche d'accès. À l'arrivée, celui de la trouver pleine — et l'exposition
     * est plus longue, puisqu'on y parvient après la marche, la prise du vélo
     * et le trajet.
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
            exposure = walkToStation + settings.pickupTime + ride,
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
     * Rend la marche directe à défaut de trajet à vélo.
     *
     * Elle est calculée ici même si la distance la rendait inutile pour la
     * comparaison : faute de vélo, c'est la seule réponse qu'on puisse donner,
     * et la donner vaut le temps qu'elle coûte.
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

    /** Une station retenue, avec ce qui la qualifie. */
    private data class Candidate(
        val station: Station,
        val count: Int,
        val straightLineMetres: Double,
    )

    /** Un couple de stations, avec ses marches et sa borne inférieure. */
    private data class Pair(
        val departure: Candidate,
        val arrival: Candidate,
        val walkToStation: RouteLeg,
        val walkToDestination: RouteLeg,
        val lowerBound: Duration,
    )

    private companion object {
        /** Nombre d'alternatives proposées en plus du meilleur trajet. */
        const val ALTERNATIVE_COUNT = 3

        /**
         * Vitesse servant à la borne inférieure, en mètres par seconde —
         * vingt-cinq kilomètres à l'heure.
         *
         * Délibérément inatteignable sur un vélo de libre-service en ville, où
         * la moyenne tourne autour de treize. La borne doit rester optimiste :
         * une vitesse réaliste risquerait d'écarter le meilleur couple avant
         * même de l'avoir calculé.
         */
        const val OPTIMISTIC_CYCLING_METRES_PER_SECOND = 7.0
    }
}

/**
 * Traduit un échec du moteur en cause exploitable par l'algorithme.
 *
 * Utilisé par la couche appelante quand aucun itinéraire n'a pu être tracé :
 * un graphe absent et une destination hors emprise n'appellent pas le même
 * message.
 */
public fun RoutingFailure.toNoBikeJourney(): NoBikeJourney = when (this) {
    RoutingFailure.GraphMissing -> NoBikeJourney.GraphMissing
    RoutingFailure.OutsideCoverage -> NoBikeJourney.OutsideCoverage
    else -> NoBikeJourney.NoRouteBetweenStations
}
