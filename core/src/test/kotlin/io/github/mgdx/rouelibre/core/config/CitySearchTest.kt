package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.address.TestRules
import io.github.mgdx.rouelibre.core.address.searchLetterFolds
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
        assertEquals(listOf("vlille"), filterCities(CITIES, "lille", FOLDS).map { it.id })
        assertEquals(listOf("velov"), filterCities(CITIES, "lyon", FOLDS).map { it.id })
    }

    @Test
    fun `the network's name finds it too`() {
        assertEquals(listOf("velov"), filterCities(CITIES, "velo'v", FOLDS).map { it.id })
    }

    @Test
    fun `accents and apostrophes are not typed`() {
        // "Vélo'v" is written with an accent and an apostrophe, and typed
        // without either.
        assertEquals(listOf("velov"), filterCities(CITIES, "velov", FOLDS).map { it.id })
    }

    @Test
    fun `a word in progress already narrows the list`() {
        assertEquals(listOf("velib"), filterCities(CITIES, "par", FOLDS).map { it.id })
    }

    @Test
    fun `the words may come in any order`() {
        assertEquals(listOf("velov"), filterCities(CITIES, "lyon velo", FOLDS).map { it.id })
    }

    @Test
    fun `an empty search returns everything`() {
        // Clearing the field must bring the whole catalogue back, not empty it.
        assertEquals(CITIES.size, filterCities(CITIES, "", FOLDS).size)
        assertEquals(CITIES.size, filterCities(CITIES, "  ", FOLDS).size)
    }

    @Test
    fun `a search matching nothing returns nothing`() {
        assertEquals(emptyList<CityEntry>(), filterCities(CITIES, "reykjavik", FOLDS))
    }

    @Test
    fun `a city is found without the letters its name needs and no keyboard has`() {
        // The seven cities of the catalogue whose name carries a letter accent
        // removal cannot reach. For "Włower" and "ŁoKeR" every word of the
        // label begins with that letter, so without the folds no ASCII typing
        // reaches them at all: they were only ever found by scrolling.
        assertEquals(listOf("biker"), filterCities(FOREIGN, "bialystok", FOLDS).map { it.id })
        assertEquals(listOf("wlower"), filterCities(FOREIGN, "wlower", FOLDS).map { it.id })
        assertEquals(listOf("wlower"), filterCities(FOREIGN, "wloclawek", FOLDS).map { it.id })
        assertEquals(listOf("loker"), filterCities(FOREIGN, "loker", FOLDS).map { it.id })
        assertEquals(listOf("loker"), filterCities(FOREIGN, "lomza", FOLDS).map { it.id })
        assertEquals(listOf("giessen"), filterCities(FOREIGN, "giessen", FOLDS).map { it.id })
    }

    @Test
    fun `the name as its network writes it still finds itself`() {
        // Both sides go through the same fold, so the reader who does have the
        // letter on their keyboard is not the one who loses out.
        assertEquals(listOf("biker"), filterCities(FOREIGN, "białystok", FOLDS).map { it.id })
        assertEquals(listOf("giessen"), filterCities(FOREIGN, "gießen", FOLDS).map { it.id })
    }

    private companion object {
        /**
         * The folds as they are shipped, gathered from every rule set.
         *
         * Read from `config/address-normalization/` rather than written out
         * here: that table is the repository's only one, and a copy taken for
         * a test would stop telling the truth the day the real one moved.
         */
        val FOLDS = searchLetterFolds(TestRules.languages().map(TestRules::of))

        val CITIES = listOf(
            entry("vlille", "V'Lille", "Lille"),
            entry("velov", "Vélo'v", "Lyon"),
            entry("velib", "Vélib' Métropole", "Paris"),
        )

        val FOREIGN = listOf(
            entry("biker", "BIKER", "Białystok"),
            entry("wlower", "Włower", "Włocławek"),
            entry("loker", "ŁoKeR", "Łomża"),
            entry("giessen", "nextbike", "Gießen"),
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
