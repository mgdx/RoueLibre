package io.github.mgdx.rouelibre.core.gbfs

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests de l'analyse des flux GBFS (SPEC §14).
 *
 * Les cas nommés « réels » s'appuient sur des captures de flux en production,
 * structure intacte, seule la liste des stations ayant été réduite : le réseau
 * lillois en GBFS 2.3, et Vélib' Métropole en GBFS 1.0. Les cas « v3 » sont
 * synthétiques : aucun réseau en GBFS 3.0 n'est nécessaire pour vérifier qu'on
 * sait le lire.
 *
 * Deux réseaux plutôt qu'un, parce que la promesse du SPEC §4.1 — « l'appli
 * fonctionne avec n'importe quel réseau GBFS du monde sans modification de
 * code » — ne se vérifie pas sur un seul producteur.
 */
class GbfsParserTest {

    private val parser = GbfsParser()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/gbfs/$name")) {
            "ressource de test absente : $name"
        }.bufferedReader().readText()

    private fun <T> assertSuccess(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw AssertionError("échec inattendu : ${outcome.error}")
    }

    // ---------------------------------------------------------- découverte --

    @Test
    fun `lit un document d'auto-decouverte GBFS 2 malgre sa cle de langue`() {
        // Le flux lillois imbrique ses flux sous « en » bien qu'il serve un
        // réseau français : la langue ne doit jamais être supposée.
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v2_real.json")))

        assertEquals("2.3", discovery.version)
        assertEquals(
            "https://media.ilevia.fr/opendata/station_information.json",
            assertSuccess(discovery.urlOf(GbfsFeedNames.STATION_INFORMATION)),
        )
        assertEquals(
            "https://media.ilevia.fr/opendata/station_status.json",
            assertSuccess(discovery.urlOf(GbfsFeedNames.STATION_STATUS)),
        )
    }

    @Test
    fun `lit un document d'auto-decouverte GBFS 3 sans cle de langue`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v3.json")))

        assertEquals("3.0", discovery.version)
        assertEquals(3, discovery.feedUrlsByName.size)
        assertEquals(
            "https://example.invalid/gbfs/3/station_status.json",
            assertSuccess(discovery.urlOf(GbfsFeedNames.STATION_STATUS)),
        )
    }

    @Test
    fun `signale precisement un flux absent de l'auto-decouverte`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v3.json")))

        val outcome = discovery.urlOf("free_bike_status")

        assertEquals(
            Outcome.Failure(DataError.FeedUnavailable("free_bike_status")),
            outcome,
        )
    }

    @Test
    fun `refuse un document d'auto-decouverte sans liste de flux`() {
        val outcome = parser.parseDiscovery("""{"version":"2.3","data":{}}""")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    @Test
    fun `refuse un document qui n'est pas du JSON`() {
        val outcome = parser.parseDiscovery("<html>503 Service Unavailable</html>")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    // ------------------------------------------------------------ stations --

    @Test
    fun `lit les stations du flux reel`() {
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v2_real.json")),
        )

        assertEquals(25, feed.stations.size)
        assertEquals("2.3", feed.version)

        val first = feed.stations.first { it.id == "1" }
        assertEquals("Metropole Europeenne de Lille (CB)", first.name)
        assertEquals(50.641926, first.position.latitude, 1e-6)
        assertEquals(3.075992, first.position.longitude, 1e-6)
        assertEquals(36, first.capacity)
        assertEquals("59000", first.postalCode)
    }

    @Test
    fun `lit un nom de station traduit du GBFS 3`() {
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        val station = feed.stations.first { it.id == "v3-1" }
        assertEquals("Place du Théâtre", station.name)
    }

    @Test
    fun `ecarte une station sans position plutot que de rejeter tout le flux`() {
        // Une seule entrée fautive chez le producteur ne doit pas priver
        // l'utilisateur de toutes les autres.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        assertEquals(2, feed.stations.size)
        assertNull(feed.stations.firstOrNull { it.id == "v3-invalid-coordinates" })
    }

    @Test
    fun `lit un horodatage entier POSIX comme un horodatage RFC 3339`() {
        val fromEpoch = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v2_real.json")),
        )
        val fromText = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        assertNotNull(fromEpoch.lastUpdated)
        assertEquals(Instant.parse("2026-08-09T10:02:00Z"), fromText.lastUpdated)
    }

    // --------------------------------------------------------- disponibilité --

    @Test
    fun `lit l'etat des stations du flux reel`() {
        val feed = assertSuccess(
            parser.parseStationStatus(fixture("station_status_v2_real.json")),
        )

        assertEquals(24, feed.availabilities.size)
        val station = feed.availabilities.first { it.stationId == "10" }
        assertEquals(6, station.bikesAvailable)
        assertEquals(26, station.docksAvailable)
        assertTrue(station.isInstalled)
        assertTrue(station.canLendBike)
        assertTrue(station.canAcceptBike)
        assertEquals(Instant.ofEpochSecond(1_786_264_892), station.reportedAt)
    }

    @Test
    fun `lit le champ renomme par le GBFS 3`() {
        // GBFS 3.0 remplace num_bikes_available par num_vehicles_available.
        val feed = assertSuccess(parser.parseStationStatus(fixture("station_status_v3.json")))

        assertEquals(7, feed.availabilities.first { it.stationId == "v3-1" }.bikesAvailable)
    }

    @Test
    fun `une station qui ne loue plus ne peut pas preter de velo`() {
        val feed = assertSuccess(parser.parseStationStatus(fixture("station_status_v3.json")))

        val closed = feed.availabilities.first { it.stationId == "v3-2" }
        assertTrue(closed.isInstalled)
        assertTrue(!closed.canLendBike)
        assertTrue(closed.canAcceptBike)
    }

    @Test
    fun `accepte des drapeaux publies en entiers`() {
        val document = """
            {"last_updated":1786264920,"ttl":0,"version":"2.3","data":{"stations":[
              {"station_id":"a","num_bikes_available":3,"num_docks_available":5,
               "is_installed":1,"is_renting":1,"is_returning":0,
               "last_reported":1786264900}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseStationStatus(document))

        val station = feed.availabilities.single()
        assertTrue(station.isInstalled)
        assertTrue(station.isRenting)
        assertTrue(!station.isReturning)
    }

    @Test
    fun `ramene un compte negatif a zero`() {
        // Afficher « -1 vélo » serait pire que d'afficher zéro.
        val document = """
            {"version":"2.3","data":{"stations":[
              {"station_id":"a","num_bikes_available":-1,"num_docks_available":-4,
               "is_installed":true,"is_renting":true,"is_returning":true}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseStationStatus(document))

        val station = feed.availabilities.single()
        assertEquals(0, station.bikesAvailable)
        assertEquals(0, station.docksAvailable)
    }

    @Test
    fun `tolere un flux sans horodatage`() {
        val document = """{"version":"2.3","data":{"stations":[]}}"""

        val feed = assertSuccess(parser.parseStationStatus(document))

        assertNull(feed.lastUpdated)
        assertTrue(feed.availabilities.isEmpty())
    }

    @Test
    fun `ignore les champs inconnus d'un flux enrichi`() {
        // Les producteurs ajoutent régulièrement des champs ; cela ne doit
        // jamais faire échouer la lecture.
        val document = """
            {"version":"2.3","data":{"stations":[
              {"station_id":"a","num_bikes_available":2,"num_docks_available":2,
               "is_installed":true,"is_renting":true,"is_returning":true,
               "champ_maison":{"quelconque":[1,2,3]}}
            ]}}
        """.trimIndent()

        assertEquals(1, assertSuccess(parser.parseStationStatus(document)).availabilities.size)
    }

    @Test
    fun `refuse un horodatage illisible`() {
        val document = """
            {"last_updated":"pas une date","version":"2.3","data":{"stations":[]}}
        """.trimIndent()

        val outcome = parser.parseStationStatus(document)

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    @Test
    fun `un flux vide reste un succes et non une erreur`() {
        // Réseau en maintenance : zéro station est une information, pas une
        // panne. L'interface doit pouvoir le dire calmement.
        val outcome = parser.parseStationInformation("""{"version":"2.3","data":{"stations":[]}}""")

        assertEquals(emptyList<Any>(), outcome.valueOrNull()?.stations)
    }

    // ------------------------------------------------- GBFS 1.0, Vélib' --

    @Test
    fun `lit le document d'auto-decouverte de Velib en GBFS 1 point 0`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v1_velib.json")))

        assertNotNull(discovery.feedUrlsByName["station_information"])
        assertNotNull(discovery.feedUrlsByName["station_status"])
    }

    @Test
    fun `accepte un identifiant de station publie en nombre`() {
        // Vélib' publie « "station_id": 213688169 » là où le format impose une
        // chaîne. Le refuser rendrait le plus grand réseau de France — mille
        // cinq cents stations — entièrement inexploitable.
        val information = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v1_velib.json")),
        )

        assertEquals(3, information.stations.size)
        assertEquals("213688169", information.stations.first().id)
        assertEquals("Benjamin Godard - Victor Hugo", information.stations.first().name)
        assertEquals(35, information.stations.first().capacity)
    }

    @Test
    fun `accepte des drapeaux publies en zero et un`() {
        val status = assertSuccess(
            parser.parseStationStatus(fixture("station_status_v1_velib.json")),
        )

        val first = status.availabilities.first { it.stationId == "213688169" }
        assertTrue(first.isInstalled)
        assertTrue(first.isRenting)
        assertTrue(first.isReturning)
    }

    @Test
    fun `les deux flux de Velib se rejoignent sur le meme identifiant`() {
        // Ce qui compte n'est pas de lire chaque flux, mais que la jointure
        // tienne : un identifiant lu « 213688169 » d'un côté et « 2.13688169E8 »
        // de l'autre ne rapprocherait aucune station de son état.
        val information = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v1_velib.json")),
        )
        val status = assertSuccess(
            parser.parseStationStatus(fixture("station_status_v1_velib.json")),
        )

        val connues = information.stations.map { it.id }.toSet()
        val etats = status.availabilities.map { it.stationId }.toSet()
        assertEquals(connues, etats)
    }
}
