package io.github.mgdx.rouelibre.data.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Sends every request over TLS, whatever scheme its address carried.
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
 * thirty-three networks served in that case, which is exactly why the answer
 * belongs here and not in its city configuration: nothing specific to a city is
 * hard-coded (SPEC §15), and the next producer to publish the same typo is
 * served without a release.
 *
 * Applied to the shared client rather than to the GBFS reader, because it holds
 * of the same reason for every address the application calls — a feed, a
 * manifest, a dataset file.
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
 * The same address in TLS, or this one when it already is.
 *
 * The port follows the scheme: an address on the cleartext default port becomes
 * an address on the TLS default port, while an explicit port is kept as it
 * stands — it was chosen by the producer and means the same thing on either
 * scheme.
 */
internal fun HttpUrl.overHttps(): HttpUrl =
    if (isHttps) this else newBuilder().scheme("https").build()
