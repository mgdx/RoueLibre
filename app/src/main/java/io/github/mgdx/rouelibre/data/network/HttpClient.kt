package io.github.mgdx.rouelibre.data.network

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.time.Duration

/**
 * The one HTTP client the application calls the network with.
 *
 * Built here rather than in the container so that the tests, and the live
 * check of every network, go through exactly the client the application ships
 * with — a redirection rule tested on another client proves nothing.
 *
 * @param registrableDomain how a host's registrable domain is found, for the
 *   redirection rule. The application leaves it to OkHttp, whose public suffix
 *   list is read from the APK's assets; a test on the JVM has no assets, and
 *   hands in a list it can read.
 */
fun roueLibreHttpClient(
    registrableDomain: (HttpUrl) -> String? = HttpUrl::topPrivateDomain,
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT)
    .readTimeout(READ_TIMEOUT)
    // Every address the application calls goes out in TLS, whatever scheme it
    // was published with (SPEC §4.1): the address asked for is secured first,
    // then each redirection is followed — or refused — by the application
    // itself, which secures every `Location` the same way before calling it.
    .addInterceptor(HttpsOnlyInterceptor())
    .addInterceptor(RedirectFollowingInterceptor(registrableDomain))
    // OkHttp's own following would go to any host, and run beneath the
    // interceptor above where nothing can stop it (SPEC §4.1).
    .followRedirects(false)
    .followSslRedirects(false)
    // No disk cache: the freshness policy is the repository's (SPEC §4.1), and
    // an HTTP cache on top would make it impossible to reason about.
    .cache(null)
    .build()

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(20)
