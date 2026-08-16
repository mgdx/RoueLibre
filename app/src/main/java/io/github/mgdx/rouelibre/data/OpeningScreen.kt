package io.github.mgdx.rouelibre.data

/**
 * The screen the application lands on when it is opened (SPEC §7.0, §7.6).
 *
 * The map is the application's content and what §7.0 opens on, and it stays the
 * default: somebody who never opens the settings sees exactly what they saw
 * before this choice existed. But it is not what everybody opens the
 * application for — somebody who always sets off from the same station reads one
 * line of a list, not a plan.
 *
 * **It settles where one lands, never where one is sent.** The welcome sequence
 * and the what's-new screen come over it (SPEC §7.9, §7.10), and a place
 * received from another application opens its journey over it too (SPEC §7.8):
 * an explicit intention always beats a preference.
 *
 * @property id the value written to disk, stable from one release to the next.
 */
enum class OpeningScreen(val id: String) {
    /** The map of §7.1, and the default. */
    Map("map"),

    /** The station list of §7.2. */
    StationList("station_list"),
    ;

    companion object {
        /**
         * Reads a stored choice back; anything unknown returns [Map].
         *
         * A word written by another version, or by a hand, is read as "nothing
         * chosen" rather than guessed at: the default is the behaviour the
         * application has always had, so an unreadable value costs nobody a
         * screen they did not ask for.
         */
        fun fromId(id: String?): OpeningScreen = entries.firstOrNull { it.id == id } ?: Map
    }
}
