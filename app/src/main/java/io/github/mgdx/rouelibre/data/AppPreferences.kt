package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Réglages et état persistant de l'application (SPEC §8).
 *
 * DataStore, et non Room, parce qu'il ne s'agit que de quelques valeurs
 * isolées. Rien de ce qui est écrit ici ne décrit un déplacement : ni
 * historique, ni position, ni destination (SPEC §2, C3).
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) : RefreshTimestampStore {

    /**
     * Date du dernier rafraîchissement des données stables des stations.
     *
     * Persistée parce que la règle « au plus une fois par jour » (SPEC §4.1)
     * doit survivre au redémarrage de l'application : sinon chaque lancement
     * retéléchargerait la liste complète des stations.
     */
    override suspend fun stationInformationFetchedAt(): Instant? =
        dataStore.data.first()[STATION_INFORMATION_FETCHED_AT]
            ?.let(Instant::ofEpochSecond)

    /** Enregistre la date du dernier rafraîchissement des données stables. */
    override suspend fun setStationInformationFetchedAt(instant: Instant) {
        dataStore.edit { preferences ->
            preferences[STATION_INFORMATION_FETCHED_AT] = instant.epochSecond
        }
    }

    /**
     * URL du document d'auto-découverte GBFS choisie par l'utilisateur.
     *
     * `null` tant qu'elle n'a pas été modifiée : c'est alors celle de la
     * configuration de ville qui s'applique. Ce réglage est ce qui rend
     * l'application utilisable avec n'importe quel réseau GBFS (SPEC §4.1).
     */
    suspend fun gbfsDiscoveryUrlOverride(): String? =
        dataStore.data.first()[GBFS_DISCOVERY_URL]?.takeIf { it.isNotBlank() }

    /** Remplace l'URL du flux, ou rétablit celle de la configuration si `null`. */
    suspend fun setGbfsDiscoveryUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(GBFS_DISCOVERY_URL)
            } else {
                preferences[GBFS_DISCOVERY_URL] = url
            }
        }
    }

    private companion object {
        val STATION_INFORMATION_FETCHED_AT =
            longPreferencesKey("station_information_fetched_at")
        val GBFS_DISCOVERY_URL = stringPreferencesKey("gbfs_discovery_url")
    }
}
