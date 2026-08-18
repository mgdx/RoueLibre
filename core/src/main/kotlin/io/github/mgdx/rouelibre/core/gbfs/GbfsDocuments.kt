package io.github.mgdx.rouelibre.core.gbfs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/*
 * GBFS documents as they arrive over the network.
 *
 * These types stick to the published format and never leave this package: the
 * rest of the application works with the domain model of `core.station`.
 *
 * Two revisions of GBFS coexist in production and both are read here. It is the
 * necessary price of the promise of SPEC §4.1 — the feed URL is configurable,
 * therefore "the application works with any GBFS network in the world without a
 * code change" — and a good share of networks have moved to 3.0 while others,
 * the Lille one among them, remain on 2.x.
 *
 * The differences absorbed:
 *
 * | | GBFS 2.x | GBFS 3.0 |
 * |---|---|---|
 * | feed list | `data.<language>.feeds` | `data.feeds` |
 * | timestamp | POSIX integer | RFC 3339 string |
 * | station name | string | `{text, language}` array |
 * | bikes available | `num_bikes_available` | `num_vehicles_available` |
 *
 * Feeds predating GBFS 2.0 add two liberties the format has since forbidden: a
 * station identifier published as a number, and flags published as `0` and `1`.
 * Refusing them would make Vélib' Métropole — fifteen hundred stations, the
 * largest network in France — entirely unusable over differences that carry no
 * consequence for meaning.
 */

/**
 * Reads a GBFS timestamp, whatever its encoding.
 *
 * GBFS 2.x publishes an integer of POSIX seconds, GBFS 3.0 an RFC 3339 string.
 * A producer may also publish nothing at all — the field is then absent and the
 * property stays null.
 */
internal object FlexibleInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsTimestamp", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return Instant.ofEpochSecond(decoder.decodeLong())
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        primitive.longOrNull?.let { return Instant.ofEpochSecond(it) }
        return try {
            Instant.parse(primitive.content)
        } catch (_: DateTimeParseException) {
            throw GbfsFormatException(
                "unreadable timestamp: \"${primitive.content}\"",
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.epochSecond)
    }
}

/**
 * Reads a station name, whether it is a string or a translated list.
 *
 * GBFS 3.0 made labels multilingual. No language preference is applied: the
 * first translation published is kept, for want of a better criterion and
 * because a station name is a place name, rarely translated in practice.
 *
 * **The blanks around the label are dropped as it is read.** Networks publish
 * them — V'lille's "4 vents " carries a trailing space — and one travelled all
 * the way to the title of the station's sheet and to its spoken label, where
 * "4 vents , 8 bikes" detached the comma from the word. It is done here and not
 * where a name is written out: nothing downstream should have to remember that
 * a name arrives untidy, and everything that reads one — a title, a list row, a
 * `geo:` label, a screen reader — would otherwise have to. The network's data
 * is not being rewritten; a blank is simply not shown.
 */
internal object FlexibleTextSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString().trim()
        val label = when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: throw GbfsFormatException("empty translated label")
            is JsonObject -> element["text"]?.jsonPrimitive?.content
                ?: throw GbfsFormatException("label without a \"text\" field")
        }
        return label.trim()
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * Reads a flag that some producers publish as an integer rather than a boolean.
 *
 * The format mandates a boolean, but real feeds send `0` and `1`; refusing them
 * would make every station unusable over a difference that carries no
 * consequence for meaning.
 */
internal object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        primitive.booleanOrNull?.let { return it }
        primitive.longOrNull?.let { return it != 0L }
        throw GbfsFormatException("unreadable flag: \"${primitive.content}\"")
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

/**
 * Reads a station identifier, whether published as a string or as a number.
 *
 * The format mandates a string, and most producers respect it. Feeds predating
 * GBFS 2.0 often publish an integer — that is the case of Vélib' Métropole, the
 * largest network in France with its fifteen hundred stations, whose
 * identifiers look like `213688169`.
 *
 * The conversion takes the number's raw text, without going through an integer:
 * an identifier is a label, not a quantity, and nothing guarantees it fits in a
 * `Long`. It is also what guarantees that both feeds — `station_information`
 * and `station_status` — produce the same join key for the same station.
 */
internal object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsIdentifier", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.content.takeIf { it.isNotBlank() }
            ?: throw GbfsFormatException("empty station identifier")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/** The envelope common to every GBFS document. */
@Serializable
internal data class GbfsEnvelope<T>(
    @SerialName("last_updated")
    @Serializable(with = FlexibleInstantSerializer::class)
    val lastUpdated: Instant? = null,
    /**
     * The announced validity period, in seconds.
     *
     * It is not followed blindly: the Lille network publishes `ttl: 0`, which
     * would mean "never cache" and would lead to polling the server
     * continuously. The application's refresh policy (SPEC §4.1) prevails.
     */
    val ttl: Int? = null,
    val version: String? = null,
    val data: T,
)

/** A feed announced by the auto-discovery document. */
@Serializable
internal data class GbfsFeedReference(val name: String, val url: String)

/** A station as published by `station_information`. */
@Serializable
internal data class GbfsStationInformation(
    @SerialName("station_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val stationId: String,
    @Serializable(with = FlexibleTextSerializer::class) val name: String,
    val lat: Double,
    val lon: Double,
    val capacity: Int? = null,
    /**
     * The docking points broken down by the vehicle types they take.
     *
     * Published since GBFS 2.3, and the standard states that its counts must
     * total `capacity`. Where the two disagree the document contradicts itself,
     * and [GbfsParser.parseStationInformation] says which one it believes.
     */
    @SerialName("vehicle_docks_capacity")
    val vehicleDocksCapacity: List<GbfsDockCount> = emptyList(),
    /**
     * The street the station stands in, where the producer publishes one.
     *
     * Optional in the format and absent from most feeds — the application reads
     * its own offline index for that (SPEC §4.3). It is kept for one purpose:
     * to name a station whose `name` arrived empty, which
     * [GbfsParser.parseStationInformation] does.
     */
    val address: String? = null,
    @SerialName("post_code") val postCode: String? = null,
)

/**
 * How many docks a station has for one group of vehicle types.
 *
 * The groups are disjoint — a dock belongs to exactly one entry, which lists
 * every type it takes — so the counts add up to the station's docking points.
 */
@Serializable
internal data class GbfsDockCount(val count: Int = 0)

/**
 * How many bikes of one vehicle type stand at a station.
 *
 * The standard breakdown, published since GBFS 2.1. The identifier is the
 * producer's own — `346` at nextbike, `mechanical` at Lyon — and says nothing
 * by itself: it takes the network's table to know what it stands for.
 */
@Serializable
internal data class GbfsVehicleTypeCount(
    @SerialName("vehicle_type_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val vehicleTypeId: String,
    val count: Int = 0,
)

/** The state of a station as published by `station_status`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class GbfsStationStatus(
    @SerialName("station_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val stationId: String,
    // GBFS 3.0 renamed the field; both names are accepted.
    @SerialName("num_bikes_available")
    @JsonNames("num_vehicles_available")
    val bikesAvailable: Int = 0,
    @SerialName("vehicle_types_available")
    val vehicleTypesAvailable: List<GbfsVehicleTypeCount> = emptyList(),
    /**
     * The breakdown as Vélib' Métropole publishes it, kept raw.
     *
     * An extension, not the standard: the network is on GBFS 1.0, which has no
     * `vehicle_types` feed to point identifiers at, so it names the kinds
     * inline — `[{"mechanical": 3}, {"ebike": 0}]`. A list of objects with
     * arbitrary keys has no shape to declare, hence the raw element, read by
     * the parser. Refusing it would hide the 7854 electric bikes of the
     * largest network in France.
     */
    @SerialName("num_bikes_available_types")
    val legacyBikesByKind: JsonArray? = null,
    @SerialName("num_docks_available") val docksAvailable: Int = 0,
    @SerialName("is_installed")
    @Serializable(with = LenientBooleanSerializer::class)
    val isInstalled: Boolean = true,
    @SerialName("is_renting")
    @Serializable(with = LenientBooleanSerializer::class)
    val isRenting: Boolean = true,
    @SerialName("is_returning")
    @Serializable(with = LenientBooleanSerializer::class)
    val isReturning: Boolean = true,
    @SerialName("last_reported")
    @Serializable(with = FlexibleInstantSerializer::class)
    val lastReported: Instant? = null,
)

/**
 * A vehicle type as declared by `vehicle_types`.
 *
 * The feed that gives the identifiers of `vehicle_types_available` a meaning:
 * what the vehicle is, and what moves it. Both fields are optional here because
 * a producer may omit them, and an entry saying nothing is an entry whose bikes
 * cannot be sorted — which the reader must be able to notice rather than guess.
 */
@Serializable
internal data class GbfsVehicleType(
    @SerialName("vehicle_type_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val vehicleTypeId: String,
    @SerialName("form_factor") val formFactor: String? = null,
    @SerialName("propulsion_type") val propulsionType: String? = null,
)

/** The contents of `vehicle_types`. */
@Serializable
internal data class GbfsVehicleTypesData(
    @SerialName("vehicle_types")
    val vehicleTypes: List<GbfsVehicleType> = emptyList(),
)

/** The contents of `station_information`. */
@Serializable
internal data class GbfsStationInformationData(
    val stations: List<GbfsStationInformation> = emptyList(),
)

/** The contents of `station_status`. */
@Serializable
internal data class GbfsStationStatusData(val stations: List<GbfsStationStatus> = emptyList())

/**
 * Signals a GBFS document that does not have the expected shape.
 *
 * Internal to the package: the parser turns it into a `DataError`, the only
 * form of failure the rest of the application knows (SPEC §14).
 */
internal class GbfsFormatException(message: String) : IllegalArgumentException(message)
