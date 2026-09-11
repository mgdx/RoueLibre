package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a station's sheet says of the bikes standing there (SPEC §7.2).
 *
 * The figures are those of the live feeds read on 11 September 2026: nextbike
 * Munich's percentages from 0.2 to 1 on its 1,674 electric bikes, Fifteen's
 * ranges on Marseille's 2,162 and on Helsinki's 3,947 bikes with no battery
 * at all. The rules under test keep the sheet silent rather than wrong: a
 * charge listed is a promise to somebody about to walk there.
 */
class DockedBikeTest {

    /** nextbike's table: a mechanical type, an electric one, and a scooter. */
    private val types = mapOf(
        "346" to VehicleKind.Mechanical,
        "348" to VehicleKind.Electric,
        "360" to VehicleKind.Other,
    )

    private val maxRanges = mapOf("348" to 60_000)

    private fun bike(
        typeId: String? = "348",
        ratio: Double? = null,
        range: Int? = null,
        disabled: Boolean = false,
        reserved: Boolean = false,
        id: String = "bike",
    ) = DockedBike(
        id = id,
        stationId = "3140",
        vehicleTypeId = typeId,
        chargeRatio = ratio,
        rangeMetres = range,
        isDisabled = disabled,
        isReserved = reserved,
    )

    @Test
    fun `the charges are listed fullest first`() {
        val detail = chargesAtStation(
            listOf(bike(ratio = 0.4), bike(ratio = 0.92), bike(ratio = 0.78)),
            types,
            maxRanges,
        )

        assertEquals(
            listOf(BikeCharge.Ratio(0.92), BikeCharge.Ratio(0.78), BikeCharge.Ratio(0.4)),
            detail!!.charges,
        )
        assertEquals(0, detail.outOfService)
    }

    @Test
    fun `a range on a bike with no battery is a figure about nothing`() {
        // Helsinki: every one of its mechanical bikes carries a
        // current_range_meters. The table says what has a battery.
        val detail = chargesAtStation(
            listOf(bike(typeId = "346", range = 24_400), bike(typeId = "346", range = 40_000)),
            types,
            mapOf("346" to 40_000),
        )

        assertFalse(detail!!.hasSummary)
        assertNull(detail.bikes.first().charge)
    }

    @Test
    fun `a type the table does not know is left out of a count`() {
        val unknown = chargesAtStation(
            listOf(bike(typeId = "999", ratio = 0.5)),
            types,
            maxRanges,
        )!!
        val undeclared = chargesAtStation(
            listOf(bike(typeId = null, ratio = 0.5)),
            types,
            maxRanges,
        )!!

        assertFalse(unknown.hasSummary)
        assertFalse(undeclared.hasSummary)
        assertNull(unknown.bikes.single().kind)
    }

    @Test
    fun `a bike out of service or reserved is not on offer, and is counted apart`() {
        val detail = chargesAtStation(
            listOf(
                bike(ratio = 0.9, disabled = true),
                bike(ratio = 0.8, reserved = true),
                bike(typeId = "346", disabled = true),
                bike(ratio = 0.3),
            ),
            types,
            maxRanges,
        )

        assertEquals(listOf(BikeCharge.Ratio(0.3)), detail!!.charges)
        assertEquals(2, detail.outOfService)
    }

    @Test
    fun `a range is read on the same terms as a street bike's`() {
        // Marseille publishes a range and no percentage, with the type's
        // maximum behind it; nextbike publishes a range of zero, which the
        // parser has already dropped, so the percentage stands alone.
        val detail = chargesAtStation(
            listOf(bike(range = 2_800), bike(range = 28_800), bike(ratio = 0.5)),
            types,
            maxRanges,
        )

        assertEquals(
            listOf(BikeCharge.Ratio(0.5), BikeCharge.Range(28_800), BikeCharge.Range(2_800)),
            detail!!.charges,
        )
    }

    @Test
    fun `a range without a maximum behind it says nothing`() {
        assertFalse(chargesAtStation(listOf(bike(range = 28_800)), types, emptyMap())!!.hasSummary)
    }

    @Test
    fun `bikes with nothing to say in a summary are still listed`() {
        assertNull(chargesAtStation(emptyList(), types, maxRanges))
        assertNull(
            "a scooter is not a bike",
            chargesAtStation(listOf(bike(typeId = "360")), types, maxRanges),
        )

        val detail = chargesAtStation(listOf(bike(typeId = "346"), bike()), types, maxRanges)!!

        assertFalse(detail.hasSummary)
        assertEquals(2, detail.bikes.size)
    }

    @Test
    fun `the list names every bike, those on offer first`() {
        // Berlin's shape: numbered bikes, a mechanical one out of service, an
        // electric one booked, the rest on offer with the fullest first and
        // an unknown type naming no kind.
        val detail = chargesAtStation(
            listOf(
                bike(id = "m-off", typeId = "346", disabled = true),
                bike(id = "e-booked", ratio = 0.9, reserved = true),
                bike(id = "e-low", ratio = 0.3),
                bike(id = "m", typeId = "346"),
                bike(id = "e-full", ratio = 0.99),
                bike(id = "unknown", typeId = "999"),
            ),
            types,
            maxRanges,
        )!!

        assertEquals(
            listOf("e-full", "e-low", "m", "unknown", "e-booked", "m-off"),
            detail.bikes.map { it.id },
        )
        assertEquals(VehicleKind.Mechanical, detail.bikes[2].kind)
        assertNull(detail.bikes[3].kind)
        assertEquals(BikeCharge.Ratio(0.9), detail.bikes[4].charge)
        assertEquals(listOf(BikeCharge.Ratio(0.99), BikeCharge.Ratio(0.3)), detail.charges)
    }

    // -------------------------------------------------------------- label --

    @Test
    fun `an identifier past twenty characters keeps its two ends`() {
        assertEquals("3e279…5fbd2", abbreviateIdentifier("3e279687-add4-458d-8d37-5a6386b5fbd2"))
        assertEquals("fdifj…oigrg", abbreviateIdentifier("fdifjregoerigjrogsigersoigrg"))
        // nextbike's number painted on the frame is in the last five
        // characters, and the whole identifier fits under the ceiling.
        assertEquals("nextbike_bb_20911", abbreviateIdentifier("nextbike_bb_20911"))
        assertEquals("exactly-twenty-chars", abbreviateIdentifier("exactly-twenty-chars"))
    }

    @Test
    fun `the label is the identifier, abbreviated where it is long`() {
        val detail = chargesAtStation(
            listOf(
                bike(id = "nextbike_bb_20911"),
                bike(id = "3e279687-add4-458d-8d37-5a6386b5fbd2"),
            ),
            types,
            maxRanges,
        )!!

        assertEquals(listOf("nextbike_bb_20911", "3e279…5fbd2"), detail.bikes.map { it.label })
        assertEquals("3e279687-add4-458d-8d37-5a6386b5fbd2", detail.bikes.last().id)
    }
}
