package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.journey.NoBikeJourney
import io.github.mgdx.rouelibre.core.station.WantedBikeKind

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

/**
 * The same failures, read on the storage screen, where a file was coming down
 * rather than an availability refresh going out.
 *
 * Two things separate a dataset transfer from a refresh of the availability,
 * and every sentence below turns on one of them.
 *
 * **The server is not the same one.** "The network's server" is the bike-share
 * operator's, which the storage screen never talks to: the files come from
 * whoever hosts them. Somebody told their network's server refused an error 503
 * while downloading a map would go looking for a breakdown at the wrong end.
 *
 * **The transfer is resumable** (SPEC §4.4), so the answer to "what now" is
 * never "start over". That is what each of these says will happen next, and it
 * is what the refresh's wordings cannot say — a refresh that fails leaves the
 * last availability on screen, which is the sentence a stopped download was
 * being answered with.
 *
 * Everything else — no city chosen, a local file that cannot be read — reads
 * the same on both screens and is left to [toUserMessage] rather than copied,
 * which is what keeps the two from drifting apart.
 *
 * @return a complete sentence, ready to be shown.
 */
fun DataError.toDownloadMessage(context: Context): String = when (this) {
    DataError.Offline -> context.getString(R.string.error_offline_download)
    DataError.Timeout -> context.getString(R.string.error_timeout_download)
    is DataError.ServerRefused ->
        context.getString(R.string.error_server_refused_download, statusCode)
    is DataError.UntrustedServer ->
        context.getString(R.string.error_untrusted_server_download)
    is DataError.MalformedResponse ->
        // Everything unreadable arrives here: a manifest that will not parse, an
        // address that is not one, a transfer cut in the middle. The technical
        // detail stays in the value, as it does for the refresh.
        context.getString(R.string.error_malformed_download)
    else -> toUserMessage(context)
}

/**
 * Turns a journey that could not be composed into a sentence for the user.
 *
 * Each says what happened **and what to do about it** (SPEC §14): the missing
 * routing data sends one to the storage screen, an empty network to another hour
 * or another starting point, and a kind of bike nobody has to the other kind.
 * Naming the kind is what makes that last one an answer — "no station nearby has
 * an electric bike right now" leaves something to do, where "no bike found"
 * leaves one looking for another address.
 *
 * [NoBikeJourney.WalkingIsQuicker] is not a failure and never reaches here: it
 * comes with a walk of its own, which the summary describes (SPEC §6).
 */
fun NoBikeJourney.toUserMessage(context: Context): String = context.getString(
    when (this) {
        NoBikeJourney.NoBikeNearby -> R.string.journey_no_bike_nearby
        is NoBikeJourney.NoWantedBikeNearby -> when (wanted) {
            WantedBikeKind.Mechanical -> R.string.journey_no_mechanical_nearby
            WantedBikeKind.Electric -> R.string.journey_no_electric_nearby
        }

        NoBikeJourney.NoDockNearby -> R.string.journey_no_dock_nearby
        NoBikeJourney.NoRouteBetweenStations -> R.string.journey_no_route
        NoBikeJourney.GraphMissing -> R.string.journey_graph_missing
        NoBikeJourney.OutsideCoverage -> R.string.journey_outside_coverage
        NoBikeJourney.WalkingIsQuicker -> R.string.journey_no_route
    },
)
