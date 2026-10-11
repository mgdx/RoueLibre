package io.github.mgdx.rouelibre.ui.city

import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Narrowing the city list to one country. */
class CountryFilterTest {

    @Test
    fun `a catalogue of a single country offers no filter`() {
        val entries = listOf(city("lille", "FR"), city("lyon", "fr"))
        assertTrue(countriesOffered(entries, Locale.ENGLISH).isEmpty())
    }

    @Test
    fun `each country is offered once, whatever the case of its code`() {
        val entries = listOf(city("lille", "FR"), city("lyon", " fr "), city("berlin", "DE"))
        assertEquals(listOf("FR", "DE"), countriesOffered(entries, Locale.ENGLISH).map { it.code })
    }

    @Test
    fun `countries are named and ordered in the interface's language`() {
        val entries = listOf(city("lille", "FR"), city("berlin", "DE"), city("madrid", "ES"))
        assertEquals(
            listOf("France", "Germany", "Spain"),
            countriesOffered(entries, Locale.ENGLISH).map { it.name },
        )
        // In French, Germany is "Allemagne" and moves to the head; "Espagne"
        // is ordered among the E's though the code is ES either way.
        assertEquals(
            listOf("Allemagne", "Espagne", "France"),
            countriesOffered(entries, Locale.FRENCH).map { it.name },
        )
    }

    @Test
    fun `an accented name is ordered among its letter, not after Z`() {
        val entries = listOf(city("paris", "FR"), city("dubai", "AE"), city("madrid", "ES"))
        val codes = countriesOffered(entries, Locale.FRENCH).map { it.code }
        // "Émirats arabes unis", "Espagne", "France": the plain order of the
        // characters would file the É after the F.
        assertEquals(listOf("AE", "ES", "FR"), codes)
    }

    @Test
    fun `a code that is no region is shown as it is`() {
        assertEquals("1A", countryName("1A", Locale.ENGLISH))
    }

    @Test
    fun `no country chosen keeps every city`() {
        val entries = listOf(city("lille", "FR"), city("berlin", "DE"))
        assertEquals(entries, filterByCountry(entries, null))
    }

    @Test
    fun `a country chosen keeps its cities, in their order`() {
        val entries = listOf(city("lyon", "FR"), city("berlin", "DE"), city("lille", "fr"))
        assertEquals(listOf("lyon", "lille"), filterByCountry(entries, "FR").map { it.id })
    }

    @Test
    fun `a remembered country no longer offered is forgotten`() {
        val offered = listOf(CountryChoice("DE", "Germany"), CountryChoice("FR", "France"))
        assertEquals("FR", countryStillOffered("FR", offered))
        assertNull(countryStillOffered("PL", offered))
        assertNull(countryStillOffered(null, offered))
        assertNull(countryStillOffered("FR", emptyList()))
    }

    private fun city(id: String, country: String) = CityEntry(
        id = id,
        displayName = id,
        operator = "operator",
        mainCity = id,
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
