package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Tout ce qui est propre à une agglomération, et rien d'autre.
 *
 * Aucune de ces valeurs n'est écrite dans le code : ni URL, ni emprise, ni
 * coordonnée de centrage, ni nom de réseau. Servir une autre ville se fait en
 * remplaçant ce fichier, sans recompiler autre chose (SPEC §15).
 */
public data class CityConfiguration(
    public val configVersion: Int,
    public val network: NetworkDescription,
    public val gbfs: GbfsSettings,
    /**
     * Emprise de référence partagée par les trois jeux de données hors ligne.
     *
     * Nulle tant que les données n'ont jamais été générées. L'application doit
     * alors se limiter à la liste des stations et le dire, plutôt que de
     * laisser croire que la carte va s'afficher (SPEC §4.4).
     */
    public val boundingBox: BoundingBox?,
    public val map: MapDefaults,
    public val dataRelease: DataReleaseSettings,
)

/** Identité du réseau servi. */
public data class NetworkDescription(
    public val id: String,
    public val displayName: String,
    public val operator: String,
    public val defaultLanguage: String,
)

/** Accès au flux temps réel. */
public data class GbfsSettings(
    /**
     * URL du document d'auto-découverte, et lui seul. Les URL des flux
     * individuels en sont déduites, jamais écrites en dur (SPEC §4.1).
     */
    public val discoveryUrl: String,
    public val attribution: String,
    public val attributionUrl: String?,
)

/** Cadrage de la carte à l'ouverture, faute de position connue. */
public data class MapDefaults(
    public val centre: Coordinates,
    public val defaultZoom: Double,
    public val minZoom: Int,
    public val maxZoom: Int,
)

/** Où trouver les jeux de données à télécharger. */
public data class DataReleaseSettings(
    public val manifestUrl: String,
    /**
     * Version de format que l'application sait lire. Un manifeste annonçant
     * autre chose doit produire une invitation à mettre à jour, pas un échec
     * à l'ouverture d'un fichier (SPEC §4.4).
     */
    public val formatVersion: Int,
)

/**
 * Lit un fichier de configuration de ville.
 *
 * Le format est du JSON ordinaire, enrichi de clés `$comment` qui documentent
 * le fichier pour la personne qui le portera vers une autre ville. Elles sont
 * ignorées ici comme tout champ inconnu.
 */
public object CityConfigurationReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Analyse le contenu d'un fichier de configuration de ville.
     *
     * @param document contenu brut du fichier `city.json`.
     * @return la configuration, ou l'erreur qui empêche de la lire.
     */
    public fun read(document: String): Outcome<CityConfiguration> = try {
        val parsed = json.decodeFromString(CityConfigurationDocument.serializer(), document)
        Outcome.Success(parsed.toDomain())
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "configuration de ville illisible",
            ),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "configuration de ville incohérente",
            ),
        )
    }
}

@Serializable
private data class CityConfigurationDocument(
    val configVersion: Int = 1,
    val network: NetworkDocument,
    val gbfs: GbfsDocument,
    val boundingBox: BoundingBoxDocument = BoundingBoxDocument(),
    val map: MapDocument,
    val dataRelease: DataReleaseDocument,
) {
    fun toDomain(): CityConfiguration = CityConfiguration(
        configVersion = configVersion,
        network = NetworkDescription(
            id = network.id,
            displayName = network.displayName,
            operator = network.operator,
            defaultLanguage = network.defaultLanguage,
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
    val operator: String,
    val defaultLanguage: String = "fr",
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
        // Les quatre bornes sont nulles tant que compute_bbox.py n'a jamais
        // tourné. Un quart de rectangle n'a pas de sens : on exige les quatre.
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
