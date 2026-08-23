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

/**
 * The screen actually landed on, given the choice and what is installed
 * (SPEC §7.0, §7.6).
 *
 * **The map is only offered where there is a map.** Without its tiles the map
 * screen is a full-screen panel saying they are missing, and landing on it
 * makes the first thing the application shows an obstacle — worse still on the
 * default setting, which nobody chose. The station list needs nothing
 * installed: it works off the availability feed alone (SPEC §4.4), so it is
 * what the application has to show, and its own way to the map is one press
 * away for whoever wants to read that panel.
 *
 * **The choice itself is untouched**: nothing is written, and the map comes
 * back as the screen landed on the moment its tiles are there. What is
 * corrected is a landing, not a preference.
 *
 * @param chosen the screen the user asked to land on.
 * @param hasBaseMap whether the tiles the map is drawn from are on the device.
 */
fun landingScreen(chosen: OpeningScreen, hasBaseMap: Boolean): OpeningScreen =
    if (hasBaseMap) chosen else OpeningScreen.StationList
