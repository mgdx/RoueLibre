package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.address.SearchableStreet
import io.github.mgdx.rouelibre.core.address.WordMatching
import io.github.mgdx.rouelibre.core.address.parseQuery
import io.github.mgdx.rouelibre.core.address.rankStreets
import io.github.mgdx.rouelibre.core.address.testNormalizer
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    // ------------------------------------ what the shared text then finds --

    /**
     * A handful of streets standing in for the index.
     *
     * Three of them are the ones a shared sentence was seen to be fitted to:
     * "on" began "Onze", "merci" began "Mercier", "20" began "208bis". The
     * SQLite side of the same search is exercised in `AddressIndexTest`, on a
     * device.
     */
    private val normalizer = testNormalizer()

    private var nextId = 1L

    private fun street(name: String, city: String, position: Coordinates): SearchableStreet {
        val split = normalizer.analyse(name)
        return SearchableStreet(
            id = nextId++,
            normalizedType = split.streetType,
            normalizedName = split.properName,
            normalizedCity = normalizer.normalize(city),
            position = position,
        )
    }

    private val roubaix = Coordinates(50.6942, 3.1746)

    private val toul = street("Rue de Toul", "Lille", Coordinates(50.6318, 3.0595))
    private val onzeNovembre = street("Rue du Onze Novembre 1918", "Lille", lille)
    private val mercier = street("Rue Alphonse Mercier", "Lille", lille)
    private val victorHugo = street("Cour du 208bis bvd Victor Hugo", "Lille", lille)
    private val nationaleLille = street("Rue Nationale", "Lille", lille)
    private val nationaleRoubaix = street("Rue Nationale", "Roubaix", roubaix)

    private val corpus = listOf(
        toul,
        onzeNovembre,
        mercier,
        victorHugo,
        nationaleLille,
        nationaleRoubaix,
    )

    /**
     * Resolves a shared text the way the application does.
     *
     * Same parsing, same ranking, same reading of the first result: what makes
     * this path a share rather than a search box is the one thing stated here —
     * the text is finished (SPEC §7.8).
     *
     * @return the street retained, or `null` if the text designates none.
     */
    private fun destinationOf(text: String, matching: WordMatching): SearchableStreet? {
        val request = findPlaceInText(text) as? PlaceRequest.Search ?: return null
        return rankStreets(
            corpus,
            normalizer.parseQuery(request.text),
            normalizer.stopWords,
            // The map's centre, as on the device: it decides between two
            // equally good matches, never against a better one.
            origin = lille,
            limit = 8,
            matching = matching,
        ).firstOrNull()?.street
    }

    @Test
    fun `a text naming no address designates nothing`() {
        // Nothing in these sentences is a street, and the application has to
        // say so rather than propose the street they happen to begin like.
        assertNull(destinationOf("on se voit demain", WordMatching.WholeWords))
        assertNull(destinationOf("merci beaucoup", WordMatching.WholeWords))
        assertNull(destinationOf("zzzzzzz qqqqqqq", WordMatching.WholeWords))
        assertNull(destinationOf("20", WordMatching.WholeWords))
    }

    @Test
    fun `a word that merely begins a street name is a destination only while typing`() {
        // The very confusion the search box lives on, and the share path must
        // not: "on" is the beginning of "Onze Novembre" for someone still
        // typing, and a word of a sentence for someone who has finished.
        assertEquals(onzeNovembre, destinationOf("on", WordMatching.Prefixes))
        assertNull(destinationOf("on", WordMatching.WholeWords))
    }

    @Test
    fun `a complete address finds the street it names`() {
        assertEquals(toul, destinationOf("20 rue de Toul, Lille", WordMatching.WholeWords))
        // And the number is read, rather than searched for as a word.
        assertEquals(20, normalizer.parseQuery("20 rue de Toul, Lille").houseNumber)
    }

    @Test
    fun `an address whose municipality closes it finds the one in that municipality`() {
        // Two streets of the same name: the municipality in the suffix decides,
        // and it decides against proximity — the reference point is Lille.
        assertEquals(
            nationaleRoubaix,
            destinationOf("12 rue Nationale, Roubaix", WordMatching.WholeWords),
        )
        assertEquals(
            nationaleLille,
            destinationOf("12 rue Nationale, Lille", WordMatching.WholeWords),
        )
    }

    @Test
    fun `a shared address is resolved as the search box resolves it`() {
        // The complaint that opened this: one index, two paths, two answers.
        // On an address, they must not differ.
        val typed = destinationOf("20 rue de Toul, Lille", WordMatching.Prefixes)
        assertNotNull(typed)
        assertEquals(typed, destinationOf("20 rue de Toul, Lille", WordMatching.WholeWords))
    }
}
