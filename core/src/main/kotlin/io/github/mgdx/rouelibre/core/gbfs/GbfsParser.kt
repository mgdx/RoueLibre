package io.github.mgdx.rouelibre.core.gbfs

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import io.github.mgdx.rouelibre.core.station.VehicleKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
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
     * the user of the other 267. It is dropped because there is nowhere to put
     * it — a station with no position is on no map and in no route.
     *
     * **A station left with no name once its blanks are gone is kept**, and
     * that is a decision rather than an oversight. It was dropped at first, on
     * the grounds that nothing could be said about it; but it is a real station
     * with real bikes, and dropping it takes it off the map and out of the list
     * because its network mistyped one string. Somebody standing in front of it
     * would find it missing and have no way of knowing why, whereas a row named
     * oddly is understood at a glance. So it stays, named with the best matter
     * at hand: **the street the feed publishes for it**, where it publishes
     * one. Where it publishes neither, the name is left empty here and the
     * layer that shows stations puts a translated label in its place —
     * `GbfsRemoteSource` — this module having no business holding a sentence in
     * one language. The producer's identifier is never used for it: `vlille_042`
     * is not the name of a place.
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
                    // The name arrives trimmed (see FlexibleTextSerializer), so
                    // an empty one is a name the feed did not really publish.
                    name = entry.name.ifEmpty { entry.address?.trim().orEmpty() },
                    position = position,
                    capacity = capacityOf(entry),
                    // A postcode is written beside the name, and it arrives
                    // from the same feed with the same liberties taken.
                    postalCode = entry.postCode?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            StationInformationFeed(
                stations = stations,
                lastUpdated = envelope.lastUpdated,
                version = envelope.version,
            )
        }

    /**
     * Reads `vehicle_types` and sorts the declared types into the three kinds.
     *
     * This is the table that gives the identifiers of `vehicle_types_available`
     * a meaning. It is read for what each identifier *is*, never for what the
     * network is: a declaration says what an operator may lend one day, and a
     * third of the networks declaring a mixed fleet have not one bike of one of
     * the two kinds in circulation. What the network lends is counted from
     * `station_status` instead, by [io.github.mgdx.rouelibre.core.station.countFleet].
     *
     * The declaration is nevertheless worth keeping on one point, which
     * [VehicleTypesFeed.declaresElectricBikes] carries: a network whose every
     * station is empty at that moment lets nothing be counted, and it must not
     * turn an electric city into a mechanical one.
     *
     * @param document the raw contents of `vehicle_types.json`.
     */
    public fun parseVehicleTypes(document: String): Outcome<VehicleTypesFeed> = parsing {
        val envelope = json.decodeFromString(
            GbfsEnvelope.serializer(GbfsVehicleTypesData.serializer()),
            document,
        )
        val kinds = envelope.data.vehicleTypes.associate { declared ->
            declared.vehicleTypeId to kindOf(declared)
        }
        VehicleTypesFeed(
            kinds = kinds,
            declaresElectricBikes = kinds.containsValue(VehicleKind.Electric),
            lastUpdated = envelope.lastUpdated,
            version = envelope.version,
        )
    }

    /**
     * Sorts one declared vehicle type into the kind the application counts by.
     *
     * Two questions, in that order. Is it a bicycle at all — a network's
     * electric SCOOTERS say nothing about its bikes, and they are counted in
     * the status feed alongside them. Then, does a motor help the rider:
     * `electric_assist` is the pedal-assist bike this is about, and `electric`
     * is a throttle vehicle, which on a bicycle form factor is still a bike one
     * does not pedal alone. Everything else — `human`, `combustion` — is not.
     *
     * A type declaring no form factor falls to [VehicleKind.Other]: the field
     * has been mandatory since GBFS 2.1, so its absence is a malformed entry,
     * and a vehicle we cannot even call a bicycle belongs in neither column.
     */
    private fun kindOf(declared: GbfsVehicleType): VehicleKind {
        if (declared.formFactor !in BICYCLE_FORM_FACTORS) return VehicleKind.Other
        return if (declared.propulsionType in ELECTRIC_PROPULSIONS) {
            VehicleKind.Electric
        } else {
            VehicleKind.Mechanical
        }
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
                bikesByVehicleType = bikesByVehicleType(entry),
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
     * Gathers a station's breakdown by vehicle type, whichever way it is
     * published.
     *
     * The two forms never coexist in a feed, and they are merged into the same
     * map so that the rest of the application knows only one shape. What the
     * identifiers mean is not decided here: the parser knows no network, and
     * the table that translates them lives in the city configuration
     * (SPEC §15).
     *
     * A malformed entry is dropped rather than failing the read: a breakdown
     * is a refinement of a count that is published on its own, and losing it
     * must never cost the user the station.
     */
    private fun bikesByVehicleType(entry: GbfsStationStatus): Map<String, Int> {
        val standard = entry.vehicleTypesAvailable.associate { count ->
            count.vehicleTypeId to count.count.coerceAtLeast(0)
        }
        if (standard.isNotEmpty()) return standard
        // Vélib' publishes one single-key object per kind, the key being the
        // kind's name: [{"mechanical": 3}, {"ebike": 0}].
        return entry.legacyBikesByKind
            ?.flatMap { element -> (element as? JsonObject)?.entries.orEmpty() }
            ?.mapNotNull { (kind, count) ->
                val bikes = (count as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                kind to bikes.coerceAtLeast(0)
            }
            ?.toMap()
            .orEmpty()
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
     * How many docking points a station has, when the document says twice.
     *
     * GBFS publishes the figure in two places and requires them to agree: the
     * plain `capacity`, and `vehicle_docks_capacity`, which itemises the same
     * docks by the vehicle types they take. Four of the three hundred and
     * thirty-two networks served publish the two in contradiction, always the
     * same way — `capacity` also counts `vehicle_types_capacity`, which is not
     * a count of docks at all but of vehicles a station may hold without one.
     * Bilbao Bizi adds 800 of them to every station and announces "822 docking
     * points" beside the nine bikes and thirteen spaces it counts on the very
     * same sheet.
     *
     * The itemised figure wins, because it is the one that can be checked and
     * the one that holds up: against the networks' own live counters, the
     * median error of `capacity` is 800 docks at Bilbao, 22 at BiciMAD and 21
     * at Nike, while the itemised figure is out by 0, 2 and 1 — the handful of
     * docks that are genuinely out of service. Preferring it needs no threshold
     * and no name of a city or an operator: it is what the standard says the
     * field is, applied wherever a producer publishes it.
     *
     * Silence is left alone. A network that itemises nothing keeps its
     * `capacity` exactly as before — that is Bixi, V'Lille, and the two hundred
     * and ninety-one others — and so does one whose two figures agree.
     */
    private fun capacityOf(entry: GbfsStationInformation): Int? {
        val itemised = entry.vehicleDocksCapacity
            .takeIf { it.isNotEmpty() }
            ?.sumOf { it.count }
            ?.takeIf { it >= 0 }
        return itemised ?: entry.capacity?.takeIf { it >= 0 }
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

    private companion object {
        /** The vehicle forms this application is about. */
        val BICYCLE_FORM_FACTORS = setOf("bicycle", "cargo_bicycle")

        /** The GBFS propulsion values that mean a motor helps the rider. */
        val ELECTRIC_PROPULSIONS = setOf("electric_assist", "electric")
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

/**
 * The useful contents of `vehicle_types`.
 *
 * @property kinds what each declared identifier is. Empty for a network that
 *   publishes the feed with nothing in it.
 * @property declaresElectricBikes whether a pedal-assist bicycle is among the
 *   types declared. All there is to go on when nothing can be counted.
 */
public data class VehicleTypesFeed(
    public val kinds: Map<String, VehicleKind>,
    public val declaresElectricBikes: Boolean,
    public val lastUpdated: Instant?,
    public val version: String?,
)

/** The standard names of the GBFS feeds the application uses. */
public object GbfsFeedNames {
    /** Static station data. */
    public const val STATION_INFORMATION: String = "station_information"

    /** Real-time station state. */
    public const val STATION_STATUS: String = "station_status"

    /**
     * What each vehicle type identifier stands for.
     *
     * Absent from GBFS 1.0, which is where Vélib' Métropole still is: a network
     * publishing no such feed names its kinds inline in `station_status`
     * instead, and the fleet is counted through those names.
     */
    public const val VEHICLE_TYPES: String = "vehicle_types"
}
