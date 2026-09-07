package io.github.mgdx.rouelibre.ui.address

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.covers
import io.github.mgdx.rouelibre.data.SavedPlace

/**
 * The shortcuts a journey's end is offered, in the order they are shown in
 * (SPEC §7.3, §7.6).
 *
 * Made here rather than in the fragment for the reason [panelFor] is: the
 * decision is a handful of rules about what may honestly be offered, and rules
 * belong where they can be run on the JVM rather than read off a device.
 *
 * The order of the questions is the reasoning, and it is asked in one place:
 *
 *  * **Once something is typed, none of them is offered.** They are ways of
 *    answering a question — which point is this? — and somebody who has begun
 *    to type has answered it. Left standing they would hold the head of the
 *    list with five rows that can no longer be what is being looked for, and
 *    push the first address, the one aimed at, down the screen by that much.
 *    Emptying the field brings all five back, so nothing is taken away: they
 *    are put aside for as long as the question has an answer under way. Settled
 *    on 7 September 2026 (SPEC §7.3), the list having until then stood whatever
 *    was typed.
 *  * **A field holding nothing but spaces has still asked nothing**, and keeps
 *    them. It is the line the rest of the screen already draws — the model runs
 *    no search on a blank query and [panelFor] concludes nothing about one — and
 *    a field must not answer two different questions about its own content
 *    depending on who is asking. It also spares the screen its worst state: a
 *    space brushed by accident, no search to run and nothing left to press.
 *  * **A place the user has not named has no row.** Not greyed out, not leading
 *    to the settings: absent. It is the rule SPEC §7.3 already applies to the
 *    kind of bike where the network lends one kind — "Not greyed out: absent" —
 *    and inviting somebody to name their home is the settings screen's work,
 *    not a result list's.
 *  * **A place the installed data cannot reach has no row either**, and is not
 *    forgotten for it: it stays in the settings, and coming back to the city it
 *    belongs to finds it again. A shortcut leading where no journey can be
 *    traced promises what nothing will be able to honour, which is the reading
 *    a station beyond the data is already given
 *    ([io.github.mgdx.rouelibre.core.station.isBeyondCoveredArea]).
 *  * **With no city chosen there is no box, and nothing is withdrawn.** That is
 *    not this function's decision to make afresh: [covers] settled it for the
 *    whole application — not knowing what was downloaded is no ground to refuse
 *    a point. Withdrawing here instead would take the two rows away exactly
 *    where the address index is missing too, and an installation with no data
 *    is the one case where the ways round the index matter most.
 *
 * On an empty field the three ways that ask the installed data for nothing —
 * one's position, a favourite, a point on the map — are never filtered: no
 * place named and no city chosen still leaves those three. **That guarantee
 * holds over what is named and what is installed, and no longer over what is
 * typed**, which is the one thing the rule above changed. [panelFor] therefore
 * can no longer be told "this screen fills a journey's end" and read it as
 * "this list has something in it": it is handed the list's own answer.
 *
 * @param home the place named as home, or `null` if none is.
 * @param work the place named as work, or `null` if none is.
 * @param coveredArea the reference box of the city in service, or `null` where
 *   no city is chosen or its box has not been read yet.
 * @param query what stands in the search field, raw and untrimmed.
 */
fun searchShortcutsFor(
    home: SavedPlace?,
    work: SavedPlace?,
    coveredArea: BoundingBox?,
    query: String,
): List<SearchShortcut> {
    if (query.isNotBlank()) return emptyList()
    return buildList {
        if (home.isWorthOffering(coveredArea)) add(SearchShortcut.Home)
        if (work.isWorthOffering(coveredArea)) add(SearchShortcut.Work)
        add(SearchShortcut.MyPosition)
        add(SearchShortcut.Favourite)
        add(SearchShortcut.OnMap)
    }
}

/** Whether a named place can be offered as one press towards a journey. */
private fun SavedPlace?.isWorthOffering(coveredArea: BoundingBox?): Boolean =
    this != null && coveredArea.covers(position)
