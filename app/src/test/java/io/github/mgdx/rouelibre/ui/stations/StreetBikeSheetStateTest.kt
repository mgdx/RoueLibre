package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.BikeCharge
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.data.StreetBikesSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tests the sheet of a bike outside stations, on the JVM (SPEC §7.2.1).
 *
 * Two rules are worth the fixture. **Which line the charge takes**, which is a
 * measurement and not a preference: nextbike publishes a percentage on every
 * one of its electric bikes and a range of zero on every one of them, so a
 * sheet reading the range first would announce a flat battery on a bike that
 * is two thirds full. And **what happens when the bike leaves the feed** while
 * its sheet is open: it is said, the actions go, and the sheet does not vanish
 * under the reader's finger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreetBikeSheetStateTest {

    private val dispatcher = StandardTestDispatcher()

    private val marseille = Coordinates(43.2965, 5.3698)

    private fun bike(
        typeId: String? = "ebike",
        chargeRatio: Double? = null,
        rangeMetres: Int? = null,
    ) = StreetBike(
        id = BIKE_ID,
        position = marseille,
        vehicleTypeId = typeId,
        chargeRatio = chargeRatio,
        rangeMetres = rangeMetres,
    )

    private fun fleet(
        maxRangeMetresByType: Map<String, Int> = emptyMap(),
        cargoVehicleTypeIds: Set<String> = emptySet(),
    ) = FleetDescription(
        hasElectricBikes = true,
        isMixed = true,
        vehicleTypes = mapOf("bike" to VehicleKind.Mechanical, "ebike" to VehicleKind.Electric),
        maxRangeMetresByType = maxRangeMetresByType,
        cargoVehicleTypeIds = cargoVehicleTypeIds,
    )

    private fun snapshot(vararg bikes: StreetBike, read: Boolean = true) = StreetBikesSnapshot(
        bikes = bikes.toList(),
        fetchedAt = if (read) Instant.parse("2026-09-08T10:00:00Z") else null,
        published = if (read) true else null,
    )

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseDispatcher() {
        Dispatchers.resetMain()
    }

    private fun model(
        bikes: MutableStateFlow<StreetBikesSnapshot>,
        fleet: FleetDescription? = fleet(),
    ) = StreetBikeViewModel(
        streetBikes = bikes,
        fleet = flowOf(fleet),
        knownPositionInCity = { null },
        bikeId = BIKE_ID,
    )

    @Test
    fun `the percentage wins over the range`() {
        // nextbike Berlin, 8 September 2026: a percentage on every electric
        // bike and `current_range_meters` at zero on every one of them.
        val bikes = MutableStateFlow(
            snapshot(bike(chargeRatio = 0.67, rangeMetres = 30_000)),
        )
        runTest(dispatcher) {
            val viewModel = model(bikes, fleet(maxRangeMetresByType = mapOf("ebike" to 60_000)))
            advanceUntilIdle()

            assertEquals(BikeCharge.Ratio(0.67), viewModel.state.value.charge)
        }
    }

    @Test
    fun `the range is stated where it and the type's maximum are both real`() {
        // Vienna publishes both consistently, 30 of 60 km.
        val bikes = MutableStateFlow(snapshot(bike(rangeMetres = 30_000)))
        runTest(dispatcher) {
            val viewModel = model(bikes, fleet(maxRangeMetresByType = mapOf("ebike" to 60_000)))
            advanceUntilIdle()

            assertEquals(BikeCharge.Range(30_000), viewModel.state.value.charge)
        }
    }

    @Test
    fun `a range with no declared maximum behind it says nothing`() {
        // A range that cannot be read as a charge is not one to write: the
        // line disappears rather than lie.
        val bikes = MutableStateFlow(snapshot(bike(rangeMetres = 30_000)))
        runTest(dispatcher) {
            val viewModel = model(bikes, fleet())
            advanceUntilIdle()

            assertNull(viewModel.state.value.charge)
            assertNotNull("the bike itself is still shown", viewModel.state.value.bike)
        }
    }

    @Test
    fun `a cargo type is named as one, whatever drives it`() {
        val bikes = MutableStateFlow(snapshot(bike()))
        runTest(dispatcher) {
            val viewModel = model(bikes, fleet(cargoVehicleTypeIds = setOf("ebike")))
            advanceUntilIdle()

            assertEquals(StreetBikeKind.Cargo, viewModel.state.value.kind)
        }
    }

    @Test
    fun `a bike that leaves the feed is said to be gone, and stays on screen`() {
        val bikes = MutableStateFlow(snapshot(bike(chargeRatio = 0.67)))
        runTest(dispatcher) {
            val viewModel = model(bikes)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isGone)

            // Somebody took it between two reads.
            bikes.value = snapshot()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isGone)
            assertNotNull(
                "the sheet must not empty under the reader",
                viewModel.state.value.bike,
            )
        }
    }

    @Test
    fun `a feed never read says nothing about a bike being gone`() {
        // Before the first read there is nothing to be absent from, and
        // "no longer reported here" would be a claim about a feed nobody
        // asked.
        val bikes = MutableStateFlow(snapshot(read = false))
        runTest(dispatcher) {
            val viewModel = model(bikes)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isGone)
            assertNull(viewModel.state.value.bike)
        }
    }

    private companion object {
        const val BIKE_ID = "levelo-7743"
    }
}
