package io.github.mgdx.rouelibre.core.network

/**
 * What a request is for, which decides how far a redirection may take it
 * (SPEC §4.1).
 *
 * Every host the application talks to was chosen by somebody: the network's by
 * its city configuration, the data releases' by the shipped catalogue or the
 * user. A redirection is a server choosing a host in their place, and how much
 * of that choice to accept depends on whose server it is.
 */
public enum class RedirectScope {

    /** A GBFS document: the discovery file or one of the feeds it announces. */
    GBFS,

    /** The city catalogue, a data release's manifest, or one of its files. */
    PUBLICATION,
}

/**
 * Decides whether a redirection may be followed (SPEC §4.1, `SECURITY.md`).
 *
 * The rule is narrow on purpose: following a `Location` anywhere would let any
 * of three hundred third-party operators — or a compromised host — make the
 * phone contact a server no configuration named, which C3 forbids. What is
 * accepted is what the real hosts were measured to need on 7 October 2026: not
 * one of the 360 discovery documents nor of the 2,994 feeds they announce
 * redirected at all, and the publication host redirects from its release
 * address to its asset storage.
 *
 * Hosts are compared as the HTTP client hands them over, canonical and in lower
 * case. The registrable domains are computed by the caller, who holds the
 * public suffix list, and are `null` where there is none — an IP address,
 * `localhost`. They are asked for only when the answer turns on them — a GBFS
 * request leaving its host — so that whatever goes wrong in reading that list
 * can never touch the same-host case or the publication path.
 */
public object RedirectPolicy {

    /**
     * The host the project publishes its catalogue and data releases from.
     *
     * Written in the code rather than in the catalogue: it is the project's own
     * host and belongs to no city (SPEC §15), and the user chose not to make it
     * a configuration value — a field read from a document is a field a
     * document can widen.
     */
    private const val PUBLICATION_HOST = "github.com"

    /**
     * Where GitHub stores the files of a release: `release-assets` today,
     * `objects` before it. A domain rather than a list of hosts, because GitHub
     * has already moved them once between its subdomains, and an exact name
     * would break every download the next time it does.
     */
    private const val PUBLICATION_STORAGE_DOMAIN = "githubusercontent.com"

    /**
     * Whether a request in [scope], sent to [originHost], may follow a
     * redirection towards [targetHost].
     *
     * @param scope what the request is for; `null` for a request nobody
     *   labelled, which only ever stays on its own host — a forgotten label
     *   must fall into the strictest answer, not the widest.
     * @param originDomain the registrable domain of [originHost], or `null`;
     *   called only when needed.
     * @param targetDomain the registrable domain of [targetHost], or `null`;
     *   called only when needed.
     */
    public fun allows(
        scope: RedirectScope?,
        originHost: String,
        originDomain: () -> String?,
        targetHost: String,
        targetDomain: () -> String?,
    ): Boolean {
        if (targetHost == originHost) return true
        return when (scope) {
            // An operator moving a feed between its own servers is still the
            // operator: `gbfs.example.com` to `api.example.com` contacts nobody
            // new. Another registrable domain is somebody else.
            RedirectScope.GBFS -> originDomain()?.let { it == targetDomain() } ?: false
            RedirectScope.PUBLICATION ->
                originHost == PUBLICATION_HOST && isPublicationStorage(targetHost)
            null -> false
        }
    }

    private fun isPublicationStorage(host: String): Boolean =
        host == PUBLICATION_STORAGE_DOMAIN || host.endsWith(".$PUBLICATION_STORAGE_DOMAIN")
}
