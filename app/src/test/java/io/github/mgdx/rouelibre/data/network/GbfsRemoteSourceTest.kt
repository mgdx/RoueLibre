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
import okio.Buffer
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

    @Test
    fun `a feed past the ceiling is refused rather than read into memory`() = runTest {
        // These feeds come from hosts the project has no hold over. One of them
        // answering with hundreds of megabytes used to raise an
        // OutOfMemoryError — an Error, which none of the catch blocks here sees
        // go by — and close the application on the most ordinary operation it
        // has. The body is served larger than the ceiling and never fully read.
        val server = MockWebServer()
        server.start()
        val oversized = Buffer().apply {
            val megabyte = ByteArray(1024 * 1024) { '.'.code.toByte() }
            repeat((MAXIMUM_DOCUMENT_BYTES / megabyte.size).toInt() + 1) { write(megabyte) }
        }
        server.enqueue(MockResponse.Builder().code(200).body(oversized).build())
        val source = GbfsRemoteSource(
            client = OkHttpClient(),
            parser = GbfsParser(),
            userAgent = "RoueLibre/test",
            unnamedStationLabel = { UNNAMED },
            ioDispatcher = Dispatchers.IO,
        )

        val outcome = source.fetchDiscovery(server.url("/gbfs.json").toString())

        server.close()
        assertTrue("expected a failure, got: $outcome", outcome is Outcome.Failure)
        // The ceiling by name, not merely a failure: a body read whole and then
        // found not to be JSON fails too, and would pass a laxer assertion
        // while having allocated everything the host chose to send.
        assertEquals(
            DataError.MalformedResponse("feed larger than $MAXIMUM_DOCUMENT_BYTES bytes"),
            (outcome as Outcome.Failure).error,
        )
    }

    @Test
    fun `reads the bikes outside stations from the feed the discovery names`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {"last_updated":1788432120,"ttl":60,"version":"2.3","data":{"bikes":[
                      {"bike_id":"a","lat":52.516,"lon":13.377,"vehicle_type_id":"348",
                       "current_fuel_percent":0.67,"current_range_meters":0},
                      {"bike_id":"b","lat":52.520,"lon":13.404,"station_id":"3140"}
                    ]}}
                """.trimIndent(),
            ),
        )
        val discovery = GbfsDiscovery(
            version = "2.3",
            feedUrlsByName = mapOf(
                GbfsFeedNames.FREE_BIKE_STATUS to server.url("/free_bike_status.json").toString(),
            ),
        )

        val feed = sourceOn(server).fetchVehicleStatus(discovery).valueOrNull()

        server.close()
        val bike = checkNotNull(feed).bikes.single()
        assertEquals("a", bike.id)
        assertEquals(0.67, checkNotNull(bike.chargeRatio), 1e-9)
    }

    @Test
    fun `a street bike feed announced and not served is read as none published`() = runTest {
        // The discovery document is the producer's word and the 404 its
        // correction: for this session the network publishes none, and the
        // map is not to say a server failed every five minutes over a feed it
        // can only do without.
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse(code = 404))
        val discovery = GbfsDiscovery(
            version = "3.0",
            feedUrlsByName = mapOf(
                GbfsFeedNames.VEHICLE_STATUS to server.url("/vehicle_status.json").toString(),
            ),
        )

        val outcome = sourceOn(server).fetchVehicleStatus(discovery)

        server.close()
        assertEquals(
            Outcome.Failure(DataError.FeedUnavailable(GbfsFeedNames.VEHICLE_STATUS)),
            outcome,
        )
    }

    private fun sourceOn(server: MockWebServer): GbfsRemoteSource = GbfsRemoteSource(
        client = OkHttpClient(),
        parser = GbfsParser(),
        userAgent = "RoueLibre/test",
        unnamedStationLabel = { UNNAMED },
        ioDispatcher = Dispatchers.IO,
    )

    private companion object {
        /** Stands in for `R.string.station_unnamed`, which no JVM test resolves. */
        const val UNNAMED = "Unnamed station"
    }
}
