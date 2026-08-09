package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de l'échelle de disponibilité et de ce que l'indicateur affiche. */
class AvailabilityLevelTest {

    private fun station(capacity: Int? = 20) = Station(
        id = "1",
        name = "Rue Nationale",
        position = Coordinates(50.633, 3.053),
        capacity = capacity,
        postalCode = "59000",
    )

    private fun availability(
        bikes: Int,
        docks: Int,
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
    ) = StationAvailability(
        stationId = "1",
        bikesAvailable = bikes,
        docksAvailable = docks,
        isInstalled = installed,
        isRenting = renting,
        isReturning = returning,
        reportedAt = null,
    )

    @Test
    fun `l'echelle suit les seuils annonces`() {
        assertEquals(AvailabilityLevel.None, availabilityLevelOf(0))
        assertEquals(AvailabilityLevel.Low, availabilityLevelOf(1))
        assertEquals(AvailabilityLevel.Low, availabilityLevelOf(2))
        assertEquals(AvailabilityLevel.Medium, availabilityLevelOf(3))
        assertEquals(AvailabilityLevel.Medium, availabilityLevelOf(5))
        assertEquals(AvailabilityLevel.Good, availabilityLevelOf(6))
        assertEquals(AvailabilityLevel.Good, availabilityLevelOf(40))
    }

    @Test
    fun `un compte negatif est traite comme une absence`() {
        assertEquals(AvailabilityLevel.None, availabilityLevelOf(-3))
    }

    @Test
    fun `le mode velos compte les velos, le mode places compte les places`() {
        val entry = StationWithAvailability(station(), availability(bikes = 7, docks = 13))

        assertEquals(7, entry.displayFor(AvailabilityMode.Bikes).count)
        assertEquals(13, entry.displayFor(AvailabilityMode.Docks).count)
    }

    @Test
    fun `l'arc rapporte le compte a la capacite publiee`() {
        val entry = StationWithAvailability(
            station(capacity = 20),
            availability(bikes = 5, docks = 15),
        )

        assertEquals(0.25f, entry.displayFor(AvailabilityMode.Bikes).filledFraction!!, 1e-4f)
    }

    @Test
    fun `sans capacite publiee l'arc se rabat sur la somme observee`() {
        val entry = StationWithAvailability(
            station(capacity = null),
            availability(bikes = 3, docks = 9),
        )

        assertEquals(0.25f, entry.displayFor(AvailabilityMode.Bikes).filledFraction!!, 1e-4f)
    }

    @Test
    fun `une station qui ne loue plus est hors service pour les velos seulement`() {
        // Elle rend encore un service réel : y déposer un vélo.
        val entry = StationWithAvailability(
            station(),
            availability(bikes = 4, docks = 8, renting = false),
        )

        assertTrue(entry.displayFor(AvailabilityMode.Bikes).isOutOfService)
        assertTrue(!entry.displayFor(AvailabilityMode.Docks).isOutOfService)
        assertEquals(8, entry.displayFor(AvailabilityMode.Docks).count)
    }

    @Test
    fun `une station non deployee est hors service des deux cotes`() {
        val entry = StationWithAvailability(
            station(),
            availability(bikes = 4, docks = 8, installed = false),
        )

        assertTrue(entry.displayFor(AvailabilityMode.Bikes).isOutOfService)
        assertTrue(entry.displayFor(AvailabilityMode.Docks).isOutOfService)
    }

    @Test
    fun `une station sans etat n'affiche ni chiffre ni niveau`() {
        val entry = StationWithAvailability(station(), availability = null)

        val display = entry.displayFor(AvailabilityMode.Bikes)
        assertNull(display.count)
        assertNull(display.level)
        assertNull(display.filledFraction)
        assertTrue(!display.isOutOfService)
    }
}
