package io.github.mgdx.rouelibre.data.network

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
 * No server here: what is under test is the address the client is about to
 * call, and it is read from an interceptor placed behind the one being tested,
 * which answers on the spot. That checks the rewriting where it happens —
 * before any connection — which is precisely what a cleartext address never
 * gets to.
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
}
