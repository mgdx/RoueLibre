package io.github.mgdx.rouelibre.ui.address

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of which shortcuts a journey's end is offered, and in what order.
 *
 * Two questions, and the second is the one that would go wrong quietly: a row
 * offered for a place no journey can reach fails only once the route has been
 * asked for, by which time the user has been promised something.
 */
class SearchShortcutsTest {

    @Test
    fun `no place named leaves the three that need no place`() {
        assertEquals(
            listOf(SearchShortcut.MyPosition, SearchShortcut.Favourite, SearchShortcut.OnMap),
            searchShortcutsFor(home = null, work = null, coveredArea = LILLE),
        )
    }

    @Test
    fun `a home alone heads the list`() {
        assertEquals(
            listOf(
                SearchShortcut.Home,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            searchShortcutsFor(home = aPlaceIn(LILLE), work = null, coveredArea = LILLE),
        )
    }

    @Test
    fun `a work alone comes before the position and after nothing`() {
        assertEquals(
            listOf(
                SearchShortcut.Work,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            searchShortcutsFor(home = null, work = aPlaceIn(LILLE), coveredArea = LILLE),
        )
    }

    @Test
    fun `both places named make five, home first`() {
        assertEquals(
            listOf(
                SearchShortcut.Home,
                SearchShortcut.Work,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            searchShortcutsFor(home = aPlaceIn(LILLE), work = aPlaceIn(LILLE), coveredArea = LILLE),
        )
    }

    @Test
    fun `a home beyond the data goes without taking the work with it`() {
        // Somebody who has moved city keeps working where they worked: the two
        // places are judged one by one, never as a pair.
        val shortcuts = searchShortcutsFor(
            home = SavedPlace("Somewhere in Paris", Coordinates(48.8566, 2.3522)),
            work = aPlaceIn(LILLE),
            coveredArea = LILLE,
        )
        assertEquals(
            listOf(
                SearchShortcut.Work,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            shortcuts,
        )
    }

    @Test
    fun `a work beyond the data goes and the home stays`() {
        val shortcuts = searchShortcutsFor(
            home = aPlaceIn(LILLE),
            work = SavedPlace("Somewhere in Paris", Coordinates(48.8566, 2.3522)),
            coveredArea = LILLE,
        )
        assertEquals(
            listOf(
                SearchShortcut.Home,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            shortcuts,
        )
    }

    @Test
    fun `no city chosen withdraws nothing`() {
        // No box is not a small box: the application has no ground to call a
        // point unreachable, and this is where the ways round a missing address
        // index matter most.
        assertEquals(
            listOf(
                SearchShortcut.Home,
                SearchShortcut.Work,
                SearchShortcut.MyPosition,
                SearchShortcut.Favourite,
                SearchShortcut.OnMap,
            ),
            searchShortcutsFor(
                home = aPlaceIn(LILLE),
                work = SavedPlace("Somewhere in Paris", Coordinates(48.8566, 2.3522)),
                coveredArea = null,
            ),
        )
    }

    @Test
    fun `the order never depends on what is named`() {
        val places = listOf(null, aPlaceIn(LILLE))
        val areas = listOf(null, LILLE)
        for (home in places) {
            for (work in places) {
                for (area in areas) {
                    val shortcuts = searchShortcutsFor(home, work, area)
                    assertEquals(
                        "the rows keep the order they are declared in",
                        shortcuts.sortedBy { it.ordinal },
                        shortcuts,
                    )
                }
            }
        }
    }

    @Test
    fun `the ways that ask the data for nothing are never withdrawn`() {
        // What AddressSearchPanel leans on: a screen filling a journey's end
        // always has something to press, whatever is named and whatever is
        // installed. Were this to become false, panelFor would answer None to a
        // screen showing an empty list.
        val places = listOf(null, aPlaceIn(LILLE), SavedPlace("Paris", Coordinates(48.85, 2.35)))
        for (home in places) {
            for (work in places) {
                for (area in listOf(null, LILLE)) {
                    assertTrue(
                        searchShortcutsFor(home, work, area).containsAll(
                            listOf(
                                SearchShortcut.MyPosition,
                                SearchShortcut.Favourite,
                                SearchShortcut.OnMap,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun aPlaceIn(area: BoundingBox) = SavedPlace("12 Rue Nationale", area.centre)

    private companion object {
        val LILLE = BoundingBox(south = 50.55, west = 2.95, north = 50.70, east = 3.20)
    }
}
