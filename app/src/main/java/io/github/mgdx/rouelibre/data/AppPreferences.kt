package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * Les stations mises en favori, par leur identifiant (SPEC §8).
     *
     * Des identifiants de stations, et rien d'autre : ce ne sont pas des
     * lieux de l'utilisateur mais des points publics du réseau, et la
     * contrainte C3 interdit d'enregistrer quoi que ce soit d'un déplacement.
     *
     * Un flux plutôt qu'une lecture : l'étoile d'une station doit se mettre à
     * jour partout où elle s'affiche, sans que les écrans se préviennent.
     */
    val favouriteStationIds: Flow<Set<String>> =
        dataStore.data.map { it[FAVOURITE_STATION_IDS].orEmpty() }

    /**
     * Ajoute ou retire une station des favoris.
     *
     * @return vrai si la station est désormais en favori.
     */
    suspend fun toggleFavourite(stationId: String): Boolean {
        var isFavourite = false
        dataStore.edit { preferences ->
            val current = preferences[FAVOURITE_STATION_IDS].orEmpty()
            isFavourite = stationId !in current
            preferences[FAVOURITE_STATION_IDS] = if (isFavourite) {
                current + stationId
            } else {
                current - stationId
            }
        }
        return isFavourite
    }

    private companion object {
        val STATION_INFORMATION_FETCHED_AT =
            longPreferencesKey("station_information_fetched_at")
        val GBFS_DISCOVERY_URL = stringPreferencesKey("gbfs_discovery_url")
        val FAVOURITE_STATION_IDS = stringSetPreferencesKey("favourite_station_ids")
    }
}
