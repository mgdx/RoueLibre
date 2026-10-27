package io.github.mgdx.rouelibre.ui.map

/**
 * Where the main map was last looking, for the map that picks a point to
 * open on (SPEC §7.3).
 *
 * The picking map is a screen of its own, built from nothing: left to
 * itself it opened on the user's position or the whole city, and somebody who
 * had just zoomed onto a street on the main map had to find it all over again
 * (issue #14). The main map's own memory cannot hand it over — it lives in
 * that fragment, which may be sitting in the back stack, or may have been
 * replaced outright, or may never have been shown at all when the list is
 * the opening screen.
 *
 * Held in memory and nowhere else, so it goes with the process: a framing
 * says where somebody was looking, which is close enough to where they are
 * going that SPEC §8 wants it written nowhere.
 */
internal object LastMainMapFraming {

    /** The framing the main map left, `null` while it has never been shown. */
    @Volatile
    var framing: Framing? = null
}
