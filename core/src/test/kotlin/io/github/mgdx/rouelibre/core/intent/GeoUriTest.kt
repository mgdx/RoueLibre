package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class GeoUriTest {

    @Test
    fun `a longitude near the Greenwich meridian is written in fixed decimals`() {
        assertEquals(
            "geo:51.500700,0.000100?q=51.500700,0.000100(Greenwich)",
            geoUri(Coordinates(51.5007, 1.0E-4), "Greenwich"),
        )
    }

    @Test
    fun `a small negative longitude keeps its sign and its decimals`() {
        assertEquals("-0.000990", geoDegrees(-0.00099))
    }

    @Test
    fun `a latitude near the equator is written in fixed decimals`() {
        assertEquals(
            "geo:0.000050,9.300000?q=0.000050,9.300000(Equator)",
            geoUri(Coordinates(5.0E-5, 9.3), "Equator"),
        )
    }

    @Test
    fun `negative coordinates are written with their sign`() {
        assertEquals(
            "geo:-33.856800,-151.215300?q=-33.856800,-151.215300(Opera)",
            geoUri(Coordinates(-33.8568, -151.2153), "Opera"),
        )
    }

    @Test
    fun `a locale writing a decimal comma does not reach the coordinates`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("50.637100", geoDegrees(50.6371))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `no character of the label can cut the query or close it early`() {
        val uri = geoUri(Coordinates(50.6371, 3.063), "Gare (Lille) & co + 1")
        val label = uri.substringAfter('(').substringBeforeLast(')')
        assertEquals("Gare%20%28Lille%29%20%26%20co%20%2B%201", label)
        assertFalse(label.any { it in "()&+ " })
    }

    @Test
    fun `an accented label is encoded as UTF-8 bytes`() {
        assertEquals("rue%20de%20l%27H%C3%B4pital", encodeGeoLabel("rue de l'Hôpital"))
    }

    @Test
    fun `the uri reads back as the point and label it was written from`() {
        val label = "Gare (Lille) & co + 1 — Hôpital"
        val request = parsePlaceUri(geoUri(Coordinates(-0.00099, 1.0E-4), label))
        assertEquals(PlaceRequest.Point(Coordinates(-0.00099, 1.0E-4), label), request)
    }
}
