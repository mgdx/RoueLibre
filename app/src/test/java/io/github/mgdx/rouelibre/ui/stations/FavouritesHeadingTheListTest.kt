package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The favourites heading the station list (SPEC §7.6, issue #11).
 *
 * Somebody who knows the city opens the list for the two or three stations
 * they always use, and found them at the alphabet's mercy or, ordered by
 * distance, wherever they happened to stand. They now head the list in the
 * order the user gave them — and nowhere a search is typed, where the
 * favourites that match are already in the answer.
 */
class FavouritesHeadingTheListTest {

    private val gare = entry("gare", "Gare")
    private val mairie = entry("mairie", "Mairie")
    private val port = entry("port", "Port")
    private val network = listOf(gare, mairie, port)

    @Test
    fun `the favourites come in the order the user gave them, not the list's`() {
        assertEquals(
            listOf(port, gare),
            favouritesHeadingTheList(network, listOf("port", "gare"), query = ""),
        )
    }

    @Test
    fun `a search typed takes the heading away`() {
        assertEquals(
            emptyList<StationWithAvailability>(),
            favouritesHeadingTheList(network, listOf("port"), query = "po"),
        )
    }

    @Test
    fun `a favourite the network no longer lists is left out rather than shown empty`() {
        assertEquals(
            listOf(mairie),
            favouritesHeadingTheList(network, listOf("closed-last-year", "mairie"), query = ""),
        )
    }

    @Test
    fun `no favourite, no heading`() {
        assertEquals(
            emptyList<StationWithAvailability>(),
            favouritesHeadingTheList(network, emptyList(), query = ""),
        )
    }

    private fun entry(id: String, name: String) = StationWithAvailability(
        Station(
            id = id,
            name = name,
            position = Coordinates(50.63, 3.06),
            capacity = 10,
            postalCode = null,
        ),
        availability = null,
    )
}
