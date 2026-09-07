package io.github.mgdx.rouelibre.ui.address

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.address.AddressEntryKind
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of what the address search shows instead of results, and of what it
 * leaves standing beside it.
 *
 * The second question is the one that cost a round trip on the device: the
 * three shortcuts were taken away under every message alike, including on an
 * installation with no data at all — where pointing at the map and one's own
 * position were the only two ways left of naming a journey's end, and neither
 * of them asks the address index for anything.
 *
 * Since 7 September 2026 the shortcuts also go as soon as anything is typed
 * (SPEC §7.3), so the two arguments are no longer free of one another: a state
 * carrying a query goes with an empty list, and a state carrying none goes with
 * a full one wherever a journey's end is being named. The cases below are
 * written in those pairs — a pair that cannot occur proves nothing about the
 * screen.
 */
class AddressSearchPanelTest {

    @Test
    fun `results speak for themselves`() {
        val state = AddressSearchUiState(query = "nationale", results = listOf(anAddress()))
        assertEquals(AddressSearchPanel.None, panelFor(state, shortcutsOnShow = false))
    }

    @Test
    fun `a missing index met with an empty field leaves the shortcuts within reach`() {
        // The round trip that cost the lesson: nothing typed, no data at all,
        // and the ways round the index are every way there is.
        val state = AddressSearchUiState(isIndexInstalled = false)
        val panel = panelFor(state, shortcutsOnShow = true)
        assertEquals(AddressSearchPanel.NeedsIndex, panel)
        assertTrue("the map and the position need no index", panel.keepsList)
    }

    @Test
    fun `a missing index met with a typed query allows a list it no longer has`() {
        // Typing has taken the shortcuts away, so the permission this panel
        // grants falls on an empty list. Keeping the permission is right and
        // drawing on it is not: keepsList says a list may stand here, never
        // that there is one, and only the caller knows which.
        val state = AddressSearchUiState(query = "rue", isIndexInstalled = false)
        val panel = panelFor(state, shortcutsOnShow = false)
        assertEquals(AddressSearchPanel.NeedsIndex, panel)
        assertTrue(panel.keepsList)
    }

    @Test
    fun `no city chosen is the same lack as no index`() {
        // Without an active city the index reports itself absent: one screen,
        // one message, whichever of the two is missing.
        val state = AddressSearchUiState(isIndexInstalled = false)
        assertEquals(AddressSearchPanel.NeedsIndex, panelFor(state, shortcutsOnShow = true))
    }

    @Test
    fun `a keystroke concludes nothing before anything has been searched`() {
        // Where a letter lands, and the reason the invitation to type can never
        // be shown to somebody who has typed: the model raises isSearching on
        // the keystroke itself, leaving no state in which a query stands
        // against an empty list and no conclusion.
        val state = AddressSearchUiState(query = "r", isSearching = true)
        val panel = panelFor(state, shortcutsOnShow = false)
        assertEquals(AddressSearchPanel.Searching, panel)
        assertFalse("nothing stands beside a conclusion still owed", panel.keepsList)
    }

    @Test
    fun `an unreadable index leaves the shortcuts within reach`() {
        val state = AddressSearchUiState(
            query = "rue",
            error = DataError.LocalStorageFailure("truncated"),
        )
        val panel = panelFor(state, shortcutsOnShow = false)
        assertEquals(AddressSearchPanel.Unreadable, panel)
        assertTrue("the map and the position need no index", panel.keepsList)
    }

    @Test
    fun `a fruitless search takes the screen`() {
        val state = AddressSearchUiState(query = "qqqzzz")
        val panel = panelFor(state, shortcutsOnShow = false)
        assertEquals(AddressSearchPanel.NoMatch, panel)
        assertFalse("nothing may be drawn beside it", panel.keepsList)
    }

    @Test
    fun `a search under way concludes nothing`() {
        val state = AddressSearchUiState(query = "rue", isSearching = true)
        assertEquals(AddressSearchPanel.Searching, panelFor(state, shortcutsOnShow = false))
    }

    @Test
    fun `nothing typed with the shortcuts on show invites nothing`() {
        assertEquals(
            AddressSearchPanel.None,
            panelFor(AddressSearchUiState(), shortcutsOnShow = true),
        )
    }

    @Test
    fun `nothing typed and nothing to press invites typing`() {
        assertEquals(
            AddressSearchPanel.Prompt,
            panelFor(AddressSearchUiState(), shortcutsOnShow = false),
        )
    }

    private fun anAddress() = AddressResult(
        streetId = 1,
        houseNumber = 12,
        houseNumberSuffix = "",
        streetName = "Rue Nationale",
        city = "Lille",
        postcode = "59000",
        kind = AddressEntryKind.Street,
        position = Coordinates(50.6292, 3.0573),
        precision = PositionPrecision.Exact,
        distanceInMetres = 120.0,
        language = "fr",
    )
}
