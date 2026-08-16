package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
 */
class GbfsRemoteSourceTest {

    private fun sourceFailingWith(error: Exception): GbfsRemoteSource = GbfsRemoteSource(
        client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw error })
            .build(),
        parser = GbfsParser(),
        userAgent = "RoueLibre/test",
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
    fun `still calls a broken exchange a malformed response`() = runTest {
        // The generic case must keep its meaning: the connection held, and what
        // came through it is what went wrong.
        val source = sourceFailingWith(java.io.IOException("unexpected end of stream"))

        val outcome = source.fetchDiscovery("https://example.invalid/gbfs.json")

        assertTrue(outcome is Outcome.Failure)
        assertTrue((outcome as Outcome.Failure).error is DataError.MalformedResponse)
    }
}
