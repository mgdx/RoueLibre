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
    fun `a city either announces the weight of its data or none at all`() {
        // SPEC §11.9 requires the size to be announced before downloading. A
        // catalogue may carry no weight for a network — its data has not been
        // produced, or the catalogue was derived before it was — and the
        // interface then says only that it does not know. What must never
        // happen is a weight of zero, which would read as "nothing to
        // download".
        val cities = publishedCatalogue().cities
        cities.forEach { city ->
            val size = city.dataSizeBytes
            assertTrue("data weight of zero for ${city.id}", size == null || size > 0)
        }
        assertTrue(
            "no city announces the weight of its data",
            cities.any { it.hasAnnouncedSize },
        )
    }

    @Test
    fun `no two cities share an identifier`() {
        // The identifier names a city's data directory and its manifest: two
        // cities sharing one would have the second overwrite the first's data.
        val identifiers = publishedCatalogue().cities.map { it.id }
        assertEquals(identifiers.size, identifiers.toSet().size)
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
        assertNull(publishedCatalogue().suggestionFor(MIDDLE_OF_THE_MORVAN))
        assertNull(publishedCatalogue().suggestionFor(REYKJAVIK))
    }

    @Test
    fun `a conurbation outside the three first served has its own network`() {
        // The catalogue grew from three networks to every French one whose
        // stations are published: a position in Marseille or in Toulouse must
        // now find its own, not the nearest of the first three.
        val catalogue = publishedCatalogue()

        assertEquals("levelo", catalogue.suggestionFor(VIEUX_PORT_DE_MARSEILLE)?.id)
        assertEquals("velotoulouse", catalogue.suggestionFor(CAPITOLE_DE_TOULOUSE)?.id)
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
    fun `an entry whose identifier could not name a directory is dropped`() {
        // The identifier names the directory a city's data lives in, and the
        // catalogue is downloaded. A "../.." would make the storage of a city —
        // its creation, its listing, and the recursive deletion of "delete this
        // city's data" — bear on a directory nobody chose.
        val document = """
            {
              "cities": [
                { "id": "../..", "displayName": "Escaping",
                  "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                  "manifestUrl": "https://example.org/manifest.json",
                  "boundingBox": { "south": 48.0, "west": 2.0,
                                   "north": 49.0, "east": 3.0 } },
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
    fun `every published identifier passes the rule`() {
        // The counterpart: the rule is the alphabet tools/add_city.py already
        // guarantees, so it must let through all three hundred and six cities
        // published today. A rule that refused one of them would take the city
        // off the list on the next update.
        publishedCatalogue().cities.forEach { city ->
            assertTrue("refused identifier: ${city.id}", isUsableCityId(city.id))
        }
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

        /** Marseille, the Vieux-Port. */
        val VIEUX_PORT_DE_MARSEILLE = Coordinates(43.2951, 5.3740)

        /** Toulouse, place du Capitole. */
        val CAPITOLE_DE_TOULOUSE = Coordinates(43.6045, 1.4442)

        /**
         * Deep in the Morvan, some fifty kilometres from any network.
         *
         * The point has to be chosen with care now that the catalogue lists
         * every French network: the country's empty quarters are what is left.
         */
        val MIDDLE_OF_THE_MORVAN = Coordinates(47.1300, 4.0300)

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
                mainCity = null,
                operator = "",
                country = "FR",
                boundingBox = box,
                centre = box.centre,
                stationCount = null,
                // The hand-made entries of these tests carry no station, so
                // they are measured on their box — the behaviour a catalogue
                // produced before the samples existed still gets.
                stationSamples = emptyList(),
                gbfsDiscoveryUrl = "https://example.org/gbfs.json",
                manifestUrl = "https://example.org/manifest.json",
                dataSizeBytes = null,
                releaseTag = null,
            )
        }
    }
}
