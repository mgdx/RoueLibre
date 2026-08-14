package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.FleetReading
import io.github.mgdx.rouelibre.core.station.VehicleKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the interface is told the city lends (SPEC §4.1, §7).
 *
 * The rule under test is that **a reading only ever adds**: it is what keeps the
 * bike glyph still on a network whose stations happen to be empty of one kind
 * for an hour, and what makes the change safe — the counting can reveal an offer
 * the configuration was seeded without, never take one away under the user.
 */
class FleetRepositoryTest {

    private val lyonTypes = mapOf(
        "mechanical" to VehicleKind.Mechanical,
        "electrical" to VehicleKind.Electric,
    )

    /** A conurbation whose survey found only mechanical bikes. */
    private val mechanicalSeed = FleetDescription(
        hasElectricBikes = false,
        isMixed = false,
        vehicleTypes = emptyMap(),
    )

    private fun reading(
        electric: Boolean,
        mixed: Boolean,
        counted: Int = 100,
        types: Map<String, VehicleKind> = lyonTypes,
    ) = FleetReading(
        vehicleTypes = types,
        hasElectricBikes = electric,
        isMixed = mixed,
        bikesCounted = counted,
    )

    private class FakeStore : MeasuredFleetStore {
        var slot: Pair<String, FleetDescription>? = null

        override suspend fun measuredFleet(cityId: String): FleetDescription? =
            slot?.takeIf { it.first == cityId }?.second

        override suspend fun setMeasuredFleet(cityId: String, fleet: FleetDescription) {
            slot = cityId to fleet
        }

        override suspend fun clearMeasuredFleet() {
            slot = null
        }
    }

    private fun repository(
        store: MeasuredFleetStore = FakeStore(),
        city: CityFleet? = CityFleet("velov", mechanicalSeed),
    ) = FleetRepository(store = store) { city }

    @Test
    fun `serves the configuration's seed until anything has been counted`() = runTest {
        val fleet = repository().fleet.first()

        assertEquals(mechanicalSeed, fleet)
    }

    @Test
    fun `no city chosen means nothing to draw`() = runTest {
        assertNull(repository(city = null).fleet.first())
    }

    @Test
    fun `a count reveals the electric bikes the seed did not know about`() = runTest {
        val store = FakeStore()
        val repository = repository(store)

        repository.record(reading(electric = true, mixed = true))

        val fleet = checkNotNull(repository.fleet.first())
        assertTrue(fleet.hasElectricBikes)
        assertTrue(fleet.isMixed)
        assertEquals(lyonTypes, fleet.vehicleTypes)
        assertEquals(
            "the reading has to survive a launch with no connection",
            "velov",
            store.slot?.first,
        )
    }

    @Test
    fun `a reading resting on nothing counted is dropped`() = runTest {
        // Every station empty at four in the morning: the reading then carries
        // the declaration's answer, and the seed already holds a better one.
        val store = FakeStore()
        val repository = repository(store)

        repository.record(reading(electric = true, mixed = true, counted = 0))

        assertEquals(mechanicalSeed, repository.fleet.first())
        assertNull(store.slot)
    }

    @Test
    fun `a kind seen once is never unseen`() = runTest {
        val repository = repository()
        repository.record(reading(electric = true, mixed = true))

        // The next minute, the mechanical bikes are all out on the road.
        repository.record(reading(electric = true, mixed = false))

        val fleet = checkNotNull(repository.fleet.first())
        assertTrue("the cog must not blink out under the user", fleet.isMixed)
        assertTrue(fleet.hasElectricBikes)
    }

    @Test
    fun `a remembered reading comes back before any network does`() = runTest {
        val store = FakeStore()
        store.setMeasuredFleet(
            "velov",
            FleetDescription(
                hasElectricBikes = true,
                isMixed = true,
                vehicleTypes = lyonTypes,
            ),
        )

        val fleet = checkNotNull(repository(store).fleet.first())

        assertTrue(fleet.isMixed)
        assertEquals(lyonTypes, fleet.vehicleTypes)
    }

    @Test
    fun `a reading taken in another city is not read here`() = runTest {
        val store = FakeStore()
        store.setMeasuredFleet(
            "vlille",
            FleetDescription(hasElectricBikes = true, isMixed = true, vehicleTypes = lyonTypes),
        )

        val fleet = checkNotNull(repository(store).fleet.first())

        assertFalse("Lille's fleet says nothing about Lyon's", fleet.isMixed)
    }

    @Test
    fun `leaving the city forgets what was counted there`() = runTest {
        val store = FakeStore()
        val repository = repository(store)
        repository.record(reading(electric = true, mixed = true))

        repository.forget()

        assertNull(store.slot)
    }

    @Test
    fun `a vehicle type added by the operator joins the table`() = runTest {
        // What silences a station's split is an identifier absent from the
        // table: picking up a new one is the whole point of counting again.
        val repository = repository()
        repository.record(reading(electric = true, mixed = true, types = lyonTypes))

        repository.record(
            reading(
                electric = true,
                mixed = true,
                types = mapOf("cargo" to VehicleKind.Electric),
            ),
        )

        val fleet = checkNotNull(repository.fleet.first())
        assertEquals(lyonTypes + mapOf("cargo" to VehicleKind.Electric), fleet.vehicleTypes)
    }
}
