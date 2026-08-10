package io.github.mgdx.rouelibre.core.data

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Ce qu'une publication de données annonce (SPEC §4.4).
 *
 * Le manifeste pèse quelques kilooctets et décrit les trois jeux : leur
 * version, leur adresse, leur taille et leur empreinte. C'est lui qui permet
 * de ne retélécharger **que ce qui a changé** — rafraîchir l'index d'adresses
 * ne doit jamais imposer de reprendre les trente-cinq mégaoctets de tuiles.
 *
 * @property formatVersion version de format des fichiers décrits. Une version
 *   que l'application ne sait pas lire doit produire une invitation à mettre à
 *   jour, pas un échec à l'ouverture d'un fichier.
 * @property releaseTag étiquette de la publication, par exemple `data-2026-08`.
 * @property generatedAt date de génération, telle qu'écrite par le script.
 * @property network identifiant du réseau servi, qui doit correspondre à celui
 *   de la configuration de ville.
 * @property boundingBox emprise couverte par ces données.
 * @property datasets les jeux publiés.
 */
public data class DataManifest(
    public val formatVersion: Int,
    public val releaseTag: String,
    public val generatedAt: String,
    public val network: String,
    public val boundingBox: BoundingBox?,
    public val datasets: List<ManifestDataset>,
) {
    /** Taille totale annoncée, tous jeux confondus. */
    public val totalSizeBytes: Long
        get() = datasets.sumOf { it.sizeBytes }

    /** Le jeu décrit pour cette catégorie, s'il figure au manifeste. */
    public fun datasetFor(kind: DatasetKind): ManifestDataset? =
        datasets.firstOrNull { it.kind == kind }
}

/**
 * Un jeu de données publié.
 *
 * @property kind lequel des trois.
 * @property description ce que le producteur en dit, en une ligne.
 * @property files les fichiers qui le composent. Le graphe de routage peut en
 *   compter plusieurs, les deux autres n'en ont qu'un.
 */
public data class ManifestDataset(
    public val kind: DatasetKind,
    public val description: String,
    public val files: List<ManifestFile>,
) {
    /** Taille annoncée de ce jeu. */
    public val sizeBytes: Long
        get() = files.sumOf { it.sizeBytes }

    /**
     * Empreinte de l'ensemble du jeu.
     *
     * Les empreintes des fichiers, dans l'ordre de leurs noms : c'est ce que
     * l'application conserve après installation, et ce qu'elle compare au
     * manifeste suivant pour décider s'il y a lieu de retélécharger. Un jeu
     * d'un seul fichier a donc pour empreinte celle de ce fichier, ce qui rend
     * la comparaison juste même après un import manuel.
     */
    public val fingerprint: String
        get() = files.sortedBy { it.name }.joinToString(separator = ",") { it.sha256 }
}

/**
 * Un fichier à télécharger.
 *
 * @property name nom sous lequel il est publié, et sous lequel il sera rangé.
 * @property url où le prendre.
 * @property sizeBytes taille annoncée, affichée avant de demander confirmation.
 * @property sha256 empreinte, revérifiée après téléchargement (SPEC §4.4).
 */
public data class ManifestFile(
    public val name: String,
    public val url: String,
    public val sizeBytes: Long,
    public val sha256: String,
)

/**
 * Ce qu'il y a lieu de faire d'un jeu, face à un manifeste.
 */
public enum class DatasetUpdate {
    /** Absent de l'appareil : il faut le télécharger pour s'en servir. */
    Missing,

    /** Installé, mais le manifeste en annonce une autre version. */
    Outdated,

    /** Installé et à jour : ne rien retélécharger. */
    UpToDate,
}

/**
 * Compare ce qui est installé à ce qui est publié (SPEC §4.4).
 *
 * La comparaison porte sur les empreintes, jamais sur les dates : une date de
 * publication plus récente ne dit pas que le contenu a changé, et un fichier
 * régénéré à l'identique ne doit pas être retéléchargé.
 *
 * @param manifest la publication annoncée.
 * @param installedFingerprints l'empreinte de chaque jeu présent sur
 *   l'appareil.
 * @return l'état de chacun des jeux du manifeste.
 */
public fun compareWithInstalled(
    manifest: DataManifest,
    installedFingerprints: Map<DatasetKind, String>,
): Map<DatasetKind, DatasetUpdate> = manifest.datasets.associate { dataset ->
    val installed = installedFingerprints[dataset.kind]
    dataset.kind to when {
        installed == null -> DatasetUpdate.Missing
        installed.equals(dataset.fingerprint, ignoreCase = true) -> DatasetUpdate.UpToDate
        else -> DatasetUpdate.Outdated
    }
}

/** Lit un manifeste de publication de données. */
public object DataManifestReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Analyse le contenu d'un manifeste.
     *
     * @param document contenu brut du fichier `manifest.json`.
     * @return le manifeste, ou l'erreur qui empêche de le lire. Un jeu dont
     *   l'identifiant est inconnu est ignoré plutôt que fatal : une
     *   publication plus récente peut en décrire d'autres, et cela ne doit pas
     *   empêcher de mettre à jour ceux que l'on connaît.
     */
    public fun read(document: String): Outcome<DataManifest> = try {
        val parsed = json.decodeFromString(ManifestDocument.serializer(), document)
        Outcome.Success(parsed.toDomain())
    } catch (error: SerializationException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "manifeste illisible"))
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(DataError.MalformedResponse(error.message ?: "manifeste incohérent"))
    }
}

@Serializable
private data class ManifestDocument(
    val formatVersion: Int = 1,
    val releaseTag: String = "",
    val generatedAt: String = "",
    val network: String = "",
    val boundingBox: BoundingBoxDocument = BoundingBoxDocument(),
    val datasets: List<DatasetDocument> = emptyList(),
) {
    fun toDomain(): DataManifest = DataManifest(
        formatVersion = formatVersion,
        releaseTag = releaseTag,
        generatedAt = generatedAt,
        network = network,
        boundingBox = boundingBox.toDomain(),
        datasets = datasets.mapNotNull { it.toDomain() },
    )
}

@Serializable
private data class DatasetDocument(
    val id: String,
    val description: String = "",
    val files: List<FileDocument> = emptyList(),
) {
    fun toDomain(): ManifestDataset? {
        val kind = DatasetKind.fromId(id) ?: return null
        if (files.isEmpty()) return null
        return ManifestDataset(
            kind = kind,
            description = description,
            files = files.map { it.toDomain() },
        )
    }
}

@Serializable
private data class FileDocument(
    val name: String,
    val url: String,
    val sizeBytes: Long = 0,
    val sha256: String = "",
) {
    fun toDomain(): ManifestFile = ManifestFile(name, url, sizeBytes, sha256)
}

@Serializable
private data class BoundingBoxDocument(
    val south: Double? = null,
    val west: Double? = null,
    val north: Double? = null,
    val east: Double? = null,
) {
    fun toDomain(): BoundingBox? {
        val southValue = south ?: return null
        val westValue = west ?: return null
        val northValue = north ?: return null
        val eastValue = east ?: return null
        return BoundingBox(southValue, westValue, northValue, eastValue)
    }
}
