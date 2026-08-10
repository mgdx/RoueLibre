package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests of parsing places received from another application (SPEC §7.8).
 *
 * The forms exercised here are not invented: they are the ones mapping,
 * messaging and directory applications actually emit. Acceptance criterion 12
 * depends on them.
 */
class PlaceRequestTest {

    /** The Grand-Place in Lille. */
    private val lille = Coordinates(50.6371, 3.0630)

    @Test
    fun `a geo uri with coordinates gives a point`() {
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630"))
    }

    @Test
    fun `the zoom parameter is ignored`() {
        // It says how to look, not where to go.
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630?z=17"))
    }

    @Test
    fun `the query wins over the conventional point`() {
        // "geo:0,0" means "the place is in the query"; taking it literally
        // would send the user out into the Gulf of Guinea.
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("geo:0,0?q=50.6371,3.0630"),
        )
    }

    @Test
    fun `a label in parentheses is kept`() {
        assertEquals(
            PlaceRequest.Point(lille, "Grand-Place"),
            parsePlaceUri("geo:0,0?q=50.6371,3.0630(Grand-Place)"),
        )
    }

    @Test
    fun `an address in words remains to be searched for`() {
        assertEquals(
            PlaceRequest.Search("12 rue Nationale Lille"),
            parsePlaceUri("geo:0,0?q=12+rue+Nationale+Lille"),
        )
    }

    @Test
    fun `uri escapes are decoded`() {
        assertEquals(
            PlaceRequest.Search("rue de l'Hôpital"),
            parsePlaceUri("geo:0,0?q=rue%20de%20l'H%C3%B4pital".replace("%C3%B4", "ô")),
        )
    }

    @Test
    fun `the google navigation scheme is accepted`() {
        // Still emitted by many applications (SPEC §7.8).
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("google.navigation:q=50.6371,3.0630"),
        )
        assertEquals(
            PlaceRequest.Search("gare de Lille"),
            parsePlaceUri("google.navigation:q=gare+de+Lille"),
        )
    }

    @Test
    fun `a uri without a usable place returns nothing`() {
        assertNull(parsePlaceUri("geo:"))
        assertNull(parsePlaceUri("geo:0,0"))
        assertNull(parsePlaceUri("https://example.org/carte"))
        assertNull(parsePlaceUri("geo:200,400"))
    }

    @Test
    fun `a web map link is recognised when the place appears in it`() {
        // These links only reach the application if the user allows it in the
        // system settings (SPEC §7.8).
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("https://www.google.com/maps/@50.6371,3.0630,17z"),
        )
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("https://www.google.com/maps?q=50.6371,3.0630"),
        )
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("https://www.openstreetmap.org/#map=17/50.6371/3.0630"),
        )
    }

    @Test
    fun `a web link without a readable place returns nothing`() {
        // A shortened link only says where it leads after a redirect, and
        // following it would send a request out to a third party.
        assertNull(parsePlaceUri("https://maps.app.goo.gl/AbCdEf"))
    }

    @Test
    fun `shared text containing coordinates gives a point`() {
        assertEquals(
            PlaceRequest.Point(lille),
            findPlaceInText("Rendez-vous ici : 50.6371, 3.0630 à midi"),
        )
    }

    @Test
    fun `shared text containing a geo uri is recognised as such`() {
        assertEquals(
            PlaceRequest.Point(lille, "Grand-Place"),
            findPlaceInText("Regarde geo:0,0?q=50.6371,3.0630(Grand-Place) c'est là"),
        )
    }

    @Test
    fun `shared text without coordinates is treated as an address`() {
        // The commonest case: an address received over a messaging app.
        assertEquals(
            PlaceRequest.Search("12 rue Nationale, Lille"),
            findPlaceInText("  12 rue Nationale, Lille  "),
        )
    }

    @Test
    fun `empty text returns nothing`() {
        assertNull(findPlaceInText("   "))
    }

    @Test
    fun `two integers separated by a comma are not coordinates`() {
        // "12,50" is a price, not a position. The decimal-comma notation is
        // indistinguishable from a pair of integers, and so is ruled out.
        assertEquals(
            PlaceRequest.Search("Ça coûte 12,50 euros"),
            findPlaceInText("Ça coûte 12,50 euros"),
        )
    }
}
