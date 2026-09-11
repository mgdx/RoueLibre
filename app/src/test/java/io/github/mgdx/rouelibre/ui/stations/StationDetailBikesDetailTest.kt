package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.BikeCharge
import io.github.mgdx.rouelibre.core.station.DockedBike
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.data.StationsSnapshot
import io.github.mgdx.rouelibre.data.StreetBikesSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The line a station's sheet adds under its split, from the vehicle feed
 * (SPEC §7.2, §7.6).
 *
 * Two rules are worth the fixture. **The setting decides**, and it decides
 * live: the repository keeps the last read in memory after the switch goes
 * off, and a sheet must not go on showing a charge the reader has just asked
 * not to pay for. And **the table is what makes a bike electric**: without
 * it no charge is listed, whatever the feed writes, because a range on a bike
 * with no battery is a figure about nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StationDetailBikesDetailTest {

    private val dispatcher = StandardTestDispatcher()

    private val station = Station(
        id = "3140",
        name = "Kottbusser Tor",
        position = Coordinates(52.4993, 13.4180),
        capacity = 20,
        postalCode = null,
    )

    private val fleet = FleetDescription(
        hasElectricBikes = true,
        isMixed = true,
        vehicleTypes = mapOf("346" to VehicleKind.Mechanical, "348" to VehicleKind.Electric),
        maxRangeMetresByType = mapOf("348" to 60_000),
        cargoVehicleTypeIds = emptySet(),
    )

    private fun docked(typeId: String, ratio: Double? = null, disabled: Boolean = false) =
        DockedBike(
            id = "bike-$typeId-$ratio",
            stationId = station.id,
            vehicleTypeId = typeId,
            chargeRatio = ratio,
            rangeMetres = null,
            isDisabled = disabled,
            isReserved = false,
        )

    private fun vehicles(vararg bikes: DockedBike) = StreetBikesSnapshot(
        bikes = emptyList(),
        dockedBikes = bikes.groupBy { it.stationId },
        fetchedAt = Instant.parse("2026-09-11T10:00:00Z"),
        published = true,
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
        vehicles: Flow<StreetBikesSnapshot>,
        wanted: Flow<Boolean>,
        fleet: FleetDescription? = this.fleet,
    ) = StationDetailViewModel(
        stations = flowOf(
            StationsSnapshot(
                stations = listOf(StationWithAvailability(station, availability = null)),
                fetchedAt = null,
            ),
        ),
        favouriteStationIds = flowOf(emptyList()),
        setFavourite = {},
        nearestAddress = { null },
        knownPositionInCity = { null },
        fleet = flowOf(fleet),
        vehicles = vehicles,
        bikesDetailWanted = wanted,
        stationId = station.id,
    )

    @Test
    fun `the charges are listed fullest first, with the bikes out of service`() =
        runTest(dispatcher) {
            val model = model(
                vehicles = flowOf(
                    vehicles(
                        docked("348", ratio = 0.4),
                        docked("348", ratio = 0.92),
                        docked("346", disabled = true),
                    ),
                ),
                wanted = flowOf(true),
            )
            advanceUntilIdle()

            val detail = model.state.value.bikesDetail!!
            assertEquals(listOf(BikeCharge.Ratio(0.92), BikeCharge.Ratio(0.4)), detail.charges)
            assertEquals(1, detail.outOfService)
        }

    @Test
    fun `the list is folded on opening, and stays as left across a read`() = runTest(dispatcher) {
        val vehicles = MutableStateFlow(vehicles(docked("348", ratio = 0.5)))
        val model = model(vehicles = vehicles, wanted = flowOf(true))
        advanceUntilIdle()
        assertFalse(model.state.value.isBikesListUnfolded)

        model.toggleBikesList()
        // The feed re-emits every few minutes: what the reader opened must
        // not fold itself under their eyes.
        vehicles.value = vehicles(docked("348", ratio = 0.6))
        advanceUntilIdle()

        assertTrue(model.state.value.isBikesListUnfolded)
        assertEquals("bike-348-0.6", model.state.value.bikesDetail!!.bikes.single().id)
    }

    @Test
    fun `switching the setting off empties the line at once`() = runTest(dispatcher) {
        val wanted = MutableStateFlow(true)
        val model = model(vehicles = flowOf(vehicles(docked("348", ratio = 0.5))), wanted = wanted)
        advanceUntilIdle()
        assertEquals(1, model.state.value.bikesDetail!!.charges.size)

        wanted.value = false
        advanceUntilIdle()

        assertNull(model.state.value.bikesDetail)
    }

    @Test
    fun `without the table, nothing is listed`() = runTest(dispatcher) {
        val model = model(
            vehicles = flowOf(vehicles(docked("348", ratio = 0.5))),
            wanted = flowOf(true),
            fleet = null,
        )
        advanceUntilIdle()

        assertNull(model.state.value.bikesDetail)
    }

    @Test
    fun `a station the feed lists nothing at has no line`() = runTest(dispatcher) {
        val elsewhere = docked("348", ratio = 0.5).copy(stationId = "other")
        val model = model(vehicles = flowOf(vehicles(elsewhere)), wanted = flowOf(true))
        advanceUntilIdle()

        assertNull(model.state.value.bikesDetail)
    }
}
