package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.flatMap
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.gbfs.GbfsFeedNames
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.gbfs.StationInformationFeed
import io.github.mgdx.rouelibre.core.gbfs.StationStatusFeed
import io.github.mgdx.rouelibre.core.gbfs.VehicleStatusFeed
import io.github.mgdx.rouelibre.core.gbfs.VehicleTypesFeed
import io.github.mgdx.rouelibre.core.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Fetches the GBFS feeds over the network.
 *
 * The only request that goes out in ordinary use is for these feeds
 * (SPEC §11.7). Nothing is triggered in the background: every call comes from a
 * user action or from a screen being shown.
 *
 * @property client the shared HTTP client, to reuse connections.
 * @property parser the parser for the documents received.
 * @property userAgent identifies the application and its version, with no
 *   identifier specific to the user or the device (SPEC §4.4).
 * @property unnamedStationLabel what a station is called when its feed
 *   publishes no name for it and no street either. It comes from here because
 *   it is a translated sentence and the parser is Android-free Kotlin
 *   (SPEC §14); it is read at every fetch rather than captured once, so it
 *   follows the language in force.
 * @property ioDispatcher the execution context for the IO.
 */
class GbfsRemoteSource(
    private val client: OkHttpClient,
    private val parser: GbfsParser,
    private val userAgent: String,
    private val unnamedStationLabel: () -> String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Reads the auto-discovery document at the given URL.
     *
     * @param discoveryUrl the `gbfs.json` URL, from the city configuration or
     *   from the user setting.
     */
    suspend fun fetchDiscovery(discoveryUrl: String): Outcome<GbfsDiscovery> =
        fetchText(discoveryUrl).flatMap(parser::parseDiscovery)

    /**
     * Reads the stations' static data.
     *
     * The feed's URL always comes from the auto-discovery document, never from
     * a constant: that is the principle of GBFS and it shields us from a feed
     * being moved on the producer's side (SPEC §4.1).
     */
    suspend fun fetchStationInformation(
        discovery: GbfsDiscovery,
    ): Outcome<StationInformationFeed> = discovery.urlOf(GbfsFeedNames.STATION_INFORMATION)
        .flatMap { fetchText(it) }
        .flatMap(parser::parseStationInformation)
        .map(::named)

    /**
     * Names the stations their network left nameless.
     *
     * A feed sometimes publishes a station with a blank name and no street to
     * fall back on. Such a station is kept — it is real, it holds bikes, and
     * taking it off the map over a mistyped string would leave whoever is
     * standing in front of it wondering why it is missing — so it needs
     * something to be called. That something is a translated label, which is
     * why it is put on here and not in the parser: the parser is Kotlin with no
     * Android in it and can hold no sentence in one language (SPEC §14).
     *
     * The producer's identifier is deliberately not used: `vlille_042` is not
     * the name of a place, and a technical key under a reader's eyes explains
     * nothing to them.
     */
    private fun named(feed: StationInformationFeed): StationInformationFeed = feed.copy(
        stations = feed.stations.map { station ->
            if (station.name.isNotEmpty()) {
                station
            } else {
                station.copy(name = unnamedStationLabel())
            }
        },
    )

    /** Reads the stations' real-time state. */
    suspend fun fetchStationStatus(discovery: GbfsDiscovery): Outcome<StationStatusFeed> =
        discovery.urlOf(GbfsFeedNames.STATION_STATUS)
            .flatMap { fetchText(it) }
            .flatMap(parser::parseStationStatus)

    /**
     * Reads what each vehicle type identifier stands for.
     *
     * The feed a network on GBFS 1.0 does not have: the failure is then
     * `FeedUnavailable`, which the caller reads as "nothing declared" rather
     * than as a breakdown.
     */
    suspend fun fetchVehicleTypes(discovery: GbfsDiscovery): Outcome<VehicleTypesFeed> =
        discovery.urlOf(GbfsFeedNames.VEHICLE_TYPES)
            .flatMap { fetchText(it) }
            .flatMap(parser::parseVehicleTypes)

    /**
     * Reads the bikes the network reports outside its stations (SPEC §4.1).
     *
     * Under whichever of its two names the producer publishes the feed, and
     * only while the setting of SPEC §7.6 is on — the caller's business. A
     * network publishing none answers `FeedUnavailable`, an ordinary answer
     * for a docked fleet. **So does a feed announced and not served**: a
     * producer that lists the feed in its discovery document and answers 404
     * on it is, for this session, a producer publishing none. There is
     * nothing the user can do about it, and reading it as a server failure
     * would put a sentence on the screen every five minutes for a feed the
     * map can only do without.
     */
    suspend fun fetchVehicleStatus(discovery: GbfsDiscovery): Outcome<VehicleStatusFeed> =
        discovery.urlOfVehicleStatus()
            .flatMap { fetchText(it) }
            .flatMap(parser::parseVehicleStatus)
            .let { outcome ->
                if (outcome is Outcome.Failure && outcome.error == DataError.ServerRefused(404)) {
                    Outcome.Failure(DataError.FeedUnavailable(GbfsFeedNames.VEHICLE_STATUS))
                } else {
                    outcome
                }
            }

    /**
     * Runs a GET and returns the response body.
     *
     * Network breakdowns are converted into a [DataError] rather than
     * propagated: the caller has to choose a message, not catch an exception
     * (SPEC §14).
     */
    private suspend fun fetchText(url: String): Outcome<String> = withContext(ioDispatcher) {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
        } catch (_: IllegalArgumentException) {
            // An invalid URL can only come from the user setting: the shipped
            // configuration is verified.
            return@withContext Outcome.Failure(
                DataError.MalformedResponse("invalid URL: $url"),
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Outcome.Failure(
                        DataError.ServerRefused(response.code),
                    )
                }
                val body = response.body.textUpTo()
                    ?: return@withContext Outcome.Failure(
                        DataError.MalformedResponse(
                            "feed larger than $MAXIMUM_DOCUMENT_BYTES bytes",
                        ),
                    )
                if (body.isBlank()) {
                    return@withContext Outcome.Failure(
                        DataError.MalformedResponse("empty response"),
                    )
                }
                Outcome.Success(body)
            }
        } catch (_: SocketTimeoutException) {
            Outcome.Failure(DataError.Timeout)
        } catch (_: UnknownHostException) {
            // A name that cannot be resolved: in practice, no connection.
            Outcome.Failure(DataError.Offline)
        } catch (error: SSLException) {
            // Before the generic case below, which it would otherwise fall into
            // — it is an IOException like any other — and be announced as a
            // feed publishing rubbish, when nothing was received at all. The
            // certificate itself never surfaces on its own: Android wraps its
            // refusal in a handshake failure, so this is where it is caught.
            Outcome.Failure(
                DataError.UntrustedServer(error.message ?: "TLS handshake refused"),
            )
        } catch (error: IOException) {
            Outcome.Failure(
                DataError.MalformedResponse(error.message ?: "network failure"),
            )
        }
    }
}

/**
 * The most a JSON document read into memory may weigh, in bytes.
 *
 * Sixteen mebibytes. The documents concerned — a GBFS feed, the catalogue, a
 * release manifest — are all read whole into a string, and the largest of them
 * is the `station_information` of the largest network served: sharedmobility.ch
 * publishes 12,896 stations, a few megabytes of JSON. This leaves that feed room
 * to grow several times over before anyone notices a ceiling, while keeping the
 * memory a host can make the application allocate to a known figure.
 *
 * Known matters more than generous here: these feeds come from 337 third-party
 * hosts the project has no hold over, and a body of a few hundred megabytes
 * raises an `OutOfMemoryError` — an `Error`, which none of the `catch` blocks
 * around these reads would see go by, so the application closes.
 */
internal const val MAXIMUM_DOCUMENT_BYTES: Long = 16L * 1024 * 1024

/**
 * The body as text, or `null` if it goes past [limit].
 *
 * Read through the buffered source rather than with `string()`: the latter has
 * the whole body in memory before its size can be looked at, which is precisely
 * what has to be avoided. Asking the source for one byte more than the ceiling
 * fills the buffer no further than that, so an endless response is stopped
 * having cost the ceiling and nothing beyond it — and the rest is never fetched.
 *
 * The announced length is deliberately not consulted: a chunked response
 * declares none, and a host that means harm declares whatever suits it.
 */
internal fun ResponseBody.textUpTo(limit: Long = MAXIMUM_DOCUMENT_BYTES): String? {
    val source = source()
    if (source.request(limit + 1)) return null
    // The charset the response declares, as `string()` would have honoured it.
    return source.readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}
