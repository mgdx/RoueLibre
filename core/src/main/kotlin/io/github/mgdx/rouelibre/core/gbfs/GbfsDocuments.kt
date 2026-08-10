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
 * Représentation des documents GBFS tels qu'ils arrivent sur le réseau.
 *
 * Ces types collent au format publié et ne sortent pas de ce paquetage : le
 * reste de l'application manipule le modèle métier de `core.station`.
 *
 * Deux révisions de GBFS coexistent en production et sont toutes deux lues
 * ici. C'est la contrepartie nécessaire de la promesse du SPEC §4.1 — l'URL du
 * flux est réglable, donc « l'appli fonctionne avec n'importe quel réseau GBFS
 * du monde sans modification de code » — et une bonne part des réseaux sont
 * passés en 3.0 pendant que d'autres, dont le réseau lillois, restent en 2.x.
 *
 * Les écarts absorbés :
 *
 * | | GBFS 2.x | GBFS 3.0 |
 * |---|---|---|
 * | liste des flux | `data.<langue>.feeds` | `data.feeds` |
 * | horodatage | entier POSIX | chaîne RFC 3339 |
 * | nom de station | chaîne | tableau `{text, language}` |
 * | vélos disponibles | `num_bikes_available` | `num_vehicles_available` |
 *
 * Les flux d'avant GBFS 2.0 ajoutent deux libertés que le format interdit
 * depuis : un identifiant de station publié en nombre, et des drapeaux publiés
 * en `0` et `1`. Les refuser rendrait Vélib' Métropole — mille cinq cents
 * stations, le plus grand réseau de France — entièrement inexploitable pour
 * des écarts sans conséquence sur le sens.
 */

/**
 * Lit un horodatage GBFS, quel que soit son encodage.
 *
 * GBFS 2.x publie un entier de secondes POSIX, GBFS 3.0 une chaîne RFC 3339.
 * Un producteur peut aussi ne rien publier du tout — le champ est alors absent
 * et la propriété reste nulle.
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
                "horodatage illisible : « ${primitive.content} »",
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.epochSecond)
    }
}

/**
 * Lit un nom de station, qu'il soit une chaîne ou une liste traduite.
 *
 * GBFS 3.0 a rendu les libellés multilingues. Aucune préférence de langue
 * n'est appliquée : la première traduction publiée est retenue, faute d'un
 * critère qui vaudrait mieux et parce qu'un nom de station est un toponyme,
 * rarement traduit en pratique.
 */
internal object FlexibleTextSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: throw GbfsFormatException("libellé traduit vide")
            is JsonObject -> element["text"]?.jsonPrimitive?.content
                ?: throw GbfsFormatException("libellé sans champ « text »")
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * Lit un drapeau que certains producteurs publient en entier plutôt qu'en
 * booléen.
 *
 * Le format impose un booléen, mais des flux réels envoient `0` et `1` ; les
 * refuser rendrait toutes les stations inexploitables pour un écart sans
 * conséquence sur le sens.
 */
internal object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        primitive.booleanOrNull?.let { return it }
        primitive.longOrNull?.let { return it != 0L }
        throw GbfsFormatException("drapeau illisible : « ${primitive.content} »")
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

/**
 * Lit un identifiant de station, qu'il soit publié en chaîne ou en nombre.
 *
 * Le format impose une chaîne, et la plupart des producteurs la respectent.
 * Les flux d'avant GBFS 2.0 publient souvent un entier — c'est le cas de
 * Vélib' Métropole, le plus grand réseau de France avec ses mille cinq cents
 * stations, dont les identifiants ressemblent à `213688169`.
 *
 * La conversion prend le texte brut du nombre, sans passer par un entier : un
 * identifiant est une étiquette, pas une quantité, et rien ne garantit qu'il
 * tienne dans un `Long`. C'est aussi ce qui garantit que les deux flux —
 * `station_information` et `station_status` — produisent la même clé de
 * jointure pour la même station.
 */
internal object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("GbfsIdentifier", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.content.takeIf { it.isNotBlank() }
            ?: throw GbfsFormatException("identifiant de station vide")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/** Enveloppe commune à tous les documents GBFS. */
@Serializable
internal data class GbfsEnvelope<T>(
    @SerialName("last_updated")
    @Serializable(with = FlexibleInstantSerializer::class)
    val lastUpdated: Instant? = null,
    /**
     * Durée de validité annoncée, en secondes.
     *
     * Elle n'est pas suivie aveuglément : le réseau lillois publie `ttl: 0`,
     * ce qui signifierait « ne jamais mettre en cache » et conduirait à
     * interroger le serveur en continu. La politique de rafraîchissement de
     * l'application (SPEC §4.1) prime.
     */
    val ttl: Int? = null,
    val version: String? = null,
    val data: T,
)

/** Un flux annoncé par le document d'auto-découverte. */
@Serializable
internal data class GbfsFeedReference(val name: String, val url: String)

/** Une station telle que publiée par `station_information`. */
@Serializable
internal data class GbfsStationInformation(
    @SerialName("station_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val stationId: String,
    @Serializable(with = FlexibleTextSerializer::class) val name: String,
    val lat: Double,
    val lon: Double,
    val capacity: Int? = null,
    @SerialName("post_code") val postCode: String? = null,
)

/** L'état d'une station tel que publié par `station_status`. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class GbfsStationStatus(
    @SerialName("station_id")
    @Serializable(with = FlexibleIdSerializer::class)
    val stationId: String,
    // GBFS 3.0 a renommé le champ ; les deux noms sont acceptés.
    @SerialName("num_bikes_available")
    @JsonNames("num_vehicles_available")
    val bikesAvailable: Int = 0,
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

/** Contenu de `station_information`. */
@Serializable
internal data class GbfsStationInformationData(
    val stations: List<GbfsStationInformation> = emptyList(),
)

/** Contenu de `station_status`. */
@Serializable
internal data class GbfsStationStatusData(val stations: List<GbfsStationStatus> = emptyList())

/**
 * Signale un document GBFS qui n'a pas la forme attendue.
 *
 * Interne au paquetage : l'analyseur la convertit en `DataError`, seule forme
 * d'échec que le reste de l'application connaît (SPEC §14).
 */
internal class GbfsFormatException(message: String) : IllegalArgumentException(message)
