package io.github.mgdx.rouelibre.journey

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.JourneyPlanner
import io.github.mgdx.rouelibre.core.journey.Router
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.joinStationsWithAvailability
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.routing.OfflineRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Exercises the journey algorithm on the real graph, with the real stations.
 *
 * This is the only place acceptance criterion §11.4 can actually be verified:
 * "a journey between two points of the metropolis returns a walk → bike → walk
 * trip in under 3 seconds". The JVM tests of the algorithm use a fake engine
 * and say nothing about real computation time.
 *
 * The data are captures from 9 August 2026: 268 stations and their state.
 */
@RunWith(AndroidJUnit4::class)
class JourneyPlannerOnDeviceTest {

    private lateinit var planner: JourneyPlanner
    private lateinit var stations: List<StationWithAvailability>

    /** Place du Théâtre, Lille. */
    private val lilleCentre = Coordinates(50.6383, 3.0640)

    /** Parc Barbieux, Roubaix — eight kilometres to the north-east. */
    private val roubaix = Coordinates(50.6805, 3.1620)

    /** Villeneuve-d'Ascq Pont de Bois station, to the east. */
    private val villeneuveDAscq = Coordinates(50.6270, 3.1400)

    @Before
    fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        val datasets = DatasetStore(target, Dispatchers.IO)
        // The sets are stored per network: the test's graph goes into its own,
        // so as not to mingle with an installed city's data.
        datasets.useCity(TEST_CITY)
        val graph = checkNotNull(datasets.directoryOf(DatasetKind.Routing)).resolve(GRAPH_FILE)
        if (!graph.isFile) {
            testAssets.open(GRAPH_FILE).use { source ->
                graph.outputStream().use { source.copyTo(it) }
            }
        }

        val parser = GbfsParser()
        val information = parser.parseStationInformation(
            testAssets.open("station_information.json").bufferedReader().use { it.readText() },
        ).orFail()
        val status = parser.parseStationStatus(
            testAssets.open("station_status.json").bufferedReader().use { it.readText() },
        ).orFail()
        stations = joinStationsWithAvailability(information.stations, status.availabilities)

        val router = OfflineRouter(target, datasets, Dispatchers.Default)
        planner = JourneyPlanner(
            object : Router {
                override suspend fun route(
                    from: Coordinates,
                    to: Coordinates,
                    mode: TravelMode,
                ): RouteResult = router.route(from, to, mode)
            },
        )
    }

    @Test
    fun composes_a_complete_journey_in_under_three_seconds() = runBlocking {
        // A first call primes the engine's caches; it is the second that
        // reflects what the user experiences, whose application will already
        // have drawn the map.
        planner.plan(lilleCentre, roubaix, stations)

        lateinit var plan: JourneyPlan
        val elapsed = measureTimeMillis {
            plan = planner.plan(lilleCentre, villeneuveDAscq, stations)
        }

        // The measurement is logged: a test that merely compares against a
        // threshold does not say by how much it passed, and that is precisely
        // what we want to watch across releases.
        Log.i(TAG, "complete journey composed in $elapsed ms")

        assertTrue("no journey found: $plan", plan is JourneyPlan.Found)
        assertTrue("too slow: $elapsed ms", elapsed < BUDGET_MILLIS)
    }

    @Test
    fun the_chosen_journey_has_a_bike_at_the_origin_and_a_dock_at_the_end() = runBlocking {
        // Acceptance criterion §11.5.
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found

        assertTrue(
            "departure station with no bike: ${plan.best.bikesAtDeparture}",
            plan.best.bikesAtDeparture >= 1,
        )
        assertTrue(
            "arrival station with no dock: ${plan.best.docksAtArrival}",
            plan.best.docksAtArrival >= 1,
        )
    }

    @Test
    fun the_journey_really_chains_walk_bike_walk() = runBlocking {
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found
        val best = plan.best

        assertTrue(TravelMode.Walking == best.walkToStation.mode)
        assertTrue(TravelMode.Cycling == best.ride.mode)
        assertTrue(TravelMode.Walking == best.walkToDestination.mode)
        // The access walks must stay access walks.
        assertTrue(
            "access walk out of all proportion: ${best.walkToStation.distanceMetres} m",
            best.walkToStation.distanceMetres < 2_000,
        )
        assertTrue("empty bike leg", best.ride.distanceMetres > 500)
    }

    @Test
    fun the_two_stations_of_a_journey_are_never_the_same() = runBlocking {
        val plan = planner.plan(lilleCentre, roubaix, stations) as JourneyPlan.Found

        assertTrue(
            "the journey starts and ends at the same station",
            plan.best.departureStation.id != plan.best.arrivalStation.id,
        )
    }

    private fun <T> Outcome<T>.orFail(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> throw AssertionError("unreadable capture: $error")
    }

    private companion object {
        const val TAG = "RoueLibrePerf"
        const val GRAPH_FILE = "E0_N50.rd5"

        /** A network of the test's own, so as to erase no installed data. */
        const val TEST_CITY = "reseau-de-test"

        /** The budget of SPEC §6, with an emulator's margin. */
        const val BUDGET_MILLIS = 3_000L
    }
}
