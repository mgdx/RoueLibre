package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests of filtering the station list by name. */
class StationSearchTest {

    private fun entry(name: String, postalCode: String? = "59000") = StationWithAvailability(
        station = Station(
            id = name,
            name = name,
            position = Coordinates(50.63, 3.06),
            capacity = 20,
            postalCode = postalCode,
        ),
        availability = null,
    )

    private val stations = listOf(
        entry("Gare Lille Flandres"),
        entry("Place du Théâtre"),
        entry("Metropole Europeenne de Lille (CB)"),
        entry("Saint-André", postalCode = "59350"),
        entry("Wazemmes Marché"),
        entry("4 vents", postalCode = "59260"),
    )

    private fun names(query: String) = filterStations(stations, query).map { it.station.name }

    @Test
    fun `an empty query returns the list untouched`() {
        assertEquals(stations.size, filterStations(stations, "").size)
        assertEquals(stations.size, filterStations(stations, "   ").size)
    }

    @Test
    fun `a query made of punctuation alone returns the list untouched`() {
        // Clearing a search field must bring everything back, not hide it.
        assertEquals(stations.size, filterStations(stations, "---").size)
    }

    @Test
    fun `the search ignores case`() {
        assertEquals(listOf("Wazemmes Marché"), names("WAZEMMES"))
    }

    @Test
    fun `an accented query finds a name published without accents`() {
        // The Lille feed publishes "Metropole Europeenne" without accents, but
        // nobody types like that.
        assertEquals(
            listOf("Metropole Europeenne de Lille (CB)"),
            names("métropole européenne"),
        )
    }

    @Test
    fun `an unaccented query finds an accented name`() {
        assertEquals(listOf("Place du Théâtre"), names("theatre"))
    }

    @Test
    fun `word order does not matter`() {
        assertEquals(listOf("Gare Lille Flandres"), names("flandres gare"))
    }

    @Test
    fun `typing in progress already finds the station`() {
        assertEquals(listOf("Wazemmes Marché"), names("waz"))
        assertEquals(listOf("Wazemmes Marché"), names("wazemmes mar"))
    }

    @Test
    fun `a hyphen counts as a word separator`() {
        assertEquals(listOf("Saint-André"), names("andre"))
        assertEquals(listOf("Saint-André"), names("saint andre"))
    }

    @Test
    fun `the postcode is searchable because it is displayed`() {
        assertEquals(listOf("Saint-André"), names("59350"))
    }

    @Test
    fun `a station without a postcode stays searchable by its name`() {
        val orphan = listOf(entry("Solférino", postalCode = null))
        assertEquals(
            listOf("Solférino"),
            filterStations(orphan, "solferino").map {
                it.station.name
            },
        )
    }

    @Test
    fun `every word of the query must match`() {
        assertEquals(emptyList<String>(), names("gare wazemmes"))
    }

    @Test
    fun `a query that matches nothing returns an empty list`() {
        assertEquals(emptyList<String>(), names("bruxelles"))
    }

    @Test
    fun `the display order is preserved`() {
        assertEquals(
            listOf("Gare Lille Flandres", "Metropole Europeenne de Lille (CB)"),
            names("lille"),
        )
    }

    @Test
    fun `a name starting with a digit is searched by that digit`() {
        assertEquals(listOf("4 vents"), names("4"))
    }
}
