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
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
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
        bikesByVehicleType: Map<String, Int> = emptyMap(),
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
    ) = StationWithAvailability(
        station = Station(id, "Station $id", position, bikes + docks, "59000"),
        availability = StationAvailability(
            stationId = id,
            bikesAvailable = bikes,
            bikesByVehicleType = bikesByVehicleType,
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
    fun `carries the breakdown of the departure station, and of that one only`() = runTest {
        // The count shown beside the journey is frozen at this instant, and so
        // is what it divides into: the interface says how many of those bikes
        // are electric (SPEC §7.4). What the arrival station holds is not
        // carried — one arrives there on the bike one already has.
        val stations = listOf(
            station(
                "depart",
                at(0.0, 200.0),
                bikes = 4,
                bikesByVehicleType = mapOf("mecanique" to 3, "electrique" to 1),
            ),
            station(
                "arrivee",
                at(0.0, 3900.0),
                bikes = 7,
                bikesByVehicleType = mapOf("electrique" to 7),
            ),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("depart", plan.best.departureStation.id)
        assertEquals(
            mapOf("mecanique" to 3, "electrique" to 1),
            plan.best.bikesByVehicleTypeAtDeparture,
        )
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
    fun `says so when no station at all has a bike`() = runTest {
        // SPEC §6 requires it: do not propose an impossible journey. Every
        // station has to be empty for this, since distance disqualifies none:
        // a station with a bike is a candidate however far off it stands.
        val stations = listOf(
            station("vide-1", at(0.0, 100.0), bikes = 0),
            station("vide-2", at(0.0, 300.0), bikes = 0),
            station("vide-3", at(0.0, 3900.0), bikes = 0),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("expected WalkOnly, got $plan", plan is JourneyPlan.WalkOnly)
        assertEquals(NoBikeJourney.NoBikeNearby, (plan as JourneyPlan.WalkOnly).reason)
    }

    @Test
    fun `says so when no station at all has a free dock`() = runTest {
        val stations = listOf(
            station("pleine-1", at(0.0, 100.0), docks = 0),
            station("pleine-2", at(0.0, 3900.0), docks = 0),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertEquals(
            NoBikeJourney.NoDockNearby,
            (plan as JourneyPlan.WalkOnly).reason,
        )
    }

    // ------------------------------------------ reach of the access walk --

    @Test
    fun `crosses the conurbation though the arrival station is far off`() = runTest {
        // Rue Nationale, Tourcoing → rue Faidherbe, Wattignies: 17.4 km apart,
        // a V'lille station 203 m from the departure point, and the nearest
        // one to the arrival ("Recherche") 2 363 m away. A threshold of 1 200 m
        // discarded it, leaving nothing but 19.7 km of walking — three hours
        // fifty-four, where the bike takes about an hour. SPEC §11.4 requires
        // the walk → bike → walk journey here.
        val wattignies = at(0.0, 17_450.0)
        val stations = listOf(
            station("avenue-millet", at(0.0, 203.0), bikes = 6),
            station("recherche", at(0.0, 17_450.0 - 2_363.0), docks = 13),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, wattignies, stations)

        assertTrue("expected Found, got $plan", plan is JourneyPlan.Found)
        val best = (plan as JourneyPlan.Found).best
        assertEquals("avenue-millet", best.departureStation.id)
        assertEquals("recherche", best.arrivalStation.id)
    }

    @Test
    fun `rides even when the only station is kilometres from the destination`() = runTest {
        // Thirty kilometres to cover and the nearest station five from the
        // arrival: an hour on foot at the far end, and still a saving of
        // hours. No distance disqualifies a station — only the comparison with
        // walking does, and here walking loses.
        val farAway = at(0.0, 30_000.0)
        val stations = listOf(
            station("depart", at(0.0, 100.0)),
            station("a-cinq-km", at(0.0, 25_000.0), docks = 8),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, farAway, stations)

        assertTrue("expected Found, got $plan", plan is JourneyPlan.Found)
        assertEquals("a-cinq-km", (plan as JourneyPlan.Found).best.arrivalStation.id)
    }

    @Test
    fun `offers the walk when fetching the bike costs more than it saves`() = runTest {
        // Four kilometres to cover, both stations five kilometres off the
        // straight line: the two access walks alone outlast walking the whole
        // way. Nothing bars that pair any more, so the guard is the comparison
        // — and it only holds because the direct walk is computed here, beyond
        // the distance at which it is worked out up front.
        val stations = listOf(
            station("depart-a-l-ecart", at(5_000.0, 0.0)),
            station("arrivee-a-l-ecart", at(5_000.0, 4_000.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.plan(origin, destination, stations)

        assertTrue("expected WalkOnly, got $plan", plan is JourneyPlan.WalkOnly)
        assertEquals(
            NoBikeJourney.WalkingIsQuicker,
            (plan as JourneyPlan.WalkOnly).reason,
        )
    }

    @Test
    fun `spares the direct walk when the bike has already outrun it`() = runTest {
        // A twenty-kilometre walk costs as much to trace as the ride itself.
        // When the journey found is quicker than the straight line covered at
        // a pace nobody holds, no real walk can win and none is computed: the
        // two access walks are the only walking legs traced.
        val wattignies = at(0.0, 17_450.0)
        // One station usable at each end and no other, so the walking legs
        // traced are exactly the two access walks.
        val stations = listOf(
            station("avenue-millet", at(0.0, 203.0), bikes = 6, docks = 0),
            station("recherche", at(0.0, 17_450.0 - 2_363.0), bikes = 0, docks = 13),
        )
        val router = FakeRouter()
        val planner = JourneyPlanner(router)

        planner.plan(origin, wattignies, stations)

        assertEquals(2, router.walkingCalls)
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

    // ------------------------------------------------- kind of bike asked --

    /** Lyon's own words for its two kinds, as its `vehicle_types` feed has them. */
    private val vehicleTypes = mapOf(
        "mecanique" to VehicleKind.Mechanical,
        "electrique" to VehicleKind.Electric,
    )

    private fun wanting(kind: WantedBikeKind) = JourneyPlanner(
        FakeRouter(),
        wantedBike = BikeKindFilter(kind, vehicleTypes),
    )

    /** The nearest station lends mechanical bikes only; a farther one, electric. */
    private val mixedNetwork = listOf(
        station(
            "mecanique-proche",
            at(0.0, 150.0),
            bikes = 5,
            bikesByVehicleType = mapOf("mecanique" to 5),
        ),
        station(
            "electrique-loin",
            at(0.0, 700.0),
            bikes = 3,
            bikesByVehicleType = mapOf("electrique" to 3),
        ),
        station("arrivee", at(0.0, 3900.0), bikes = 0, docks = 9),
    )

    @Test
    fun `a kind asked for keeps only the stations that hold one`() = runTest {
        val plan = wanting(WantedBikeKind.Electric)
            .plan(origin, destination, mixedNetwork) as JourneyPlan.Found

        assertEquals("electrique-loin", plan.best.departureStation.id)
    }

    @Test
    fun `by default no kind is asked for at all`() = runTest {
        // The non-regression that matters most: built without a word about
        // kinds, the algorithm is the one that existed before the choice did —
        // here it takes the nearest station, which lends the other kind.
        val plan = JourneyPlanner(FakeRouter())
            .plan(origin, destination, mixedNetwork) as JourneyPlan.Found

        assertEquals("mecanique-proche", plan.best.departureStation.id)
    }

    @Test
    fun `a station whose breakdown cannot be read is left out when a kind is asked for`() =
        runTest {
            // The feed counts its bikes under a vehicle type the network never
            // declared: the station may well hold an electric bike, and nothing
            // can say so. Walking somebody to a bike we failed to count would
            // promise what we cannot deliver (SPEC §7.2).
            val stations = listOf(
                station(
                    "illisible-proche",
                    at(0.0, 150.0),
                    bikes = 5,
                    bikesByVehicleType = mapOf("431" to 5),
                ),
                station(
                    "electrique-loin",
                    at(0.0, 700.0),
                    bikes = 3,
                    bikesByVehicleType = mapOf("electrique" to 3),
                ),
                station("arrivee", at(0.0, 3900.0), bikes = 0, docks = 9),
            )

            val plan = wanting(WantedBikeKind.Electric)
                .plan(origin, destination, stations) as JourneyPlan.Found

            assertEquals("electrique-loin", plan.best.departureStation.id)
        }

    @Test
    fun `a station whose breakdown cannot be read is kept when no kind is asked for`() = runTest {
        // The same station, the same feed: what excludes it above is the
        // request, never the reading itself.
        val stations = listOf(
            station(
                "illisible-proche",
                at(0.0, 150.0),
                bikes = 5,
                bikesByVehicleType = mapOf("431" to 5),
            ),
            station(
                "electrique-loin",
                at(0.0, 700.0),
                bikes = 3,
                bikesByVehicleType = mapOf("electrique" to 3),
            ),
            station("arrivee", at(0.0, 3900.0), bikes = 0, docks = 9),
        )

        val plan = JourneyPlanner(FakeRouter())
            .plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("illisible-proche", plan.best.departureStation.id)
    }

    @Test
    fun `says which kind is missing when no station holds it`() = runTest {
        // SPEC §6 again: say so rather than propose a journey towards a bike
        // that is not there — and say WHICH bike, so there is something to do
        // about it.
        val stations = listOf(
            station(
                "mecanique-1",
                at(0.0, 150.0),
                bikes = 5,
                bikesByVehicleType = mapOf("mecanique" to 5),
            ),
            station(
                "mecanique-2",
                at(0.0, 3900.0),
                bikes = 4,
                docks = 9,
                bikesByVehicleType = mapOf("mecanique" to 4),
            ),
        )

        val plan = wanting(WantedBikeKind.Electric).plan(origin, destination, stations)

        assertTrue("expected WalkOnly, got $plan", plan is JourneyPlan.WalkOnly)
        assertEquals(
            NoBikeJourney.NoWantedBikeNearby(WantedBikeKind.Electric),
            (plan as JourneyPlan.WalkOnly).reason,
        )
    }

    @Test
    fun `the arrival end is indifferent to the kind asked for`() = runTest {
        // A free dock is a free dock whatever is returned to it (SPEC §6): the
        // nearest arrival station wins though it holds not one electric bike.
        val stations = listOf(
            station(
                "depart",
                at(0.0, 150.0),
                bikes = 3,
                bikesByVehicleType = mapOf("electrique" to 3),
            ),
            station(
                "arrivee-proche",
                at(0.0, 3900.0),
                bikes = 6,
                docks = 9,
                bikesByVehicleType = mapOf("mecanique" to 6),
            ),
            station(
                "arrivee-loin",
                at(0.0, 3300.0),
                bikes = 6,
                docks = 9,
                bikesByVehicleType = mapOf("electrique" to 6),
            ),
        )

        val plan = wanting(WantedBikeKind.Electric)
            .plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("arrivee-proche", plan.best.arrivalStation.id)
    }

    @Test
    fun `the risk is weighed on the bikes of the kind asked for`() = runTest {
        // One electric bike among eight, six minutes away, against four
        // electric bikes one minute further. Counting the whole rack, the near
        // station looks safe and wins; counting what the rider actually asked
        // for, the single electric bike is exactly as likely to go as a lone
        // bike would be — and losing it costs the walk to the next station.
        val stations = listOf(
            station(
                "un-seul-electrique",
                at(0.0, 450.0),
                bikes = 8,
                bikesByVehicleType = mapOf("mecanique" to 7, "electrique" to 1),
            ),
            station(
                "quatre-electriques",
                at(0.0, 530.0),
                bikes = 4,
                bikesByVehicleType = mapOf("electrique" to 4),
            ),
            station("arrivee", at(0.0, 3900.0), bikes = 0, docks = 9),
        )

        val asked = wanting(WantedBikeKind.Electric)
            .plan(origin, destination, stations) as JourneyPlan.Found
        val unasked = JourneyPlanner(FakeRouter())
            .plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("quatre-electriques", asked.best.departureStation.id)
        // Asked for nothing, the nearer rack still wins: what moved the choice
        // is the request, not a change to the penalty itself.
        assertEquals("un-seul-electrique", unasked.best.departureStation.id)
    }

    @Test
    fun `the arrival risk still weighs the free docks themselves`() = runTest {
        // A dock takes back any bike: the kind asked for must not narrow what
        // the arrival end is weighed on, or a station holding no electric bike
        // would be penalised for having none to lend.
        val stations = listOf(
            station(
                "depart",
                at(0.0, 150.0),
                bikes = 3,
                bikesByVehicleType = mapOf("electrique" to 3),
            ),
            station(
                "arrivee",
                at(0.0, 3900.0),
                bikes = 6,
                docks = 9,
                bikesByVehicleType = mapOf("mecanique" to 6),
            ),
        )

        val asked = (
            wanting(WantedBikeKind.Electric)
                .plan(origin, destination, stations) as JourneyPlan.Found
            ).best
        val unasked = (
            JourneyPlanner(FakeRouter())
                .plan(origin, destination, stations) as JourneyPlan.Found
            ).best

        assertEquals("arrivee", asked.arrivalStation.id)
        assertEquals(unasked.riskPenalty, asked.riskPenalty)
    }

    @Test
    fun `the breakdown carried does not depend on the kind asked for`() = runTest {
        // What the screens SHOW is both counts, whatever was asked for: they
        // answer "what is waiting there", and the filter is a question put
        // elsewhere (SPEC §7.2, §7.4). The same journey, worked out for an
        // electric bike and for none, must carry the same two figures.
        val stations = listOf(
            station(
                "depart",
                at(0.0, 150.0),
                bikes = 4,
                bikesByVehicleType = mapOf("mecanique" to 3, "electrique" to 1),
            ),
            station("arrivee", at(0.0, 3900.0), bikes = 0, docks = 9),
        )

        val asked = wanting(WantedBikeKind.Electric)
            .plan(origin, destination, stations) as JourneyPlan.Found
        val unasked = JourneyPlanner(FakeRouter())
            .plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals("depart", asked.best.departureStation.id)
        assertEquals(
            unasked.best.bikesByVehicleTypeAtDeparture,
            asked.best.bikesByVehicleTypeAtDeparture,
        )
        assertEquals(unasked.best.bikesAtDeparture, asked.best.bikesAtDeparture)
    }

    // --------------------------------------------------------- own bike --

    @Test
    fun `on one's own bike, one leg is traced and no station is looked at`() = runTest {
        val router = FakeRouter()
        val planner = JourneyPlanner(router)

        val plan = planner.planWithOwnBike(origin, destination) as JourneyPlan.OwnBike

        assertEquals(TravelMode.Cycling, plan.ride.mode)
        assertEquals(1, router.cyclingCalls)
        // Not one walk: neither an access leg, since nothing is fetched, nor
        // the direct walk, since it is not being compared against (SPEC §7.3).
        assertEquals(0, router.walkingCalls)
    }

    @Test
    fun `on one's own bike, the ride is offered even where the walk is quicker`() = runTest {
        // Two hundred metres is quicker on foot than on a bike one has to
        // wheel out; the user has said they are riding, and the answer is the
        // ride they asked for.
        val planner = JourneyPlanner(FakeRouter())

        val plan = planner.planWithOwnBike(origin, at(0.0, 200.0))

        assertTrue("expected a ride, got $plan", plan is JourneyPlan.OwnBike)
    }

    @Test
    fun `on one's own bike, a missing graph is said to be missing`() = runTest {
        // The engine's reason has to survive: telling somebody there is no
        // route, when the routing data was never installed, sends them looking
        // for another address instead of to the storage screen (SPEC §7.4).
        val router = object : Router {
            override suspend fun route(
                from: Coordinates,
                to: Coordinates,
                mode: TravelMode,
            ): RouteResult = RouteResult.Failure(RoutingFailure.GraphMissing)
        }
        val planner = JourneyPlanner(router)

        val plan = planner.planWithOwnBike(origin, destination)

        assertEquals(JourneyPlan.Impossible(NoBikeJourney.GraphMissing), plan)
    }

    // ------------------------------------------------------------- units --

    @Test
    fun `the units the reader chose reach nothing the algorithm decides`() = runTest {
        // The invariant this whole application rests on (SPEC §14): everything
        // is measured in metres, and a system of units is consulted at one
        // moment only — when a figure becomes text. A rider in miles and a
        // rider in kilometres must get the same journey, the same pair of
        // stations and the same announced time, written two ways.
        //
        // Checked twice over, because a behavioural test alone cannot fail
        // here: the planner takes no unit system, so the two plans below are
        // the same call made twice. What holds the boundary is the second
        // half — the day somebody threads units into the algorithm, one of the
        // signatures below stops being what it is, and this fails.
        val stations = listOf(
            station("un-seul-velo", at(0.0, 200.0), bikes = 1),
            station("bien-fournie", at(0.0, 320.0), bikes = 12),
            station("arrivee", at(0.0, 3900.0)),
        )
        val planner = JourneyPlanner(FakeRouter())

        val first = planner.plan(origin, destination, stations) as JourneyPlan.Found
        val again = planner.plan(origin, destination, stations) as JourneyPlan.Found

        assertEquals(first.best.departureStation.id, again.best.departureStation.id)
        assertEquals(first.best.arrivalStation.id, again.best.arrivalStation.id)
        assertEquals(first.best.distanceMetres, again.best.distanceMetres)
        assertEquals(first.best.climbMetres, again.best.climbMetres)
        assertEquals(first.best.travelTime, again.best.travelTime)

        val measures = "io.github.mgdx.rouelibre.core.measure"
        val decidingClasses = listOf(
            JourneyPlanner::class.java,
            JourneyOption::class.java,
            RouteLeg::class.java,
            Station::class.java,
            StationAvailability::class.java,
        )
        val leaked = decidingClasses
            .flatMap { it.declaredMethods.toList() + it.declaredConstructors.toList() }
            .flatMap { member ->
                val types = member.parameterTypes.toList() +
                    listOfNotNull((member as? java.lang.reflect.Method)?.returnType)
                types.map { member.name to it }
            }
            .filter { (_, type) -> type.name.startsWith(measures) }

        assertEquals("units reached the algorithm through $leaked", emptyList<Any>(), leaked)
    }
}
