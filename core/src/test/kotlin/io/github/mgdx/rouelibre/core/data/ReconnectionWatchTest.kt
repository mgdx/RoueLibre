package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.DataError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a transfer that failed offline becomes when the connection comes back
 * (SPEC §4.4).
 *
 * The defect: a download cut by a lost Wi-Fi left the storage screen saying "No
 * connection. The download picks up where it stopped." more than three minutes
 * after the Wi-Fi had returned, and nothing picked up. The screen must say at
 * once that the connection is back, and leave the resumption to a press.
 */
class ReconnectionWatchTest {

    @Test
    fun `an offline failure becomes ready to resume when the connection comes back`() {
        val watch = ReconnectionWatch()

        watch.transferFailed(DataError.Offline)
        assertFalse(watch.connectionChanged(connected = false))
        assertFalse(watch.isReadyToResume)

        assertTrue(watch.connectionChanged(connected = true))
        assertTrue(watch.isReadyToResume)
    }

    /**
     * Ready to resume is an offer and nothing else: it stands, connection after
     * connection, until a transfer starts — and only a press starts one.
     */
    @Test
    fun `the offer waits for a press rather than spending itself`() {
        val watch = ReconnectionWatch()
        watch.transferFailed(DataError.Offline)
        watch.connectionChanged(connected = true)

        // Announced once: a second report of the same connection says nothing new.
        assertFalse(watch.connectionChanged(connected = true))
        assertTrue(watch.isReadyToResume)

        watch.transferStarted()
        assertFalse(watch.isReadyToResume)
    }

    /** The order the system reports things in is not the order they happened in. */
    @Test
    fun `the connection may be reported lost before the transfer fails`() {
        val watch = ReconnectionWatch()

        watch.connectionChanged(connected = false)
        watch.transferFailed(DataError.Offline)

        assertTrue(watch.connectionChanged(connected = true))
    }

    @Test
    fun `losing the connection again puts the offer away until it returns`() {
        val watch = ReconnectionWatch()
        watch.transferFailed(DataError.Offline)
        watch.connectionChanged(connected = true)

        watch.connectionChanged(connected = false)
        assertFalse(watch.isReadyToResume)

        assertTrue(watch.connectionChanged(connected = true))
        assertTrue(watch.isReadyToResume)
    }

    /** A server that refused is not answered by a connection coming back. */
    @Test
    fun `a failure other than the connection is not followed`() {
        val watch = ReconnectionWatch()

        watch.transferFailed(DataError.ServerRefused(503))
        watch.connectionChanged(connected = false)

        assertFalse(watch.connectionChanged(connected = true))
        assertFalse(watch.isReadyToResume)
    }

    @Test
    fun `nothing failed, nothing is announced`() {
        val watch = ReconnectionWatch()

        assertFalse(watch.connectionChanged(connected = true))
        assertFalse(watch.isReadyToResume)
    }

    /** A transfer started while offline answers the earlier failure itself. */
    @Test
    fun `a transfer started before the connection returns leaves nothing to announce`() {
        val watch = ReconnectionWatch()
        watch.transferFailed(DataError.Offline)

        watch.transferStarted()

        assertFalse(watch.connectionChanged(connected = true))
    }
}
