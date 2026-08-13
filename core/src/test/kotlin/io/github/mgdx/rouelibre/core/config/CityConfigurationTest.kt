package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests of the city configuration reader.
 *
 * The first two replay the configurations the tools actually write, and not an
 * example composed for the occasion: what is verified is that the scripts and
 * the application agree on the one file that holds everything specific to a
 * conurbation (SPEC §15).
 */
class CityConfigurationTest {

    @Test
    fun `every configuration the tools write is readable`() {
        val configurations = publishedConfigurations()

        assertTrue("no configuration to read", configurations.isNotEmpty())
        configurations.forEach { (name, configuration) ->
            assertTrue(
                "network without an identifier in $name",
                configuration.network.id.isNotBlank(),
            )
            assertTrue("feed missing in $name", configuration.gbfs.discoveryUrl.isNotBlank())
        }
    }

    @Test
    fun `a city whose feed declares pedal-assist bikes says so`() {
        // The bolt on the bike glyphs (SPEC §7) hangs on this single field, and
        // tools/read_fleet.py writes it from the network's own vehicle_types
        // feed. At least one served network lends electric bikes and at least
        // one does not: a reader silently answering "false" everywhere would
        // pass every other test in this file.
        val fleets = publishedConfigurations().map { it.second.fleet.hasElectricBikes }

        assertTrue("no city declares pedal-assist bikes", fleets.any { it })
        assertTrue("no city declares a mechanical fleet", fleets.any { !it })
    }

    @Test
    fun `a configuration saying nothing of its fleet is read as mechanical`() {
        // What a network whose feed declares no vehicle type leaves behind, and
        // what every configuration written before the fleet was ever read looks
        // like. The plain bike is then drawn: nobody verified a motor.
        val outcome = CityConfigurationReader.read(MINIMAL_CONFIGURATION)

        val configuration = (outcome as Outcome.Success).value
        assertFalse(configuration.fleet.hasElectricBikes)
        assertEquals("example", configuration.network.id)
    }

    private fun publishedConfigurations(): List<Pair<String, CityConfiguration>> {
        val directory = checkNotNull(System.getProperty("rouelibre.cityConfigurations")) {
            "configuration directory not supplied by the build"
        }
        return File(directory).listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                val outcome = CityConfigurationReader.read(file.readText())
                file.name to (outcome as Outcome.Success).value
            }
    }

    private companion object {
        val MINIMAL_CONFIGURATION = """
            {
              "configVersion": 1,
              "network": {
                "id": "example",
                "displayName": "Example",
                "operator": "Example",
                "defaultLanguage": "en"
              },
              "gbfs": { "discoveryUrl": "https://example.org/gbfs.json" },
              "map": {
                "defaultCenterLatitude": 50.0,
                "defaultCenterLongitude": 3.0,
                "defaultZoom": 12.0,
                "minZoom": 10,
                "maxZoom": 16
              },
              "dataRelease": { "manifestUrl": "https://example.org/manifest.json" }
            }
        """.trimIndent()
    }
}
