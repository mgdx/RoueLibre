package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests of the city catalogue.
 *
 * The first replays the catalogue `tools/build_catalogue.py` actually produces,
 * and not an example written for the occasion: what is verified is that the
 * generator and the reader agree, and that a position in each served city
 * designates the right one.
 */
class CityCatalogueTest {

    @Test
    fun `the catalogue the generator produces is readable`() {
        val catalogue = publishedCatalogue()

        assertTrue("empty catalogue", catalogue.cities.isNotEmpty())
        assertNotNull("refresh address missing", catalogue.catalogueUrl)
        catalogue.cities.forEach { city ->
            assertTrue("unusable bounding box for ${city.id}", city.boundingBox.isUsable)
            assertTrue("centre outside the box for ${city.id}", city.centre in city.boundingBox)
            assertTrue("GBFS feed missing for ${city.id}", city.gbfsDiscoveryUrl.isNotBlank())
        }
    }

    @Test
    fun `every published city announces the weight of its data`() {
        // SPEC §11.9 requires the size to be announced before downloading: a
        // city whose data is generated but whose weight is missing would make
        // that screen lie.
        publishedCatalogue().cities.forEach { city ->
            assertTrue(
                "data weight unknown for ${city.id}",
                city.isAvailable,
            )
        }
    }

    @Test
    fun `a position inside a served city designates its network`() {
        val catalogue = publishedCatalogue()

        assertEquals("vlille", catalogue.suggestionFor(GRAND_PLACE_DE_LILLE)?.id)
        assertEquals("velov", catalogue.suggestionFor(PLACE_BELLECOUR)?.id)
        assertEquals("velib", catalogue.suggestionFor(NOTRE_DAME_DE_PARIS)?.id)
    }

    @Test
    fun `a municipality on the outskirts designates the metropolis's network`() {
        // Seclin is outside the V'lille box but inside its ring: the proposal
        // must hold, otherwise it would only serve the city centre.
        assertEquals("vlille", publishedCatalogue().suggestionFor(SECLIN)?.id)
    }

    @Test
    fun `no city is proposed far from every network`() {
        assertNull(publishedCatalogue().suggestionFor(MARSEILLE))
        assertNull(publishedCatalogue().suggestionFor(REYKJAVIK))
    }

    @Test
    fun `between two overlapping networks, the nearer one wins`() {
        val catalogue = catalogueOf(
            entry("large", south = 48.0, west = 2.0, north = 49.0, east = 3.0),
            entry("near", south = 48.8, west = 2.3, north = 48.9, east = 2.4),
        )

        assertEquals("near", catalogue.rank(NOTRE_DAME_DE_PARIS).first().id)
    }

    @Test
    fun `an entry with an absurd rectangle is dropped without losing the others`() {
        val document = """
            {
              "cities": [
                { "id": "broken", "displayName": "Broken",
                  "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                  "manifestUrl": "https://example.org/manifest.json",
                  "boundingBox": { "south": 49.0, "west": 2.0,
                                   "north": 48.0, "east": 3.0 } },
                { "id": "sound", "displayName": "Sound",
                  "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                  "manifestUrl": "https://example.org/manifest.json",
                  "boundingBox": { "south": 48.0, "west": 2.0,
                                   "north": 49.0, "east": 3.0 } }
              ]
            }
        """.trimIndent()

        val catalogue = (CityCatalogueReader.read(document) as Outcome.Success).value
        assertEquals(listOf("sound"), catalogue.cities.map { it.id })
    }

    @Test
    fun `an unreadable catalogue returns a failure, not an exception`() {
        assertTrue(CityCatalogueReader.read("{ not json") is Outcome.Failure)
        assertTrue(CityCatalogueReader.read("""{"cities": []}""") is Outcome.Failure)
    }

    private companion object {

        /** Lille, the Grand-Place. */
        val GRAND_PLACE_DE_LILLE = Coordinates(50.6371, 3.0630)

        /** Lyon, place Bellecour. */
        val PLACE_BELLECOUR = Coordinates(45.7578, 4.8320)

        /** Paris, Notre-Dame. */
        val NOTRE_DAME_DE_PARIS = Coordinates(48.8530, 2.3499)

        /** Seclin, some fifteen kilometres south of Lille. */
        val SECLIN = Coordinates(50.5496, 3.0284)

        val MARSEILLE = Coordinates(43.2965, 5.3698)
        val REYKJAVIK = Coordinates(64.1466, -21.9426)

        /**
         * The catalogue as it will be published.
         *
         * The path comes from the build: it is the file the generator produces,
         * not a copy made for the test — a copy would end up describing a state
         * nobody publishes any more.
         */
        fun publishedCatalogue(): CityCatalogue {
            val path = checkNotNull(System.getProperty("rouelibre.cityCatalogue")) {
                "catalogue path not supplied by the build"
            }
            val outcome = CityCatalogueReader.read(File(path).readText())
            return (outcome as Outcome.Success).value
        }

        fun catalogueOf(vararg cities: CityEntry) = CityCatalogue(
            catalogueVersion = 1,
            generatedAt = null,
            catalogueUrl = null,
            cities = cities.toList(),
        )

        fun entry(
            id: String,
            south: Double,
            west: Double,
            north: Double,
            east: Double,
        ): CityEntry {
            val box = io.github.mgdx.rouelibre.core.geo.BoundingBox(south, west, north, east)
            return CityEntry(
                id = id,
                displayName = id,
                operator = "",
                country = "FR",
                boundingBox = box,
                centre = box.centre,
                stationCount = null,
                gbfsDiscoveryUrl = "https://example.org/gbfs.json",
                manifestUrl = "https://example.org/manifest.json",
                dataSizeBytes = null,
                releaseTag = null,
            )
        }
    }
}
