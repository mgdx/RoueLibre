package io.github.mgdx.rouelibre.core.gbfs

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests of GBFS feed parsing (SPEC §14).
 *
 * The cases called "real" rest on captures of production feeds, structure
 * intact, only the station list having been shortened: the Lille network in
 * GBFS 2.3, and Vélib' Métropole in GBFS 1.0. The "v3" cases are synthetic: no
 * network on GBFS 3.0 is needed to check that we can read it.
 *
 * Two networks rather than one, because the promise of SPEC §4.1 — "the
 * application works with any GBFS network in the world without a code change" —
 * cannot be verified against a single producer.
 */
class GbfsParserTest {

    private val parser = GbfsParser()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/gbfs/$name")) {
            "ressource de test absente : $name"
        }.bufferedReader().readText()

    private fun <T> assertSuccess(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw AssertionError("unexpected failure: ${outcome.error}")
    }

    // ----------------------------------------------------------- discovery --

    @Test
    fun `reads a GBFS 2 auto-discovery document despite its language key`() {
        // The Lille feed nests its feeds under "en" although it serves a French
        // network: the language must never be assumed.
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
    fun `reads a GBFS 3 auto-discovery document without a language key`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v3.json")))

        assertEquals("3.0", discovery.version)
        assertEquals(3, discovery.feedUrlsByName.size)
        assertEquals(
            "https://example.invalid/gbfs/3/station_status.json",
            assertSuccess(discovery.urlOf(GbfsFeedNames.STATION_STATUS)),
        )
    }

    @Test
    fun `reports precisely which feed the auto-discovery lacks`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v3.json")))

        val outcome = discovery.urlOf("free_bike_status")

        assertEquals(
            Outcome.Failure(DataError.FeedUnavailable("free_bike_status")),
            outcome,
        )
    }

    @Test
    fun `refuses an auto-discovery document without a feed list`() {
        val outcome = parser.parseDiscovery("""{"version":"2.3","data":{}}""")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    @Test
    fun `refuses a document that is not JSON`() {
        val outcome = parser.parseDiscovery("<html>503 Service Unavailable</html>")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    // ------------------------------------------------------------ stations --

    @Test
    fun `reads the stations of the real feed`() {
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
    fun `reads a translated station name from GBFS 3`() {
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        val station = feed.stations.first { it.id == "v3-1" }
        assertEquals("Place du Théâtre", station.name)
    }

    @Test
    fun `strips the blanks a network publishes around a station name`() {
        // V'lille publishes "4 vents " with its trailing space, which reached
        // the title of the station's sheet and its spoken label — "4 vents ,
        // 8 bikes" — with the comma detached from the word.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_blanks.json")),
        )

        val station = feed.stations.first { it.id == "blanks-plain" }
        assertEquals("4 vents", station.name)
        assertEquals("59000", station.postalCode)
    }

    @Test
    fun `strips the blanks around a translated name too`() {
        // The same liberty, taken inside a GBFS 3 label: the trimming belongs
        // to the reading of the name and not to one of its two encodings.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_blanks.json")),
        )

        val station = feed.stations.first { it.id == "blanks-translated" }
        assertEquals("Place du Théâtre", station.name)
    }

    @Test
    fun `a name that was nothing but blanks falls back on the street`() {
        // The station is kept, unlike one without a position: it is real and it
        // holds bikes, and taking it off the map because its network mistyped a
        // string leaves whoever stands in front of it with no explanation. The
        // street the feed publishes is the best name at hand.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_blanks.json")),
        )

        val station = feed.stations.first { it.id == "blanks-only-with-a-street" }
        assertEquals("12 Rue Nationale", station.name)
    }

    @Test
    fun `a station with neither name nor street is kept for the layer above to name`() {
        // No name is invented here: naming it takes a sentence in the reader's
        // language, which this module holds none of (SPEC §14). What matters is
        // that the station is still in the feed — GbfsRemoteSource names it.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_blanks.json")),
        )

        assertEquals(4, feed.stations.size)
        val station = feed.stations.first { it.id == "blanks-only" }
        assertEquals("", station.name)
    }

    @Test
    fun `drops a station without a position rather than rejecting the whole feed`() {
        // A single faulty entry on the producer's side must not deprive the
        // user of all the others.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        assertEquals(2, feed.stations.size)
        assertNull(feed.stations.firstOrNull { it.id == "v3-invalid-coordinates" })
    }

    @Test
    fun `a capacity the document itemises against is not the one kept`() {
        // ABANDO, in Bilbao, exactly as its producer publishes it: "capacity"
        // 822, while the "vehicle_docks_capacity" beside it counts 22 — and
        // the station's own status counts nine bikes and thirteen free spaces.
        // The station sheet used to write "822 docking points" under them.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3_bilbao.json")),
        )

        val abando = feed.stations.first { it.id == "25" }
        assertEquals("ABANDO", abando.name)
        assertEquals(22, abando.capacity)
    }

    @Test
    fun `a capacity nothing contradicts is kept as published`() {
        // The other two hundred and ninety-one networks, which itemise nothing:
        // their figure must come through untouched.
        val feed = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v2_real.json")),
        )

        assertEquals(36, feed.stations.first { it.id == "1" }.capacity)
    }

    @Test
    fun `reads a POSIX integer timestamp as well as an RFC 3339 one`() {
        val fromEpoch = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v2_real.json")),
        )
        val fromText = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v3.json")),
        )

        assertNotNull(fromEpoch.lastUpdated)
        assertEquals(Instant.parse("2026-08-09T10:02:00Z"), fromText.lastUpdated)
    }

    // ------------------------------------------------------- availability --

    @Test
    fun `reads the station state of the real feed`() {
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
    fun `reads the field GBFS 3 renamed`() {
        // GBFS 3.0 replaces num_bikes_available with num_vehicles_available.
        val feed = assertSuccess(parser.parseStationStatus(fixture("station_status_v3.json")))

        assertEquals(7, feed.availabilities.first { it.stationId == "v3-1" }.bikesAvailable)
    }

    @Test
    fun `reads the standard breakdown by vehicle type`() {
        val feed = assertSuccess(parser.parseStationStatus(fixture("station_status_v3.json")))

        val station = feed.availabilities.first { it.stationId == "v3-1" }
        assertEquals(mapOf("bike" to 5, "ebike" to 2), station.bikesByVehicleType)
    }

    @Test
    fun `reads the breakdown Velib publishes in its own way`() {
        // GBFS 1.0 has no vehicle_types feed to point identifiers at, so the
        // network names the kinds inline. Refusing that shape would hide the
        // electric bikes of the largest network in France.
        val feed = assertSuccess(
            parser.parseStationStatus(fixture("station_status_v1_velib.json")),
        )

        val station = feed.availabilities.first { it.stationId == "213688169" }
        assertEquals(mapOf("mechanical" to 16, "ebike" to 1), station.bikesByVehicleType)
    }

    @Test
    fun `a feed publishing no breakdown leaves it empty rather than guessing`() {
        val document = """
            {"last_updated":1786264920,"ttl":60,"version":"2.0","data":{"stations":[
              {"station_id":"a","num_bikes_available":3,"num_docks_available":5}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseStationStatus(document))

        assertTrue(feed.availabilities.single().bikesByVehicleType.isEmpty())
    }

    @Test
    fun `a station that no longer rents cannot lend a bike`() {
        val feed = assertSuccess(parser.parseStationStatus(fixture("station_status_v3.json")))

        val closed = feed.availabilities.first { it.stationId == "v3-2" }
        assertTrue(closed.isInstalled)
        assertTrue(!closed.canLendBike)
        assertTrue(closed.canAcceptBike)
    }

    @Test
    fun `accepts flags published as integers`() {
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
    fun `brings a negative count back to zero`() {
        // Showing "-1 bike" would be worse than showing zero.
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
    fun `tolerates a feed without a timestamp`() {
        val document = """{"version":"2.3","data":{"stations":[]}}"""

        val feed = assertSuccess(parser.parseStationStatus(document))

        assertNull(feed.lastUpdated)
        assertTrue(feed.availabilities.isEmpty())
    }

    @Test
    fun `ignores the unknown fields of an enriched feed`() {
        // Producers add fields regularly; that must never make the read
        // fail.
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
    fun `refuses an unreadable timestamp`() {
        val document = """
            {"last_updated":"not a date","version":"2.3","data":{"stations":[]}}
        """.trimIndent()

        val outcome = parser.parseStationStatus(document)

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    @Test
    fun `an empty feed stays a success and not an error`() {
        // A network under maintenance: zero stations is information, not a
        // breakdown. The interface must be able to say so calmly.
        val outcome = parser.parseStationInformation("""{"version":"2.3","data":{"stations":[]}}""")

        assertEquals(emptyList<Any>(), outcome.valueOrNull()?.stations)
    }

    // ------------------------------------------------- GBFS 1.0, Vélib' --

    @Test
    fun `reads Velib's auto-discovery document in GBFS 1 point 0`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v1_velib.json")))

        assertNotNull(discovery.feedUrlsByName["station_information"])
        assertNotNull(discovery.feedUrlsByName["station_status"])
    }

    @Test
    fun `accepts a station identifier published as a number`() {
        // Vélib' publishes "station_id": 213688169 where the format mandates a
        // string. Refusing it would make the largest network in France —
        // fifteen hundred stations — entirely unusable.
        val information = assertSuccess(
            parser.parseStationInformation(fixture("station_information_v1_velib.json")),
        )

        assertEquals(3, information.stations.size)
        assertEquals("213688169", information.stations.first().id)
        assertEquals("Benjamin Godard - Victor Hugo", information.stations.first().name)
        assertEquals(35, information.stations.first().capacity)
    }

    @Test
    fun `accepts flags published as zero and one`() {
        val status = assertSuccess(
            parser.parseStationStatus(fixture("station_status_v1_velib.json")),
        )

        val first = status.availabilities.first { it.stationId == "213688169" }
        assertTrue(first.isInstalled)
        assertTrue(first.isRenting)
        assertTrue(first.isReturning)
    }

    @Test
    fun `Velib's two feeds meet on the same identifier`() {
        // What matters is not reading each feed but that the join holds: an
        // identifier read as "213688169" on one side and "2.13688169E8" on the
        // other would bring no station together with its state.
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

    // --------------------------------------------------- the vehicle types --

    @Test
    fun `sorts the declared types into the three kinds`() {
        val document = """
            {"last_updated":1786264920,"ttl":0,"version":"2.3","data":{"vehicle_types":[
              {"vehicle_type_id":"346","form_factor":"bicycle","propulsion_type":"human"},
              {"vehicle_type_id":"348","form_factor":"bicycle",
               "propulsion_type":"electric_assist"},
              {"vehicle_type_id":"cargo","form_factor":"cargo_bicycle",
               "propulsion_type":"human"},
              {"vehicle_type_id":"trot","form_factor":"scooter",
               "propulsion_type":"electric"}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleTypes(document))

        assertEquals(
            mapOf(
                "346" to VehicleKind.Mechanical,
                "348" to VehicleKind.Electric,
                "cargo" to VehicleKind.Mechanical,
                // An electric SCOOTER says nothing about the network's bikes,
                // and the status feed counts it alongside them.
                "trot" to VehicleKind.Other,
            ),
            feed.kinds,
        )
        assertTrue(feed.declaresElectricBikes)
    }

    @Test
    fun `a throttle bicycle is still a bike one does not pedal alone`() {
        val document = """
            {"version":"3.0","data":{"vehicle_types":[
              {"vehicle_type_id":"e","form_factor":"bicycle","propulsion_type":"electric"}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleTypes(document))

        assertEquals(mapOf("e" to VehicleKind.Electric), feed.kinds)
    }

    @Test
    fun `a type declaring nothing falls to the plain bike`() {
        // The bolt is what has to be earned: a producer omitting both fields
        // must not be read as lending pedal-assist bikes.
        val document = """
            {"version":"2.3","data":{"vehicle_types":[{"vehicle_type_id":"bike"}]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleTypes(document))

        assertEquals(mapOf("bike" to VehicleKind.Other), feed.kinds)
        assertFalse(feed.declaresElectricBikes)
    }

    @Test
    fun `an empty declaration is read, not refused`() {
        val document = """{"version":"2.3","data":{"vehicle_types":[]}}"""

        val feed = assertSuccess(parser.parseVehicleTypes(document))

        assertTrue(feed.kinds.isEmpty())
        assertFalse(feed.declaresElectricBikes)
    }

    @Test
    fun `reads how far each type goes, and which types are cargo bikes`() {
        // Vienna's shape: nextbike declares max_range_meters as 0 on its own
        // electric type and a real figure on the city's, and one producer
        // writes the figure as a decimal.
        val feed = assertSuccess(parser.parseVehicleTypes(fixture("vehicle_types_v2_ranges.json")))

        assertEquals(
            mapOf("351" to 60_000, "353" to 45_000, "360" to 30_000),
            feed.maxRangeMetresByType,
        )
        assertEquals(setOf("353"), feed.cargoVehicleTypeIds)
        // A cargo bike is a bicycle in the kinds like any other.
        assertEquals(VehicleKind.Electric, feed.kinds["353"])
    }

    // ------------------------------------------- the bikes outside stations --

    @Test
    fun `reads the street bikes of a nextbike feed in GBFS 2`() {
        // Berlin's shape: a bike at a station carries its station_id, a street
        // bike carries none, the electric ones publish a percentage and a
        // range of zero.
        val feed = assertSuccess(
            parser.parseVehicleStatus(fixture("free_bike_status_v2_nextbike.json")),
        )

        assertEquals(Instant.ofEpochSecond(1788432120), feed.lastUpdated)
        assertEquals("2.3", feed.version)
        assertEquals(
            listOf(
                "nextbike_bb_20412",
                "nextbike_bb_20587",
                "nextbike_bb_11023",
                "nextbike_bb_11340",
                "nextbike_bb_12777",
                "nextbike_bb_21150",
                "nextbike_bb_13419",
            ),
            feed.bikes.map { it.id },
        )
        val electric = feed.bikes.first()
        assertEquals("348", electric.vehicleTypeId)
        assertEquals(0.67, electric.chargeRatio!!, 1e-9)
        assertNull("a range of zero is no range", electric.rangeMetres)
        assertEquals(52.516278, electric.position.latitude, 1e-9)
        val mechanical = feed.bikes[2]
        assertEquals("346", mechanical.vehicleTypeId)
        assertNull(mechanical.chargeRatio)
        assertNull(mechanical.rangeMetres)
    }

    @Test
    fun `a blank station identifier means no station`() {
        // Fifteen writes station_id as "" on every bike on the street: read
        // as a station, it would empty the feed of Marseille's 647 bikes.
        val feed = assertSuccess(
            parser.parseVehicleStatus(fixture("free_bike_status_v2_fifteen.json")),
        )

        assertEquals(4, feed.bikes.size)
        assertEquals(listOf(42_000, 18_500, 27_300, 12_750), feed.bikes.map { it.rangeMetres })
        assertTrue(feed.bikes.all { it.chargeRatio == null })
    }

    @Test
    fun `reads the renamed feed of GBFS 3`() {
        // vehicles rather than bikes, vehicle_id rather than bike_id, and both
        // charge figures published.
        val feed = assertSuccess(
            parser.parseVehicleStatus(fixture("vehicle_status_v3_ecovelo.json")),
        )

        assertEquals(Instant.parse("2026-09-08T10:02:10Z"), feed.lastUpdated)
        assertEquals(6, feed.bikes.size)
        val first = feed.bikes.first()
        assertEquals("4b6d2e8a-1f3c-4a5b-9c7d-0e2f4a6b8c1d", first.id)
        assertEquals(0.5, first.chargeRatio!!, 1e-9)
        assertEquals(30_000, first.rangeMetres)
        assertEquals("cargo", feed.bikes[3].vehicleTypeId)
    }

    @Test
    fun `a bike within a station but without a station_id is a street bike`() {
        // Vel'in Calais publishes its whole fleet so: every bike within thirty
        // metres of a station, none with a station_id. They are read as what
        // the feed says they are, and drawn beside their stations: guessing
        // them back into one would be a distance heuristic with a coefficient
        // nobody measured, which SPEC §4.1 refuses — as §7.1 refuses the same
        // for the depots. The identifiers are numbers written as strings.
        val feed = assertSuccess(
            parser.parseVehicleStatus(fixture("free_bike_status_v2_all_docked.json")),
        )

        assertEquals(9, feed.bikes.size)
        assertEquals("10234", feed.bikes.first().id)
    }

    @Test
    fun `a disabled or a reserved vehicle is dropped`() {
        val document = """
            {"version":"2.3","data":{"bikes":[
              {"bike_id":"a","lat":50.63,"lon":3.05,"is_reserved":true,"is_disabled":false},
              {"bike_id":"b","lat":50.63,"lon":3.05,"is_reserved":false,"is_disabled":true},
              {"bike_id":"c","lat":50.63,"lon":3.05,"is_reserved":1,"is_disabled":0},
              {"bike_id":"d","lat":50.63,"lon":3.05}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleStatus(document))

        // The flags default to false where a producer omits them, and read
        // 0 and 1 as the station flags do.
        assertEquals(listOf("d"), feed.bikes.map { it.id })
    }

    @Test
    fun `the bikes at a station are kept apart, whatever their state`() {
        // Berlin's shape again: three bikes carry a station_id, two of them
        // at station 3140, one electric with its percentage. A station's
        // sheet says which bikes are out of service, so the flags are kept
        // rather than used to drop the entry as they are on the street.
        val feed = assertSuccess(
            parser.parseVehicleStatus(fixture("free_bike_status_v2_nextbike.json")),
        )

        assertEquals(listOf("3140", "3140", "3208"), feed.dockedBikes.map { it.stationId })
        val electric = feed.dockedBikes[1]
        assertEquals("348", electric.vehicleTypeId)
        assertEquals(0.92, electric.chargeRatio!!, 1e-9)
        assertNull("a range of zero is no range", electric.rangeMetres)
        assertFalse(electric.isDisabled)
    }

    @Test
    fun `a docked bike needs no position, and keeps its flags`() {
        val document = """
            {"version":"2.3","data":{"bikes":[
              {"bike_id":"a","station_id":"12","is_disabled":true},
              {"bike_id":"b","station_id":"12","is_reserved":1},
              {"bike_id":"c","station_id":"","lat":50.63,"lon":3.05},
              {"bike_id":"d","lat":50.63,"lon":3.05}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleStatus(document))

        assertEquals(listOf(true, false), feed.dockedBikes.map { it.isDisabled })
        assertEquals(listOf(false, true), feed.dockedBikes.map { it.isReserved })
        // A blank station_id is a street bike here as it is above.
        assertEquals(listOf("c", "d"), feed.bikes.map { it.id })
    }

    @Test
    fun `a vehicle without a usable position is dropped, not the feed`() {
        val document = """
            {"version":"2.3","data":{"bikes":[
              {"bike_id":"docked","station_id":"12"},
              {"bike_id":"nowhere"},
              {"bike_id":"gulf","lat":0,"lon":0},
              {"bike_id":"here","lat":50.63,"lon":3.05}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleStatus(document))

        assertEquals(listOf("here"), feed.bikes.map { it.id })
    }

    @Test
    fun `a charge that cannot be believed is left out rather than guessed`() {
        // A producer writing 67 for 67 % is not rescaled: the standard says a
        // ratio, and a guess about a battery is a promise to somebody walking.
        val document = """
            {"version":"2.3","data":{"bikes":[
              {"bike_id":"a","lat":50.63,"lon":3.05,"current_fuel_percent":67},
              {"bike_id":"b","lat":50.63,"lon":3.05,"current_fuel_percent":-0.1},
              {"bike_id":"c","lat":50.63,"lon":3.05,"current_range_meters":0},
              {"bike_id":"d","lat":50.63,"lon":3.05,"current_range_meters":-5},
              {"bike_id":"e","lat":50.63,"lon":3.05,"current_fuel_percent":1,
               "current_range_meters":12345.6}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleStatus(document))

        assertEquals(listOf(null, null, null, null, 1.0), feed.bikes.map { it.chargeRatio })
        assertEquals(listOf(null, null, null, null, 12_345), feed.bikes.map { it.rangeMetres })
    }

    @Test
    fun `accepts vehicle and station identifiers published as numbers`() {
        val document = """
            {"version":"2.0","data":{"bikes":[
              {"bike_id":41230,"lat":50.63,"lon":3.05,"vehicle_type_id":346},
              {"bike_id":41231,"lat":50.63,"lon":3.05,"station_id":3140}
            ]}}
        """.trimIndent()

        val feed = assertSuccess(parser.parseVehicleStatus(document))

        val bike = feed.bikes.single()
        assertEquals("41230", bike.id)
        assertEquals("346", bike.vehicleTypeId)
    }

    @Test
    fun `finds the feed under its GBFS 3 name first, then under the older one`() {
        val both = GbfsDiscovery(
            version = "3.0",
            feedUrlsByName = mapOf(
                "free_bike_status" to "https://example.invalid/free_bike_status.json",
                "vehicle_status" to "https://example.invalid/vehicle_status.json",
            ),
        )
        val older = GbfsDiscovery(
            version = "2.3",
            feedUrlsByName = mapOf(
                "free_bike_status" to "https://example.invalid/free_bike_status.json",
            ),
        )

        assertEquals(
            "https://example.invalid/vehicle_status.json",
            assertSuccess(both.urlOfVehicleStatus()),
        )
        assertEquals(
            "https://example.invalid/free_bike_status.json",
            assertSuccess(older.urlOfVehicleStatus()),
        )
    }

    @Test
    fun `a network publishing the feed under neither name says so under one`() {
        val discovery = assertSuccess(parser.parseDiscovery(fixture("discovery_v2_real.json")))

        assertEquals(
            Outcome.Failure(DataError.FeedUnavailable("vehicle_status")),
            discovery.urlOfVehicleStatus(),
        )
    }
}
