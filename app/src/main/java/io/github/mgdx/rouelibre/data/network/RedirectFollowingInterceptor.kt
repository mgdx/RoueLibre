package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.network.RedirectPolicy
import io.github.mgdx.rouelibre.core.network.RedirectScope
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Follows a redirection only where [RedirectPolicy] allows it (SPEC §4.1).
 *
 * OkHttp's own following goes to any host, twenty times over, and nothing in
 * its configuration narrows it: an operator's server, or a compromised one,
 * could then make the phone contact a host no configuration chose, which C3
 * forbids. The client is therefore told to follow nothing, and this application
 * interceptor does the following instead, one hop at a time, deciding each one
 * before a connection is opened to it.
 *
 * Every hop is weighed against the host the call **started from**, not the one
 * the previous hop reached: a chain of redirections can then never wander
 * further than a single one could.
 *
 * What the request is for is read from its [RedirectScope] tag, set by each
 * caller. A request without one only ever follows a redirection on its own host.
 *
 * The request that follows keeps everything the first one carried —
 * `User-Agent`, `Range`, `If-None-Match`, `If-Modified-Since`, the tag itself —
 * so a resumed download stays resumed and a conditional catalogue request stays
 * conditional. Its method is kept too, which a `303` would ask to turn into a
 * GET: every call the application makes is a GET already.
 *
 * @param registrableDomain the registrable domain of an address's host, or
 *   `null` where it has none — an IP address, `localhost`.
 * @throws RefusedRedirectException when the policy refuses a hop, or when a
 *   call is still being redirected after [MAXIMUM_HOPS] of them.
 */
class RedirectFollowingInterceptor(private val registrableDomain: (HttpUrl) -> String?) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val origin = chain.request()
        val scope = origin.tag(RedirectScope::class.java)
        var request: Request = origin
        var response = chain.proceed(request)
        var hops = 0
        while (response.code in FOLLOWED_CODES) {
            // Without a `Location` that resolves to an address, there is
            // nowhere to go: the answer goes up as it is, and the caller reads
            // a status that is not a success.
            val location = response.header("Location") ?: return response
            val target = response.request.url.resolve(location)?.overHttps() ?: return response
            // The body of a redirection is a page nobody reads, and an
            // unclosed one holds its connection out of the pool.
            response.close()
            val allowed = hops < MAXIMUM_HOPS &&
                RedirectPolicy.allows(
                    scope = scope,
                    originHost = origin.url.host,
                    originDomain = { domainOrNull(origin.url) },
                    targetHost = target.host,
                    targetDomain = { domainOrNull(target) },
                )
            if (!allowed) throw RefusedRedirectException(target.host)
            request = request.newBuilder().url(target).build()
            response = chain.proceed(request)
            hops++
        }
        return response
    }

    /**
     * [registrableDomain], or `null` when it cannot be read.
     *
     * OkHttp reads its public suffix list from the APK's assets once its
     * initialiser has run, and throws an unchecked exception otherwise — one
     * no caller catches, which would close the application on a redirection.
     * An unknown domain makes the policy refuse a GBFS hop off its host, the
     * strictest answer: never a crash, and never a door opened.
     */
    private fun domainOrNull(url: HttpUrl): String? = try {
        registrableDomain(url)
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        /**
         * The publication path is the longest one measured: the release's
         * address, its tag's, then the asset storage — two hops. One more is
         * room for a host to move without a release; past that, a call is going
         * round in circles or being led somewhere.
         */
        const val MAXIMUM_HOPS = 3

        /**
         * The codes that mean "the same thing is over there". A `300` offers a
         * choice nobody here would make, and a `304` is an answer in itself.
         */
        val FOLLOWED_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * A redirection the application refused to follow, or one too many.
 *
 * An [IOException] because that is what OkHttp lets an interceptor throw through
 * a call; each caller catches it before its generic network failure and names
 * it [io.github.mgdx.rouelibre.core.DataError.UntrustedServer]: nothing wrong
 * was received, a server tried to send the phone somewhere it was not asked to
 * go.
 *
 * The message carries the host it pointed to and nothing else — never the path
 * or the query, which could hold whatever the server chose to put there.
 *
 * @property targetHost the host the redirection pointed to.
 */
class RefusedRedirectException(val targetHost: String) :
    IOException("redirect towards $targetHost refused")
