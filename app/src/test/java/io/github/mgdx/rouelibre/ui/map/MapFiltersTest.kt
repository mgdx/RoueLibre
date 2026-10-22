package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two filters laid over the main map (SPEC §7.1). */
class MapFiltersTest {

    @Test
    fun `the toggle passes between bikes and free docks`() {
        assertEquals(AvailabilityMode.Docks, AvailabilityMode.Bikes.toggled())
        assertEquals(AvailabilityMode.Bikes, AvailabilityMode.Docks.toggled())
    }

    @Test
    fun `a network whose free docks are not shown is counted in bikes`() {
        // Left on free docks with the toggle gone, the map would be a column of
        // ∞ with no button to leave it by (SPEC §7.1).
        assertEquals(AvailabilityMode.Bikes, AvailabilityMode.Docks.where(freeDocksShown = false))
        assertEquals(AvailabilityMode.Bikes, AvailabilityMode.Bikes.where(freeDocksShown = false))
    }

    @Test
    fun `a network whose free docks are shown keeps the mode chosen`() {
        assertEquals(AvailabilityMode.Docks, AvailabilityMode.Docks.where(freeDocksShown = true))
        assertEquals(AvailabilityMode.Bikes, AvailabilityMode.Bikes.where(freeDocksShown = true))
    }

    @Test
    fun `the kind filter runs all, mechanical, electric, and back to all`() {
        val allKinds: WantedBikeKind? = null
        assertEquals(WantedBikeKind.Mechanical, allKinds.nextOnTheMap())
        assertEquals(WantedBikeKind.Electric, WantedBikeKind.Mechanical.nextOnTheMap())
        assertNull(WantedBikeKind.Electric.nextOnTheMap())
    }

    @Test
    fun `the kind filter is offered over bikes of a mixed fleet`() {
        assertTrue(showsBikeKindFilter(controlsUp = true, AvailabilityMode.Bikes, MIXED))
    }

    @Test
    fun `nor while the controls are down, nor over free docks`() {
        assertFalse(showsBikeKindFilter(controlsUp = false, AvailabilityMode.Bikes, MIXED))
        assertFalse(showsBikeKindFilter(controlsUp = true, AvailabilityMode.Docks, MIXED))
    }

    /** Sorting bikes into two piles where there is one promises what nobody can collect. */
    @Test
    fun `nor where the network lends one kind, or before the reading lands`() {
        assertFalse(showsBikeKindFilter(controlsUp = true, AvailabilityMode.Bikes, SINGLE))
        assertFalse(showsBikeKindFilter(controlsUp = true, AvailabilityMode.Bikes, null))
    }

    @Test
    fun `a kind chosen over a mixed fleet filters the markers`() {
        assertEquals(
            BikeKindFilter(WantedBikeKind.Electric, MIXED.vehicleTypes),
            bikeKindFilterOf(WantedBikeKind.Electric, MIXED),
        )
    }

    @Test
    fun `the filter is silent wherever its button is`() {
        assertNull(bikeKindFilterOf(null, MIXED))
        assertNull(bikeKindFilterOf(WantedBikeKind.Electric, SINGLE))
        assertNull(bikeKindFilterOf(WantedBikeKind.Electric, null))
    }

    private companion object {
        val MIXED = FleetDescription(
            hasElectricBikes = true,
            isMixed = true,
            vehicleTypes = mapOf("bike" to VehicleKind.Mechanical, "ebike" to VehicleKind.Electric),
        )
        val SINGLE = FleetDescription(
            hasElectricBikes = false,
            isMixed = false,
            vehicleTypes = mapOf("bike" to VehicleKind.Mechanical),
        )
    }
}
