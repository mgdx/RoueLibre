package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the station list offers when it holds nothing (SPEC §7.2).
 *
 * It used to offer one thing only: "pull the list down to fetch the network's
 * stations". On a phone serving no conurbation that gesture asks nothing of
 * anybody — there is no network to ask — and it came back to the same empty
 * screen without a word. The map, looking at the same phone at the same
 * instant, says "Which city?" and offers the chooser; the list now says the
 * same thing in the same words.
 *
 * The invitation to refresh keeps the case it was written for: a city is in
 * service and its availability has not arrived yet.
 */
class EmptyListWithoutACityTest {

    @Test
    fun `an empty cache without a city sends the reader to the chooser`() {
        assertEquals(
            EmptyListOffer.ChooseCity,
            offerForEmptyList(Emptiness.NothingLoaded, cityChosen = false),
        )
    }

    @Test
    fun `an empty cache with a city still invites a refresh`() {
        assertEquals(
            EmptyListOffer.Refresh,
            offerForEmptyList(Emptiness.NothingLoaded, cityChosen = true),
        )
    }

    @Test
    fun `a fruitless search is cleared, city or no city`() {
        // The field is what stands in the way, and it does so whether a
        // conurbation is in service or not.
        listOf(true, false).forEach { chosen ->
            assertEquals(
                EmptyListOffer.ClearSearch,
                offerForEmptyList(Emptiness.NoMatch, cityChosen = chosen),
            )
        }
    }

    @Test
    fun `a list with stations in it offers nothing at all`() {
        listOf(true, false).forEach { chosen ->
            assertEquals(
                EmptyListOffer.None,
                offerForEmptyList(Emptiness.None, cityChosen = chosen),
            )
        }
    }

    /**
     * The map's own words, and not a second set written for this screen.
     *
     * Two screens diagnosing the same phone must not word it differently, and
     * a sentence duplicated is a sentence that drifts across thirty-one
     * translations.
     */
    @Test
    fun `the empty list borrows the map's sentence and the map's button`() {
        listOf(
            "R.string.map_needs_city_title",
            "R.string.map_needs_city_message",
            "R.string.city_choose",
            "showCityChooser()",
        ).forEach { expected ->
            assertTrue(
                "The empty station list no longer offers $expected.",
                fragment.contains(expected),
            )
        }
    }

    /** `app/src/main/java`, the sibling of the resources the build hands over. */
    private val fragment by lazy {
        val resources = File(
            checkNotNull(System.getProperty("rouelibre.locales")) {
                "The resource directory was not handed to the test."
            },
        )
        File(
            resources.parentFile,
            "java/io/github/mgdx/rouelibre/ui/stations/StationListFragment.kt",
        ).readText()
    }
}
