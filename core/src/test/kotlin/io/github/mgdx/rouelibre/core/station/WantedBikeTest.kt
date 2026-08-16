package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Asking for one kind of bike (SPEC §7.1, §7.3).
 *
 * Two rules are under test, and both are about refusing to guess: a word that
 * cannot be read asks for nothing, and a station whose bikes could not be
 * counted by kind holds none of the kind wanted as far as anybody promising one
 * is concerned.
 */
class WantedBikeTest {

    private val lyon = mapOf(
        "mechanical" to VehicleKind.Mechanical,
        "electrical" to VehicleKind.Electric,
        "scooter" to VehicleKind.Other,
    )

    private fun availability(bikes: Int, byType: Map<String, Int>) = StationAvailability(
        stationId = "1",
        bikesAvailable = bikes,
        docksAvailable = 4,
        bikesByVehicleType = byType,
        isInstalled = true,
        isRenting = true,
        isReturning = true,
        reportedAt = Instant.EPOCH,
    )

    // ------------------------------------------------- reading the choice --

    @Test
    fun `a stored word reads back as the kind it names`() {
        assertEquals(
            WantedBikeKind.Electric,
            WantedBikeKind.ofWireName(WantedBikeKind.Electric.wireName),
        )
        assertEquals(
            WantedBikeKind.Mechanical,
            WantedBikeKind.ofWireName(WantedBikeKind.Mechanical.wireName),
        )
    }

    @Test
    fun `nothing written down asks for nothing`() {
        assertNull(WantedBikeKind.ofWireName(null))
    }

    @Test
    fun `a word this build cannot read asks for nothing, never a kind`() {
        // Standing in for it with a guess would send somebody towards a bike
        // nobody promised (SPEC §7.3).
        assertNull(WantedBikeKind.ofWireName("moped"))
        assertNull(WantedBikeKind.ofWireName(""))
        assertNull(WantedBikeKind.ofWireName("other"))
    }

    // ---------------------------------------------- counting at a station --

    @Test
    fun `counts the bikes of the kind wanted`() {
        val filter = BikeKindFilter(WantedBikeKind.Electric, lyon)
        val state = availability(4, mapOf("mechanical" to 3, "electrical" to 1))

        assertEquals(1, filter.bikesAt(state))
        assertTrue(filter.isSatisfiedBy(state))
    }

    @Test
    fun `a station with none of the kind wanted does not satisfy it`() {
        val filter = BikeKindFilter(WantedBikeKind.Electric, lyon)
        val state = availability(3, mapOf("mechanical" to 3))

        assertEquals(0, filter.bikesAt(state))
        assertFalse(filter.isSatisfiedBy(state))
    }

    @Test
    fun `a breakdown that cannot be read counts as nothing promised`() {
        // An identifier the network never declared: the station may well hold
        // an electric bike, and nothing here can say so. Null is not zero, and
        // the caller must not draw a nought (SPEC §7.1).
        val filter = BikeKindFilter(WantedBikeKind.Electric, lyon)
        val state = availability(5, mapOf("mechanical" to 4, "431" to 1))

        assertNull(filter.bikesAt(state))
        assertFalse(filter.isSatisfiedBy(state))
    }

    @Test
    fun `a station with no state at all promises nothing either`() {
        assertNull(BikeKindFilter(WantedBikeKind.Mechanical, lyon).bikesAt(null))
    }

    // ------------------------------------------------- what stays unfiltered --

    @Test
    fun `the station split takes no kind, so a filter cannot reach it`() {
        // A negative requirement, and therefore worth a test of its own: a
        // station's sheet and a journey's detail show BOTH counts whatever was
        // asked for elsewhere (SPEC §7.2, §7.4.1). They answer "what is waiting
        // there", and a filter is a question asked on another screen. The day
        // somebody wires a kind into this function, this fails.
        val split = Class.forName("io.github.mgdx.rouelibre.core.station.BikeSplitKt")
            .declaredMethods
            .single { it.name == "splitBikesByKind" }

        assertEquals(
            listOf(Map::class.java, Int::class.javaPrimitiveType, Map::class.java),
            split.parameterTypes.toList(),
        )
    }
}
