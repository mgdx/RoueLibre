package io.github.mgdx.rouelibre.data.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests of the rule that sends every request in TLS (SPEC §4.1).
 *
 * No server for the address the call starts from: what is under test there is
 * the address the client is about to call, and it is read from an interceptor
 * placed behind the one being tested, which answers on the spot. That checks
 * the rewriting where it happens — before any connection — which is precisely
 * what a cleartext address never gets to.
 *
 * The redirection is the one case that needs a real server: an answer is only
 * a redirection once OkHttp has read it as such, from a connection it opened
 * itself. What is checked there is the `Location` handed back to it, which is
 * the address the next request is built from.
 */
class HttpsOnlyInterceptorTest {

    /** The address the client would have called, for the address asked for. */
    private fun addressCalled(requested: String): HttpUrl {
        lateinit var called: HttpUrl
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpsOnlyInterceptor())
            .addInterceptor(
                Interceptor { chain ->
                    called = chain.request().url
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody())
                        .build()
                },
            )
            .build()

        client.newCall(Request.Builder().url(requested).get().build()).execute().close()
        return called
    }

    @Test
    fun `calls in TLS a feed the producer published in cleartext`() {
        // Mi Bici Tu Bici serves its auto-discovery document in https and names
        // its feeds in http: the paths below answer in TLS, and Android refuses
        // to fetch them otherwise.
        val called = addressCalled(
            "http://www.mibicitubici.gob.ar/opendata/station_information.json",
        )

        assertEquals(
            "https://www.mibicitubici.gob.ar/opendata/station_information.json",
            called.toString(),
        )
    }

    @Test
    fun `leaves an address already in TLS untouched`() {
        val requested = "https://gbfs.capitalbikeshare.com/gbfs/2.3/gbfs.json"

        assertEquals(requested, addressCalled(requested).toString())
    }

    @Test
    fun `keeps an explicit port and drops the one that came with the scheme`() {
        // A port chosen by the producer means the same thing on either scheme;
        // the cleartext default port would mean an address nobody serves.
        assertEquals(
            "https://example.invalid:8080/gbfs.json",
            addressCalled("http://example.invalid:8080/gbfs.json").toString(),
        )
        assertEquals(
            "https://example.invalid/gbfs.json",
            addressCalled("http://example.invalid:80/gbfs.json").toString(),
        )
    }

    @Test
    fun `secures an address on its own, without going through a call`() {
        assertEquals(
            "https://example.invalid/station_status.json".toHttpUrl(),
            "http://example.invalid/station_status.json".toHttpUrl().overHttps(),
        )
    }

    @Test
    fun `points a redirection towards TLS before it is followed`() {
        // What OkHttp builds the next request from is this header, so a
        // `Location` corrected here is a request that goes out in TLS. The
        // client is told not to follow it only so that the answer can be read:
        // following it would need a server holding a certificate, and what is
        // under test is the address, not a handshake.
        val producer = MockWebServer()
        producer.start()
        producer.enqueue(
            MockResponse(
                code = 301,
                headers = headersOf("Location", "http://mibicitubici.gob.ar/opendata/gbfs.json"),
            ),
        )
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(HttpsOnlyRedirectInterceptor())
            .followRedirects(false)
            .build()

        val response = client.newCall(
            Request.Builder().url(producer.url("/gbfs.json")).get().build(),
        ).execute()

        assertEquals(
            "https://mibicitubici.gob.ar/opendata/gbfs.json",
            response.header("Location"),
        )
        response.close()
        producer.close()
    }

    @Test
    fun `leaves alone a Location that does not point anywhere`() {
        // On a `201` the header names what has just been created rather than
        // where to go next, and nothing follows it: rewriting it would be
        // answering a question nobody asked.
        val producer = MockWebServer()
        producer.start()
        producer.enqueue(
            MockResponse(
                code = 201,
                headers = headersOf("Location", "http://example.invalid/created"),
            ),
        )
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(HttpsOnlyRedirectInterceptor())
            .build()

        val response = client.newCall(
            Request.Builder().url(producer.url("/anything")).get().build(),
        ).execute()

        assertEquals("http://example.invalid/created", response.header("Location"))
        response.close()
        producer.close()
    }
}
