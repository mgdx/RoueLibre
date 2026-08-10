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
 * Tests du catalogue des villes.
 *
 * Le premier rejoue le catalogue réellement produit par
 * `tools/build_catalogue.py`, et non un exemple écrit pour l'occasion : ce qui
 * est vérifié est que le générateur et le lecteur s'accordent, et qu'une
 * position dans chacune des villes servies désigne bien la bonne.
 */
class CityCatalogueTest {

    @Test
    fun `le catalogue produit par le generateur est lisible`() {
        val catalogue = publishedCatalogue()

        assertTrue("catalogue vide", catalogue.cities.isNotEmpty())
        assertNotNull("adresse de rafraîchissement absente", catalogue.catalogueUrl)
        catalogue.cities.forEach { city ->
            assertTrue("emprise inutilisable pour ${city.id}", city.boundingBox.isUsable)
            assertTrue("centre hors emprise pour ${city.id}", city.centre in city.boundingBox)
            assertTrue("flux GBFS absent pour ${city.id}", city.gbfsDiscoveryUrl.isNotBlank())
        }
    }

    @Test
    fun `chaque ville publiee annonce le poids de ses donnees`() {
        // Le SPEC §11.9 exige que la taille soit annoncée avant le
        // téléchargement : une ville dont les données sont générées mais dont
        // le poids manque ferait mentir cet écran.
        publishedCatalogue().cities.forEach { city ->
            assertTrue(
                "poids des données inconnu pour ${city.id}",
                city.isAvailable,
            )
        }
    }

    @Test
    fun `une position dans une ville servie designe son reseau`() {
        val catalogue = publishedCatalogue()

        assertEquals("vlille", catalogue.suggestionFor(GRAND_PLACE_DE_LILLE)?.id)
        assertEquals("velov", catalogue.suggestionFor(PLACE_BELLECOUR)?.id)
        assertEquals("velib", catalogue.suggestionFor(NOTRE_DAME_DE_PARIS)?.id)
    }

    @Test
    fun `une commune de la peripherie designe le reseau de la metropole`() {
        // Seclin est hors de l'emprise du V'lille mais dans sa couronne : la
        // proposition doit tenir, sans quoi elle ne servirait qu'au centre-ville.
        assertEquals("vlille", publishedCatalogue().suggestionFor(SECLIN)?.id)
    }

    @Test
    fun `aucune ville n'est proposee loin de tout reseau`() {
        assertNull(publishedCatalogue().suggestionFor(MARSEILLE))
        assertNull(publishedCatalogue().suggestionFor(REYKJAVIK))
    }

    @Test
    fun `entre deux reseaux qui se recouvrent, le plus proche l'emporte`() {
        val catalogue = catalogueOf(
            entry("large", south = 48.0, west = 2.0, north = 49.0, east = 3.0),
            entry("proche", south = 48.8, west = 2.3, north = 48.9, east = 2.4),
        )

        assertEquals("proche", catalogue.rank(NOTRE_DAME_DE_PARIS).first().id)
    }

    @Test
    fun `une entree au rectangle absurde est ecartee sans perdre les autres`() {
        val document = """
            {
              "cities": [
                { "id": "cassee", "displayName": "Cassée",
                  "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                  "manifestUrl": "https://example.org/manifest.json",
                  "boundingBox": { "south": 49.0, "west": 2.0,
                                   "north": 48.0, "east": 3.0 } },
                { "id": "saine", "displayName": "Saine",
                  "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                  "manifestUrl": "https://example.org/manifest.json",
                  "boundingBox": { "south": 48.0, "west": 2.0,
                                   "north": 49.0, "east": 3.0 } }
              ]
            }
        """.trimIndent()

        val catalogue = (CityCatalogueReader.read(document) as Outcome.Success).value
        assertEquals(listOf("saine"), catalogue.cities.map { it.id })
    }

    @Test
    fun `un catalogue illisible rend un echec, pas une exception`() {
        assertTrue(CityCatalogueReader.read("{ pas du json") is Outcome.Failure)
        assertTrue(CityCatalogueReader.read("""{"cities": []}""") is Outcome.Failure)
    }

    private companion object {

        /** Lille, Grand-Place. */
        val GRAND_PLACE_DE_LILLE = Coordinates(50.6371, 3.0630)

        /** Lyon, place Bellecour. */
        val PLACE_BELLECOUR = Coordinates(45.7578, 4.8320)

        /** Paris, Notre-Dame. */
        val NOTRE_DAME_DE_PARIS = Coordinates(48.8530, 2.3499)

        /** Seclin, à une quinzaine de kilomètres au sud de Lille. */
        val SECLIN = Coordinates(50.5496, 3.0284)

        val MARSEILLE = Coordinates(43.2965, 5.3698)
        val REYKJAVIK = Coordinates(64.1466, -21.9426)

        /**
         * Le catalogue tel qu'il sera publié.
         *
         * Le chemin vient du build : c'est le fichier que produit le
         * générateur, pas une copie faite pour le test — une copie finirait par
         * décrire un état que plus personne ne publie.
         */
        fun publishedCatalogue(): CityCatalogue {
            val path = checkNotNull(System.getProperty("rouelibre.cityCatalogue")) {
                "chemin du catalogue non fourni par le build"
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
