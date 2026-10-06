package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.DataError

/**
 * Follows a dataset transfer that failed for want of a connection, until the
 * connection comes back (SPEC §4.4).
 *
 * The screen that failed used to keep saying "No connection" long after the
 * Wi-Fi had returned, under a sentence promising that the download picked up
 * where it stopped — which it never did, since nothing may start again from the
 * background. What is decided here is the moment the screen must change its
 * words: the connection has come back, the transfer can resume, and the press
 * that resumes it is the user's.
 *
 * **It offers, it never starts.** Nothing in this class can start a transfer,
 * and becoming [isReadyToResume] does not spend anything: the offer stands
 * until a transfer actually starts, which only a press does.
 *
 * Kept free of Android, as [MeteredTransferGate] is, so that the transition is
 * something a test can hold.
 */
public class ReconnectionWatch {

    /** The last transfer failed offline and no connection has been seen since. */
    private var waitingForConnection: Boolean = false

    /** The connection came back after such a failure, and nothing has started since. */
    private var readyToResume: Boolean = false

    /**
     * Whether the last transfer failed offline and the connection has come back
     * since: resuming it is offered, and waits for a press.
     */
    public val isReadyToResume: Boolean
        get() = readyToResume

    /**
     * A transfer is starting: whatever failure was being followed is answered.
     */
    public fun transferStarted() {
        waitingForConnection = false
        readyToResume = false
    }

    /**
     * A transfer has failed.
     *
     * Only [DataError.Offline] is followed: a server that refused or a file
     * that would not read are not answered by a connection coming back, and
     * announcing one over them would be announcing a remedy for the wrong
     * fault.
     */
    public fun transferFailed(error: DataError) {
        waitingForConnection = error == DataError.Offline
        readyToResume = false
    }

    /**
     * The connection in use has changed.
     *
     * A connection lost again after it had come back puts the offer away: a
     * press would only fail the same way, and the screen goes back to saying
     * there is no connection.
     *
     * @param connected whether the device has a connection to the internet.
     * @return `true` exactly when this change is the connection coming back
     *   after an offline failure — the moment the screen must say so at once.
     */
    public fun connectionChanged(connected: Boolean): Boolean {
        if (!connected) {
            if (readyToResume) {
                readyToResume = false
                waitingForConnection = true
            }
            return false
        }
        if (!waitingForConnection) return false
        waitingForConnection = false
        readyToResume = true
        return true
    }
}
