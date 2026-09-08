package io.github.mgdx.rouelibre.ui.storage

import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.ManifestFile
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a manifest has to say about itself before anything is fetched on its
 * word (SPEC §4.4, §15).
 *
 * The manifest is fetched from an address the city configuration holds, and
 * nothing checked that what came back describes that city: a host serving
 * Stirling's address with Lille's release would have dropped Lille's map and
 * Lille's addresses into Stirling's folder, without a word. What is left
 * afterwards is a map of somewhere else and a search that finds nothing, with
 * no way to guess why.
 *
 * The refusal is decided on the values alone, which is what makes "nothing is
 * downloaded" a property of the rule rather than of the order the view model
 * happens to do things in: a refused manifest never reaches the state, and the
 * download reads the state.
 */
class ManifestAcceptanceTest {

    private companion object {
        /** The format version this build of the application reads. */
        const val SUPPORTED = 2

        /** A real published identifier, copied from a real published manifest. */
        const val SERVED = "nextbike-stirling"
    }

    /**
     * A manifest as `tools/build_manifest.py` writes it.
     *
     * The values are those of the release published for Stirling, digest
     * included: what is exercised has to be the shape the generator produces,
     * not one invented for the test.
     */
    private fun manifest(network: String = SERVED, formatVersion: Int = SUPPORTED) = DataManifest(
        formatVersion = formatVersion,
        releaseTag = "data-2026-08-gb",
        generatedAt = "2026-08-23T07:53:44Z",
        network = network,
        boundingBox = BoundingBox(
            south = 56.092045,
            west = -4.005579,
            north = 56.183656,
            east = -3.858234,
        ),
        datasets = listOf(
            ManifestDataset(
                kind = DatasetKind.Tiles,
                description = "Vector base map",
                files = listOf(
                    ManifestFile(
                        name = "tiles.mbtiles",
                        url = "https://example.invalid/nextbike-stirling-tiles.mbtiles",
                        sizeBytes = 3_174_400,
                        sha256 = "3bc421bf3a29e988e724e6370f46612b9" +
                            "98cd4bb319fb6f28a8512e0fa578526",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `the published manifest of the city in service is acted on`() {
        assertNull(manifest().refusalFor(SUPPORTED, SERVED))
    }

    @Test
    fun `a manifest describing another network is refused`() {
        val refusal = manifest(network = "velib-paris").refusalFor(SUPPORTED, SERVED)

        assertEquals(StorageMessage.OtherNetwork("velib-paris", SERVED), refusal)
    }

    /**
     * And refused in its own words: inviting an update, as an unreadable format
     * does, would send the reader after a fault no version of the application
     * can mend.
     */
    @Test
    fun `the refusal is not the one written for an unreadable format`() {
        val refusal = manifest(network = "velib-paris").refusalFor(SUPPORTED, SERVED)

        assertTrue("$refusal", refusal is StorageMessage.OtherNetwork)
    }

    /**
     * The identifiers are written in lower case throughout, so no two cities
     * differ by their case alone: refusing a release over a capital letter
     * would be refusing it for something that says nothing about what it holds.
     */
    @Test
    fun `the comparison ignores the case`() {
        assertNull(manifest(network = "Nextbike-Stirling").refusalFor(SUPPORTED, SERVED))
    }

    /**
     * The field defaults to the empty string, so a release published before it
     * was written names nobody rather than naming somebody else. An
     * installation that works must not stop working over a missing line: what
     * is refused is a manifest naming *another* network.
     */
    @Test
    fun `a manifest naming no network at all is not refused`() {
        assertNull(manifest(network = "").refusalFor(SUPPORTED, SERVED))
    }

    /** With no city in service there is nothing to hold the manifest against. */
    @Test
    fun `without a city in service the network is not held against anything`() {
        assertNull(manifest(network = "velib-paris").refusalFor(SUPPORTED, servedNetwork = null))
    }

    /** The format stays the first thing answered: it is the older refusal. */
    @Test
    fun `an unreadable format is refused before the network is looked at`() {
        val refusal = manifest(network = "velib-paris", formatVersion = 1)
            .refusalFor(SUPPORTED, SERVED)

        assertEquals(StorageMessage.UnsupportedFormat(1, SUPPORTED), refusal)
    }
}
