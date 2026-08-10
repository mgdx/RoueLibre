package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lecture d'un manifeste de publication (SPEC §4.4).
 *
 * Le document éprouvé est celui que `tools/build_manifest.py` produit
 * réellement : une divergence entre le script et cette lecture rendrait les
 * mises à jour impossibles sans que rien ne le signale.
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
                  "sha256": "96ba5e62"
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
                  "sha256": "87c3857f"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun manifest(): DataManifest =
        DataManifestReader.read(document).valueOrNull() ?: error("manifeste illisible")

    @Test
    fun `lit ce que le script de génération produit`() {
        val manifest = manifest()

        assertEquals(2, manifest.formatVersion)
        assertEquals("data-2026-08", manifest.releaseTag)
        assertEquals("vlille", manifest.network)
        assertEquals(2, manifest.datasets.size)
        assertEquals(50.576061, manifest.boundingBox?.south ?: 0.0, 1e-6)
    }

    @Test
    fun `annonce la taille totale, celle qu'il faut montrer avant de télécharger`() {
        assertEquals(34992128L + 1659390L, manifest().totalSizeBytes)
    }

    @Test
    fun `retrouve un jeu par sa catégorie`() {
        assertEquals(
            "tiles.mbtiles",
            manifest().datasetFor(DatasetKind.Tiles)?.files?.first()?.name,
        )
        assertNull(manifest().datasetFor(DatasetKind.Addresses))
    }

    @Test
    fun `un jeu inconnu est ignoré plutôt que fatal`() {
        // Une publication plus récente peut décrire un jeu que cette version
        // ne connaît pas ; cela ne doit pas empêcher de mettre à jour les
        // autres.
        val withUnknown = document.replace("\"id\": \"routing\"", "\"id\": \"meteo\"")
        val manifest = DataManifestReader.read(withUnknown).valueOrNull()

        assertEquals(listOf(DatasetKind.Tiles), manifest?.datasets?.map { it.kind })
    }

    @Test
    fun `un manifeste illisible rend un échec, pas une exception`() {
        assertTrue(DataManifestReader.read("{ ceci n'est pas du json") is Outcome.Failure)
    }

    @Test
    fun `un jeu absent de l'appareil est à télécharger`() {
        val states = compareWithInstalled(manifest(), installedFingerprints = emptyMap())

        assertEquals(DatasetUpdate.Missing, states[DatasetKind.Tiles])
        assertEquals(DatasetUpdate.Missing, states[DatasetKind.Routing])
    }

    @Test
    fun `un jeu dont l'empreinte n'a pas changé n'est pas retéléchargé`() {
        // C'est tout l'intérêt du manifeste : rafraîchir l'index d'adresses ne
        // doit pas imposer de reprendre trente-cinq mégaoctets de tuiles.
        val states = compareWithInstalled(
            manifest(),
            installedFingerprints = mapOf(
                DatasetKind.Tiles to "96ba5e62",
                DatasetKind.Routing to "0000",
            ),
        )

        assertEquals(DatasetUpdate.UpToDate, states[DatasetKind.Tiles])
        assertEquals(DatasetUpdate.Outdated, states[DatasetKind.Routing])
    }

    @Test
    fun `la casse de l'empreinte ne change rien`() {
        val states = compareWithInstalled(
            manifest(),
            installedFingerprints = mapOf(DatasetKind.Tiles to "96BA5E62"),
        )

        assertEquals(DatasetUpdate.UpToDate, states[DatasetKind.Tiles])
    }

    @Test
    fun `l'empreinte d'un jeu à plusieurs fichiers ne dépend pas de leur ordre`() {
        // Le graphe de routage peut compter plusieurs segments ; l'ordre dans
        // lequel le manifeste les liste ne doit pas provoquer un
        // retéléchargement inutile.
        val files = listOf(
            ManifestFile("b.rd5", "https://example.org/b", 1, "bbbb"),
            ManifestFile("a.rd5", "https://example.org/a", 1, "aaaa"),
        )
        val direct = ManifestDataset(DatasetKind.Routing, "", files)
        val reversed = ManifestDataset(DatasetKind.Routing, "", files.reversed())

        assertEquals(direct.fingerprint, reversed.fingerprint)
    }
}
