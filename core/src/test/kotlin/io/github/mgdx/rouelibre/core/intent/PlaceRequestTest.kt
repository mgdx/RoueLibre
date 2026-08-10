package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de l'analyse des lieux reçus d'une autre application (SPEC §7.8).
 *
 * Les formes éprouvées ici ne sont pas imaginées : ce sont celles qu'émettent
 * réellement les applications de cartographie, de messagerie et d'annuaire.
 * Le critère d'acceptation 12 en dépend.
 */
class PlaceRequestTest {

    /** Grand-Place de Lille. */
    private val lille = Coordinates(50.6371, 3.0630)

    @Test
    fun `a geo uri with coordinates gives a point`() {
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630"))
    }

    @Test
    fun `the zoom parameter is ignored`() {
        // Il dit comment regarder, pas où aller.
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630?z=17"))
    }

    @Test
    fun `the query wins over the conventional point`() {
        // « geo:0,0 » veut dire « le lieu est dans la requête » ; le prendre au
        // pied de la lettre enverrait au large du golfe de Guinée.
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
        // Encore émis par de nombreuses applications (SPEC §7.8).
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
        // Ces liens ne parviennent à l'application que si l'utilisateur
        // l'autorise dans les paramètres du système (SPEC §7.8).
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
        // Un lien raccourci ne dit où il mène qu'après redirection, et la
        // suivre ferait sortir une requête vers un tiers.
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
        // Le cas le plus fréquent : une adresse reçue par messagerie.
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
        // « 12,50 » est un prix, pas une position. La notation décimale à
        // virgule est indistinguable d'un couple d'entiers, donc écartée.
        assertEquals(
            PlaceRequest.Search("Ça coûte 12,50 euros"),
            findPlaceInText("Ça coûte 12,50 euros"),
        )
    }
}
