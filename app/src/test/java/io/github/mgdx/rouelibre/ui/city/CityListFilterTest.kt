package io.github.mgdx.rouelibre.ui.city

import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the city list goes back to its head, and what it says when empty. */
class CityListFilterTest {

    @Test
    fun `the first rows shown keep the list where it is`() {
        assertFalse(shouldReturnToTop(null, CityListFilter("", null)))
        assertFalse(shouldReturnToTop(null, CityListFilter("Bergen", "NO")))
    }

    @Test
    fun `rows rebuilt under the same filter keep the list where it is`() {
        val filter = CityListFilter("lyon", "FR")
        assertFalse(shouldReturnToTop(filter, filter.copy()))
    }

    @Test
    fun `widening to every country goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("", "DE"), CityListFilter("", null)))
    }

    @Test
    fun `choosing another country goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("", "DE"), CityListFilter("", "FR")))
    }

    @Test
    fun `clearing the search goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("Bergen", null), CityListFilter("", null)))
    }

    @Test
    fun `typing a letter goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("Ber", null), CityListFilter("Berg", null)))
    }

    @Test
    fun `the first rows shown keep the list where it is, whatever city is in service`() {
        assertFalse(activeCityMoved(null, ActiveCity("lille")))
        assertFalse(activeCityMoved(null, ActiveCity(null)))
    }

    @Test
    fun `rows rebuilt for the same city in service keep the list where it is`() {
        assertFalse(activeCityMoved(ActiveCity("lille"), ActiveCity("lille")))
        assertFalse(activeCityMoved(ActiveCity(null), ActiveCity(null)))
    }

    @Test
    fun `choosing another city goes back to the head`() {
        assertTrue(activeCityMoved(ActiveCity("lille"), ActiveCity("berlin")))
    }

    @Test
    fun `choosing a first city goes back to the head`() {
        assertTrue(activeCityMoved(ActiveCity(null), ActiveCity("berlin")))
    }

    private val cities = listOf(city("lille", "Lille", "FR"), city("berlin", "Berlin", "DE"))

    @Test
    fun `a search found in another country than the one chosen blames the country`() {
        assertEquals(NoMatchReason.COUNTRY, noMatchReason(cities, "Lille", "DE", emptyMap()))
    }

    @Test
    fun `a search found nowhere blames the spelling, country chosen or not`() {
        assertEquals(NoMatchReason.NAME, noMatchReason(cities, "Lile", "DE", emptyMap()))
        assertEquals(NoMatchReason.NAME, noMatchReason(cities, "Lile", null, emptyMap()))
    }

    @Test
    fun `with every country shown only the spelling is left to blame`() {
        assertEquals(NoMatchReason.NAME, noMatchReason(cities, "Lille", null, emptyMap()))
    }

    @Test
    fun `a blank search under a country chosen blames the country`() {
        assertEquals(NoMatchReason.COUNTRY, noMatchReason(cities, "", "PL", emptyMap()))
    }

    @Test
    fun `a blank search over an empty catalogue blames nothing it could widen`() {
        assertEquals(NoMatchReason.NAME, noMatchReason(emptyList(), "", "DE", emptyMap()))
        assertEquals(NoMatchReason.NAME, noMatchReason(emptyList(), "", null, emptyMap()))
    }

    private fun city(id: String, name: String, country: String) = CityEntry(
        id = id,
        displayName = name,
        operator = "operator",
        mainCity = name,
        country = country,
        stationCount = 100,
        stationSamples = listOf(Coordinates(50.6, 3.06)),
        boundingBox = BoundingBox(south = 50.5, west = 2.9, north = 50.7, east = 3.2),
        centre = Coordinates(50.6, 3.06),
        dataSizeBytes = 1_000_000,
        releaseTag = "data-2026-08-fr",
        manifestUrl = "https://example.invalid/manifest.json",
        gbfsDiscoveryUrl = "https://example.invalid/gbfs.json",
    )
}
