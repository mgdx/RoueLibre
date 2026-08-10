package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of placing a house number along a street.
 *
 * Acceptance criterion 10 of the specification asks that an address with a
 * house number in a long thoroughfare be located within fifty metres. The
 * streets in this test are therefore of real lengths: a kilometre-long
 * thoroughfare, where falling back on the middle would cost several hundred
 * metres.
 */
class HouseNumberResolutionTest {

    /** The starting point of the test thoroughfare, south of Lille. */
    private val streetStart = Coordinates(50.6200, 3.0600)

    /**
     * A straight thoroughfare about a kilometre long running north, numbered
     * the French way: odds on one side, evens on the other, offset by twenty
     * metres — the width of the roadway.
     */
    private fun straightStreet(numbers: IntRange): List<KnownHouseNumber> = numbers.map { number ->
        val progress = number / 100.0
        KnownHouseNumber(
            number = number,
            suffix = "",
            position = Coordinates(
                latitude = streetStart.latitude + progress * 0.009,
                longitude = streetStart.longitude + if (number % 2 == 0) 0.00028 else 0.0,
            ),
        )
    }

    private val streetCentre = Coordinates(50.6245, 3.0601)

    @Test
    fun `a number present in the index is returned as it stands`() {
        val known = straightStreet(1..100)
        val resolved = resolveHouseNumber(42, "", known, streetCentre)

        assertEquals(PositionPrecision.Exact, resolved.precision)
        assertEquals(
            known.first { it.number == 42 }.position,
            resolved.coordinates,
        )
    }

    @Test
    fun `an absent number is interpolated between its neighbours`() {
        val complete = straightStreet(1..100)
        val withGap = complete.filterNot { it.number == 51 }

        val resolved = resolveHouseNumber(51, "", withGap, streetCentre)
        val truth = complete.first { it.number == 51 }.position

        assertEquals(PositionPrecision.Interpolated, resolved.precision)
        // Interpolating between 49 and 53 must land within metres of 51.
        assertTrue(
            "interpolated ${resolved.coordinates.distanceInMetresTo(truth)} m away",
            resolved.coordinates.distanceInMetresTo(truth) < 15.0,
        )
        // And above all: far better than the middle of the street, which is
        // the fallback this interpolation exists to avoid.
        assertTrue(
            resolved.coordinates.distanceInMetresTo(truth) <
                streetCentre.distanceInMetresTo(truth),
        )
    }

    @Test
    fun `the interpolation stays on the right side of the roadway`() {
        // Odds are on one side, evens on the other: interpolating 51 between
        // 50 and 52 would put it on the opposite pavement.
        val known = straightStreet(1..100).filterNot { it.number == 51 }
        val resolved = resolveHouseNumber(51, "", known, streetCentre)
        val oddSideLongitude = streetStart.longitude

        assertEquals(oddSideLongitude, resolved.coordinates.longitude, 1e-9)
    }

    @Test
    fun `a single known neighbour serves as a landmark, and says so`() {
        val known = straightStreet(1..10)
        val resolved = resolveHouseNumber(15, "", known, streetCentre)

        assertEquals(PositionPrecision.NearestKnown, resolved.precision)
        assertEquals(known.first { it.number == 9 }.position, resolved.coordinates)
    }

    @Test
    fun `a neighbour too far away is useless and leaves the street's point`() {
        // Returning number 9's position for a 500 would suggest a precision
        // that does not exist.
        val resolved = resolveHouseNumber(500, "", straightStreet(1..10), streetCentre)

        assertEquals(PositionPrecision.StreetOnly, resolved.precision)
        assertEquals(streetCentre, resolved.coordinates)
    }

    @Test
    fun `a street with no numbers at all returns its representative point`() {
        val resolved = resolveHouseNumber(12, "", emptyList(), streetCentre)

        assertEquals(PositionPrecision.StreetOnly, resolved.precision)
        assertEquals(streetCentre, resolved.coordinates)
    }

    @Test
    fun `a number typed without a mark accepts the known mark`() {
        val bis = KnownHouseNumber(12, "bis", Coordinates(50.6250, 3.0605))
        val resolved = resolveHouseNumber(12, "", listOf(bis), streetCentre)

        assertEquals(PositionPrecision.Exact, resolved.precision)
        assertEquals(bis.position, resolved.coordinates)
    }

    @Test
    fun `the mark typed wins over the bare number`() {
        val nu = KnownHouseNumber(12, "", Coordinates(50.6250, 3.0605))
        val bis = KnownHouseNumber(12, "bis", Coordinates(50.6252, 3.0606))
        val resolved = resolveHouseNumber(12, "bis", listOf(nu, bis), streetCentre)

        assertEquals(bis.position, resolved.coordinates)
    }
}
