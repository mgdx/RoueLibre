package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.VehicleKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Everything specific to one conurbation, and nothing else.
 *
 * None of these values is written in the code: no URL, no bounding box, no
 * centring coordinate, no network name. Serving another city is done by
 * replacing this file, without recompiling anything else (SPEC §15).
 */
public data class CityConfiguration(
    public val configVersion: Int,
    /**
     * The country the conurbation is in, as an ISO 3166-1 alpha-2 code, or
     * null when the configuration names none.
     *
     * What hangs on it is the side of the road traffic drives on, which the
     * routing profiles need to read the per-side cycleway tags (SPEC §5): a
     * lane painted on the with-traffic side serves the forward direction, and
     * which side that is belongs to the country, not to the city. Read but
     * absent means the right-hand default — the commoner side, and the
     * behaviour of every version before the tags were read at all.
     */
    public val country: String?,
    public val network: NetworkDescription,
    public val fleet: FleetDescription,
    public val gbfs: GbfsSettings,
    /**
     * The reference bounding box shared by the three offline datasets.
     *
     * Null as long as the data has never been generated. The application must
     * then limit itself to the station list and say so, rather than letting the
     * user believe the map is about to appear (SPEC §4.4).
     */
    public val boundingBox: BoundingBox?,
    public val map: MapDefaults,
    public val dataRelease: DataReleaseSettings,
)

/** The identity of the network served. */
public data class NetworkDescription(
    public val id: String,
    public val displayName: String,
    /** The conurbation served, when the configuration names it. */
    public val city: String?,
    public val operator: String,
    public val defaultLanguage: String,
)

/**
 * What the network lends, as counted from its own feeds.
 *
 * A city-specific fact like any other, and therefore never decided in the code
 * (SPEC §15): the same application serves a mechanical fleet and an electric
 * one.
 *
 * Counted rather than declared, and that distinction is the whole of it: a
 * third of the networks declaring a mixed fleet have not one bike of one of the
 * two kinds in circulation.
 *
 * What sits here is the **seed**: `tools/read_fleet.py` counts the bikes when
 * the city is added and writes down what it saw, so that a first launch — with
 * no network yet, or none ever — still draws the right bike. The application
 * counts again from the live feeds and refines it as it goes (SPEC §4.1); the
 * reading in force is the one the fleet repository holds, not this one.
 */
public data class FleetDescription(
    /**
     * Whether the fleet holds pedal-assist bikes.
     *
     * True as soon as one is in circulation, mixed fleets included: what this
     * answers is whether the city lends electric bikes, and the interface marks
     * its bike glyph with a bolt when it does (SPEC §7).
     *
     * False when the configuration says nothing, which is the case of a network
     * whose GBFS feeds let nothing be counted and declare no vehicle type: the
     * plain bike is then drawn rather than a motor nobody verified.
     */
    public val hasElectricBikes: Boolean,
    /**
     * Whether both kinds are lent side by side, in numbers that make an offer.
     *
     * Only then is a station's count worth splitting (SPEC §7.2). Elsewhere the
     * split would be noise — "5 mechanical · 0 electric" suggests a shortage
     * that does not exist — and a bike counted in a kind nobody lends is a
     * promise the user cannot collect.
     */
    public val isMixed: Boolean,
    /**
     * The kind of every vehicle type identifier the status feed counts by.
     *
     * Empty for a network publishing no breakdown, which then shows one figure.
     * An identifier absent from the table is what silences a station's split:
     * five networks publish at their stations a type they never declared.
     */
    public val vehicleTypes: Map<String, VehicleKind>,
)

/** Access to the real-time feed. */
public data class GbfsSettings(
    /**
     * The URL of the auto-discovery document, and of that alone. The individual
     * feed URLs are derived from it, never hard-coded (SPEC §4.1).
     */
    public val discoveryUrl: String,
    public val attribution: String,
    public val attributionUrl: String?,
)

/** How the map is framed on opening, for want of a known position. */
public data class MapDefaults(
    public val centre: Coordinates,
    public val defaultZoom: Double,
    public val minZoom: Int,
    public val maxZoom: Int,
)

/** Where to find the datasets to download. */
public data class DataReleaseSettings(
    public val manifestUrl: String,
    /**
     * The format version the application can read. A manifest announcing
     * anything else must produce an invitation to update, not a failure when
     * opening a file (SPEC §4.4).
     */
    public val formatVersion: Int,
)

/**
 * Reads a city configuration file.
 *
 * The format is ordinary JSON, enriched with `$comment` keys documenting the
 * file for whoever ports it to another city. They are ignored here like any
 * unknown field.
 */
public object CityConfigurationReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the contents of a city configuration file.
     *
     * @param document the raw contents of the `city.json` file.
     * @return the configuration, or the error preventing it from being read.
     */
    public fun read(document: String): Outcome<CityConfiguration> = try {
        val parsed = json.decodeFromString(CityConfigurationDocument.serializer(), document)
        Outcome.Success(parsed.toDomain())
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "unreadable city configuration",
            ),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "inconsistent city configuration",
            ),
        )
    }
}

@Serializable
private data class CityConfigurationDocument(
    val configVersion: Int = 1,
    // Absent from no configuration the tools write, but tolerated all the
    // same: a country is a convenience the routing profiles read, never a
    // reason to refuse a city.
    val country: String? = null,
    val network: NetworkDocument,
    val fleet: FleetDocument = FleetDocument(),
    val gbfs: GbfsDocument,
    val boundingBox: BoundingBoxDocument = BoundingBoxDocument(),
    val map: MapDocument,
    val dataRelease: DataReleaseDocument,
) {
    fun toDomain(): CityConfiguration = CityConfiguration(
        configVersion = configVersion,
        // Uppercased once here so that every reader compares ISO codes and
        // never spellings; Kotlin's uppercase() is locale-invariant, which is
        // what a country code needs.
        country = country?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
        network = NetworkDescription(
            id = network.id,
            displayName = network.displayName,
            city = network.city?.takeIf { it.isNotBlank() },
            operator = network.operator,
            defaultLanguage = network.defaultLanguage,
        ),
        fleet = FleetDescription(
            hasElectricBikes = fleet.electricBikes,
            isMixed = fleet.mixed,
            vehicleTypes = fleet.vehicleTypes.mapValues { (_, kind) ->
                VehicleKind.ofWireName(kind)
            },
        ),
        gbfs = GbfsSettings(
            discoveryUrl = gbfs.discoveryUrl,
            attribution = gbfs.attribution,
            attributionUrl = gbfs.attributionUrl,
        ),
        boundingBox = boundingBox.toDomain(),
        map = MapDefaults(
            centre = Coordinates(map.defaultCenterLatitude, map.defaultCenterLongitude),
            defaultZoom = map.defaultZoom,
            minZoom = map.minZoom,
            maxZoom = map.maxZoom,
        ),
        dataRelease = DataReleaseSettings(
            manifestUrl = dataRelease.manifestUrl,
            formatVersion = dataRelease.formatVersion,
        ),
    )
}

@Serializable
private data class NetworkDocument(
    val id: String,
    val displayName: String,
    val city: String? = null,
    val operator: String,
    val defaultLanguage: String = "fr",
)

@Serializable
private data class FleetDocument(
    // Absent from a configuration written before the fleet was ever read, and
    // from one whose network declares no vehicle type: both mean "not known to
    // be electric", which is drawn as the plain bike.
    val electricBikes: Boolean = false,
    // Absent from a configuration written before the bikes were ever counted.
    // Not known to be mixed is shown as a single figure, which is always true.
    val mixed: Boolean = false,
    val vehicleTypes: Map<String, String> = emptyMap(),
)

@Serializable
private data class GbfsDocument(
    val discoveryUrl: String,
    val attribution: String = "",
    val attributionUrl: String? = null,
)

@Serializable
private data class BoundingBoxDocument(
    val south: Double? = null,
    val west: Double? = null,
    val north: Double? = null,
    val east: Double? = null,
) {
    fun toDomain(): BoundingBox? {
        // All four bounds are null until compute_bbox.py has ever run. A
        // quarter of a rectangle means nothing: all four are required.
        val southValue = south ?: return null
        val westValue = west ?: return null
        val northValue = north ?: return null
        val eastValue = east ?: return null
        return BoundingBox(southValue, westValue, northValue, eastValue)
    }
}

@Serializable
private data class MapDocument(
    val defaultCenterLatitude: Double,
    val defaultCenterLongitude: Double,
    val defaultZoom: Double,
    val minZoom: Int,
    val maxZoom: Int,
)

@Serializable
private data class DataReleaseDocument(val manifestUrl: String, val formatVersion: Int = 1)
