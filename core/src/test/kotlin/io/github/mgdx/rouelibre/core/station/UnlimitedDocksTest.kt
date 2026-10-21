package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the stations whose free docks limit nothing (SPEC §4.1).
 *
 * The documents are trimmed from nextbike's MyRadl feed for Munich as it stood
 * on 21 October 2026 — 805 stations, 800 of them virtual, three publishing a
 * capacity, and nought free docks at 804 — with the cases around it added: a
 * station leaving the count out, as GBFS asks of one with unlimited docking, a
 * physical rack at nought beside its capacity, and a zone that has stopped
 * taking bikes back. The feeds of Düsseldorf and Philadelphia, built station by
 * station further down, settle the nought of a rack publishing no capacity.
 */
class UnlimitedDocksTest {

    private val parser = GbfsParser()

    private val information = """
        {"last_updated":1792569600,"ttl":60,"version":"2.3",
         "data":{"stations":[
          {"station_id":"zone","name":"Marienplatz","lat":48.1374,"lon":11.5755,
           "is_virtual_station":true},
          {"station_id":"zone-with-capacity","name":"Hauptbahnhof","lat":48.1402,
           "lon":11.5600,"is_virtual_station":true,"capacity":12},
          {"station_id":"rack","name":"Sendlinger Tor","lat":48.1339,"lon":11.5668,
           "capacity":9},
          {"station_id":"uncounted","name":"Odeonsplatz","lat":48.1428,"lon":11.5770},
          {"station_id":"closed-zone","name":"Isartor","lat":48.1355,"lon":11.5822,
           "is_virtual_station":true},
          {"station_id":"counted-zone","name":"Stachus","lat":48.1390,"lon":11.5660,
           "is_virtual_station":true}
        ]}}
    """.trimIndent()

    private val status = """
        {"last_updated":1792569600,"ttl":60,"version":"2.3",
         "data":{"stations":[
          {"station_id":"zone","num_bikes_available":4,"num_docks_available":0,
           "is_installed":true,"is_renting":true,"is_returning":true,
           "last_reported":1792569590},
          {"station_id":"zone-with-capacity","num_bikes_available":12,
           "num_docks_available":0,"is_installed":true,"is_renting":true,
           "is_returning":true,"last_reported":1792569590},
          {"station_id":"rack","num_bikes_available":9,"num_docks_available":0,
           "is_installed":true,"is_renting":true,"is_returning":true,
           "last_reported":1792569590},
          {"station_id":"uncounted","num_bikes_available":2,
           "is_installed":true,"is_renting":true,"is_returning":true,
           "last_reported":1792569590},
          {"station_id":"closed-zone","num_bikes_available":1,"num_docks_available":0,
           "is_installed":true,"is_renting":true,"is_returning":false,
           "last_reported":1792569590},
          {"station_id":"counted-zone","num_bikes_available":1,"num_docks_available":3,
           "is_installed":true,"is_renting":true,"is_returning":true,
           "last_reported":1792569590}
        ]}}
    """.trimIndent()

    private fun <T> success(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Success -> outcome.value
        is Outcome.Failure -> throw AssertionError("parse failed: ${outcome.error}")
    }

    private fun joined(): Map<String, StationWithAvailability> = joinStationsWithAvailability(
        success(parser.parseStationInformation(information)).stations,
        success(parser.parseStationStatus(status)).availabilities,
    ).associateBy { it.station.id }

    private fun stateOf(id: String): StationAvailability =
        checkNotNull(joined().getValue(id).availability)

    @Test
    fun `an absent free-dock count is read as no count, not as nought`() {
        val state = stateOf("uncounted")

        assertNull(state.docksAvailable)
        assertTrue(state.hasUnlimitedDocks)
        assertTrue(state.canAcceptBike)
    }

    @Test
    fun `a virtual station without capacity at nought takes any bike back`() {
        val state = stateOf("zone")

        assertTrue(state.hasUnlimitedDocks)
        assertTrue(state.canAcceptBike)
    }

    @Test
    fun `the nought is kept as the feed sent it`() {
        // What is stored is what the feed said; the reading is applied where the
        // state meets its station, as `is_installed` is for a virtual station.
        val parsed = success(parser.parseStationStatus(status)).availabilities
            .single { it.stationId == "zone" }

        assertEquals(0, parsed.docksAvailable)
        assertFalse(parsed.hasUnlimitedDocks)
        assertEquals(0, stateOf("zone").docksAvailable)
    }

    @Test
    fun `a virtual station declaring a capacity keeps its count`() {
        val state = stateOf("zone-with-capacity")

        assertFalse(state.hasUnlimitedDocks)
        assertFalse(state.canAcceptBike)
        assertEquals(
            0,
            joined().getValue("zone-with-capacity").displayFor(AvailabilityMode.Docks).count,
        )
    }

    @Test
    fun `a virtual station counting its free docks keeps the count`() {
        val state = stateOf("counted-zone")

        assertFalse(state.hasUnlimitedDocks)
        assertEquals(3, joined().getValue("counted-zone").displayFor(AvailabilityMode.Docks).count)
    }

    @Test
    fun `a physical rack at nought beside its capacity is full`() {
        val state = stateOf("rack")

        assertFalse(state.hasUnlimitedDocks)
        assertFalse(state.canAcceptBike)
    }

    @Test
    fun `a zone that no longer takes bikes back still refuses them`() {
        val entry = joined().getValue("closed-zone")

        assertFalse(entry.availability!!.canAcceptBike)
        assertTrue(entry.displayFor(AvailabilityMode.Docks).isOutOfService)
    }

    @Test
    fun `an unlimited station shows no figure and the best level`() {
        listOf("zone", "uncounted").forEach { id ->
            val display = joined().getValue(id).displayFor(AvailabilityMode.Docks)

            assertTrue(id, display.isUnlimited)
            assertNull(id, display.count)
            assertEquals(id, AvailabilityLevel.Good, display.level)
            assertFalse(id, display.isOutOfService)
            assertNull(id, display.filledFraction)
        }
    }

    @Test
    fun `counting bikes, an unlimited station draws no arc it cannot scale`() {
        // Its bikes plus its free docks would be its bikes alone, and every
        // such station would be drawn full.
        val display = joined().getValue("zone").displayFor(AvailabilityMode.Bikes)

        assertEquals(4, display.count)
        assertFalse(display.isUnlimited)
        assertNull(display.filledFraction)
    }

    @Test
    fun `hiding the empty stations keeps the unlimited ones`() {
        val kept = stationsShownOnMap(
            stations = joined().values.toList(),
            filter = StationFilter(hideEmpty = true),
            mode = AvailabilityMode.Docks,
        ).map { it.station.id }

        assertTrue("zone" in kept)
        assertTrue("uncounted" in kept)
        assertFalse("rack" in kept)
    }

    // ------------------------------------- a nought without a capacity --

    private fun rack(id: String, capacity: Int? = null, virtual: Boolean = false) = Station(
        id = id,
        name = id,
        position = io.github.mgdx.rouelibre.core.geo.Coordinates(51.22, 6.77),
        capacity = capacity,
        postalCode = null,
        isVirtual = virtual,
    )

    private fun counted(id: String, docks: Int?, returning: Boolean = true) = StationAvailability(
        stationId = id,
        bikesAvailable = 4,
        docksAvailable = docks,
        isInstalled = true,
        isRenting = true,
        isReturning = returning,
        reportedAt = null,
    )

    private fun feed(vararg pairs: kotlin.Pair<Station, StationAvailability>) =
        joinStationsWithAvailability(pairs.map { it.first }, pairs.map { it.second })
            .associate { it.station.id to checkNotNull(it.availability) }

    @Test
    fun `a rack without a capacity at nought takes any bike where nothing is counted`() {
        // Düsseldorf, 22 October 2026: 359 of 360 stations at nought, 345 of
        // them racks rather than zones, and no capacity to subtract the bikes
        // from. The nought is nextbike's derivation, not a full rack.
        val states = feed(
            rack("Burgplatz") to counted("Burgplatz", 0),
            rack("Hauptbahnhof") to counted("Hauptbahnhof", 0),
            rack("Zone", virtual = true) to counted("Zone", 0),
        )

        assertTrue(states.getValue("Burgplatz").hasUnlimitedDocks)
        assertTrue(states.getValue("Burgplatz").acceptsAnyBike)
    }

    @Test
    fun `a rack without a capacity at nought is full where the feed counts`() {
        // Indego, Philadelphia, 22 October 2026: no capacity anywhere, 317
        // racks counted above nought and 4 at nought — full racks, measured.
        val states = feed(
            rack("Rittenhouse") to counted("Rittenhouse", 0),
            rack("City Hall") to counted("City Hall", 7),
            rack("Zone", virtual = true) to counted("Zone", 0),
        )

        assertFalse(states.getValue("Rittenhouse").hasUnlimitedDocks)
        assertFalse(states.getValue("Rittenhouse").canAcceptBike)
        // A zone has no dock to fill, whatever the racks beside it count.
        assertTrue(states.getValue("Zone").hasUnlimitedDocks)
    }

    @Test
    fun `a few real docks among derived noughts leave the noughts unlimited`() {
        // Sarajevo, 22 October 2026: 38 of the 42 stations without a capacity
        // at nought, four counting real docks. The noughts are nextbike's.
        val racks = (1..10).map { index -> rack("rack-" + index) to counted("rack-" + index, 0) }
        val states = feed(*racks.toTypedArray(), rack("Bascarsija") to counted("Bascarsija", 6))

        assertTrue(states.getValue("rack-1").hasUnlimitedDocks)
        assertFalse(states.getValue("Bascarsija").hasUnlimitedDocks)
    }

    @Test
    fun `a capacity elsewhere in the feed proves nothing about a rack without one`() {
        // Munich's three capacities, and racks at nought beside them: only a
        // count above nought at a station without a capacity says the feed
        // measures such stations.
        val states = feed(
            rack("Odeonsplatz") to counted("Odeonsplatz", 0),
            rack("Hauptbahnhof", capacity = 12) to counted("Hauptbahnhof", 5),
        )

        assertTrue(states.getValue("Odeonsplatz").hasUnlimitedDocks)
        assertFalse(states.getValue("Hauptbahnhof").hasUnlimitedDocks)
    }

    @Test
    fun `a state nobody joined reads its nought as full`() {
        assertFalse(counted("seul", 0).hasUnlimitedDocks)
        assertTrue(counted("seul", null).hasUnlimitedDocks)
    }

    @Test
    fun `a station refusing returns accepts no bike, however its docks read`() {
        val states = feed(rack("Burgplatz") to counted("Burgplatz", 0, returning = false))
        val state = states.getValue("Burgplatz")

        assertTrue(state.hasUnlimitedDocks)
        assertFalse(state.acceptsAnyBike)
    }

    // ----------------------------------------- edge cases of the feed --

    private fun parsedState(stationFields: String, statusFields: String): StationAvailability {
        val information = """
            {"last_updated":1792569600,"ttl":60,"version":"2.3","data":{"stations":[
             {"station_id":"s","name":"S","lat":48.1,"lon":11.5$stationFields}]}}
        """.trimIndent()
        val status = """
            {"last_updated":1792569600,"ttl":60,"version":"2.3","data":{"stations":[
             {"station_id":"s","num_bikes_available":2,"is_installed":true,
              "is_renting":true,"is_returning":true$statusFields}]}}
        """.trimIndent()
        return checkNotNull(
            joinStationsWithAvailability(
                success(parser.parseStationInformation(information)).stations,
                success(parser.parseStationStatus(status)).availabilities,
            ).single().availability,
        )
    }

    @Test
    fun `a negative free-dock count is read as nought`() {
        // A count below nought is malformed; the parser has always brought it
        // back to nought, and the rule then reads it as any nought. The
        // discovery tool does the same (test_dock_verdict.py).
        val withCapacity = parsedState(""","capacity":10""", ""","num_docks_available":-2""")
        val withoutCapacity = parsedState("", ""","num_docks_available":-2""")

        assertEquals(0, withCapacity.docksAvailable)
        assertFalse(withCapacity.hasUnlimitedDocks)
        assertTrue(withoutCapacity.hasUnlimitedDocks)
    }

    @Test
    fun `a negative capacity is no capacity`() {
        val state = parsedState(""","capacity":-1""", ""","num_docks_available":0""")

        assertFalse(state.isAtStationWithCapacity)
        assertTrue(state.hasUnlimitedDocks)
    }

    @Test
    fun `a flag written as a string is read as the boolean it spells`() {
        val zone = parsedState(
            ""","is_virtual_station":"true","capacity":4""",
            ""","num_docks_available":1""",
        )

        assertTrue(zone.isAtVirtualStation)
    }
}
