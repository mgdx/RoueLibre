package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Les villes que l'application sait servir.
 *
 * Un index de quelques kilo-octets, dérivé des configurations de ville par
 * `tools/build_catalogue.py`. Il tient en mémoire, se télécharge en une requête
 * et suffit à répondre aux deux questions du premier lancement : quelles villes
 * existent, et laquelle correspond à l'endroit où l'on se trouve.
 *
 * Le catalogue ne remplace pas la configuration d'une ville : il la référence.
 * Les réglages complets — cadrage, attribution, versions de format — arrivent
 * avec les données téléchargées (SPEC §15).
 */
public data class CityCatalogue(
    public val catalogueVersion: Int,
    /** Date de production, telle que publiée. Sert à dater ce qui est affiché. */
    public val generatedAt: String?,
    /**
     * Adresse à laquelle se retélécharge le catalogue.
     *
     * Portée par le document plutôt qu'écrite dans le code : c'est ce qui
     * permet à un dérivé de publier son propre catalogue en le régénérant, sans
     * toucher au Kotlin (SPEC §15). `null` sur un catalogue produit sans
     * adresse de publication ; il n'y a alors rien à rafraîchir.
     */
    public val catalogueUrl: String?,
    public val cities: List<CityEntry>,
) {

    /** La ville d'identifiant [id], ou `null` si le catalogue l'ignore. */
    public fun entry(id: String): CityEntry? = cities.firstOrNull { it.id == id }

    /**
     * Les villes classées par proximité avec [position].
     *
     * Celles dont l'emprise contient le point viennent d'abord, la plus
     * resserrée en tête : deux réseaux peuvent se recouvrir, et c'est alors
     * celui dont on est le plus près du centre qui est le plus plausible.
     * Viennent ensuite les autres, par distance croissante à leur emprise.
     */
    public fun rank(position: Coordinates): List<CityEntry> = cities
        .sortedWith(
            compareBy(
                { it.boundingBox.distanceOutsideInMetres(position) },
                { it.centre.distanceInMetresTo(position) },
                // À égalité, un ordre stable plutôt que celui du fichier.
                { it.displayName },
            ),
        )

    /**
     * La ville à proposer pour [position], s'il y en a une de plausible.
     *
     * Proposer la ville la plus proche quoi qu'il arrive donnerait Lille à
     * quelqu'un qui se trouve à Marseille : au-delà de [SUGGESTION_RADIUS_METRES]
     * du réseau le plus proche, mieux vaut ne rien proposer et laisser
     * choisir dans la liste.
     */
    public fun suggestionFor(position: Coordinates): CityEntry? =
        rank(position).firstOrNull { entry ->
            entry.boundingBox.distanceOutsideInMetres(position) <= SUGGESTION_RADIUS_METRES
        }

    public companion object {
        /**
         * Distance au-delà de laquelle une ville n'est plus proposée, en mètres.
         *
         * Cinquante kilomètres : de quoi couvrir la couronne périurbaine d'une
         * métropole — on habite Seclin et on prend le V'lille à Lille — sans
         * atteindre l'agglomération suivante, qui aurait alors son propre
         * réseau et sa propre entrée dans le catalogue.
         */
        public const val SUGGESTION_RADIUS_METRES: Double = 50_000.0
    }
}

/**
 * Une ville du catalogue.
 *
 * Ne porte que ce qui permet de la présenter et de la situer. Tout le reste est
 * dans sa configuration, livrée avec ses données.
 */
public data class CityEntry(
    /** Identifiant du réseau, qui nomme aussi son répertoire de données. */
    public val id: String,
    public val displayName: String,
    public val operator: String,
    /** Code pays ISO 3166-1 alpha-2, pour regrouper la liste. */
    public val country: String,
    public val boundingBox: BoundingBox,
    public val centre: Coordinates,
    public val stationCount: Int?,
    public val gbfsDiscoveryUrl: String,
    public val manifestUrl: String,
    /**
     * Poids total des données hors ligne, en octets, ou `null` si elles n'ont
     * pas encore été produites.
     *
     * Le SPEC §11.9 exige que la taille soit annoncée avant le téléchargement.
     * Une ville sans taille connue est une ville qu'on ne peut pas installer :
     * elle reste listée, mais l'interface doit le dire.
     */
    public val dataSizeBytes: Long?,
    public val releaseTag: String?,
) {
    /** Vrai si les données de cette ville sont publiées et téléchargeables. */
    public val isAvailable: Boolean
        get() = dataSizeBytes != null && dataSizeBytes > 0
}

/** Lit un catalogue de villes. */
public object CityCatalogueReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Analyse le contenu d'un catalogue.
     *
     * Une entrée dont l'emprise est absurde est écartée sans faire échouer le
     * reste : le catalogue est téléchargé, donc produit ailleurs et plus tard
     * que l'application qui le lit. Un catalogue entièrement vide, en revanche,
     * est un échec — il n'y aurait rien à choisir.
     *
     * @param document contenu brut du fichier `catalogue.json`.
     */
    public fun read(document: String): Outcome<CityCatalogue> = try {
        val parsed = json.decodeFromString(CityCatalogueDocument.serializer(), document)
        val cities = parsed.cities.mapNotNull { it.toDomainOrNull() }
        if (cities.isEmpty()) {
            Outcome.Failure(DataError.MalformedResponse("catalogue sans aucune ville lisible"))
        } else {
            Outcome.Success(
                CityCatalogue(
                    catalogueVersion = parsed.catalogueVersion,
                    generatedAt = parsed.generatedAt,
                    catalogueUrl = parsed.catalogueUrl?.takeIf { it.isNotBlank() },
                    cities = cities,
                ),
            )
        }
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "catalogue de villes illisible"),
        )
    }
}

@Serializable
private data class CityCatalogueDocument(
    val catalogueVersion: Int = 1,
    val generatedAt: String? = null,
    val catalogueUrl: String? = null,
    val cities: List<CityEntryDocument> = emptyList(),
)

@Serializable
private data class CityEntryDocument(
    val id: String,
    val displayName: String,
    val operator: String = "",
    val country: String = "FR",
    val boundingBox: CatalogueBoundingBoxDocument? = null,
    val centreLatitude: Double? = null,
    val centreLongitude: Double? = null,
    val stationCount: Int? = null,
    val gbfsDiscoveryUrl: String,
    val manifestUrl: String,
    val dataSizeBytes: Long? = null,
    val releaseTag: String? = null,
) {
    fun toDomainOrNull(): CityEntry? {
        val box = boundingBox?.toDomainOrNull() ?: return null
        // Le centrage par défaut peut manquer : celui de l'emprise le vaut.
        val latitude = centreLatitude
        val longitude = centreLongitude
        val centre = if (latitude != null && longitude != null) {
            runCatching { Coordinates(latitude, longitude) }.getOrNull()
        } else {
            null
        }
        return CityEntry(
            id = id.takeIf { it.isNotBlank() } ?: return null,
            displayName = displayName.takeIf { it.isNotBlank() } ?: return null,
            operator = operator,
            country = country,
            boundingBox = box,
            centre = centre ?: box.centre,
            stationCount = stationCount,
            gbfsDiscoveryUrl = gbfsDiscoveryUrl,
            manifestUrl = manifestUrl,
            dataSizeBytes = dataSizeBytes,
            releaseTag = releaseTag,
        )
    }
}

@Serializable
private data class CatalogueBoundingBoxDocument(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    // `BoundingBox` refuse un rectangle inversé ; ici cela écarte l'entrée au
    // lieu de faire tomber la lecture de tout le catalogue.
    fun toDomainOrNull(): BoundingBox? = runCatching { BoundingBox(south, west, north, east) }
        .getOrNull()
        ?.takeIf { it.isUsable }
}
