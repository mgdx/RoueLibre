package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests of the search over the city catalogue.
 *
 * What matters here is what somebody looking for their own city types: the name
 * of the town, rarely the name of the network, and never with the apostrophes
 * and accents the catalogue writes.
 */
class CitySearchTest {

    @Test
    fun `the town's name finds its network`() {
        assertEquals(listOf("vlille"), filterCities(CITIES, "lille").map { it.id })
        assertEquals(listOf("velov"), filterCities(CITIES, "lyon").map { it.id })
    }

    @Test
    fun `the network's name finds it too`() {
        assertEquals(listOf("velov"), filterCities(CITIES, "velo'v").map { it.id })
    }

    @Test
    fun `accents and apostrophes are not typed`() {
        // "Vélo'v" is written with an accent and an apostrophe, and typed
        // without either.
        assertEquals(listOf("velov"), filterCities(CITIES, "velov").map { it.id })
    }

    @Test
    fun `a word in progress already narrows the list`() {
        assertEquals(listOf("velib"), filterCities(CITIES, "par").map { it.id })
    }

    @Test
    fun `the words may come in any order`() {
        assertEquals(listOf("velov"), filterCities(CITIES, "lyon velo").map { it.id })
    }

    @Test
    fun `an empty search returns everything`() {
        // Clearing the field must bring the whole catalogue back, not empty it.
        assertEquals(CITIES.size, filterCities(CITIES, "").size)
        assertEquals(CITIES.size, filterCities(CITIES, "  ").size)
    }

    @Test
    fun `a search matching nothing returns nothing`() {
        assertEquals(emptyList<CityEntry>(), filterCities(CITIES, "reykjavik"))
    }

    private companion object {
        val CITIES = listOf(
            entry("vlille", "V'Lille", "Lille"),
            entry("velov", "Vélo'v", "Lyon"),
            entry("velib", "Vélib' Métropole", "Paris"),
        )

        fun entry(id: String, displayName: String, mainCity: String) = CityEntry(
            id = id,
            displayName = displayName,
            mainCity = mainCity,
            operator = "",
            country = "FR",
            boundingBox = BoundingBox(48.0, 2.0, 49.0, 3.0),
            centre = BoundingBox(48.0, 2.0, 49.0, 3.0).centre,
            stationCount = null,
            // This test searches by name; where the stations are does not
            // enter into it.
            stationSamples = emptyList(),
            gbfsDiscoveryUrl = "https://example.org/gbfs.json",
            manifestUrl = "https://example.org/manifest.json",
            dataSizeBytes = null,
            releaseTag = null,
        )
    }
}
