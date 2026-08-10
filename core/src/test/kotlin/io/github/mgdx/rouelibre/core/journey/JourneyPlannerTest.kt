package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests de l'algorithme de trajet (SPEC.md §6).
 *
 * Le moteur d'itinéraire est simulé : ce qui est éprouvé ici est le CHOIX du
 * couple de stations, pas la qualité d'un tracé. Le routeur simulé compte ses
 * appels, ce qui permet de vérifier que l'élagage tient — un algorithme juste
 * mais qui calculerait les vingt-cinq couples raterait le budget de trois
 * secondes du cahier des charges.
 */
class JourneyPlannerTest {

    /**
     * Routeur simulé : distance à vol d'oiseau, majorée d'un détour constant.
     *
     * Le détour de 25 % correspond à ce qu'on observe en ville entre la ligne
     * droite et le chemin réel.
     */
    private class FakeRouter(private val unreachable: Set<String> = emptySet()) : Router {
        var cyclingCalls = 0
            private set
        var walkingCalls = 0
            private set

        override suspend fun route(
            from: Coordinates,
            to: Coordinates,
            mode: TravelMode,
        ): RouteResult {
            if (key(from, to) in unreachable) {
                return RouteResult.Failure(RoutingFailure.NoRouteFound)
            }
            when (mode) {
                TravelMode.Cycling -> cyclingCalls++
                TravelMode.Walking -> walkingCalls++
            }
            val metres = (from.distanceInMetresTo(to) * DETOUR).roundToInt()
            val speed = when (mode) {
                TravelMode.Walking -> WALKING_METRES_PER_SECOND
                TravelMode.Cycling -> CYCLING_METRES_PER_SECOND
            }
            return RouteResult.Success(
                RouteLeg(
                    mode = mode,
                    distanceMetres = metres,
                    duration = (metres / speed).roundToInt().seconds,
                    ascentMetres = 0,
                    geometry = listOf(from, to),
                ),
            )
        }

        companion object {
            const val DETOUR = 1.25
            const val WALKING_METRES_PER_SECOND = 1.25
            const val CYCLING_METRES_PER_SECOND = 3.6

            fun key(from: Coordinates, to: Coordinates) = "%.4f,%.4f>%.4f,%.4f".format(
                from.latitude,
                from.longitude,
                to.latitude,
                to.longitude,
            )
        }
    }

    /** Un degré de latitude vaut environ 111 km ; 0,001 ° font donc ~111 m. */
    private fun at(northMetres: Double, eastMetres: Double) = Coordinates(
        latitude = 50.6300 + northMetres / 111_320.0,
        longitude = 3.0600 + eastMetres / (111_320.0 * 0.635),
    )

    private fun station(
        id: String,
        position: Coordinates,
        bikes: Int = 10,
        docks: Int = 10,
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
    ) = StationWithAvailability(
        station = Station(id, "Station $id", position, bikes + docks, "59000"),
        availability = StationAvailability(
            stationId = id,
            bikesAvailable = bikes,
            docksAvailable = docks,
            isInstalled = installed,
            isRenting = renting,
            isReturning = returning,
            reportedAt = null,
        ),
    )

    private val origin = at(0.0, 0.0)
    private val destination = at(0.0, 4000.0)

    // ------------------------------------------------------ choix du couple --

    @Test
    fun `optimises the pair and not the station nearest the origin`() = runTest {
        // La station A est la plus proche du départ, mais elle mène à une
        // arrivée très mal placée. B est un peu plus loin et dessert bien.
        val stations = listOf(
            station("A-proche", at(0.0, 100.0)),
            station("B-loin", at(0.0, 400.0)),
            station("arrivee-loin-de-A", at(1500.0, 4000.0)),
            station("arrivee-bien", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("arrivee-bien", plan.best.arrivalStation.id)
    }

    @Test
    fun `prefers a well-stocked station to one with a single bike`() = runTest {
        // La station à un vélo est plus proche, mais le risque qu'elle soit
        // vide à l'arrivée justifie de marcher un peu plus (SPEC §6).
        val stations = listOf(
            station("un-seul-velo", at(0.0, 200.0), bikes = 1),
            station("bien-fournie", at(0.0, 320.0), bikes = 12),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("bien-fournie", plan.best.departureStation.id)
    }

    @Test
    fun `the risk penalty does not overturn a clear margin`() = runTest {
        // Un vélo à deux cents mètres contre douze à un kilomètre et demi :
        // le risque ne doit pas faire préférer la marche interminable.
        val stations = listOf(
            station("un-seul-velo", at(0.0, 200.0), bikes = 1),
            station("bien-fournie-tres-loin", at(1500.0, 200.0), bikes = 12),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("un-seul-velo", plan.best.departureStation.id)
    }

    // --------------------------------------------------------- disponibilité --

    @Test
    fun `never keeps a station without a bike at the origin`() = runTest {
        val stations = listOf(
            station("vide", at(0.0, 100.0), bikes = 0),
            station("fournie", at(0.0, 600.0), bikes = 5),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("fournie", plan.best.departureStation.id)
        assertTrue(plan.best.bikesAtDeparture >= 1)
    }

    @Test
    fun `never keeps a station without a dock at the destination`() = runTest {
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("pleine", at(0.0, 3900.0), docks = 0),
            station("avec-places", at(0.0, 3500.0), docks = 7),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("avec-places", plan.best.arrivalStation.id)
        assertTrue(plan.best.docksAtArrival >= 1)
    }

    @Test
    fun `ignores a station that no longer rents, even full of bikes`() = runTest {
        val stations = listOf(
            station("hors-service", at(0.0, 100.0), bikes = 20, renting = false),
            station("en-service", at(0.0, 600.0), bikes = 4),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("en-service", plan.best.departureStation.id)
    }

    @Test
    fun `says so when no nearby station has a bike`() = runTest {
        // Le SPEC §6 l'exige : ne pas proposer un trajet impossible.
        val stations = listOf(
            station("vide-1", at(0.0, 100.0), bikes = 0),
            station("vide-2", at(0.0, 300.0), bikes = 0),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("attendu WalkOnly, obtenu $plan", plan is JourneyPlan.WalkOnly)
        assertEquals(NoBikeJourney.NoBikeNearby, (plan as JourneyPlan.WalkOnly).reason)
    }

    @Test
    fun `says so when no arrival station has a dock`() = runTest {
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("pleine", at(0.0, 3900.0), docks = 0),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertEquals(
            NoBikeJourney.NoDockNearby,
            (plan as JourneyPlan.WalkOnly).reason,
        )
    }

    // ------------------------------------------------------------ marche --

    @Test
    fun `reports that walking is faster on a very short journey`() = runTest {
        // Deux cents mètres à parcourir, avec des forfaits de quatre minutes :
        // prendre un vélo n'a aucun sens, et le SPEC §6 impose de le dire.
        val closeDestination = at(0.0, 200.0)
        val stations = listOf(
            station("depart", at(0.0, 150.0)),
            station("arrivee", at(0.0, 250.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, closeDestination, stations) as JourneyPlan.Found

        assertTrue("la marche devrait être annoncée plus rapide", plan.walkingIsFaster)
    }

    @Test
    fun `does not report walking on a journey the bike wins`() = runTest {
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertTrue(!plan.walkingIsFaster)
    }

    // ------------------------------------------------------ alternatives --

    @Test
    fun `offers up to three alternatives, in order`() = runTest {
        val stations = (0 until 5).map { station("depart-$it", at(it * 60.0, 150.0)) } +
            (0 until 5).map { station("arrivee-$it", at(it * 60.0, 3900.0)) }
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertTrue("trop d'alternatives : ${plan.alternatives.size}", plan.alternatives.size <= 3)
        val times = listOf(plan.best) + plan.alternatives
        assertEquals(times.sortedBy { it.rankingTime }, times)
    }

    // -------------------------------------------------------------- coût --

    @Test
    fun `evaluates only a fraction of the pairs thanks to the pruning`() = runTest {
        // Cinq départs et cinq arrivées font vingt-cinq couples. Les calculer
        // tous coûterait dix secondes sur un vrai graphe.
        val stations = (0 until 6).map { station("depart-$it", at(it * 80.0, 150.0)) } +
            (0 until 6).map { station("arrivee-$it", at(it * 80.0, 3900.0)) }
        val router = FakeRouter()
        val planner = JourneyPlanner(router)

        planner.plan(origin, destination, stations)

        // Le plafond de réglage garantit six calculs au plus, quelle que soit
        // la géométrie. C'est lui qui tient le budget de temps du SPEC §6.
        assertTrue(
            "trop de trajets à vélo calculés : ${router.cyclingCalls}",
            router.cyclingCalls <= 6,
        )
    }

    @Test
    fun `the announced time excludes the risk penalty`() = runTest {
        // La pénalité sert à classer, jamais à être annoncée : l'utilisateur
        // verrait une durée qu'il n'observera pas.
        val stations = listOf(
            station("depart", at(0.0, 200.0), bikes = 1),
            station("arrivee", at(0.0, 3900.0), docks = 1),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertTrue(plan.best.riskPenalty > 0.seconds)
        assertEquals(
            plan.best.travelTime + plan.best.riskPenalty,
            plan.best.rankingTime,
        )
    }

    @Test
    fun `the pick-up and drop-off allowances are counted`() = runTest {
        val settings = JourneySettings(pickupTime = 3.minutes, dropoffTime = 1.minutes)
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter(), settings)

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals(4.minutes, plan.best.handlingTime)
    }

    @Test
    fun `never uses the same station at both ends`() = runTest {
        val stations = listOf(station("unique", at(0.0, 100.0)))
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("attendu un repli à pied, obtenu $plan", plan is JourneyPlan.WalkOnly)
    }
}
