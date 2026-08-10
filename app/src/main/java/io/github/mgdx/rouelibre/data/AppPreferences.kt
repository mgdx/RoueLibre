package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Le thème dont l'utilisateur veut que l'application s'habille (SPEC §7.6).
 *
 * @property id valeur écrite sur le disque, stable d'une version à l'autre.
 */
enum class AppTheme(val id: String) {
    /** Celui du système, et c'est le défaut. */
    System("systeme"),

    /** Toujours clair. */
    Light("clair"),

    /** Toujours sombre. */
    Dark("sombre"),
    ;

    companion object {
        /** Relit un thème enregistré ; une valeur inconnue rend [System]. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Les deux temps forfaitaires du trajet, en secondes (SPEC §6).
 *
 * @property pickupSeconds temps de prise du vélo à la station de départ.
 * @property dropoffSeconds temps de dépose à la station d'arrivée.
 */
data class HandlingTimes(val pickupSeconds: Int, val dropoffSeconds: Int)

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

    /**
     * Thème choisi : clair, sombre, ou celui du système (SPEC §7.6).
     *
     * Le défaut suit le système, seul choix qui respecte un réglage que
     * l'utilisateur a déjà exprimé ailleurs.
     */
    val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromId(preferences[THEME])
    }

    /** Enregistre le thème choisi. */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.id }
    }

    /**
     * Temps forfaitaires de prise et de dépose du vélo (SPEC §6).
     *
     * Réglables parce qu'ils dépendent de la personne et de la station : deux
     * minutes pour qui connaît le geste, davantage avec un antivol récalcitrant
     * ou une borne capricieuse. Ils pèsent sur le choix des stations autant que
     * sur le temps annoncé.
     */
    val handlingTimes: Flow<HandlingTimes> = dataStore.data.map { preferences ->
        HandlingTimes(
            pickupSeconds = preferences[PICKUP_SECONDS] ?: DEFAULT_HANDLING_SECONDS,
            dropoffSeconds = preferences[DROPOFF_SECONDS] ?: DEFAULT_HANDLING_SECONDS,
        )
    }

    /** Enregistre les temps forfaitaires, bornés à des valeurs plausibles. */
    suspend fun setHandlingTimes(times: HandlingTimes) {
        dataStore.edit { preferences ->
            preferences[PICKUP_SECONDS] = times.pickupSeconds.coerceIn(0, MAX_HANDLING_SECONDS)
            preferences[DROPOFF_SECONDS] = times.dropoffSeconds.coerceIn(0, MAX_HANDLING_SECONDS)
        }
    }

    /**
     * URL du manifeste des jeux de données choisie par l'utilisateur.
     *
     * `null` tant qu'elle n'a pas été modifiée. Ce réglage existe pour que
     * l'hébergeur par défaut ne soit jamais un point de défaillance unique
     * (SPEC §4.4).
     */
    suspend fun dataManifestUrlOverride(): String? =
        dataStore.data.first()[DATA_MANIFEST_URL]?.takeIf { it.isNotBlank() }

    /** Remplace l'URL du manifeste, ou rétablit celle de la configuration. */
    suspend fun setDataManifestUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(DATA_MANIFEST_URL)
            } else {
                preferences[DATA_MANIFEST_URL] = url
            }
        }
    }

    private companion object {
        val STATION_INFORMATION_FETCHED_AT =
            longPreferencesKey("station_information_fetched_at")
        val GBFS_DISCOVERY_URL = stringPreferencesKey("gbfs_discovery_url")
        val FAVOURITE_STATION_IDS = stringSetPreferencesKey("favourite_station_ids")
        val THEME = stringPreferencesKey("theme")
        val PICKUP_SECONDS = intPreferencesKey("pickup_seconds")
        val DROPOFF_SECONDS = intPreferencesKey("dropoff_seconds")
        val DATA_MANIFEST_URL = stringPreferencesKey("data_manifest_url")

        /** Deux minutes, la valeur par défaut du SPEC §6. */
        const val DEFAULT_HANDLING_SECONDS = 120

        /** Un quart d'heure pour prendre un vélo n'est plus un forfait. */
        const val MAX_HANDLING_SECONDS = 900
    }
}
