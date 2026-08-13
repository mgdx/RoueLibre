package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of reading a release manifest (SPEC §4.4).
 *
 * The document exercised is the one `tools/build_manifest.py` actually
 * produces: a divergence between the script and this reader would make updates
 * impossible with nothing to signal it.
 */
class DataManifestTest {

    private val document = """
        {
          "formatVersion": 2,
          "releaseTag": "data-2026-08",
          "generatedAt": "2026-08-09T11:25:03Z",
          "network": "vlille",
          "boundingBox": {
            "south": 50.576061, "west": 2.941358,
            "north": 50.763769, "east": 3.241396
          },
          "datasets": [
            {
              "id": "tiles",
              "description": "Fond de carte vectoriel",
              "files": [
                {
                  "name": "tiles.mbtiles",
                  "url": "https://example.org/data-2026-08/tiles.mbtiles",
                  "sizeBytes": 34992128,
                  "sha256": "96ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e62"
                }
              ]
            },
            {
              "id": "routing",
              "description": "Graphe de routage",
              "files": [
                {
                  "name": "E0_N50.rd5",
                  "url": "https://example.org/data-2026-08/E0_N50.rd5",
                  "sizeBytes": 1659390,
                  "sha256": "87c3857f87c3857f87c3857f87c3857f87c3857f87c3857f87c3857f87c3857f"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun manifest(): DataManifest =
        DataManifestReader.read(document).valueOrNull() ?: error("manifeste illisible")

    @Test
    fun `reads what the generation script produces`() {
        val manifest = manifest()

        assertEquals(2, manifest.formatVersion)
        assertEquals("data-2026-08", manifest.releaseTag)
        assertEquals("vlille", manifest.network)
        assertEquals(2, manifest.datasets.size)
        assertEquals(50.576061, manifest.boundingBox?.south ?: 0.0, 1e-6)
    }

    @Test
    fun `announces the total size, the one to show before downloading`() {
        assertEquals(34992128L + 1659390L, manifest().totalSizeBytes)
    }

    @Test
    fun `finds a set by its category`() {
        assertEquals(
            "tiles.mbtiles",
            manifest().datasetFor(DatasetKind.Tiles)?.files?.first()?.name,
        )
        assertNull(manifest().datasetFor(DatasetKind.Addresses))
    }

    @Test
    fun `an unknown set is ignored rather than fatal`() {
        // A more recent release may describe a set this version does not know;
        // that must not prevent updating the others.
        val withUnknown = document.replace("\"id\": \"routing\"", "\"id\": \"meteo\"")
        val manifest = DataManifestReader.read(withUnknown).valueOrNull()

        assertEquals(listOf(DatasetKind.Tiles), manifest?.datasets?.map { it.kind })
    }

    @Test
    fun `an unreadable manifest returns a failure, not an exception`() {
        assertTrue(DataManifestReader.read("{ this is not json") is Outcome.Failure)
    }

    @Test
    fun `a file name that is not a file name has the whole manifest refused`() {
        // This name becomes a path component on the device. A manifest naming
        // "../../elsewhere" would have the download land outside the directory
        // prepared for it, and the digest is no protection: whoever writes the
        // manifest supplies the content and the digest it is checked against.
        //
        // Refused whole, not ignored like an unknown set: a set this version
        // does not know is a later release doing its job, a name like this one
        // is a manifest that must not be acted on at all.
        for (name in listOf("../evil", "sous/dossier", "..", ".", "")) {
            val forged = document.replace("\"name\": \"tiles.mbtiles\"", "\"name\": \"$name\"")

            assertTrue(
                "the name \"$name\" should have been refused",
                DataManifestReader.read(forged) is Outcome.Failure,
            )
        }
    }

    @Test
    fun `an ordinary file name is still accepted`() {
        // The counterpart of the test above: the rule bears on what designates a
        // path, not on what a name looks like. A space or an accent is a
        // legitimate name, and tools/build_manifest.py may publish one.
        val accented = document.replace(
            "\"name\": \"tiles.mbtiles\"",
            "\"name\": \"fond é.mbtiles\"",
        )

        assertEquals(
            "fond é.mbtiles",
            DataManifestReader.read(accented).valueOrNull()
                ?.datasetFor(DatasetKind.Tiles)?.files?.first()?.name,
        )
    }

    @Test
    fun `a file announced without a usable digest has the manifest refused`() {
        // The digest is what the whole download rests on (SPEC §4.4): what
        // arrives is hashed and put against what was announced. Leaving it
        // optional left that verification to the discretion of whoever writes
        // the manifest — that is, of the one party it protects against.
        for (digest in listOf("", "96ba5e62", "zz".repeat(32), "96ba5e62 ")) {
            val forged = document.replace(
                "\"sha256\": \"$TILES_DIGEST\"",
                "\"sha256\": \"$digest\"",
            )

            assertTrue(
                "the digest \"$digest\" should have been refused",
                DataManifestReader.read(forged) is Outcome.Failure,
            )
        }
        // A field renamed is a field absent: the reader ignores what it does not
        // know, and an absent digest must be refused like an unusable one.
        val absent = document.replace("\"sha256\"", "\"digest\"")
        assertTrue(DataManifestReader.read(absent) is Outcome.Failure)
    }

    @Test
    fun `a set absent from the device is to be downloaded`() {
        val states = compareWithInstalled(manifest(), installedFingerprints = emptyMap())

        assertEquals(DatasetUpdate.Missing, states[DatasetKind.Tiles])
        assertEquals(DatasetUpdate.Missing, states[DatasetKind.Routing])
    }

    @Test
    fun `a set whose digest has not changed is not downloaded again`() {
        // That is the whole point of the manifest: refreshing the address
        // index must not force thirty-five megabytes of tiles to come again.
        val states = compareWithInstalled(
            manifest(),
            installedFingerprints = mapOf(
                DatasetKind.Tiles to TILES_DIGEST,
                DatasetKind.Routing to "0000",
            ),
        )

        assertEquals(DatasetUpdate.UpToDate, states[DatasetKind.Tiles])
        assertEquals(DatasetUpdate.Outdated, states[DatasetKind.Routing])
    }

    @Test
    fun `the digest's case changes nothing`() {
        val states = compareWithInstalled(
            manifest(),
            installedFingerprints = mapOf(DatasetKind.Tiles to TILES_DIGEST.uppercase()),
        )

        assertEquals(DatasetUpdate.UpToDate, states[DatasetKind.Tiles])
    }

    @Test
    fun `the digest of a multi-file set does not depend on their order`() {
        // The routing graph may hold several segments; the order the manifest
        // lists them in must not cause a needless re-download.
        val files = listOf(
            ManifestFile("b.rd5", "https://example.org/b", 1, "b".repeat(64)),
            ManifestFile("a.rd5", "https://example.org/a", 1, "a".repeat(64)),
        )
        val direct = ManifestDataset(DatasetKind.Routing, "", files)
        val reversed = ManifestDataset(DatasetKind.Routing, "", files.reversed())

        assertEquals(direct.fingerprint, reversed.fingerprint)
    }

    private companion object {
        /** The digest the fixture announces for the tiles. */
        const val TILES_DIGEST = "96ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e6296ba5e62"
    }
}
