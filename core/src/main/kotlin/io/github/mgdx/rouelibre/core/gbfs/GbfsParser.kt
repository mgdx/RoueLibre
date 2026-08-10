package io.github.mgdx.rouelibre.core.gbfs

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Parses the three GBFS documents the application needs.
 *
 * Nothing here touches the network: the parser takes text and returns domain
 * objects, which makes it entirely testable on the JVM from real captures of
 * the feeds (SPEC §14).
 */
public class GbfsParser {

    private val json = Json {
        // Producers regularly enrich their feeds; an unknown field must never
        // make the read fail.
        ignoreUnknownKeys = true
        // Some feeds omit fields that are nevertheless mandatory. The default
        // values declared on the models then take over.
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Reads the auto-discovery document and returns the feeds it publishes.
     *
     * Going through this document rather than guessing the URLs is the very
     * principle of GBFS, and shields the application from a feed being moved on
     * the producer's side (SPEC §4.1).
     *
     * @param document the raw contents of `gbfs.json`.
     * @return the URLs by feed name, or the error encountered.
     */
    public fun parseDiscovery(document: String): Outcome<GbfsDiscovery> = parsing {
        val root = json.parseToJsonElement(document).jsonObject
        val version = (root["version"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val data = root["data"]?.jsonObject
            ?: throw GbfsFormatException("the document has no \"data\" field")

        // GBFS 3.0 puts the feed list directly under "data". Earlier versions
        // nest it inside a language key whose name is not standardised: the
        // Lille feed publishes "en" although it serves a French network. The
        // first language present is therefore taken, rather than an assumed one.
        val feedsElement = data["feeds"]
            ?: data.values.firstOrNull()?.jsonObject?.get("feeds")
            ?: throw GbfsFormatException("no feed list inside \"data\"")

        val feeds = feedsElement.jsonArray.associate { element ->
            val feed = json.decodeFromJsonElement(
                GbfsFeedReference.serializer(),
                element,
            )
            feed.name to feed.url
        }
        if (feeds.isEmpty()) {
            throw GbfsFormatException("the feed list is empty")
        }
        GbfsDiscovery(version = version, feedUrlsByName = feeds)
    }

    /**
     * Reads `station_information` and returns the network's stations.
     *
     * A station whose coordinates are absurd is dropped rather than failing the
     * whole feed: a single faulty entry on the producer's side must not deprive
     * the user of the other 267.
     *
     * @param document the raw contents of `station_information.json`.
     */
    public fun parseStationInformation(document: String): Outcome<StationInformationFeed> =
        parsing {
            val envelope = json.decodeFromString(
                GbfsEnvelope.serializer(GbfsStationInformationData.serializer()),
                document,
            )
            val stations = envelope.data.stations.mapNotNull { entry ->
                val position = coordinatesOrNull(entry.lat, entry.lon) ?: return@mapNotNull null
                Station(
                    id = entry.stationId,
                    name = entry.name,
                    position = position,
                    capacity = entry.capacity?.takeIf { it >= 0 },
                    postalCode = entry.postCode?.takeIf { it.isNotBlank() },
                )
            }
            StationInformationFeed(
                stations = stations,
                lastUpdated = envelope.lastUpdated,
                version = envelope.version,
            )
        }

    /**
     * Reads `station_status` and returns the current state of the stations.
     *
     * @param document the raw contents of `station_status.json`.
     */
    public fun parseStationStatus(document: String): Outcome<StationStatusFeed> = parsing {
        val envelope = json.decodeFromString(
            GbfsEnvelope.serializer(GbfsStationStatusData.serializer()),
            document,
        )
        val availabilities = envelope.data.stations.map { entry ->
            StationAvailability(
                stationId = entry.stationId,
                // A negative count makes no sense; it is brought back to zero
                // rather than displaying "-1 bike".
                bikesAvailable = entry.bikesAvailable.coerceAtLeast(0),
                docksAvailable = entry.docksAvailable.coerceAtLeast(0),
                isInstalled = entry.isInstalled,
                isRenting = entry.isRenting,
                isReturning = entry.isReturning,
                reportedAt = entry.lastReported,
            )
        }
        StationStatusFeed(
            availabilities = availabilities,
            lastUpdated = envelope.lastUpdated,
            version = envelope.version,
        )
    }

    /**
     * Runs [block], converting any parsing failure into a [DataError].
     *
     * Serialization libraries report their problems through exceptions; the
     * rest of the application, for its part, knows only result values
     * (SPEC §14). The conversion happens here, at the boundary.
     */
    private inline fun <T> parsing(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (error: GbfsFormatException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "unexpected format"))
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "unreadable JSON"),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "value out of bounds"),
        )
    }

    /**
     * Builds a point, or `null` if the pair is unusable.
     *
     * The point (0, 0) is treated as absent: it falls in the Gulf of Guinea
     * and, in practice, signals a coordinate that was never filled in.
     */
    private fun coordinatesOrNull(latitude: Double, longitude: Double): Coordinates? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Coordinates(latitude, longitude)
    }
}

/**
 * What the auto-discovery document publishes.
 *
 * @property version the GBFS revision announced, if the producer publishes it.
 * @property feedUrlsByName the URL of each feed, keyed by its GBFS name.
 */
public data class GbfsDiscovery(
    public val version: String?,
    public val feedUrlsByName: Map<String, String>,
) {
    /**
     * The URL of the feed named [feedName].
     *
     * @return the URL, or a failure describing the missing feed — which allows
     *   saying precisely what the producer does not publish.
     */
    public fun urlOf(feedName: String): Outcome<String> = feedUrlsByName[feedName]
        ?.let { Outcome.Success(it) }
        ?: Outcome.Failure(DataError.FeedUnavailable(feedName))
}

/** The useful contents of `station_information`. */
public data class StationInformationFeed(
    public val stations: List<Station>,
    public val lastUpdated: Instant?,
    public val version: String?,
)

/** The useful contents of `station_status`. */
public data class StationStatusFeed(
    public val availabilities: List<StationAvailability>,
    public val lastUpdated: Instant?,
    public val version: String?,
)

/** The standard names of the GBFS feeds the application uses. */
public object GbfsFeedNames {
    /** Static station data. */
    public const val STATION_INFORMATION: String = "station_information"

    /** Real-time station state. */
    public const val STATION_STATUS: String = "station_status"
}
