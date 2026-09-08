package io.github.mgdx.rouelibre.data.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sends the address the application asked for over TLS, whatever scheme it
 * carried.
 *
 * The application permits no cleartext traffic — it declares no network
 * security exception, so Android refuses an `http://` call outright. An address
 * in cleartext is therefore a certain failure, and rewriting it can only turn
 * that certainty into a chance: at worst the host answers no better in TLS, at
 * best it answers.
 *
 * That chance is not theoretical. Mi Bici Tu Bici, in Rosario, serves its
 * auto-discovery document over `https://` but names its four feeds over
 * `http://` — the very same paths that answer perfectly in TLS, and that the
 * server itself redirects there. Without this rule the network shows no station,
 * ever, over a producer's typo. It was the only one of the three hundred and
 * thirty-two networks served in that case, which is exactly why the answer
 * belongs here and not in its city configuration: nothing specific to a city is
 * hard-coded (SPEC §15), and the next producer to publish the same typo is
 * served without a release.
 *
 * Applied to the shared client rather than to the GBFS reader, because it holds
 * of the same reason for every address the application calls — a feed, a
 * manifest, a dataset file.
 *
 * It rewrites the address the call started from and nothing else: an
 * application interceptor is run once per call, before OkHttp has followed a
 * single redirect, so a `301` towards cleartext went past it. That half is
 * [HttpsOnlyRedirectInterceptor]'s, and the two are needed together — this one
 * because the rewriting has to happen before the connection is opened, the
 * other because by then the redirections have not been read yet.
 */
class HttpsOnlyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val secured = request.url.overHttps()
        if (secured == request.url) return chain.proceed(request)
        return chain.proceed(request.newBuilder().url(secured).build())
    }
}

/**
 * Sends over TLS the address a redirection points the call towards.
 *
 * Placed as a network interceptor, so that it is run for every request that
 * really goes out — the redirections and the retries included, which is what
 * [HttpsOnlyInterceptor] cannot see from where it stands.
 *
 * What it rewrites is the `Location` of the answer, not the address of the
 * request that follows from it. A network interceptor is run once the
 * connection is already open, and OkHttp holds it to the host and the port that
 * connection was made to; the cleartext address it would have had to rewrite is
 * moreover refused by Android before ever reaching here, the platform's policy
 * turning it down at connection time. Correcting the `Location` on its way back
 * up is earlier than both: OkHttp reads the header we hand it, and the request
 * it builds from it is in TLS from the start.
 *
 * Only a redirection's `Location` is touched. The header means something else
 * on a `201`, where it names what has just been created rather than where to go
 * next, and rewriting that would be answering a question nobody asked.
 */
class HttpsOnlyRedirectInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isRedirect) return response
        val location = response.header("Location") ?: return response
        // An address OkHttp cannot resolve is one it will not follow either:
        // left as it stands, it ends the call rather than opening a connection.
        val target = response.request.url.resolve(location) ?: return response
        val secured = target.overHttps()
        if (secured == target) return response
        return response.newBuilder().header("Location", secured.toString()).build()
    }
}

/**
 * The same address in TLS, or this one when it already is.
 *
 * The port follows the scheme: an address on the cleartext default port becomes
 * an address on the TLS default port, while an explicit port is kept as it
 * stands — it was chosen by the producer and means the same thing on either
 * scheme.
 */
internal fun HttpUrl.overHttps(): HttpUrl =
    if (isHttps) this else newBuilder().scheme("https").build()
