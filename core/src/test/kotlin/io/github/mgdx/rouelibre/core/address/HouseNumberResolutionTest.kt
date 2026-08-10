package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du placement d'un numéro dans une voie.
 *
 * Le critère d'acceptation 10 du SPEC demande qu'une adresse avec numéro dans
 * une longue artère soit localisée à moins de cinquante mètres. Les rues de ce
 * test sont donc de vraies longueurs : une artère d'un kilomètre, où retomber
 * sur le centre coûterait plusieurs centaines de mètres.
 */
class HouseNumberResolutionTest {

    /** Point de départ de l'artère de test, au sud de Lille. */
    private val streetStart = Coordinates(50.6200, 3.0600)

    /**
     * Une artère rectiligne d'environ un kilomètre vers le nord, numérotée à
     * la française : impairs d'un côté, pairs de l'autre, décalés de vingt
     * mètres — la largeur de la chaussée.
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
        // Interpoler entre le 49 et le 53 doit tomber à quelques mètres du 51.
        assertTrue(
            "interpolation à ${resolved.coordinates.distanceInMetresTo(truth)} m",
            resolved.coordinates.distanceInMetresTo(truth) < 15.0,
        )
        // Et surtout : bien mieux que le centre de la rue, qui est le repli
        // que cette interpolation existe pour éviter.
        assertTrue(
            resolved.coordinates.distanceInMetresTo(truth) <
                streetCentre.distanceInMetresTo(truth),
        )
    }

    @Test
    fun `the interpolation stays on the right side of the roadway`() {
        // Les impairs sont d'un côté, les pairs de l'autre : interpoler le 51
        // entre le 50 et le 52 le placerait sur le trottoir d'en face.
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
        // Rendre la position du 9 pour un 500 laisserait croire à une
        // précision qui n'existe pas.
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
