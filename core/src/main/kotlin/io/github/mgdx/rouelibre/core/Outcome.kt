package io.github.mgdx.rouelibre.core

/**
 * The result of an operation that can fail.
 *
 * SPEC §14 mandates result types rather than silent exceptions. Failures
 * therefore travel back as values, which the caller cannot ignore by accident.
 *
 * No variant carries text meant for the user: the business module is not
 * allowed to hold a displayable string (SPEC §9). It is the Android layer that
 * turns each error into a French message.
 */
public sealed interface Outcome<out T> {

    /** The operation succeeded and carries its value. */
    public data class Success<out T>(public val value: T) : Outcome<T>

    /** The operation failed for the reason described by [error]. */
    public data class Failure(public val error: DataError) : Outcome<Nothing>

    public companion object {
        /** Construction shorthand, to lighten call sites. */
        public fun <T> success(value: T): Outcome<T> = Success(value)

        /** Construction shorthand, to lighten call sites. */
        public fun failure(error: DataError): Outcome<Nothing> = Failure(error)
    }
}

/**
 * Applies [transform] to the value carried, propagating failure unchanged.
 */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/**
 * Chains an operation that can itself fail, propagating failure unchanged.
 */
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }

/** The value carried, or `null` on failure. */
public fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

/**
 * The cause of a failure to fetch or read data.
 *
 * Each variant corresponds to different conduct for the user, and therefore to
 * a distinct message — that is the criterion that guided this split, not the
 * technical nature of the breakdown.
 */
public sealed interface DataError {

    /** The device has no connection. The last known state stays displayable. */
    public data object Offline : DataError

    /** The request went through but the server answered with an error. */
    public data class ServerRefused(public val statusCode: Int) : DataError

    /** The request did not complete within the allotted time. */
    public data object Timeout : DataError

    /**
     * The server's identity could not be established, so nothing was fetched.
     *
     * An expired certificate, a chain that leads nowhere: the exchange stopped
     * before a single byte of data was received, which is what separates this
     * from [MalformedResponse] — there is nothing to blame the producer's feed
     * for, and nothing the user can do but wait for the operator to put its
     * certificate back in order. sharedmobility.ch let its own expire on
     * 15 August 2026, and the application said its data was unreadable.
     *
     * @property detail a technical description, meant for the log and the bug
     *   report, never for the screen.
     */
    public data class UntrustedServer(public val detail: String) : DataError

    /**
     * The response does not have the expected shape.
     *
     * @property detail a technical description, meant for the log and the bug
     *   report, never for the screen.
     */
    public data class MalformedResponse(public val detail: String) : DataError

    /**
     * The auto-discovery feed does not publish the feed asked for.
     *
     * @property feedName the name of the missing GBFS feed.
     */
    public data class FeedUnavailable(public val feedName: String) : DataError

    /**
     * The producer announces a GBFS version the application cannot read. It has
     * to be said, with an invitation to update, not fail in silence.
     */
    public data class UnsupportedFeedVersion(public val version: String) : DataError

    /** The local data is absent or unreadable. */
    public data class LocalStorageFailure(public val detail: String) : DataError

    /**
     * No city is chosen.
     *
     * Distinct from a breakdown: there is nothing to retry, only a city to
     * designate. It is the state of a first launch, and that of a device whose
     * last installed city's data has just been deleted.
     */
    public data object NoCityChosen : DataError
}
