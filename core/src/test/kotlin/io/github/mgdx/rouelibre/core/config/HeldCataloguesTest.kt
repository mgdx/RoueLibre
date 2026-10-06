package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of which catalogue decides, and of what a chosen city resolves to
 * between the two a device holds (SPEC §15.1).
 *
 * The matrix: a downloaded copy newer than the shipped one, older, or absent;
 * a network served, withdrawn, brought back, or named nowhere; and its
 * configuration in the build or not.
 */
class HeldCataloguesTest {

    // --- Which catalogue is in force -----------------------------------------

    @Test
    fun `a downloaded copy older than the shipped one is set aside (A2)`() {
        // The phone of the report: a copy downloaded on 28 September, a build
        // shipping the catalogue of 6 October and Oslo with it.
        val catalogues = HeldCatalogues(
            downloaded = held(SEPTEMBER, cities = listOf("lille")),
            shipped = held(OCTOBER, cities = listOf("lille", "oslo")),
        )

        assertFalse(catalogues.isDownloadedInForce)
        assertNotNull(catalogues.inForce.catalogue.entry("oslo"))
    }

    @Test
    fun `a downloaded copy newer than the shipped one replaces it whole`() {
        // No union: dropping a network from the published catalogue is what
        // retires it without a release.
        val catalogues = HeldCatalogues(
            downloaded = held(OCTOBER, cities = listOf("lille")),
            shipped = held(SEPTEMBER, cities = listOf("lille", "tarnow")),
        )

        assertTrue(catalogues.isDownloadedInForce)
        assertNull(catalogues.inForce.catalogue.entry("tarnow"))
    }

    @Test
    fun `without a downloaded copy the shipped one is in force`() {
        val shipped = held(OCTOBER, cities = listOf("lille"))
        val catalogues = HeldCatalogues(downloaded = null, shipped = shipped)

        assertFalse(catalogues.isDownloadedInForce)
        assertEquals(shipped, catalogues.inForce)
    }

    @Test
    fun `on a tie the downloaded copy stays in force, and its validators with it`() {
        assertTrue(downloadedCatalogueOutranks(OCTOBER, OCTOBER))
    }

    @Test
    fun `an undated catalogue is older than a dated one`() {
        assertFalse(downloadedCatalogueOutranks(null, OCTOBER))
        assertFalse(downloadedCatalogueOutranks("yesterday", OCTOBER))
        assertTrue(downloadedCatalogueOutranks(SEPTEMBER, null))
        assertTrue(downloadedCatalogueOutranks(null, null))
    }

    // --- What a chosen city resolves to --------------------------------------

    @Test
    fun `no chosen city is none`() {
        assertEquals(ActiveCity.None, onlyShipped().resolve(null, configuration = null))
    }

    @Test
    fun `a city listed and configured is served`() {
        val configuration = configuration("lille")

        assertEquals(
            ActiveCity.Served(configuration),
            onlyShipped().resolve("lille", configuration),
        )
    }

    @Test
    fun `a network brought back is served, though an older copy still withdraws it (B1)`() {
        // Pau back in config/cities/, out of withdrawn-cities.json, and the
        // update installed offline over a copy that still lists it as gone.
        val catalogues = HeldCatalogues(
            downloaded = held(SEPTEMBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
            shipped = held(OCTOBER, cities = listOf("lille", PAU)),
        )
        val configuration = configuration(PAU)

        assertEquals(ActiveCity.Served(configuration), catalogues.resolve(PAU, configuration))
        assertEquals(emptyList<WithdrawnCity>(), catalogues.withdrawnCities(setOf("lille", PAU)))
    }

    @Test
    fun `a network brought back that this build lacks needs a newer version (B3)`() {
        // The build dropped Pau and shipped its withdrawal; the catalogue
        // published since serves it again. Gone is not the news.
        val catalogues = HeldCatalogues(
            downloaded = held(OCTOBER, cities = listOf("lille", PAU)),
            shipped = held(SEPTEMBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
        )

        val resolved = catalogues.resolve(PAU, configuration = null)

        assertEquals(
            ActiveCity.NoLongerServed(
                PAU,
                withdrawal = null,
                servedByNewerVersion = catalogues.inForce.catalogue.entry(PAU),
            ),
            resolved,
        )
        assertNotNull((resolved as ActiveCity.NoLongerServed).servedByNewerVersion)
        assertEquals(emptyList<WithdrawnCity>(), catalogues.withdrawnCities(setOf("lille")))
    }

    @Test
    fun `a withdrawal in the catalogue in force outweighs a configuration`() {
        val downloadedNewer = HeldCatalogues(
            downloaded = held(OCTOBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
            shipped = held(SEPTEMBER, cities = listOf("lille", PAU)),
        )
        val shippedOnly = HeldCatalogues(
            downloaded = null,
            shipped = held(OCTOBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
        )

        for (catalogues in listOf(downloadedNewer, shippedOnly)) {
            assertEquals(
                ActiveCity.NoLongerServed(PAU, pauWithdrawn()),
                catalogues.resolve(PAU, configuration(PAU)),
            )
            assertEquals(listOf(pauWithdrawn()), catalogues.withdrawnCities(setOf("lille", PAU)))
        }
    }

    @Test
    fun `a withdrawn network this build lacks is gone, and named`() {
        val catalogues = HeldCatalogues(
            downloaded = held(OCTOBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
            shipped = held(SEPTEMBER, cities = listOf("lille")),
        )

        assertEquals(
            ActiveCity.NoLongerServed(PAU, pauWithdrawn()),
            catalogues.resolve(PAU, configuration = null),
        )
    }

    @Test
    fun `a withdrawal only an older copy records does not outweigh a configuration`() {
        // The catalogue in force names the network nowhere: the configuration
        // decides, as it did before withdrawals were published.
        val catalogues = HeldCatalogues(
            downloaded = held(SEPTEMBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
            shipped = held(OCTOBER, cities = listOf("lille")),
        )
        val configuration = configuration(PAU)

        assertEquals(ActiveCity.Served(configuration), catalogues.resolve(PAU, configuration))
        assertEquals(emptyList<WithdrawnCity>(), catalogues.withdrawnCities(setOf("lille", PAU)))
    }

    @Test
    fun `a network the catalogue in force ignores is named by the other one`() {
        // The shipped catalogue was built with this release, which dropped
        // Pau; the copy downloaded since names it nowhere.
        val catalogues = HeldCatalogues(
            downloaded = held(OCTOBER, cities = listOf("lille")),
            shipped = held(SEPTEMBER, cities = listOf("lille"), withdrawn = listOf(PAU)),
        )

        assertEquals(
            ActiveCity.NoLongerServed(PAU, pauWithdrawn()),
            catalogues.resolve(PAU, configuration = null),
        )
        assertEquals(listOf(pauWithdrawn()), catalogues.withdrawnCities(setOf("lille")))
    }

    @Test
    fun `a network the other catalogue still served is named from its entry`() {
        val catalogues = HeldCatalogues(
            downloaded = held(SEPTEMBER, cities = listOf("lille", PAU)),
            shipped = held(OCTOBER, cities = listOf("lille")),
        )

        assertEquals(
            ActiveCity.NoLongerServed(PAU, pauWithdrawn()),
            catalogues.resolve(PAU, configuration = null),
        )
    }

    @Test
    fun `a network no catalogue names is gone, without a name`() {
        assertEquals(
            ActiveCity.NoLongerServed(PAU, withdrawal = null),
            onlyShipped().resolve(PAU, configuration = null),
        )
    }

    @Test
    fun `a network no catalogue names is served when this build carries it`() {
        val configuration = configuration(PAU)

        assertEquals(ActiveCity.Served(configuration), onlyShipped().resolve(PAU, configuration))
    }

    private fun onlyShipped() = HeldCatalogues(
        downloaded = null,
        shipped = held(OCTOBER, cities = listOf("lille")),
    )

    private fun pauWithdrawn() = WithdrawnCity(PAU, displayName = "IDEcycle", mainCity = "Pau")

    private fun configuration(id: String): CityConfiguration {
        val document = """
            {
              "configVersion": 1,
              "network": {
                "id": "$id", "displayName": "Example",
                "operator": "Example", "defaultLanguage": "en"
              },
              "gbfs": { "discoveryUrl": "https://example.org/gbfs.json" },
              "map": {
                "defaultCenterLatitude": 50.0, "defaultCenterLongitude": 3.0,
                "defaultZoom": 12.0, "minZoom": 10, "maxZoom": 16
              },
              "dataRelease": { "manifestUrl": "https://example.org/manifest.json" }
            }
        """.trimIndent()
        return (CityConfigurationReader.read(document) as Outcome.Success).value
    }

    private companion object {
        const val PAU = "idecycle"
        const val SEPTEMBER = "2026-09-28T09:00:00Z"
        const val OCTOBER = "2026-10-06T14:50:45Z"

        /**
         * A catalogue produced at [generatedAt], read the way the application
         * reads the files it holds.
         */
        fun held(
            generatedAt: String,
            cities: List<String>,
            withdrawn: List<String> = emptyList(),
        ): HeldCatalogue {
            val document = """
                {
                  "catalogueVersion": 1,
                  "generatedAt": "$generatedAt",
                  "cities": [${cities.joinToString(",") { city(it) }}],
                  "withdrawnCities": [${withdrawn.joinToString(",") { withdrawal(it) }}]
                }
            """.trimIndent()
            return HeldCatalogue(
                catalogue = (CityCatalogueReader.read(document) as Outcome.Success).value,
                withdrawnCities = CityCatalogueReader.readWithdrawn(document),
            )
        }

        fun city(id: String) = """
            {
              "id": "$id", "displayName": "${displayName(id)}", "mainCity": "${mainCity(id)}",
              "boundingBox": { "south": 43.2, "west": -0.5, "north": 43.4, "east": -0.2 },
              "gbfsDiscoveryUrl": "https://example.org/$id/gbfs.json",
              "manifestUrl": "https://example.org/$id/manifest.json"
            }
        """.trimIndent()

        fun withdrawal(id: String) = """
            {
              "id": "$id", "displayName": "${displayName(id)}",
              "mainCity": "${mainCity(id)}", "withdrawnOn": "2026-10-06"
            }
        """.trimIndent()

        fun displayName(id: String) = if (id ==
            PAU
        ) {
            "IDEcycle"
        } else {
            id.replaceFirstChar(Char::uppercase)
        }

        fun mainCity(id: String) = if (id == PAU) "Pau" else id.replaceFirstChar(Char::uppercase)
    }
}
