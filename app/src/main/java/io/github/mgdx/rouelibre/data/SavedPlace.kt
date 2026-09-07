package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * Which of the two places the user may name (SPEC §7.6).
 *
 * Two and no more: what is stored is what somebody declares about themselves,
 * and an open-ended list of places would be a list of the places they go — the
 * very thing constraint C3 forbids (SPEC §2).
 */
enum class SavedPlaceKind {
    /** Where the user says they live. */
    Home,

    /** Where the user says they work. */
    Work,
}

/**
 * A place the user has named for themselves: their home or their work.
 *
 * **Declared, not observed.** Constraint C3 (SPEC §2, §8) forbids keeping what
 * the application learns of somebody — a journey made, a destination looked up,
 * a position passed through. It does not forbid what the user states of their
 * own accord, at their own request, and takes back with one press: the
 * favourites are already of that kind. This type carries the second one, and
 * nothing observed ever becomes one.
 *
 * It is not [io.github.mgdx.rouelibre.ui.journey.JourneyEndpoint], which says in
 * its own KDoc that it never reaches the disk, and that sentence must stay true.
 * The two live apart — this one in `data/`, that one in `ui/journey/` — so that
 * neither is reached for by mistake.
 *
 * @property label what the user reads, an address as its own country writes it.
 * @property position the place named.
 */
data class SavedPlace(val label: String, val position: Coordinates)
