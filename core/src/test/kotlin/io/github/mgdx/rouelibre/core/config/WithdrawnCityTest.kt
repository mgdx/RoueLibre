package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests of how the catalogue reads the networks it withdraws (SPEC §15.1).
 * What a chosen city resolves to, between the two catalogues a device holds,
 * is [HeldCataloguesTest]'s.
 *
 * What matters most is held by the first two: the catalogue's cities read the
 * same with the withdrawals as without them, which is what an older build
 * reading a newer catalogue relies on; and a catalogue without them withdraws
 * nothing, which is what a newer build reading an older one relies on.
 */
class WithdrawnCityTest {

    @Test
    fun `the withdrawals change nothing in the cities a catalogue lists`() {
        val without = CityCatalogueReader.read(catalogue(withdrawals = null))
        val with = CityCatalogueReader.read(catalogue(withdrawals = PAU_WITHDRAWN))

        assertEquals(without, with)
        assertEquals(listOf("lille"), (with as Outcome.Success).value.cities.map { it.id })
    }

    @Test
    fun `a catalogue published before the withdrawals withdraws nothing`() {
        assertEquals(emptyList<WithdrawnCity>(), CityCatalogueReader.readWithdrawn(catalogue(null)))
    }

    @Test
    fun `a withdrawal is read with the name it last had`() {
        assertEquals(
            listOf(WithdrawnCity(id = "idecycle", displayName = "IDEcycle", mainCity = "Pau")),
            CityCatalogueReader.readWithdrawn(catalogue(PAU_WITHDRAWN)),
        )
    }

    @Test
    fun `an unusable withdrawal is dropped alone`() {
        val withdrawals = """
            [
              { "id": "../idecycle", "displayName": "IDEcycle", "withdrawnOn": "2026-10-06" },
              { "id": "tarnowski-rower-miejski", "displayName": " ", "withdrawnOn": "2026-10-06" },
              { "id": "donkey-prignitz", "displayName": "Donkey Prignitz", "mainCity": "" }
            ]
        """.trimIndent()

        assertEquals(
            listOf(WithdrawnCity("donkey-prignitz", "Donkey Prignitz", mainCity = null)),
            CityCatalogueReader.readWithdrawn(catalogue(withdrawals)),
        )
    }

    @Test
    fun `an unreadable catalogue withdraws nothing`() {
        assertEquals(emptyList<WithdrawnCity>(), CityCatalogueReader.readWithdrawn("{ truncated"))
        assertEquals(
            emptyList<WithdrawnCity>(),
            CityCatalogueReader.readWithdrawn(catalogue("\"not a list\"")),
        )
    }

    @Test
    fun `the published catalogue never lists a withdrawn network among its cities`() {
        // The one rule that keeps every build in the field safe: a build older
        // than the withdrawals sees only the cities, and would offer one of
        // these networks again if it stood among them.
        val document = File(checkNotNull(System.getProperty("rouelibre.cityCatalogue"))).readText()
        val withdrawn = CityCatalogueReader.readWithdrawn(document).map { it.id }
        val cities = (CityCatalogueReader.read(document) as Outcome.Success).value.cities

        assertTrue("idecycle is not listed as withdrawn", "idecycle" in withdrawn)
        assertEquals(emptySet<String>(), cities.map { it.id }.toSet() intersect withdrawn.toSet())
    }

    private companion object {
        val PAU_WITHDRAWN = """
            [
              {
                "id": "idecycle", "displayName": "IDEcycle",
                "mainCity": "Pau", "withdrawnOn": "2026-10-06"
              }
            ]
        """.trimIndent()

        /** A catalogue of one city, with [withdrawals] as its withdrawn list if any. */
        fun catalogue(withdrawals: String?): String {
            val withdrawn = withdrawals?.let { ""","withdrawnCities": $it""" }.orEmpty()
            return """
                {
                  "catalogueVersion": 1,
                  "cities": [
                    {
                      "id": "lille", "displayName": "V'lille", "mainCity": "Lille",
                      "boundingBox": { "south": 50.5, "west": 2.9, "north": 50.8, "east": 3.3 },
                      "gbfsDiscoveryUrl": "https://example.org/gbfs.json",
                      "manifestUrl": "https://example.org/manifest.json"
                    }
                  ]$withdrawn
                }
            """.trimIndent()
        }
    }
}
