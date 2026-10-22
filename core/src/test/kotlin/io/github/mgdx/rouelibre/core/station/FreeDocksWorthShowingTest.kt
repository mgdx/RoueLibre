package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the decision to stop talking about free docks in a network where
 * most stations take back any bike (SPEC §7.1).
 */
class FreeDocksWorthShowingTest {

    /**
     * A network of [zones] painted zones — virtual, no capacity, nought free
     * docks, which nextbike publishes for them and the application reads as
     * unlimited — and [racks] physical racks counting their free docks.
     */
    private fun network(zones: Int, racks: Int): List<StationWithAvailability> =
        List(zones) { station("zone-$it", virtual = true, docks = 0) } +
            List(racks) { station("rack-$it", virtual = false, docks = 5) }

    private fun station(
        id: String,
        virtual: Boolean,
        docks: Int?,
        installed: Boolean = true,
        returning: Boolean = true,
        withState: Boolean = true,
    ) = joinStationsWithAvailability(
        listOf(Station(id, id, Coordinates(48.14, 11.57), null, null, isVirtual = virtual)),
        if (withState) {
            listOf(
                StationAvailability(
                    stationId = id,
                    bikesAvailable = 2,
                    docksAvailable = docks,
                    isInstalled = installed,
                    isRenting = true,
                    isReturning = returning,
                    reportedAt = null,
                ),
            )
        } else {
            emptyList()
        },
    ).single()

    // ------------------------------------------------------- the threshold --

    @Test
    fun `free docks go silent from sixty per cent of unlimited stations`() {
        // MyRadl, Munich: a ∞ at nearly every one of 805 stations.
        assertEquals(false, freeDocksAreWorthShowing(network(zones = 797, racks = 8)))
        // Zürich, kept to its bike operators: about 77 %.
        assertEquals(false, freeDocksAreWorthShowing(network(zones = 77, racks = 23)))
        assertEquals(false, freeDocksAreWorthShowing(network(zones = 61, racks = 39)))
    }

    @Test
    fun `exactly sixty per cent goes silent`() {
        // "At least 60 %", the owner's words, and in whole numbers so that ten
        // stations and a thousand are cut at the same place.
        assertEquals(false, freeDocksAreWorthShowing(network(zones = 60, racks = 40)))
        assertEquals(false, freeDocksAreWorthShowing(network(zones = 6, racks = 4)))
        assertEquals(true, freeDocksAreWorthShowing(network(zones = 599, racks = 401)))
    }

    @Test
    fun `a network just under the line keeps its free docks`() {
        // Mannheim, the nearest network under the line: 59.2 %.
        assertEquals(true, freeDocksAreWorthShowing(network(zones = 592, racks = 408)))
    }

    @Test
    fun `a docked network with a few zones keeps its free docks`() {
        // Every network of physical docks surveyed stood under a quarter.
        assertEquals(true, freeDocksAreWorthShowing(network(zones = 24, racks = 76)))
        assertEquals(true, freeDocksAreWorthShowing(network(zones = 0, racks = 40)))
    }

    @Test
    fun `racks whose nought measures nothing count as unlimited`() {
        // Düsseldorf: racks without a capacity, nearly all at nought, which
        // nextbike derives rather than measures (SPEC §4.1).
        val stations =
            List(19) { station("rack-$it", virtual = false, docks = 0) } +
                station("counting", virtual = false, docks = 3)

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `full racks in a feed that counts are not unlimited`() {
        // Indego, Philadelphia: no capacity anywhere, a few full racks at
        // nought among hundreds counting. Those noughts are measured.
        val stations =
            List(4) { station("full-$it", virtual = false, docks = 0) } +
                List(96) { station("rack-$it", virtual = false, docks = 5) }

        assertEquals(true, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `a network publishing no free-dock count anywhere goes silent`() {
        val stations = List(20) { station("s-$it", virtual = false, docks = null) }

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    // ------------------------------------------------- what is weighed --

    @Test
    fun `a station without a live status is not weighed`() {
        // Nineteen zones and one rack: silent. A hundred stations the real-time
        // feed says nothing about change nothing — they have no free docks to
        // read, limited or not.
        val stations = network(zones = 19, racks = 1) +
            List(100) { station("silent-$it", virtual = false, docks = 5, withState = false) }

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `a rack not yet installed is not weighed`() {
        // Twenty racks still in their crates would pull a zone network under
        // the threshold; they are a plan, not stations.
        val stations = network(zones = 19, racks = 1) +
            List(20) { station("crate-$it", virtual = false, docks = 5, installed = false) }

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `a zone not installed is weighed, a virtual station being there regardless`() {
        // Pony publishes every one of its zones as not installed.
        val stations = List(30) {
            station("pony-$it", virtual = true, docks = null, installed = false)
        }

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `a station refusing returns for now is weighed all the same`() {
        val stations =
            List(19) { station("zone-$it", virtual = true, docks = 0, returning = false) } +
                station("rack", virtual = false, docks = 5)

        assertEquals(false, freeDocksAreWorthShowing(stations))
    }

    @Test
    fun `nothing to weigh settles nothing`() {
        assertNull(freeDocksAreWorthShowing(emptyList()))
        assertNull(
            freeDocksAreWorthShowing(
                List(5) { station("known-$it", virtual = true, docks = 0, withState = false) },
            ),
        )
    }

    // --------------------------------------------------------- stability --

    @Test
    fun `the first snapshot that can be weighed decides`() {
        val silent = network(zones = 95, racks = 5)

        assertEquals(false, settleFreeDocksWorthShowing(null, silent))
    }

    @Test
    fun `a settled verdict outlives a share crossing the threshold`() {
        // A network sitting at the edge: one refresh at 61 %, the next at 59 %.
        // The toggle does not come back under the rider's thumb.
        val first = settleFreeDocksWorthShowing(null, network(zones = 61, racks = 39))
        val second = settleFreeDocksWorthShowing(first, network(zones = 59, racks = 41))

        assertFalse(checkNotNull(first))
        assertFalse(checkNotNull(second))
        // And the other way round.
        assertTrue(checkNotNull(settleFreeDocksWorthShowing(true, network(zones = 99, racks = 1))))
    }

    @Test
    fun `nothing to weigh leaves the verdict open`() {
        // The stations written before their states, as a first fetch does.
        val unweighed =
            List(5) { station("known-$it", virtual = true, docks = 0, withState = false) }

        assertNull(settleFreeDocksWorthShowing(null, unweighed))
    }

    @Test
    fun `an emptied cache forgets the verdict`() {
        // Changing city empties the cache: Munich's silence must not follow the
        // rider into Logroño's racks.
        val munich = settleFreeDocksWorthShowing(null, network(zones = 797, racks = 8))
        val between = settleFreeDocksWorthShowing(munich, emptyList())
        val logrono = settleFreeDocksWorthShowing(between, network(zones = 0, racks = 30))

        assertEquals(false, munich)
        assertNull(between)
        assertEquals(true, logrono)
    }
}
