package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.StationsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests the distance shown under a station's name (SPEC §7.2).
 *
 * The sheet used to measure from whatever fix the system still held, wherever
 * that was: a phone in Lille consulting Dubai's network was told its station
 * stood 5,236.1 km away, while the list behind it — which applies the served
 * area — gave the postcode and no distance at all. The rule is that the
 * position handed to the model is already filtered by the city served, so the
 * two screens cannot disagree.
 *
 * Driven here rather than on screen because the fault needs a recent fix in
 * cache to appear at all, which no device offers on demand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StationDetailDistanceTest {

    private val dispatcher = StandardTestDispatcher()

    /** A station of the network being consulted, in Dubai. */
    private val station = Station(
        id = "wasl-onyx-karama",
        name = "Wasl Onyx Karama",
        position = Coordinates(25.2455, 55.3021),
        capacity = 12,
        postalCode = null,
    )

    /** Where the phone last saw itself: Lille, five thousand kilometres off. */
    private val lille = Coordinates(50.6292, 3.0573)

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseDispatcher() {
        Dispatchers.resetMain()
    }

    private fun model(knownPositionInCity: suspend () -> Coordinates?) = StationDetailViewModel(
        stations = flowOf(
            StationsSnapshot(
                stations = listOf(StationWithAvailability(station, availability = null)),
                fetchedAt = null,
            ),
        ),
        favouriteStationIds = flowOf(emptyList()),
        setFavourite = {},
        nearestAddress = { null },
        knownPositionInCity = knownPositionInCity,
        fleet = flowOf(null),
        stationId = station.id,
    )

    @Test
    fun `a position outside the city served gives no distance`() = runTest(dispatcher) {
        // What the container answers for a fix taken outside the box of the
        // conurbation consulted: nothing to measure from.
        val viewModel = model { null }
        advanceUntilIdle()

        assertNotNull("the station itself must still be shown", viewModel.state.value.entry)
        assertNull(
            "a distance measured from another continent must not be shown",
            viewModel.state.value.distanceInMetres,
        )
    }

    @Test
    fun `a position inside the city served gives the distance`() = runTest(dispatcher) {
        // Two hundred metres north of the station, and inside the same city.
        val nearby = Coordinates(station.position.latitude + 0.002, station.position.longitude)
        val viewModel = model { nearby }
        advanceUntilIdle()

        val distance = viewModel.state.value.distanceInMetres
        assertNotNull("a position in the city served is worth a distance", distance)
        assertEquals(222.0, checkNotNull(distance), 5.0)
    }

    @Test
    fun `the far position is the one the rule keeps out`() = runTest(dispatcher) {
        // Guards the test above from passing for the wrong reason: unfiltered,
        // this very position is what produced the 5,236.1 km on screen.
        val viewModel = model { lille }
        advanceUntilIdle()

        val distance = checkNotNull(viewModel.state.value.distanceInMetres)
        assertEquals(
            "the measurement itself is right; it is the position that must be refused",
            5_234_790.0,
            distance,
            1_000.0,
        )
    }
}
