package io.github.mgdx.rouelibre.data.routing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises BRouter where it will really run: on Android.
 *
 * This test exists because the rest of the chain proves nothing about what
 * matters here. That the submodule compiles does not say its code runs under
 * ART; that it computes a route on the development machine does not say it will
 * find its files inside an application's private storage.
 *
 * The routing graph lives in the test's resources, never in the application's:
 * SPEC §5 requires it to be downloaded or imported, not embedded.
 */
@RunWith(AndroidJUnit4::class)
class OfflineRouterTest {

    private lateinit var router: OfflineRouter
    private lateinit var datasets: DatasetStore

    /** The Grand-Place in Lille. */
    private val lilleCentre = Coordinates(50.6371, 3.0630)

    /** The Grand-Place in Roubaix, a dozen kilometres away. */
    private val roubaixCentre = Coordinates(50.6900, 3.1750)

    @Before
    fun installGraph() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

        datasets = DatasetStore(target, Dispatchers.IO)
        // The sets are stored per network: the test's graph goes into its own,
        // so as not to mingle with an installed city's data.
        datasets.useCity(TEST_CITY)
        val segments = checkNotNull(datasets.directoryOf(DatasetKind.Routing))
        val graph = segments.resolve(GRAPH_FILE)
        if (!graph.isFile) {
            testAssets.open(GRAPH_FILE).use { source ->
                graph.outputStream().use { source.copyTo(it) }
            }
        }
        router = OfflineRouter(target, datasets, Dispatchers.Default)
    }

    @Test
    fun computes_a_bike_route_between_two_towns_of_the_metropolis() = runBlocking {
        val result = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)

        val leg = (result as? RouteResult.Success)?.leg
            ?: throw AssertionError("unexpected failure: $result")

        assertEquals(TravelMode.Cycling, leg.mode)
        // As the crow flies it is about 10 km; a usable route is necessarily
        // longer, without being able to double the distance.
        assertTrue(
            "distance invraisemblable : ${leg.distanceMetres} m",
            leg.distanceMetres in 10_000..20_000,
        )
        assertTrue("implausible duration: ${leg.duration}", leg.duration.inWholeMinutes in 20..90)
        assertTrue("track too sparse: ${leg.geometry.size} points", leg.geometry.size > 100)
    }

    @Test
    fun computes_a_walking_route_slower_than_the_same_one_by_bike() = runBlocking {
        val onFoot = router.route(lilleCentre, roubaixCentre, TravelMode.Walking)
        val onBike = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)

        val walking = (onFoot as? RouteResult.Success)?.leg
            ?: throw AssertionError("walking failure: $onFoot")
        val cycling = (onBike as? RouteResult.Success)?.leg
            ?: throw AssertionError("cycling failure: $onBike")

        assertTrue(
            "walking should be slower than cycling",
            walking.duration > cycling.duration,
        )
    }

    @Test
    fun the_track_really_starts_at_the_origin_and_ends_at_the_destination() = runBlocking {
        val result = router.route(lilleCentre, roubaixCentre, TravelMode.Cycling)
        val leg = (result as? RouteResult.Success)?.leg
            ?: throw AssertionError("unexpected failure: $result")

        // The engine snaps the ends to the nearest usable node; a few tens of
        // metres of difference are normal, kilometres are not.
        assertTrue(
            "the track does not start at the requested point",
            leg.geometry.first().distanceInMetresTo(lilleCentre) < TOLERANCE_METRES,
        )
        assertTrue(
            "the track does not end at the requested point",
            leg.geometry.last().distanceInMetresTo(roubaixCentre) < TOLERANCE_METRES,
        )
    }

    @Test
    fun a_point_outside_the_box_is_reported_and_does_not_crash() = runBlocking {
        // Brussels: outside the downloaded graph.
        val outside = Coordinates(50.8467, 4.3525)

        val result = router.route(lilleCentre, outside, TravelMode.Cycling)

        assertTrue("expected a failure, got: $result", result is RouteResult.Failure)
        val reason = (result as RouteResult.Failure).reason
        assertTrue(
            "unexpected cause: $reason",
            reason is RoutingFailure.OutsideCoverage ||
                reason is RoutingFailure.NoRouteFound ||
                reason is RoutingFailure.EngineFailure,
        )
    }

    private companion object {
        const val GRAPH_FILE = "E0_N50.rd5"

        /** A network of the test's own, so as to erase no installed data. */
        const val TEST_CITY = "reseau-de-test"
        const val TOLERANCE_METRES = 200.0
    }
}
