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
    fun `une uri geo avec des coordonnées donne un point`() {
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630"))
    }

    @Test
    fun `le paramètre de zoom est ignoré`() {
        // Il dit comment regarder, pas où aller.
        assertEquals(PlaceRequest.Point(lille), parsePlaceUri("geo:50.6371,3.0630?z=17"))
    }

    @Test
    fun `la requête l'emporte sur le point de convention`() {
        // « geo:0,0 » veut dire « le lieu est dans la requête » ; le prendre au
        // pied de la lettre enverrait au large du golfe de Guinée.
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("geo:0,0?q=50.6371,3.0630"),
        )
    }

    @Test
    fun `un libellé entre parenthèses est conservé`() {
        assertEquals(
            PlaceRequest.Point(lille, "Grand-Place"),
            parsePlaceUri("geo:0,0?q=50.6371,3.0630(Grand-Place)"),
        )
    }

    @Test
    fun `une adresse en toutes lettres reste à chercher`() {
        assertEquals(
            PlaceRequest.Search("12 rue Nationale Lille"),
            parsePlaceUri("geo:0,0?q=12+rue+Nationale+Lille"),
        )
    }

    @Test
    fun `les échappements d'uri sont décodés`() {
        assertEquals(
            PlaceRequest.Search("rue de l'Hôpital"),
            parsePlaceUri("geo:0,0?q=rue%20de%20l'H%C3%B4pital".replace("%C3%B4", "ô")),
        )
    }

    @Test
    fun `le schéma google navigation est accepté`() {
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
    fun `une uri sans lieu exploitable ne rend rien`() {
        assertNull(parsePlaceUri("geo:"))
        assertNull(parsePlaceUri("geo:0,0"))
        assertNull(parsePlaceUri("https://example.org/carte"))
        assertNull(parsePlaceUri("geo:200,400"))
    }

    @Test
    fun `un lien web cartographique est reconnu quand le lieu y figure`() {
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
    fun `un lien web sans lieu lisible ne rend rien`() {
        // Un lien raccourci ne dit où il mène qu'après redirection, et la
        // suivre ferait sortir une requête vers un tiers.
        assertNull(parsePlaceUri("https://maps.app.goo.gl/AbCdEf"))
    }

    @Test
    fun `un texte partagé contenant des coordonnées donne un point`() {
        assertEquals(
            PlaceRequest.Point(lille),
            findPlaceInText("Rendez-vous ici : 50.6371, 3.0630 à midi"),
        )
    }

    @Test
    fun `un texte partagé contenant une uri geo est reconnu comme tel`() {
        assertEquals(
            PlaceRequest.Point(lille, "Grand-Place"),
            findPlaceInText("Regarde geo:0,0?q=50.6371,3.0630(Grand-Place) c'est là"),
        )
    }

    @Test
    fun `un texte partagé sans coordonnées est traité comme une adresse`() {
        // Le cas le plus fréquent : une adresse reçue par messagerie.
        assertEquals(
            PlaceRequest.Search("12 rue Nationale, Lille"),
            findPlaceInText("  12 rue Nationale, Lille  "),
        )
    }

    @Test
    fun `un texte vide ne rend rien`() {
        assertNull(findPlaceInText("   "))
    }

    @Test
    fun `deux entiers séparés par une virgule ne sont pas des coordonnées`() {
        // « 12,50 » est un prix, pas une position. La notation décimale à
        // virgule est indistinguable d'un couple d'entiers, donc écartée.
        assertEquals(
            PlaceRequest.Search("Ça coûte 12,50 euros"),
            findPlaceInText("Ça coûte 12,50 euros"),
        )
    }
}
