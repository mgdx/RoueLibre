package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests du rapprochement entre données stables et état temps réel.
 *
 * Le cas limite n'est pas théorique : le flux du réseau lillois publiait, le
 * 9 août 2026, 268 stations dans `station_information` et 267 dans
 * `station_status`.
 */
class StationJoinTest {

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.63, 3.06),
        capacity = 20,
        postalCode = "59000",
    )

    private fun availability(
        id: String,
        bikes: Int = 5,
        docks: Int = 15,
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
    ) = StationAvailability(
        stationId = id,
        bikesAvailable = bikes,
        docksAvailable = docks,
        isInstalled = installed,
        isRenting = renting,
        isReturning = returning,
        reportedAt = null,
    )

    @Test
    fun `une station sans etat reste presente et son etat reste inconnu`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("1"), station("2")),
            availabilities = listOf(availability("2")),
        )

        assertEquals(2, joined.size)
        val orphan = joined.first { it.station.id == "1" }
        assertNull(orphan.availability)
        assertEquals(ServiceState.Unknown, orphan.serviceState)
    }

    @Test
    fun `un etat orphelin est ignore faute de savoir ou le placer`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("1")),
            availabilities = listOf(availability("1"), availability("fantome")),
        )

        assertEquals(1, joined.size)
        assertEquals("1", joined.single().station.id)
    }

    @Test
    fun `l'ordre des stations est conserve`() {
        val joined = joinStationsWithAvailability(
            stations = listOf(station("c"), station("a"), station("b")),
            availabilities = emptyList(),
        )

        assertEquals(listOf("c", "a", "b"), joined.map { it.station.id })
    }

    @Test
    fun `une station non deployee est hors service`() {
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", installed = false)),
        )

        assertEquals(ServiceState.OutOfService, joined.single().serviceState)
    }

    @Test
    fun `une station qui ne loue ni ne recoit plus est hors service`() {
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", renting = false, returning = false)),
        )

        assertEquals(ServiceState.OutOfService, joined.single().serviceState)
    }

    @Test
    fun `une station qui ne fait plus que recevoir reste en service`() {
        // Elle rend encore un service réel : y déposer un vélo.
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", renting = false)),
        )

        assertEquals(ServiceState.InService, joined.single().serviceState)
    }

    @Test
    fun `une station vide reste en service`() {
        // Zéro vélo n'est pas une panne : c'est une information à afficher.
        val joined = joinStationsWithAvailability(
            listOf(station("1")),
            listOf(availability("1", bikes = 0)),
        )

        assertEquals(ServiceState.InService, joined.single().serviceState)
        assertEquals(false, joined.single().availability?.canLendBike)
    }
}
