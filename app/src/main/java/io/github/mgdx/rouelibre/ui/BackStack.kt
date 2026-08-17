package io.github.mgdx.rouelibre.ui

import androidx.fragment.app.FragmentManager

/**
 * The name carried by every transaction that puts the map up over the list.
 *
 * Named so that the list's way to the map can find a map already behind it and
 * come back to it, instead of building a second one (SPEC §7.6).
 */
internal const val MAP_BACK_STACK_ENTRY = "map"

/** The same, for every transaction that puts the station list up over the map. */
internal const val STATION_LIST_BACK_STACK_ENTRY = "station-list"

/** Where a screen stands, seen from the one on display. */
internal enum class ScreenBehind {
    /** Behind us, and its own transaction is on the stack: it can be returned to. */
    Stacked,

    /** Under the stack, uncovered by undoing the transaction that put us up. */
    Underneath,

    /** Nowhere behind us, so it has to be built. */
    Nowhere,
}

/**
 * Where the screen whose transactions carry [screenEntry] is, given the names on
 * the back stack from the oldest to the newest.
 *
 * Two ways it may already be behind the screen on display. It was put up over
 * us, and its transaction is there to be popped back to. Or we were put up over
 * it, our own transaction carrying [ownEntry], and undoing that one uncovers it
 * — which is the case whenever the application opened on it, the screen opened
 * on sitting under the back stack rather than in it.
 *
 * [Stacked] is answered wherever both hold, being the nearer of the two: a
 * transaction that put the sought screen up over us can only be more recent than
 * the one that put us up over it. Which of several same-named entries is then
 * popped back to is the pop's own business, and it takes the topmost.
 */
internal fun screenBehind(
    screenEntry: String,
    ownEntry: String,
    entryNames: List<String?>,
): ScreenBehind = when {
    entryNames.contains(screenEntry) -> ScreenBehind.Stacked
    entryNames.contains(ownEntry) -> ScreenBehind.Underneath
    else -> ScreenBehind.Nowhere
}

/** The names of the transactions on the back stack, oldest first. */
internal fun FragmentManager.backStackEntryNames(): List<String?> =
    (0 until backStackEntryCount).map { getBackStackEntryAt(it).name }
