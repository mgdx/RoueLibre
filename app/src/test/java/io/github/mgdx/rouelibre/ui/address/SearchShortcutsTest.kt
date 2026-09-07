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
 * Three questions. The second is the one that would go wrong quietly: a row
 * offered for a place no journey can reach fails only once the route has been
 * asked for, by which time the user has been promised something. The third is
 * whether anything has been typed, which since 7 September 2026 takes every row
 * away — and it is a rule the list beyond this file leans on, so what it does
 * on an empty field is pinned as firmly as what it does on a full one.
 */
class SearchShortcutsTest {

    @Test
    fun `no place named leaves the three that need no place`() {
        assertEquals(
            listOf(SearchShortcut.MyPosition, SearchShortcut.Favourite, SearchShortcut.OnMap),
            searchShortcutsFor(home = null, work = null, coveredArea = LILLE, query = ""),
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
            searchShortcutsFor(
                home = aPlaceIn(LILLE),
                work = null,
                coveredArea = LILLE,
                query = "",
            ),
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
            searchShortcutsFor(
                home = null,
                work = aPlaceIn(LILLE),
                coveredArea = LILLE,
                query = "",
            ),
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
            searchShortcutsFor(
                home = aPlaceIn(LILLE),
                work = aPlaceIn(LILLE),
                coveredArea = LILLE,
                query = "",
            ),
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
            query = "",
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
            query = "",
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
                query = "",
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
                    val shortcuts = searchShortcutsFor(home, work, area, query = "")
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
    fun `on an empty field the ways that ask the data for nothing are never withdrawn`() {
        // The guarantee still holds over what is named and what is installed —
        // it is what keeps an empty field from ever facing an empty list. It no
        // longer holds over what is typed, which is the whole of the change of
        // 7 September 2026, and why the query is pinned at empty here.
        val places = listOf(null, aPlaceIn(LILLE), SavedPlace("Paris", Coordinates(48.85, 2.35)))
        for (home in places) {
            for (work in places) {
                for (area in listOf(null, LILLE)) {
                    assertTrue(
                        searchShortcutsFor(home, work, area, query = "").containsAll(
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

    @Test
    fun `one character takes every shortcut away`() {
        // Somebody who has begun to type has answered the question the rows
        // were asking. All five go, the two named places included.
        assertEquals(
            emptyList<SearchShortcut>(),
            searchShortcutsFor(
                home = aPlaceIn(LILLE),
                work = aPlaceIn(LILLE),
                coveredArea = LILLE,
                query = "r",
            ),
        )
    }

    @Test
    fun `a field holding nothing but spaces has asked nothing and keeps them`() {
        // The line the rest of the screen already draws: no search is run on a
        // blank query and no panel concludes anything about one. A space
        // brushed by accident must not leave a screen with nothing found and
        // nothing left to press.
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
                work = aPlaceIn(LILLE),
                coveredArea = LILLE,
                query = "   ",
            ),
        )
    }

    @Test
    fun `emptying the field brings all five back`() {
        // Nothing is taken away by typing, only put aside: the rows are a
        // function of the field's content, so clearing it restores exactly what
        // stood there before the first keystroke.
        val places = Triple(aPlaceIn(LILLE), aPlaceIn(LILLE), LILLE)
        val before = searchShortcutsFor(places.first, places.second, places.third, query = "")
        val whileTyping = searchShortcutsFor(places.first, places.second, places.third, "rue nat")
        val after = searchShortcutsFor(places.first, places.second, places.third, query = "")
        assertEquals(emptyList<SearchShortcut>(), whileTyping)
        assertEquals(before, after)
        assertEquals(5, after.size)
    }

    @Test
    fun `typing withdraws the rows without regard to what is named`() {
        val places = listOf(null, aPlaceIn(LILLE), SavedPlace("Paris", Coordinates(48.85, 2.35)))
        for (home in places) {
            for (work in places) {
                for (area in listOf(null, LILLE)) {
                    assertEquals(
                        emptyList<SearchShortcut>(),
                        searchShortcutsFor(home, work, area, query = "a"),
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
