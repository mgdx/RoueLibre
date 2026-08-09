package io.github.mgdx.rouelibre.core.data

import java.time.Instant

/**
 * Les trois jeux de données hors ligne (SPEC.md §4.4).
 *
 * Ils sont publiés ensemble, versionnés ensemble, mais installés séparément :
 * rafraîchir l'index d'adresses ne doit jamais imposer de reprendre les
 * dizaines de mégaoctets du fond de carte.
 *
 * @property id identifiant tel qu'il apparaît dans le manifeste.
 * @property fileName nom sous lequel le fichier est rangé sur l'appareil,
 *   indépendant de celui qu'il portait à la source.
 */
public enum class DatasetKind(public val id: String, public val fileName: String) {
    /** Fond de carte vectoriel, au format MBTiles. */
    Tiles("tiles", "tiles.mbtiles"),

    /** Graphe de routage, au format BRouter rd5. */
    Routing("routing", "routing.rd5"),

    /** Index d'adresses, base SQLite. */
    Addresses("addresses", "addresses.sqlite"),

    ;

    public companion object {
        /** Retrouve un jeu par son identifiant de manifeste, ou `null`. */
        public fun fromId(id: String): DatasetKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Un jeu de données présent sur l'appareil.
 *
 * @property kind lequel des trois.
 * @property sizeBytes taille du fichier installé.
 * @property sha256 empreinte du fichier installé. Elle sert à décider, face à
 *   un manifeste, s'il y a lieu de retélécharger (SPEC §4.4).
 * @property installedAt date d'installation, affichée dans l'écran stockage.
 * @property formatVersion version de format lue dans le fichier lui-même,
 *   quand il la porte.
 */
public data class InstalledDataset(
    public val kind: DatasetKind,
    public val sizeBytes: Long,
    public val sha256: String,
    public val installedAt: Instant,
    public val formatVersion: Int?,
)

/**
 * Pourquoi un fichier proposé à l'installation a été refusé.
 *
 * Aucun de ces cas n'est une panne : ce sont des situations que l'utilisateur
 * peut corriger, à condition qu'on lui dise laquelle (SPEC §14).
 */
public sealed interface DatasetRejection {

    /** Le fichier n'a pas la forme attendue pour ce jeu de données. */
    public data class WrongFormat(public val detail: String) : DatasetRejection

    /**
     * Le fichier est d'une version de format que cette version de
     * l'application ne sait pas lire. Il faut le dire et inviter à mettre à
     * jour l'application, pas échouer à l'ouverture (SPEC §4.4).
     *
     * @property found version trouvée dans le fichier.
     * @property supported version que l'application sait lire.
     */
    public data class UnsupportedFormatVersion(public val found: Int, public val supported: Int) :
        DatasetRejection

    /** L'empreinte ne correspond pas à celle annoncée par le manifeste. */
    public data class ChecksumMismatch(public val expected: String, public val actual: String) :
        DatasetRejection

    /** Le fichier n'a pas pu être lu ou écrit. */
    public data class TransferFailed(public val detail: String) : DatasetRejection

    /** Le fichier est vide. */
    public data object Empty : DatasetRejection
}

/**
 * Issue d'une tentative d'installation.
 *
 * Un type à part plutôt que le `DataError` des échecs réseau : les causes
 * n'ont rien de commun, et les fondre ensemble obligerait chaque écran à
 * traiter des cas qui ne le concernent pas.
 */
public sealed interface DatasetImportResult {

    /** Le fichier a été validé et mis en place. */
    public data class Installed(public val dataset: InstalledDataset) : DatasetImportResult

    /** Le fichier a été refusé ; l'installation précédente est intacte. */
    public data class Rejected(public val reason: DatasetRejection) : DatasetImportResult
}
