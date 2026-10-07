package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.network.RedirectScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * Tests of which redirections the application follows (SPEC §4.1).
 *
 * Every call goes through [roueLibreHttpClient] itself — a rule tested on
 * another client would prove nothing about the one shipped — with two things
 * added behind it, where the application's own interceptors have already done
 * their work: every host name leads to the loopback, so that `github.com` or
 * `tracker.example.org` can be played by a local server, and TLS is taken off
 * at the last moment, a local server holding no certificate. The address the
 * application meant to call is recorded just before, so what is checked is
 * still the address over TLS. Registrable domains are read from the JDK's
 * public suffix list, OkHttp's being in the APK's assets ([jvmRegistrableDomain]).
 */
class RedirectFollowingInterceptorTest {

    private val first = MockWebServer()
    private val second = MockWebServer()

    /** Every address the client was about to call, in TLS as the application asked. */
    private val called = mutableListOf<HttpUrl>()

    private val client: OkHttpClient = roueLibreHttpClient(::jvmRegistrableDomain).newBuilder()
        .dns(
            object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("localhost"))
            },
        )
        .addInterceptor { chain ->
            val url = chain.request().url
            called += url
            chain.proceed(
                chain.request().newBuilder().url(url.newBuilder().scheme("http").build()).build(),
            )
        }
        .build()

    @Before
    fun start() {
        first.start()
        second.start()
    }

    @After
    fun stop() {
        first.close()
        second.close()
    }

    /** [server]'s address for [path], under the host name [host]. */
    private fun address(server: MockWebServer, host: String, path: String = "/"): String =
        "https://$host:${server.port}$path"

    private fun redirect(to: String, code: Int = 302) =
        MockResponse(code = code, headers = headersOf("Location", to))

    private fun get(url: String, scope: RedirectScope?, vararg headers: Pair<String, String>) =
        client.newCall(
            Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .apply { if (scope != null) tag(RedirectScope::class.java, scope) }
                .build(),
        ).execute()

    @Test
    fun `a feed sent to another host is refused before anything reaches it`() = runTest {
        // `localhost` and `127.0.0.1` are the same machine and two hosts: a
        // registrable domain neither of them has cannot make them one.
        first.enqueue(redirect(address(second, "127.0.0.1", "/gbfs.json")))
        val source = GbfsRemoteSource(
            client = client,
            parser = GbfsParser(),
            userAgent = "RoueLibre/test",
            unnamedStationLabel = { "unnamed" },
            ioDispatcher = Dispatchers.IO,
        )

        val outcome = source.fetchDiscovery(address(first, "localhost", "/gbfs.json"))

        assertEquals(0, second.requestCount)
        val error = (outcome as Outcome.Failure).error
        assertTrue("expected UntrustedServer, got $error", error is DataError.UntrustedServer)
        assertEquals(
            "redirect towards 127.0.0.1 refused",
            (error as DataError.UntrustedServer).detail,
        )
    }

    @Test
    fun `a redirection on the same host is followed`() {
        first.enqueue(redirect("/moved.json"))
        first.enqueue(MockResponse(code = 200, body = "{}"))

        get(address(first, "localhost", "/gbfs.json"), scope = null).use { response ->
            assertEquals(200, response.code)
            assertEquals("{}", response.body.string())
        }
        first.takeRequest()
        assertEquals("/moved.json", first.takeRequest().url.encodedPath)
    }

    @Test
    fun `a feed may move within its operator's domain and no further`() {
        first.enqueue(redirect(address(second, "api.example.com", "/feed.json")))
        second.enqueue(MockResponse(code = 200, body = "{}"))
        get(address(first, "gbfs.example.com"), RedirectScope.GBFS).use {
            assertEquals(200, it.code)
        }

        first.enqueue(redirect(address(second, "tracker.example.org", "/feed.json")))
        assertRefused { get(address(first, "gbfs.example.com"), RedirectScope.GBFS) }
        assertEquals("only the first redirection reached it", 1, second.requestCount)

        // Two councils under one public suffix are two owners, not one domain.
        first.enqueue(redirect(address(second, "oxford.co.uk")))
        assertRefused { get(address(first, "bristol.co.uk"), RedirectScope.GBFS) }
        assertEquals(1, second.requestCount)
    }

    @Test
    fun `a release on GitHub goes down to its asset storage, and only there`() {
        // The real chain: the release's address, its tag's, then the storage.
        first.enqueue(
            redirect(address(first, "github.com", "/mgdx/RoueLibre-data/releases/download/v1/x")),
        )
        first.enqueue(redirect(address(second, "release-assets.githubusercontent.com", "/x")))
        second.enqueue(MockResponse(code = 200, body = "data"))
        get(
            address(first, "github.com", "/mgdx/RoueLibre-data/releases/latest/download/x"),
            RedirectScope.PUBLICATION,
        ).use { assertEquals("data", it.body.string()) }

        first.enqueue(redirect(address(second, "evil.example", "/x")))
        assertRefused { get(address(first, "github.com"), RedirectScope.PUBLICATION) }
        assertEquals(1, second.requestCount)
    }

    @Test
    fun `the asset storage is no exception for another host or for a feed`() {
        first.enqueue(redirect(address(second, "x.githubusercontent.com")))
        assertRefused { get(address(first, "example.org"), RedirectScope.PUBLICATION) }
        first.enqueue(redirect(address(second, "x.githubusercontent.com")))
        assertRefused { get(address(first, "github.com"), RedirectScope.GBFS) }
        assertEquals(0, second.requestCount)
    }

    @Test
    fun `three redirections are followed and a fourth is refused`() {
        repeat(3) { first.enqueue(redirect("/again")) }
        first.enqueue(MockResponse(code = 200, body = "{}"))
        get(address(first, "localhost"), scope = null).use { assertEquals(200, it.code) }

        repeat(4) { first.enqueue(redirect("/again")) }
        assertRefused { get(address(first, "localhost"), scope = null) }
        assertEquals("3 + 1 for the first call, then 4 for the second", 8, first.requestCount)
    }

    @Test
    fun `a followed redirection keeps the request's headers`() {
        // A resumed download that lost its `Range` on the way would start again
        // from the first byte; a catalogue request that lost its validators
        // would download what it already holds.
        first.enqueue(redirect("/moved"))
        first.enqueue(MockResponse(code = 206, body = "x"))

        get(
            address(first, "localhost"),
            RedirectScope.PUBLICATION,
            "User-Agent" to "RoueLibre/test",
            "Range" to "bytes=10-",
            "If-None-Match" to "\"abc\"",
        ).close()

        first.takeRequest()
        val followed = first.takeRequest().headers
        assertEquals("RoueLibre/test", followed["User-Agent"])
        assertEquals("bytes=10-", followed["Range"])
        assertEquals("\"abc\"", followed["If-None-Match"])
    }

    @Test
    fun `a redirection towards cleartext is followed over TLS`() {
        // Mi Bici Tu Bici's server redirects to the very paths it names in
        // cleartext: they answer in TLS, and Android refuses them otherwise.
        first.enqueue(redirect("http://localhost:${first.port}/opendata/gbfs.json", code = 301))
        first.enqueue(MockResponse(code = 200, body = "{}"))

        get(address(first, "localhost"), scope = null).close()

        assertEquals(
            "https://localhost:${first.port}/opendata/gbfs.json".toHttpUrl(),
            called.last(),
        )
    }

    private fun assertRefused(call: () -> Unit) {
        try {
            call()
            fail("the redirection was followed")
        } catch (_: RefusedRedirectException) {
            // What the callers turn into an untrusted server.
        }
    }
}
