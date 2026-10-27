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

    // ------------------------------ free docks repeating the capacity --

    private fun holding(id: String, bikes: Int, docks: Int?, returning: Boolean = true) =
        StationAvailability(
            stationId = id,
            bikesAvailable = bikes,
            docksAvailable = docks,
            isInstalled = true,
            isRenting = true,
            isReturning = returning,
            reportedAt = null,
        )

    /** Stations of [capacity] docks, one per count of [bikes], reading [docks] free. */
    private fun stationsOf(capacity: Int, docks: (Int) -> Int, vararg bikes: Int) =
        bikes.mapIndexed { index, held ->
            val id = "c$capacity-$index"
            rack(id, capacity = capacity) to holding(id, held, docks(held))
        }

    private fun joinedFeed(pairs: List<kotlin.Pair<Station, StationAvailability>>) =
        joinStationsWithAvailability(pairs.map { it.first }, pairs.map { it.second })

    private fun statesOf(pairs: List<kotlin.Pair<Station, StationAvailability>>) =
        joinedFeed(pairs).associate { it.station.id to checkNotNull(it.availability) }

    @Test
    fun `a feed writing the capacity at every station holding bikes takes any bike`() {
        // Ecovelo's Auch-Vélo'c, 22 October 2026: 10 free beside 10 bikes at a
        // ten-dock station, 5 free beside 7 bikes at a five-dock one.
        val auch = stationsOf(10, { 10 }, 10, 2, 8) +
            stationsOf(5, { 5 }, 7, 4, 1, 2, 6) +
            listOf(rack("empty", capacity = 5) to holding("empty", 0, 5))
        val states = statesOf(auch)

        assertTrue(states.values.all { it.isInFeedWritingDocksFromCapacity })
        assertTrue(states.values.all { it.hasUnlimitedDocks })
        assertTrue(states.getValue("c5-0").acceptsAnyBike)
        assertEquals(false, freeDocksAreWorthShowing(joinedFeed(auch)))
    }

    @Test
    fun `a station refusing returns stays refused in a feed repeating its capacity`() {
        val auch = stationsOf(5, { 5 }, 7, 4, 1, 2, 6) +
            listOf(rack("closed", capacity = 5) to holding("closed", 3, 5, returning = false))
        val closed = statesOf(auch).getValue("closed")

        assertTrue(closed.hasUnlimitedDocks)
        assertFalse(closed.canAcceptBike)
        assertFalse(closed.acceptsAnyBike)
    }

    @Test
    fun `a docked feed keeps its counts beside a few stations reading their capacity`() {
        // Berlin, 22 October 2026: 2 of 704 stations holding bikes read their
        // whole capacity free — a capacity lagging behind the rack, not a
        // feed that never counts.
        val docked = stationsOf(20, { 20 - it }, 3, 7, 12, 15) + stationsOf(10, { 10 }, 2, 4)
        val states = statesOf(docked)

        assertFalse(states.values.any { it.isInFeedWritingDocksFromCapacity })
        assertFalse(states.getValue("c20-0").hasUnlimitedDocks)
        assertEquals(17, states.getValue("c20-0").docksAvailable)
    }

    @Test
    fun `half the stations holding bikes is no majority`() {
        val tied = stationsOf(10, { 10 }, 2, 4, 6) + stationsOf(20, { 20 - it }, 1, 2, 3)

        assertFalse(statesOf(tied).values.any { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `empty stations reading their capacity are counted, not repeated`() {
        // Agen's empty fleet: five free docks at five-dock racks with no bike
        // in them is exactly what a count says.
        val agen = stationsOf(5, { 5 }, 0, 0, 0, 0, 0, 0, 0) + stationsOf(6, { 6 - it }, 2)

        assertFalse(statesOf(agen).values.any { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `too few stations holding bikes decide nothing`() {
        val small = stationsOf(5, { 5 }, 1, 2, 3, 4) + stationsOf(6, { 6 }, 0, 0, 0)
        val enough = stationsOf(5, { 5 }, 1, 2, 3, 4, 5)

        assertFalse(statesOf(small).values.any { it.isInFeedWritingDocksFromCapacity })
        assertTrue(statesOf(enough).values.all { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `a station without a capacity or a count weighs nothing`() {
        // Nought at a capacity of nought is a full station, never the whole
        // capacity free; no capacity or no count leaves nothing to compare.
        val unweighed = listOf(
            rack("zero", capacity = 0) to holding("zero", 3, 0),
            rack("none") to holding("none", 3, 4),
            rack("uncounted", capacity = 5) to holding("uncounted", 3, null),
        ) + stationsOf(8, { 8 }, 1, 2) + stationsOf(9, { 9 - it }, 1, 2, 3)

        assertFalse(statesOf(unweighed).values.any { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `a state nobody joined keeps its count`() {
        assertFalse(holding("seul", 3, 5).isInFeedWritingDocksFromCapacity)
        assertFalse(holding("seul", 3, 5).hasUnlimitedDocks)
    }

    @Test
    fun `a feed writing half the capacity beside the whole of it takes any bike`() {
        // Niort, 22 October 2026: 10 free at twenty-dock stations and 7 at
        // fourteen-dock ones whatever stands there, 8 at the eight-dock ones.
        val niort = stationsOf(20, { 10 }, 1, 3, 2) +
            stationsOf(14, { 7 }, 1, 4) +
            stationsOf(8, { 8 }, 2, 5) +
            stationsOf(22, { 13 }, 4)
        val states = statesOf(niort)

        assertTrue(states.values.all { it.isInFeedWritingDocksFromCapacity })
        assertTrue(states.getValue("c22-0").hasUnlimitedDocks)
    }

    @Test
    fun `a feed found writing its docks from its capacity stays so until emptied`() {
        val above = stationsOf(20, { 10 }, 1, 2, 3) + stationsOf(14, { 14 - it }, 1, 2)
        val below = stationsOf(20, { 10 }, 1, 2) + stationsOf(14, { 14 - it }, 1, 2, 3)
        fun settle(settled: Boolean, pairs: List<kotlin.Pair<Station, StationAvailability>>) =
            settleWritingDocksFromCapacity(settled, pairs.map { it.first }, pairs.map { it.second })

        val first = settle(false, above)
        val second = settle(first, below)

        assertTrue(first)
        assertTrue(second)
        assertFalse(settle(false, below))
        assertFalse(settle(true, emptyList()))
        val states = joinStationsWithAvailability(
            below.map { it.first },
            below.map { it.second },
            writesDocksFromCapacitySettled = second,
        )
        assertTrue(states.all { it.availability!!.hasUnlimitedDocks })
    }

    @Test
    fun `half an odd capacity is read rounded either way`() {
        // Cherbourg: 8 free at fifteen docks holding 6. Soissons: 3 free at
        // seven docks holding 1.
        val odd = stationsOf(15, { 8 }, 6, 2, 3) + stationsOf(7, { 3 }, 1, 2) +
            stationsOf(30, { 30 - it }, 4, 5, 6)

        assertTrue(statesOf(odd).values.all { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `half a small rack is not read as written from its capacity`() {
        // LoveSharing, Las Palmas: small racks reading half their docks, a
        // dock or a bike out of service away from a real count. Below six
        // docks, half is left alone; the whole capacity is not.
        val small = stationsOf(4, { 2 }, 1, 1) + stationsOf(5, { 2 }, 1, 2) +
            stationsOf(10, { 10 - it }, 1, 2)

        assertFalse(statesOf(small).values.any { it.isInFeedWritingDocksFromCapacity })
        val whole = stationsOf(4, { 4 }, 1, 2, 3) + stationsOf(10, { 10 - it }, 1, 2)
        assertTrue(statesOf(whole).values.all { it.isInFeedWritingDocksFromCapacity })
    }

    @Test
    fun `half the capacity left by as many bikes is a count`() {
        // Twenty docks holding ten bikes do have ten free: a docked network
        // at half its fill is measured, not written from its capacity.
        val halfFull = stationsOf(20, { 20 - it }, 10, 11, 9) +
            stationsOf(14, { 14 - it }, 7, 7) +
            stationsOf(16, { 8 }, 3)

        assertFalse(statesOf(halfFull).values.any { it.isInFeedWritingDocksFromCapacity })
    }

    // ------------------------------------- bikes past the capacity --

    /** [count] six-dock racks read full beside [bikes] bikes, nextbike's derivation. */
    private fun fullRacks(prefix: String, count: Int, bikes: Int) = (0 until count).map { index ->
        val id = "$prefix-$index"
        rack(id, capacity = 6) to holding(id, bikes, (6 - bikes).coerceAtLeast(0))
    }

    /** [count] ten-dock racks counted honestly, holding four bikes each. */
    private fun countedRacks(count: Int) = (0 until count).map { index ->
        val id = "counted-$index"
        rack(id, capacity = 10) to holding(id, 4, 6)
    }

    @Test
    fun `a feed taking bikes past its full racks takes any bike`() {
        // Frelo, Freiburg, 23 October 2026: 44 of its 157 stations read
        // nought free docks beside more bikes than their capacity — 15 at a
        // six-dock one.
        val frelo = fullRacks("over", 3, 15) + fullRacks("full", 4, 6) + countedRacks(10)
        val states = statesOf(frelo)

        assertTrue(states.values.all { it.isInFeedTakingBikesPastCapacity })
        assertTrue(states.getValue("full-0").acceptsAnyBike)
        assertTrue(states.getValue("counted-0").hasUnlimitedDocks)
        assertEquals(false, freeDocksAreWorthShowing(joinedFeed(frelo)))
    }

    @Test
    fun `a docked feed keeps its full racks beside a few past their capacity`() {
        // Under five per cent: Chicago, 13 of 2,076 that evening, a capacity
        // lagging behind its rack here and there.
        val docked = fullRacks("over", 3, 8) + fullRacks("full", 7, 6) + countedRacks(51)
        val states = statesOf(docked)

        assertFalse(states.values.any { it.isInFeedTakingBikesPastCapacity })
        assertFalse(states.getValue("full-0").canAcceptBike)
        val atTheLine = fullRacks("over", 3, 8) + countedRacks(57)
        assertTrue(statesOf(atTheLine).values.all { it.isInFeedTakingBikesPastCapacity })
    }

    @Test
    fun `bikes past the capacity beside free docks say nothing of a full rack`() {
        // Vélib', that evening: 49 bikes and 8 free docks at a station
        // declaring 42, a capacity lagging behind the rack.
        val lagging = (0 until 5).map { index ->
            val id = "lagging-$index"
            rack(id, capacity = 42) to holding(id, 49, 8)
        } + countedRacks(5)

        assertFalse(statesOf(lagging).values.any { it.isInFeedTakingBikesPastCapacity })
    }

    @Test
    fun `fewer than three stations past their capacity decide nothing`() {
        // nextbike's one-station towns, Třebíč and Uherské Hradiště: one
        // full rack holding seven bikes is the whole feed.
        val two = fullRacks("over", 2, 9) + fullRacks("full", 1, 6)
        val three = fullRacks("over", 3, 9)

        assertFalse(statesOf(two).values.any { it.isInFeedTakingBikesPastCapacity })
        assertTrue(statesOf(three).values.all { it.isInFeedTakingBikesPastCapacity })
    }

    @Test
    fun `a feed found taking bikes past its capacity stays so until emptied`() {
        // The evening's reading decides the day's: bikes spread again in the
        // morning, and the full racks still take any bike.
        val evening = fullRacks("over", 4, 12) + countedRacks(20)
        val morning = fullRacks("full", 4, 6) + countedRacks(20)
        fun settle(settled: Boolean, pairs: List<kotlin.Pair<Station, StationAvailability>>) =
            settleTakingBikesPastCapacity(settled, pairs.map { it.first }, pairs.map { it.second })

        val first = settle(false, evening)
        val second = settle(first, morning)

        assertTrue(first)
        assertTrue(second)
        assertFalse(settle(false, morning))
        assertFalse(settle(true, emptyList()))
        val states = joinStationsWithAvailability(
            morning.map { it.first },
            morning.map { it.second },
            takesBikesPastCapacitySettled = second,
        )
        assertTrue(states.all { it.availability!!.acceptsAnyBike })
    }

    @Test
    fun `a rider turning the reading off keeps the full racks full`() {
        // The rider knows the network's rule better than its feed: off, the
        // evening's settled verdict and the snapshot's own both give way.
        val evening = fullRacks("over", 4, 12) + fullRacks("full", 2, 6) + countedRacks(20)
        val states = joinStationsWithAvailability(
            evening.map { it.first },
            evening.map { it.second },
            takesBikesPastCapacitySettled = true,
            bikesPastCapacityAnswer = BikesPastCapacityAnswer.Never,
        ).associate { it.station.id to checkNotNull(it.availability) }

        assertFalse(states.values.any { it.isInFeedTakingBikesPastCapacity })
        assertFalse(states.getValue("full-0").canAcceptBike)
        assertTrue(states.getValue("counted-0").canAcceptBike)
    }

    @Test
    fun `a rider turning the reading on reads full racks as taking any bike`() {
        // Prague's nextbike, 2.1 % on 23 October 2026: under the line, and its
        // riders know its full stations take bikes back.
        val prague = fullRacks("over", 1, 9) + fullRacks("full", 3, 6) + countedRacks(40)
        fun join(answer: BikesPastCapacityAnswer) = joinStationsWithAvailability(
            prague.map { it.first },
            prague.map { it.second },
            bikesPastCapacityAnswer = answer,
        ).associate { it.station.id to checkNotNull(it.availability) }

        assertFalse(join(BikesPastCapacityAnswer.Automatic).getValue("full-0").canAcceptBike)
        val always = join(BikesPastCapacityAnswer.Always)
        assertTrue(always.values.all { it.isInFeedTakingBikesPastCapacity })
        assertTrue(always.getValue("full-0").acceptsAnyBike)
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
