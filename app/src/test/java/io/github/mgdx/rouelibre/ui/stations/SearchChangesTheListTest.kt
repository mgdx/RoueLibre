package io.github.mgdx.rouelibre.ui.stations

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * When a search hands the reader another list, and when it hands back the same
 * one (SPEC §7.6).
 *
 * What rests on the answer is where the list is shown from. A search that keeps
 * other stations is another list and is shown from its first row: the field
 * used to keep the row the list was anchored on and find it again among the
 * matches, so three matches came up shown from the one 1.3 km away, and
 * clearing the field brought the whole list back still shown from it — with the
 * station 40 m off somewhere above the top of the screen.
 *
 * The other half matters as much: the field is written to again when the screen
 * turns over, and the availability is refreshed every minute. Neither changes
 * the question asked, and neither may carry the reader off what they were
 * reading.
 */
class SearchChangesTheListTest {

    @Test
    fun `a first search hands another list`() {
        assertTrue(StationsUiState().searchWouldChangeTheList("rue"))
    }

    @Test
    fun `narrowing a search hands another list`() {
        assertTrue(StationsUiState(query = "rue").searchWouldChangeTheList("rue de"))
    }

    @Test
    fun `clearing the field hands another list`() {
        assertTrue(StationsUiState(query = "e").searchWouldChangeTheList(""))
    }

    @Test
    fun `the same question asked again hands back the same list`() {
        // The field restored as it was when the screen turns over: a keystroke
        // this screen never received.
        assertFalse(StationsUiState(query = "rue").searchWouldChangeTheList("rue"))
    }

    @Test
    fun `an empty field left alone hands back the same list`() {
        assertFalse(StationsUiState().searchWouldChangeTheList(""))
    }

    @Test
    fun `fresher counts under the same search hand back the same list`() {
        // The refresh that comes round every minute: the state is another one,
        // the stations shown are not.
        val refreshed = StationsUiState(
            query = "rue",
            fetchedAt = Instant.parse("2026-08-24T10:00:00Z"),
            hasLoadedOnce = true,
            orderingOrigin = Coordinates(50.6292, 3.0573),
        )
        assertFalse(refreshed.searchWouldChangeTheList("rue"))
    }
}
