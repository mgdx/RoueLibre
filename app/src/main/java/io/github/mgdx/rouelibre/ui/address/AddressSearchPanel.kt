package io.github.mgdx.rouelibre.ui.address

/**
 * What the address search shows in place of a list of results (SPEC §7.3).
 *
 * The choice is made here rather than in the fragment so that it can be run on
 * the JVM: it decides, among other things, whether the three shortcuts stay
 * within reach, and that answer was got wrong once already — on an installation
 * holding no data at all, where two of the three were the only ways left of
 * naming a point.
 *
 * @property keepsList whether the list may stay on screen below the panel.
 *   A panel that reports the **outcome of a search** takes the screen: what it
 *   has to say is about the very rows it replaces. A panel that reports an
 *   **absent capacity** does not: the address index has nothing to do with
 *   pointing at the map or with one's own position, and taking those away
 *   because an index is missing punishes the user for the lack twice over.
 */
enum class AddressSearchPanel(val keepsList: Boolean) {

    /** Nothing to say: the list, results or shortcuts, speaks for itself. */
    None(true),

    /** A search is owed or under way, and concludes nothing yet. */
    Searching(false),

    /** The address index is not on the device — no city installed, or none chosen. */
    NeedsIndex(true),

    /** The index is on the device and could not be read. */
    Unreadable(true),

    /** The search ran, and matched nothing. */
    NoMatch(false),

    /** Nothing typed yet, and no shortcut standing in the list to invite. */
    Prompt(false),
}

/**
 * The panel a state calls for.
 *
 * The order of the questions is the whole of the reasoning, which is why they
 * are asked in one place: a search under way concludes nothing, an index that
 * is not there was never searched, and only what got as far as being searched
 * can be said to have matched nothing.
 *
 * @param state what the screen knows.
 * @param showsShortcuts whether the list carries the three ways of naming a
 *   point that do not go through the index (SPEC §7.3).
 */
fun panelFor(state: AddressSearchUiState, showsShortcuts: Boolean): AddressSearchPanel = when {
    state.results.isNotEmpty() -> AddressSearchPanel.None
    state.isSearching && state.query.isNotBlank() -> AddressSearchPanel.Searching
    !state.isIndexInstalled -> AddressSearchPanel.NeedsIndex
    state.error != null -> AddressSearchPanel.Unreadable
    state.hasNoMatch -> AddressSearchPanel.NoMatch
    // Nothing typed yet. With the shortcuts on screen there is nothing to
    // invite: the invitation would stand above the very rows that answer it.
    showsShortcuts -> AddressSearchPanel.None
    else -> AddressSearchPanel.Prompt
}
