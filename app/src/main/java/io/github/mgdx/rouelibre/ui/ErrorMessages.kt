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
 * @param hasKnownAvailability whether any availability at all has ever been
 *   received for the network in service. Being offline reads differently on
 *   either side of that line: "the last known availability stays on screen"
 *   is true and useful over counters that are merely old, and is a promise of
 *   something the reader has never had on a fresh install whose first refresh
 *   never got through — an empty map, under a pill saying "Never updated".
 *   The screens read it from `state.fetchedAt`, the same value the pill is
 *   written from. It defaults to the ordinary case, availability on screen.
 * @return a complete sentence, ready to be shown.
 */
fun DataError.toUserMessage(context: Context, hasKnownAvailability: Boolean = true): String =
    when (this) {
        DataError.Offline -> context.getString(
            if (hasKnownAvailability) {
                R.string.error_offline
            } else {
                R.string.error_offline_no_availability
            },
        )
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
 * A sixth failure is neither the server's nor resumable in the same way: the
 * device refusing the file. Downloading writes where refreshing reads, so the
 * refresh's "unable to read the data stored on the device" describes the
 * opposite gesture, and the way out is the reader's own rather than the host's.
 *
 * Everything else — no city chosen above all — reads the same on both screens
 * and is left to [toUserMessage] rather than copied, which is what keeps the
 * two from drifting apart.
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
    is DataError.LocalStorageFailure ->
        // The one failure of the six that is not the server's: downloading
        // WRITES to the device where refreshing reads from it, so "unable to
        // read the data stored on the device" describes the opposite gesture.
        // It is also the one whose way out is the reader's own — free some
        // space — rather than waiting.
        context.getString(R.string.error_local_storage_download)
    else -> toUserMessage(context)
}

/**
 * The same failures again, read after a press on "Check for updates", where a
 * manifest was being read rather than a file coming down.
 *
 * Checking is a third gesture, and none of the two registers above fits it.
 * [toDownloadMessage] promises the transfer picks up where it stopped, which is
 * exactly what a failed check cannot promise: **nothing was ever being
 * transferred**. A single request goes out, it reads what is published, and it
 * is over — so the whole check has to be made again, and every sentence below
 * says so rather than announcing a resumption that would never come. That was
 * the defect: an update check made in flight mode answered "No connection. The
 * download picks up where it stopped.", about a download nobody had started.
 *
 * What it keeps from [toDownloadMessage] is whose server this is: the one
 * hosting the datasets, never the bike network's, which this screen no more
 * talks to when checking than when downloading. So no sentence here names the
 * network either.
 *
 * The device's own refusal is the one that changes side. Downloading writes,
 * and its wording sends the reader to free some space; checking only **reads**
 * — the installed versions, to hold them against what is published — so freeing
 * space answers nothing, and what is said instead is that there was nothing to
 * compare the manifest against.
 *
 * Everything else, [DataError.NoCityChosen] first of all — the check's own way
 * of saying there is no manifest to ask for — reads the same on all three and is
 * left to [toUserMessage].
 *
 * @return a complete sentence, ready to be shown.
 */
fun DataError.toUpdateCheckMessage(context: Context): String = when (this) {
    DataError.Offline -> context.getString(R.string.error_offline_check)
    DataError.Timeout -> context.getString(R.string.error_timeout_check)
    is DataError.ServerRefused ->
        context.getString(R.string.error_server_refused_check, statusCode)
    is DataError.UntrustedServer ->
        context.getString(R.string.error_untrusted_server_check)
    is DataError.MalformedResponse ->
        // The manifest that will not parse, as for the download: the technical
        // detail stays in the value, and the user is told what to do.
        context.getString(R.string.error_malformed_check)
    is DataError.LocalStorageFailure ->
        context.getString(R.string.error_local_storage_check)
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
