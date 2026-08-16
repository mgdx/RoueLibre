package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.flatMap
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.gbfs.GbfsFeedNames
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.gbfs.StationInformationFeed
import io.github.mgdx.rouelibre.core.gbfs.StationStatusFeed
import io.github.mgdx.rouelibre.core.gbfs.VehicleTypesFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * @property ioDispatcher the execution context for the IO.
 */
class GbfsRemoteSource(
    private val client: OkHttpClient,
    private val parser: GbfsParser,
    private val userAgent: String,
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
                val body = response.body.string()
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
