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
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of the journey algorithm (SPEC.md §6).
 *
 * The routing engine is faked: what is exercised here is the CHOICE of the
 * station pair, not the quality of a track. The fake router counts its calls,
 * which allows checking that the pruning holds — an algorithm that were correct
 * but computed all twenty-five pairs would miss the specification's three-second
 * budget.
 */
class JourneyPlannerTest {

    /**
     * A fake router: straight-line distance, raised by a constant detour.
     *
     * The 25 % detour matches what one observes in town between the straight
     * line and the real path. A ride listed in [slowRides] keeps its distance
     * but takes three times longer — roadworks the lower bound cannot see.
     */
    private class FakeRouter(
        private val unreachable: Set<String> = emptySet(),
        private val slowRides: Set<String> = emptySet(),
    ) : Router {
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
            val slowdown = if (mode == TravelMode.Cycling && key(from, to) in slowRides) {
                SLOW_FACTOR
            } else {
                1
            }
            return RouteResult.Success(
                RouteLeg(
                    mode = mode,
                    distanceMetres = metres,
                    duration = (metres / speed).roundToInt().seconds * slowdown,
                    ascentMetres = 0,
                    geometry = listOf(from, to),
                ),
            )
        }

        companion object {
            const val DETOUR = 1.25
            const val WALKING_METRES_PER_SECOND = 1.25
            const val CYCLING_METRES_PER_SECOND = 3.6
            const val SLOW_FACTOR = 3

            fun key(from: Coordinates, to: Coordinates) = "%.4f,%.4f>%.4f,%.4f".format(
                from.latitude,
                from.longitude,
                to.latitude,
                to.longitude,
            )
        }
    }

    /** A degree of latitude is about 111 km; 0.001° is therefore ~111 m. */
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

    // ---------------------------------------------------- choice of pair --

    @Test
    fun `optimises the pair and not the station nearest the origin`() = runTest {
        // Station A is the nearest to the origin, but it leads to a very badly
        // placed arrival. B is slightly further and serves well.
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
        // The one-bike station is nearer, but the risk of finding it empty on
        // arrival justifies walking a little further (SPEC §6).
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
        // One bike two hundred metres away against twelve a kilometre and a
        // half away: the risk must not make the endless walk preferable.
        val stations = listOf(
            station("un-seul-velo", at(0.0, 200.0), bikes = 1),
            station("bien-fournie-tres-loin", at(1500.0, 200.0), bikes = 12),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("un-seul-velo", plan.best.departureStation.id)
    }

    // ------------------------------------------------------- availability --

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
            station("with-docks", at(0.0, 3500.0), docks = 7),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("with-docks", plan.best.arrivalStation.id)
        assertTrue(plan.best.docksAtArrival >= 1)
    }

    @Test
    fun `ignores a station that no longer rents, even full of bikes`() = runTest {
        val stations = listOf(
            station("out-of-service", at(0.0, 100.0), bikes = 20, renting = false),
            station("en-service", at(0.0, 600.0), bikes = 4),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("en-service", plan.best.departureStation.id)
    }

    @Test
    fun `says so when no nearby station has a bike`() = runTest {
        // SPEC §6 requires it: do not propose an impossible journey.
        val stations = listOf(
            station("vide-1", at(0.0, 100.0), bikes = 0),
            station("vide-2", at(0.0, 300.0), bikes = 0),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("expected WalkOnly, got $plan", plan is JourneyPlan.WalkOnly)
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

    // -------------------------------------------------------------- walk --

    @Test
    fun `offers the walk itself on a journey the bike loses`() = runTest {
        // Two hundred metres to cover: fetching a bike costs two detours on
        // foot for a ride of a few seconds. SPEC §6 wants the walk offered, not
        // a ride carrying a note about it.
        val closeDestination = at(0.0, 200.0)
        val stations = listOf(
            station("depart", at(0.0, 150.0)),
            station("arrivee", at(0.0, 250.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, closeDestination, stations)

        assertTrue("expected WalkOnly, got $plan", plan is JourneyPlan.WalkOnly)
        assertEquals(
            NoBikeJourney.WalkingIsQuicker,
            (plan as JourneyPlan.WalkOnly).reason,
        )
    }

    @Test
    fun `keeps the bike on a journey it wins`() = runTest {
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("expected Found, got $plan", plan is JourneyPlan.Found)
    }

    // -------------------------------------------------------------- cost --

    @Test
    fun `evaluates only a fraction of the pairs thanks to the pruning`() = runTest {
        // Five origins and five destinations make twenty-five pairs. Computing
        // them all would cost ten seconds on a real graph.
        val stations = (0 until 6).map { station("depart-$it", at(it * 80.0, 150.0)) } +
            (0 until 6).map { station("arrivee-$it", at(it * 80.0, 3900.0)) }
        val router = FakeRouter()
        val settings = JourneySettings()
        val planner = JourneyPlanner(router, settings)

        planner.plan(origin, destination, stations)

        // The hard budget — a first wave, then only pairs that could still
        // change the answer — caps the computations whatever the geometry.
        // It is what holds the time budget of SPEC §6.
        assertTrue(
            "too many bike legs computed: ${router.cyclingCalls}",
            router.cyclingCalls <= settings.maxRideEvaluations + settings.extraRideEvaluations,
        )
    }

    @Test
    fun `replaces a failed ride rather than giving up`() = runTest {
        // The nearest arrival station is on an island the bike cannot reach:
        // every pair of the first wave leaning on it fails. The budget freed
        // must go to the pairs behind, so the user still gets a journey rather
        // than "no route between these stations".
        val departures = (0 until 3).map { station("depart-$it", at(it * 60.0, 150.0)) }
        val island = station("ile", at(0.0, 3950.0))
        val arrivals = listOf(
            island,
            station("arrivee-1", at(0.0, 3650.0)),
            station("arrivee-2", at(0.0, 3350.0)),
        )
        val unreachable = departures.map {
            FakeRouter.key(it.station.position, island.station.position)
        }.toSet()
        val planner = JourneyPlanner(FakeRouter(unreachable = unreachable))

        val plan = planner.plan(origin, destination, departures + arrivals) as JourneyPlan.Found

        assertTrue("the island must not be reachable", plan.best.arrivalStation.id != "ile")
    }

    @Test
    fun `computes a pair beyond the first wave when it can still win`() = runTest {
        // Roadworks slow every ride the first wave can try: same distance,
        // three times slower. The lower bound cannot see that, but after the
        // first wave the remaining pairs' bounds still beat everything found:
        // they must be computed, and one of them is the true optimum.
        val d0 = at(0.0, 100.0)
        val d1 = at(0.0, 300.0)
        val a0 = at(0.0, 3900.0)
        val a1 = at(0.0, 3750.0)
        val a2 = at(0.0, 3600.0)
        val a3 = at(0.0, 3450.0)
        val stations = listOf(
            station("depart-0", d0),
            station("depart-1", d1),
            station("arrivee-0", a0),
            station("arrivee-1", a1),
            station("arrivee-2", a2),
            station("arrivee-3", a3),
        )
        // Every pair is slow except (depart-1, arrivee-2) and
        // (depart-1, arrivee-3), which the bound ranks seventh and eighth.
        val slow = listOf(d0, d1).flatMap { from ->
            listOf(a0, a1, a2, a3).map { to -> FakeRouter.key(from, to) }
        }.toSet() - FakeRouter.key(d1, a2) - FakeRouter.key(d1, a3)
        val router = FakeRouter(slowRides = slow)
        val settings = JourneySettings()
        val planner = JourneyPlanner(router, settings)

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertTrue(
            "the planner must have looked beyond the first wave",
            router.cyclingCalls > settings.maxRideEvaluations,
        )
        assertEquals("depart-1", plan.best.departureStation.id)
        assertEquals("arrivee-2", plan.best.arrivalStation.id)
    }

    @Test
    fun `the announced time excludes the risk penalty`() = runTest {
        // The penalty serves to rank, never to be announced: the user would
        // see a duration they will not experience.
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
    fun `the announced time is the three legs and nothing else`() = runTest {
        // No fixed allowance is added any more (SPEC §6): the time shown is
        // the one the routing engine traced, walk, ride and walk.
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        val best = plan.best
        assertEquals(
            best.walkToStation.duration + best.ride.duration + best.walkToDestination.duration,
            best.travelTime,
        )
    }

    @Test
    fun `never uses the same station at both ends`() = runTest {
        val stations = listOf(station("unique", at(0.0, 100.0)))
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("expected a walking fallback, got $plan", plan is JourneyPlan.WalkOnly)
    }
}
