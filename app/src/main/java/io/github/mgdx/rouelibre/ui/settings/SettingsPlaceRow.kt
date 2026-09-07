package io.github.mgdx.rouelibre.ui.settings

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.covers
import io.github.mgdx.rouelibre.data.SavedPlace

/**
 * What one row of the "my places" section shows (SPEC §7.6).
 *
 * @property label what the row writes, which is the place itself and never the
 *   action: the section title already says what the row settles, and a row
 *   reading "change home" would spend its one line saying what pressing it does
 *   instead of answering the question the reader came with. Where no place is
 *   named there is nothing to name, and the row invites the choice instead —
 *   that invitation, and that one alone, says what pressing does.
 * @property spokenLabel what a screen reader announces, which names the setting
 *   as well as the value: "12 rue Nationale" alone does not say what question it
 *   is the answer to, and this screen puts the same question twice.
 * @property isOutsideCityServed whether the line saying the place falls outside
 *   the city served belongs under the row.
 * @property canBeForgotten whether the row carries the button that forgets the
 *   place. There is nothing to forget where nothing was named.
 */
data class SettingsPlaceRow(
    val label: String,
    val spokenLabel: String,
    val isOutsideCityServed: Boolean,
    val canBeForgotten: Boolean,
)

/**
 * Puts one saved place into the words its row shows (SPEC §7.6).
 *
 * **A place outside the city served stays, and is said to be outside.** It is
 * the rule SPEC §7.3 already applies to the kind of bike asked of the network —
 * "ignored, never applied in silence — and never erased either" — and it holds
 * here for the same reason: somebody who named their home in Lille and is
 * looking at Lyon today has not moved house. Reading the place while another
 * city is served must therefore neither hide it nor drop it; coming back to
 * Lille finds it again, and it can be forgotten from here in the meantime, which
 * is the one erasure this application performs — the one the user asks for.
 *
 * **Where no city is chosen, nothing is outside anything.** The served area is
 * read from the city in service, so without one there is no box, and calling a
 * place "outside the city served" then would be saying something we do not know.
 * [covers] answers exactly that: a box that is absent or unusable covers
 * everything.
 *
 * @param setting the name of the setting this row carries — "Home", "Work".
 * @param place what the user named, or `null` if they have named nothing.
 * @param coveredArea the reference box of the city in service, or `null` when
 *   no city is chosen.
 * @param none how "no place named" reads, for the ear: the eye is given the
 *   invitation below instead, which is a call to act rather than a state.
 * @param invite writes the invitation shown where no place is named, through
 *   the string resource that owns its wording (SPEC §9).
 * @param describe joins the setting and its value into the spoken label,
 *   through the resource that owns their order and their punctuation (SPEC §9).
 *   Taken as functions rather than read here, so that what this decides is
 *   pinned on the JVM, where no `Context` writes anything.
 */
fun settingsPlaceRow(
    setting: String,
    place: SavedPlace?,
    coveredArea: BoundingBox?,
    none: String,
    invite: (String) -> String,
    describe: (String, String) -> String,
): SettingsPlaceRow = SettingsPlaceRow(
    label = place?.label ?: invite(setting),
    spokenLabel = describe(setting, place?.label ?: none),
    isOutsideCityServed = place != null && !coveredArea.covers(place.position),
    canBeForgotten = place != null,
)
