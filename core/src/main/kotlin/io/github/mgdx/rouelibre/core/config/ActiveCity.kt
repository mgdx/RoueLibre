package io.github.mgdx.rouelibre.core.config

/**
 * Where the application stands with the city the settings name.
 *
 * Three answers and not two. Until 6 October 2026 a city the build could not
 * serve came back as no city at all, so whoever had chosen a network since
 * withdrawn was greeted as a first installation — "Which city?" over their own
 * map, the stations of a dead feed offered as merely stale, and their data
 * listed under "No city selected" until another city made it invisible for good
 * (SPEC §15.1).
 */
public sealed interface ActiveCity {

    /** Nothing has been chosen: the first launch, or the data was deleted. */
    public data object None : ActiveCity

    /** A city this build serves, with everything needed to serve it. */
    public data class Served(public val configuration: CityConfiguration) : ActiveCity

    /**
     * A city was chosen and this build no longer serves it.
     *
     * @property id the identifier the settings hold, which still names the
     *   directory its data sits in.
     * @property withdrawal what the catalogue says of it, or `null` when no
     *   catalogue on the device names it — the network can then not be named.
     */
    public data class NoLongerServed(
        public val id: String,
        public val withdrawal: WithdrawnCity?,
    ) : ActiveCity
}

/**
 * Settles which of the three [ActiveCity] answers holds.
 *
 * **A withdrawal outweighs a configuration.** Retiring a network is a catalogue
 * matter and no release is waited for, so a build still carrying the
 * configuration of a network the catalogue has since withdrawn must say it is
 * gone rather than keep querying a feed that answers nothing.
 *
 * @param id the city the settings name, `null` if none.
 * @param configuration its configuration in this build, `null` if there is none.
 * @param withdrawal the catalogue's record of its withdrawal, if any.
 */
public fun resolveActiveCity(
    id: String?,
    configuration: CityConfiguration?,
    withdrawal: WithdrawnCity?,
): ActiveCity = when {
    id == null -> ActiveCity.None
    withdrawal != null -> ActiveCity.NoLongerServed(id, withdrawal)
    configuration != null -> ActiveCity.Served(configuration)
    else -> ActiveCity.NoLongerServed(id, withdrawal = null)
}
