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
 */
class AddressSearchPanelTest {

    @Test
    fun `results speak for themselves`() {
        val state = AddressSearchUiState(query = "nationale", results = listOf(anAddress()))
        assertEquals(AddressSearchPanel.None, panelFor(state, showsShortcuts = true))
    }

    @Test
    fun `a missing index leaves the shortcuts within reach`() {
        val state = AddressSearchUiState(query = "rue", isIndexInstalled = false)
        val panel = panelFor(state, showsShortcuts = true)
        assertEquals(AddressSearchPanel.NeedsIndex, panel)
        assertTrue("the map and the position need no index", panel.keepsList)
    }

    @Test
    fun `no city chosen is the same lack as no index`() {
        // Without an active city the index reports itself absent: one screen,
        // one message, whichever of the two is missing.
        val state = AddressSearchUiState(isIndexInstalled = false)
        assertEquals(AddressSearchPanel.NeedsIndex, panelFor(state, showsShortcuts = true))
    }

    @Test
    fun `an unreadable index leaves the shortcuts within reach`() {
        val state = AddressSearchUiState(
            query = "rue",
            error = DataError.LocalStorageFailure("truncated"),
        )
        val panel = panelFor(state, showsShortcuts = true)
        assertEquals(AddressSearchPanel.Unreadable, panel)
        assertTrue("the map and the position need no index", panel.keepsList)
    }

    @Test
    fun `a fruitless search takes the screen`() {
        val state = AddressSearchUiState(query = "qqqzzz")
        val panel = panelFor(state, showsShortcuts = true)
        assertEquals(AddressSearchPanel.NoMatch, panel)
        assertFalse("nothing may be drawn beside it", panel.keepsList)
    }

    @Test
    fun `a search under way concludes nothing`() {
        val state = AddressSearchUiState(query = "rue", isSearching = true)
        assertEquals(AddressSearchPanel.Searching, panelFor(state, showsShortcuts = true))
    }

    @Test
    fun `nothing typed with the shortcuts on show invites nothing`() {
        assertEquals(
            AddressSearchPanel.None,
            panelFor(AddressSearchUiState(), showsShortcuts = true),
        )
    }

    @Test
    fun `nothing typed and nothing to press invites typing`() {
        assertEquals(
            AddressSearchPanel.Prompt,
            panelFor(AddressSearchUiState(), showsShortcuts = false),
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
    )
}
