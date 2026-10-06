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
     * @property servedByNewerVersion the catalogue's entry when the catalogue
     *   in force still serves the network and only this build lacks its
     *   configuration — a newer version of the application serves it, which is
     *   not the same news as a network gone. `null` otherwise.
     */
    public data class NoLongerServed(
        public val id: String,
        public val withdrawal: WithdrawnCity?,
        public val servedByNewerVersion: CityEntry? = null,
    ) : ActiveCity
}
