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
 * Analyse les trois documents GBFS dont l'application a besoin.
 *
 * Rien ici ne touche au réseau : l'analyseur reçoit du texte et rend des
 * objets métier, ce qui le rend intégralement testable sur la JVM à partir de
 * captures réelles des flux (SPEC §14).
 */
public class GbfsParser {

    private val json = Json {
        // Les producteurs enrichissent régulièrement leurs flux ; un champ
        // inconnu ne doit jamais faire échouer la lecture.
        ignoreUnknownKeys = true
        // Certains flux omettent des champs pourtant obligatoires. Les valeurs
        // par défaut déclarées sur les modèles prennent alors le relais.
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Lit le document d'auto-découverte et renvoie les flux qu'il publie.
     *
     * Passer par ce document plutôt que de deviner les URL est le principe
     * même de GBFS, et met l'application à l'abri d'un déplacement de flux
     * côté producteur (SPEC §4.1).
     *
     * @param document contenu brut de `gbfs.json`.
     * @return les URL par nom de flux, ou l'erreur rencontrée.
     */
    public fun parseDiscovery(document: String): Outcome<GbfsDiscovery> = parsing {
        val root = json.parseToJsonElement(document).jsonObject
        val version = (root["version"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val data = root["data"]?.jsonObject
            ?: throw GbfsFormatException("le document n'a pas de champ « data »")

        // GBFS 3.0 place la liste des flux directement sous « data ». Les
        // versions antérieures l'imbriquent dans une clé de langue, dont le
        // nom n'est pas normalisé : le flux lillois publie « en » bien qu'il
        // serve un réseau français. La première langue présente est donc
        // retenue, plutôt qu'une langue supposée.
        val feedsElement = data["feeds"]
            ?: data.values.firstOrNull()?.jsonObject?.get("feeds")
            ?: throw GbfsFormatException("aucune liste de flux dans « data »")

        val feeds = feedsElement.jsonArray.associate { element ->
            val feed = json.decodeFromJsonElement(
                GbfsFeedReference.serializer(),
                element,
            )
            feed.name to feed.url
        }
        if (feeds.isEmpty()) {
            throw GbfsFormatException("la liste des flux est vide")
        }
        GbfsDiscovery(version = version, feedUrlsByName = feeds)
    }

    /**
     * Lit `station_information` et renvoie les stations du réseau.
     *
     * Une station dont les coordonnées sont absurdes est écartée plutôt que de
     * faire échouer tout le flux : une seule entrée fautive chez le producteur
     * ne doit pas priver l'utilisateur des 267 autres.
     *
     * @param document contenu brut de `station_information.json`.
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
     * Lit `station_status` et renvoie l'état courant des stations.
     *
     * @param document contenu brut de `station_status.json`.
     */
    public fun parseStationStatus(document: String): Outcome<StationStatusFeed> = parsing {
        val envelope = json.decodeFromString(
            GbfsEnvelope.serializer(GbfsStationStatusData.serializer()),
            document,
        )
        val availabilities = envelope.data.stations.map { entry ->
            StationAvailability(
                stationId = entry.stationId,
                // Un compte négatif n'a pas de sens ; on le ramène à zéro
                // plutôt que d'afficher « -1 vélo ».
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
     * Exécute [block] en convertissant tout échec d'analyse en [DataError].
     *
     * Les bibliothèques de sérialisation signalent leurs problèmes par des
     * exceptions ; le reste de l'application, lui, ne connaît que des valeurs
     * de résultat (SPEC §14). La conversion a lieu ici, à la frontière.
     */
    private inline fun <T> parsing(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (error: GbfsFormatException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "format inattendu"))
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "JSON illisible"),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "valeur hors bornes"),
        )
    }

    /**
     * Construit un point, ou `null` si le couple est inexploitable.
     *
     * Le point (0, 0) est traité comme absent : il tombe dans le golfe de
     * Guinée et signale, en pratique, une coordonnée non renseignée.
     */
    private fun coordinatesOrNull(latitude: Double, longitude: Double): Coordinates? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Coordinates(latitude, longitude)
    }
}

/**
 * Ce que publie le document d'auto-découverte.
 *
 * @property version révision de GBFS annoncée, si le producteur la publie.
 * @property feedUrlsByName URL de chaque flux, indexées par leur nom GBFS.
 */
public data class GbfsDiscovery(
    public val version: String?,
    public val feedUrlsByName: Map<String, String>,
) {
    /**
     * URL du flux nommé [feedName].
     *
     * @return l'URL, ou un échec décrivant le flux manquant — ce qui permet
     *   d'expliquer précisément ce que le producteur ne publie pas.
     */
    public fun urlOf(feedName: String): Outcome<String> = feedUrlsByName[feedName]
        ?.let { Outcome.Success(it) }
        ?: Outcome.Failure(DataError.FeedUnavailable(feedName))
}

/** Contenu utile de `station_information`. */
public data class StationInformationFeed(
    public val stations: List<Station>,
    public val lastUpdated: Instant?,
    public val version: String?,
)

/** Contenu utile de `station_status`. */
public data class StationStatusFeed(
    public val availabilities: List<StationAvailability>,
    public val lastUpdated: Instant?,
    public val version: String?,
)

/** Noms normalisés des flux GBFS utilisés par l'application. */
public object GbfsFeedNames {
    /** Données stables des stations. */
    public const val STATION_INFORMATION: String = "station_information"

    /** État temps réel des stations. */
    public const val STATION_STATUS: String = "station_status"
}
