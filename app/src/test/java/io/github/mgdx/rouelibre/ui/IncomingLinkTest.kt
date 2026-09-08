package io.github.mgdx.rouelibre.ui

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Telling a link nobody can read from no link at all (SPEC §7.8).
 *
 * Both used to arrive as the same `null`, and the caller read that as "nothing
 * to do": `geo:999,999` opened the application on its map and said nothing at
 * all. Every `VIEW` filter the manifest declares is a map link, so the two are
 * not the same thing, and the reading that separates them stays on the JVM —
 * no Android runtime is involved (SPEC §14).
 */
class IncomingLinkTest {

    /** The Grand-Place in Lille. */
    private val lille = Coordinates(50.6371, 3.0630)

    @Test
    fun `a link whose coordinates cannot be read is answered, not ignored`() {
        // The three the report opened the application with: out of bounds,
        // written in letters, and past the poles.
        assertEquals(IncomingRequest.Unreadable, linkReceived("geo:999,999"))
        assertEquals(IncomingRequest.Unreadable, linkReceived("geo:abc,def"))
        assertEquals(IncomingRequest.Unreadable, linkReceived("geo:-91.5,181.7"))
    }

    @Test
    fun `a web link whose place only a redirect would give is answered too`() {
        // A shortened link says nothing without a request to a third party,
        // which constraint C3 forbids: the user is told, rather than left in
        // front of a map that opened by itself.
        assertEquals(IncomingRequest.Unreadable, linkReceived("https://maps.app.goo.gl/AbCdEf"))
    }

    @Test
    fun `an intent carrying no link at all asks for nothing`() {
        // The application opened from its own icon: there is no link to fail to
        // read, and a message would come out of nowhere.
        assertNull(linkReceived(null))
        assertNull(linkReceived("   "))
    }

    @Test
    fun `the links that already worked are still places`() {
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Point(lille)),
            linkReceived("geo:50.6371,3.0630"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Point(lille, "Grand-Place")),
            linkReceived("geo:0,0?q=50.6371,3.0630(Grand-Place)"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Search("12 rue Nationale Lille")),
            linkReceived("geo:0,0?q=12+rue+Nationale+Lille"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Search("12 rue Nationale Lille")),
            linkReceived("geo:0,0?q=12%20rue%20Nationale%20Lille"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Point(lille)),
            linkReceived("google.navigation:q=50.6371,3.0630"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Point(lille)),
            linkReceived("https://www.openstreetmap.org/#map=17/50.6371/3.0630"),
        )
        assertEquals(
            IncomingRequest.Place(PlaceRequest.Point(lille)),
            linkReceived("https://www.google.com/maps/@50.6371,3.0630,17z"),
        )
    }
}
