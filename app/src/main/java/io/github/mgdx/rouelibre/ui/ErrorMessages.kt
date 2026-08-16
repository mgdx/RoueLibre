package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.DataError

/**
 * Turns a business failure into a sentence meant for the user.
 *
 * Here, and nowhere else, is where errors take words: the business module is
 * not allowed to hold displayable text (SPEC §9), and every failure must say
 * what happened and what to do (SPEC §14).
 *
 * @return a complete sentence, ready to be shown.
 */
fun DataError.toUserMessage(context: Context): String = when (this) {
    DataError.Offline -> context.getString(R.string.error_offline)
    DataError.Timeout -> context.getString(R.string.error_timeout)
    is DataError.ServerRefused ->
        context.getString(R.string.error_server_refused, statusCode)
    is DataError.UntrustedServer ->
        // Like the malformed case, the technical detail stays in the value: the
        // user is told whose problem it is, not what the handshake said.
        context.getString(R.string.error_untrusted_server)
    is DataError.MalformedResponse ->
        // The technical detail stays in the value, for the log and the bug
        // report; the user gets an instruction, not a trace.
        context.getString(R.string.error_malformed)
    is DataError.FeedUnavailable ->
        context.getString(R.string.error_feed_unavailable, feedName)
    is DataError.UnsupportedFeedVersion ->
        context.getString(R.string.error_unsupported_version, version)
    is DataError.LocalStorageFailure ->
        context.getString(R.string.error_local_storage)
    DataError.NoCityChosen -> context.getString(R.string.error_no_city_chosen)
}
