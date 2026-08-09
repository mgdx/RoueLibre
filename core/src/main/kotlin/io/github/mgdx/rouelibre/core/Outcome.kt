package io.github.mgdx.rouelibre.core

/**
 * Résultat d'une opération pouvant échouer.
 *
 * Le SPEC §14 impose des types de résultat plutôt que des exceptions
 * silencieuses. Les échecs remontent donc comme des valeurs, que l'appelant ne
 * peut pas ignorer par inadvertance.
 *
 * Aucune variante ne porte de texte destiné à l'utilisateur : le module métier
 * n'a pas le droit de contenir de chaîne de caractères affichable (SPEC §9).
 * C'est la couche Android qui traduit chaque erreur en message français.
 */
public sealed interface Outcome<out T> {

    /** L'opération a abouti et porte sa valeur. */
    public data class Success<out T>(public val value: T) : Outcome<T>

    /** L'opération a échoué pour la raison décrite par [error]. */
    public data class Failure(public val error: DataError) : Outcome<Nothing>

    public companion object {
        /** Raccourci de construction, pour alléger les appels. */
        public fun <T> success(value: T): Outcome<T> = Success(value)

        /** Raccourci de construction, pour alléger les appels. */
        public fun failure(error: DataError): Outcome<Nothing> = Failure(error)
    }
}

/**
 * Applique [transform] à la valeur portée, en propageant l'échec inchangé.
 */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/**
 * Enchaîne une opération elle-même faillible, en propageant l'échec inchangé.
 */
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }

/** La valeur portée, ou `null` en cas d'échec. */
public fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

/**
 * Cause d'un échec de récupération ou de lecture de données.
 *
 * Chaque variante correspond à une conduite à tenir différente pour
 * l'utilisateur, et donc à un message distinct — c'est le critère qui a guidé
 * ce découpage, pas la nature technique de la panne.
 */
public sealed interface DataError {

    /** L'appareil n'a pas de connexion. Le dernier état connu reste affichable. */
    public data object Offline : DataError

    /** La requête a abouti mais le serveur a répondu par une erreur. */
    public data class ServerRefused(public val statusCode: Int) : DataError

    /** La requête n'a pas abouti dans le délai imparti. */
    public data object Timeout : DataError

    /**
     * La réponse n'a pas la forme attendue.
     *
     * @property detail description technique, destinée au journal et au
     *   rapport de bogue, jamais à l'écran.
     */
    public data class MalformedResponse(public val detail: String) : DataError

    /**
     * Le flux d'auto-découverte ne publie pas le flux demandé.
     *
     * @property feedName nom du flux GBFS manquant.
     */
    public data class FeedUnavailable(public val feedName: String) : DataError

    /**
     * Le producteur annonce une version de GBFS que l'application ne sait pas
     * lire. Il faut le dire et inviter à mettre à jour, pas échouer en silence.
     */
    public data class UnsupportedFeedVersion(public val version: String) : DataError

    /** Les données locales sont absentes ou illisibles. */
    public data class LocalStorageFailure(public val detail: String) : DataError
}
