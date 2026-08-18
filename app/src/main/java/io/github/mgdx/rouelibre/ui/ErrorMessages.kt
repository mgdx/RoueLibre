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
 * Only the wordings that name the availability refresh are said a second time
 * here. A dataset transfer cut short answered "the last known availability
 * stays on screen" tells its reader about bikes they never asked after, and
 * nothing at all about the transfer they were watching. Everything else reads
 * the same on both screens and is left to [toUserMessage], which is what keeps
 * the two from drifting apart.
 *
 * @return a complete sentence, ready to be shown.
 */
fun DataError.toDownloadMessage(context: Context): String = when (this) {
    // What is worth saying to somebody whose download has just stopped is that
    // nothing received is lost: these files come down resumably (SPEC §4.4).
    DataError.Offline -> context.getString(R.string.error_offline_download)
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
