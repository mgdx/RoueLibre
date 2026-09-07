package io.github.mgdx.rouelibre.ui.settings

import io.github.mgdx.rouelibre.data.SavedPlaceKind

/**
 * Which row of "my places" is still waiting for an address (SPEC §7.6, §14).
 *
 * The settings screen sends the user to the address search and reads the answer
 * when it comes back, so it has to remember which of the two rows asked. That
 * memory outlives the screen's view, and it does so **in two quite different
 * ways** — which is the whole reason this decision is a function rather than a
 * line in the fragment.
 *
 *  * **The screen is only unstacked.** Going to the address search puts the
 *    settings behind it: the view is destroyed, the fragment instance is not,
 *    and `onSaveInstanceState` is never called because there is no state to
 *    save. The field is still standing on its own. Coming back, the view is
 *    built again with **no bundle at all**, and the answer arrives just after.
 *  * **The screen is destroyed and built afresh.** A rotation while one
 *    searches, or a process the system killed, recreates the fragment: the
 *    field is back to nothing and the bundle carries everything.
 *
 * So neither source can be trusted alone, and — this is the defect this
 * function exists to hold shut — **the bundle must never be allowed to erase
 * what the instance still holds**. Reading it unconditionally is the obvious
 * way to write those two lines, it survives the rotation, and it breaks the
 * ordinary path: the surviving `Home` was overwritten with `null` by an absent
 * bundle, the answer then landed on no row, and no place could be saved at all.
 * That is what shipped, and it took a phone to find. Hence: the bundle answers
 * where it has an answer, and stays silent where it has none.
 *
 * A name that matches no kind counts as silence for the same reason. A settings
 * file written by a later version, or a bundle truncated by a device out of
 * space, must leave the field as it was rather than empty it — the reading
 * [io.github.mgdx.rouelibre.data.AppPreferences] already gives a place it
 * cannot make out.
 *
 * @param held what the fragment instance is still holding: the row that was
 *   pressed, or `null` on an instance that has just been created.
 * @param savedName the kind's name as the bundle carries it, or `null` where
 *   there is no bundle or it holds nothing.
 * @return the row the answer belongs to, or `null` if none is waiting.
 */
fun awaitedPlace(held: SavedPlaceKind?, savedName: String?): SavedPlaceKind? =
    savedName?.let { name -> SavedPlaceKind.entries.firstOrNull { it.name == name } } ?: held
