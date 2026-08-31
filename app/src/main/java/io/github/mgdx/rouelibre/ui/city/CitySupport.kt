package io.github.mgdx.rouelibre.ui.city

/**
 * Whether the build can actually serve a city the catalogue names.
 *
 * The catalogue and the configurations do not travel together: the catalogue is
 * refreshed over the network, and each city's configuration ships in the APK
 * (SPEC §15). A catalogue published after this release therefore names cities
 * this build carries nothing for, and choosing one would land on an empty
 * screen with nothing to explain it.
 *
 * @param cityId the catalogue entry's identifier.
 * @param known the identifiers this build carries a configuration for.
 * @return true while the city can be served — **including when [known] is
 *   empty**. An empty set is not a build that serves no city, it is one whose
 *   asset directory could not be listed at all, and refusing all three hundred
 *   and thirty-seven cities on the strength of that failure would be far worse
 *   than the mismatch it is meant to catch.
 */
fun isCitySupported(cityId: String, known: Set<String>): Boolean =
    known.isEmpty() || cityId in known

/**
 * The order the city list is shown in.
 *
 * Four questions, in the order they matter to somebody opening the screen: the
 * city in service is the one they came back to; then the cities whose data is
 * already on the device, which cost nothing to return to; then the plain
 * alphabet. **The cities this version cannot serve fall between the first
 * question and the second**, which is to say to the foot of the list: they are
 * shown so that somebody looking for their city finds it and reads why it is
 * not here yet, never so that they are chosen from.
 */
fun cityDisplayOrder(): Comparator<CityRow> = compareByDescending<CityRow> { it.isActive }
    .thenByDescending { it.isSupported }
    .thenByDescending { it.installedBytes > 0 }
    .thenBy { it.entry.displayName }
