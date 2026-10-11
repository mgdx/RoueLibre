package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * The decisions "locate me" takes on both map screens, apart from them
 * (SPEC §7.1, §7.4, §10).
 *
 * Pure Kotlin, so that each can be read and tested without an Android runtime
 * (SPEC §14).
 */

/** What a press on "locate me" does. */
internal enum class LocateMeStep {
    /** No permission yet: it is asked for, the one moment it may be. */
    AskPermission,

    /** Location is switched off: that is said, and nothing more is done. */
    SayUnavailable,

    /** A position to go and get. */
    Locate,
}

/**
 * Tells apart the three cases of a press on "locate me".
 *
 * Only the first asks for anything, and it is the one that comes back: after
 * a refusal, this button is what the user has left to change their mind
 * (SPEC §10).
 *
 * @param available asked only once the permission is known to be granted,
 *   which is when the answer means something.
 */
internal fun locateMeStep(permitted: Boolean, available: () -> Boolean): LocateMeStep = when {
    !permitted -> LocateMeStep.AskPermission
    !available() -> LocateMeStep.SayUnavailable
    else -> LocateMeStep.Locate
}

/**
 * Says whether [position] falls outside the area a map covers.
 *
 * Outside it the camera cannot follow — it is penned inside the city's box —
 * and it would settle at the edge nearest the user, passing that off as where
 * they stand. The city whose configuration declares no usable box covers
 * everything as far as the map is concerned: nothing pens its camera either,
 * so nothing is being passed off as anything.
 */
internal fun liesOutside(position: Coordinates, servedArea: BoundingBox?): Boolean {
    val area = servedArea?.takeIf { it.isUsable } ?: return false
    return position !in area
}
