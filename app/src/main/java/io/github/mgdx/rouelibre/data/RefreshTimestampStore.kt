package io.github.mgdx.rouelibre.data

import java.time.Instant

/**
 * Mémorise quand les données stables des stations ont été récupérées.
 *
 * Cette date doit survivre au redémarrage de l'application, sinon la règle
 * « au plus une fois par jour » du SPEC §4.1 ne tiendrait pas : chaque
 * lancement retéléchargerait la liste complète des stations.
 *
 * L'interface existe pour que la politique de rafraîchissement soit testable
 * sur la JVM sans DataStore ni appareil. `AppPreferences` en est la seule mise
 * en œuvre livrée.
 */
interface RefreshTimestampStore {

    /** Date du dernier rafraîchissement réussi, ou `null` s'il n'y en a jamais eu. */
    suspend fun stationInformationFetchedAt(): Instant?

    /** Enregistre la date d'un rafraîchissement réussi. */
    suspend fun setStationInformationFetchedAt(instant: Instant)
}
