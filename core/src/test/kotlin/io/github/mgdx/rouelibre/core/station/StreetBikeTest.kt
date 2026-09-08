package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a bike outside stations says of itself, once read against the type
 * table (SPEC §4.1, §7.2.1).
 *
 * The figures are those of the live feeds read on 8 September 2026: nextbike
 * Berlin's ranges of zero beside a median charge of 0.67, Vienna's 30,000 of
 * 60,000 m. A wrong charge is a promise about a battery the rider will find
 * flat, so the rules under test are the ones that keep the sheet silent rather
 * than wrong.
 */
class StreetBikeTest {

    private val berlin = Coordinates(52.52, 13.405)

    private fun bike(typeId: String? = "348", ratio: Double? = null, range: Int? = null) =
        StreetBike(
            id = "bike-1",
            position = berlin,
            vehicleTypeId = typeId,
            chargeRatio = ratio,
            rangeMetres = range,
        )

    /** nextbike's table: numbered types, one of them a scooter. */
    private val types = mapOf(
        "346" to VehicleKind.Mechanical,
        "348" to VehicleKind.Electric,
        "350" to VehicleKind.Other,
    )

    // ------------------------------------------------------------- charge --

    @Test
    fun `the percentage wins over the range when both are published`() {
        // Vienna publishes both consistently; the percentage is the figure
        // that holds up across producers, so it is the one read first.
        val charge = bike(ratio = 0.5, range = 30_000).charge(maxRangeMetres = 60_000)

        assertEquals(BikeCharge.Ratio(0.5), charge)
    }

    @Test
    fun `the range is stated when it and the type's maximum are both above zero`() {
        val charge = bike(range = 30_000).charge(maxRangeMetres = 60_000)

        assertEquals(BikeCharge.Range(30_000), charge)
    }

    @Test
    fun `a range without a maximum behind it says nothing`() {
        // A type declaring no max_range_meters gives the range no scale to be
        // read on.
        assertNull(bike(range = 30_000).charge(maxRangeMetres = null))
        assertNull(bike(range = 30_000).charge(maxRangeMetres = 0))
    }

    @Test
    fun `a bike publishing neither figure has no charge to state`() {
        assertNull(bike().charge(maxRangeMetres = 60_000))
    }

    // --------------------------------------------------------------- kind --

    @Test
    fun `the kind is read through the network's table`() {
        assertEquals(VehicleKind.Electric, bike(typeId = "348").kind(types))
        assertEquals(VehicleKind.Mechanical, bike(typeId = "346").kind(types))
    }

    @Test
    fun `a type the table does not know is a mechanical bike`() {
        // One bike drawn on its own, counted nowhere: the plain bike is the
        // drawing that promises the least.
        assertEquals(VehicleKind.Mechanical, bike(typeId = "999").kind(types))
        assertEquals(VehicleKind.Mechanical, bike(typeId = null).kind(types))
    }

    // -------------------------------------------------------------- shown --

    @Test
    fun `a scooter is dropped and an undeclared type is kept`() {
        val scooter = bike(typeId = "350")
        val electric = bike(typeId = "348")
        val undeclared = bike(typeId = "999")
        val untyped = bike(typeId = null)

        val shown = streetBikesShown(listOf(scooter, electric, undeclared, untyped), types)

        assertEquals(listOf(electric, undeclared, untyped), shown)
    }
}
