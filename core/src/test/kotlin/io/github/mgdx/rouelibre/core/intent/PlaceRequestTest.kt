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
import org.junit.Assert.assertTrue
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
            parsePlaceUri("geo:0,0?q=rue%20de%20l'H%C3%B4pital"),
        )
    }

    @Test
    fun `an escaped letter spanning two bytes is one letter`() {
        // A URI escapes UTF-8 bytes: "é" travels as two of them. Read one by
        // one they became "Ã©", which is how a station named "Pédaler" came
        // back from a navigation application handover (SPEC §7.4).
        assertEquals(
            PlaceRequest.Point(lille, "Église Saint-Maurice"),
            parsePlaceUri("geo:0,0?q=50.6371,3.0630(%C3%89glise%20Saint-Maurice)"),
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
        // What the "share" button of Google Maps writes today: the named
        // place, then the coordinates. Its host is `www.google.com`, declared
        // in the manifest beside the historical `maps.google.com`.
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("https://www.google.com/maps/place/Grand+Place/@50.6371,3.0630,17z"),
        )
        assertEquals(
            PlaceRequest.Point(lille),
            parsePlaceUri("https://maps.google.com/maps?q=50.6371,3.0630"),
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

    // Two of the landmarks the real index answered the report's sentence with,
    // and the reason it did: their proper name holds the town's own name, so
    // the one word "lille" matches them in full. They stand in the corpus so
    // that a shared sentence has to beat them rather than avoid them.
    private val hei = street("HEI Lille - Junia", "Lille", lille)
    private val portDeLille = street("Port de Lille", "Lille", lille)

    private val corpus = listOf(
        toul,
        onzeNovembre,
        mercier,
        victorHugo,
        nationaleLille,
        nationaleRoubaix,
        hei,
        portDeLille,
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
    private fun destinationOf(text: String, matching: WordMatching): SearchableStreet? =
        streetsIn(text, matching).firstOrNull()

    /** The same resolution, kept whole: what a list is offered from. */
    private fun streetsIn(text: String, matching: WordMatching): List<SearchableStreet> {
        val request = findPlaceInText(text) as? PlaceRequest.Search ?: return emptyList()
        return rankStreets(
            corpus,
            normalizer.parseQuery(request.text),
            normalizer.stopWords,
            // The map's centre, as on the device: it decides between two
            // equally good matches, never against a better one.
            origin = lille,
            limit = 8,
            matching = matching,
        ).map { it.street }
    }

    /**
     * What a shared text offers, the way the application offers it (SPEC §7.8).
     *
     * The finished text is asked for first, and its answer is the journey. Only
     * where it answers nothing is the sentence read through, and what that
     * brings back is a list the user chooses from.
     */
    private fun candidatesOf(text: String): List<SearchableStreet> {
        val found = destinationOf(text, WordMatching.WholeWords)
        if (found != null) return listOf(found)
        return streetsIn(text, WordMatching.WholeWordsInSentence)
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
    fun `an address written into a sentence is offered rather than lost`() {
        // How an address is really shared: a phrase around it. The finished
        // text finds nothing there, and the sentence read through offers the
        // street it names — the street in Lille first, the namesake in Roubaix
        // behind it, since the sentence's own words say nothing either way.
        val sentence = "Rendez-vous ici : 12 rue Nationale, Lille"

        assertNull(destinationOf(sentence, WordMatching.WholeWords))
        // First, and not merely present: the street answers three words of the
        // sentence where the landmarks named after the town answer one.
        assertEquals(nationaleLille, candidatesOf(sentence).firstOrNull())
        assertTrue(candidatesOf(sentence).size > 1)
        // And without the municipality, where the town's name no longer helps
        // anybody: the report's second reading.
        assertEquals(
            nationaleLille,
            candidatesOf("Rendez-vous ici : 12 rue Nationale").firstOrNull(),
        )
    }

    @Test
    fun `the address alone is resolved exactly as it was`() {
        // The two shapes that already worked: the sentence path must never be
        // reached for them, and their answer must not move by a street.
        assertEquals(
            listOf(nationaleLille),
            candidatesOf("12 rue Nationale, Lille"),
        )
        assertEquals(
            listOf(nationaleLille),
            candidatesOf("12 rue Nationale\n59000 Lille"),
        )
    }

    @Test
    fun `a sentence naming no address is still refused`() {
        // Reading through the words around an address must not turn a text
        // holding none into a destination: nothing is offered, and the screen
        // says so (SPEC §7.8).
        assertEquals(emptyList<SearchableStreet>(), candidatesOf("coucou"))
        assertEquals(emptyList<SearchableStreet>(), candidatesOf("on se voit demain"))
        assertEquals(emptyList<SearchableStreet>(), candidatesOf("merci beaucoup"))
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
