package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.gbfs.GbfsFeedNames
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

/**
 * Tests of how a network failure is named (SPEC §14).
 *
 * A server whose certificate cannot be trusted is not a server publishing
 * rubbish, and the two must not reach the user as the same sentence. The
 * failure is raised by an interceptor rather than by a real handshake: what is
 * under test is the name given to it, and standing up a TLS server with an
 * expired certificate would test the JDK's validation, which nobody doubts.
 *
 * The naming of a station its network left nameless is tested here too: it is
 * this class that puts the reader's language on a feed the parser can only read
 * (SPEC §14).
 */
class GbfsRemoteSourceTest {

    private fun sourceFailingWith(error: Exception): GbfsRemoteSource = GbfsRemoteSource(
        client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw error })
            .build(),
        parser = GbfsParser(),
        userAgent = "RoueLibre/test",
        unnamedStationLabel = { UNNAMED },
        ioDispatcher = Dispatchers.IO,
    )

    @Test
    fun `names a certificate that cannot be trusted for what it is`() = runTest {
        // sharedmobility.ch served Zürich with a certificate expired since the
        // day before: the application announced unreadable data, blaming a feed
        // it had not received one byte of.
        val source = sourceFailingWith(SSLHandshakeException("certificate expired"))

        val outcome = source.fetchDiscovery("https://www.sharedmobility.ch/gbfs.json")

        assertTrue(outcome is Outcome.Failure)
        val error = (outcome as Outcome.Failure).error
        assertTrue("expected UntrustedServer, got $error", error is DataError.UntrustedServer)
        assertEquals("certificate expired", (error as DataError.UntrustedServer).detail)
    }

    @Test
    fun `names a station its network published without a name`() = runTest {
        // A station is kept even when its feed carries neither name nor street
        // — it is real and it holds bikes — so something has to call it
        // something. The producer's identifier is not that something.
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {"last_updated":1755512400,"ttl":60,"version":"2.3","data":{"stations":[
                      {"station_id":"nameless","name":"  ","lat":50.633,"lon":3.058}
                    ]}}
                """.trimIndent(),
            ),
        )
        val source = GbfsRemoteSource(
            client = OkHttpClient(),
            parser = GbfsParser(),
            userAgent = "RoueLibre/test",
            unnamedStationLabel = { UNNAMED },
            ioDispatcher = Dispatchers.IO,
        )
        val discovery = GbfsDiscovery(
            version = "2.3",
            feedUrlsByName = mapOf(
                GbfsFeedNames.STATION_INFORMATION to server.url("/information.json").toString(),
            ),
        )

        val feed = source.fetchStationInformation(discovery).valueOrNull()

        server.close()
        val station = checkNotNull(feed).stations.single()
        assertEquals(UNNAMED, station.name)
    }

    @Test
    fun `still calls a broken exchange a malformed response`() = runTest {
        // The generic case must keep its meaning: the connection held, and what
        // came through it is what went wrong.
        val source = sourceFailingWith(java.io.IOException("unexpected end of stream"))

        val outcome = source.fetchDiscovery("https://example.invalid/gbfs.json")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }

    private companion object {
        /** Stands in for `R.string.station_unnamed`, which no JVM test resolves. */
        const val UNNAMED = "Unnamed station"
    }
}
