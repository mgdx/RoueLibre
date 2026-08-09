package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.core.station.StationAvailability
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.joinStationsWithAvailability
import io.github.mgdx.rouelibre.data.local.StationAvailabilityEntity
import io.github.mgdx.rouelibre.data.local.StationDao
import io.github.mgdx.rouelibre.data.local.StationEntity
import io.github.mgdx.rouelibre.data.network.GbfsRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Source unique des stations et de leur disponibilité.
 *
 * Elle applique la politique de rafraîchissement du SPEC §4.1, qui tient en
 * trois règles : les données stables au plus une fois par jour, l'état temps
 * réel au plus une fois par minute, et **jamais rien en arrière-plan**. Aucune
 * tâche périodique n'est planifiée : chaque appel vient d'un écran affiché ou
 * d'un geste de l'utilisateur.
 *
 * @property remote accès aux flux GBFS.
 * @property dao cache local.
 * @property refreshTimestamps mémorise la date du dernier rafraîchissement des
 *   données stables, qui doit survivre au redémarrage de l'application.
 * @property discoveryUrlProvider donne l'URL du document d'auto-découverte.
 *   C'est une fonction et non une valeur parce que ce réglage est modifiable
 *   par l'utilisateur (SPEC §4.1) et peut changer entre deux appels.
 * @property clock horloge, injectée pour rendre la politique testable.
 */
class StationRepository(
    private val remote: GbfsRemoteSource,
    private val dao: StationDao,
    private val refreshTimestamps: RefreshTimestampStore,
    private val discoveryUrlProvider: suspend () -> String,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Le document d'auto-découverte de la session.
     *
     * Gardé en mémoire pour ne pas le redemander à chaque rafraîchissement de
     * l'état : il ne change qu'exceptionnellement, et le relire toutes les
     * minutes doublerait le trafic pour rien.
     */
    private var cachedDiscovery: GbfsDiscovery? = null
    private var cachedDiscoveryUrl: String? = null

    /** Sérialise les rafraîchissements : deux écrans peuvent en demander un. */
    private val refreshLock = Mutex()

    private var lastStatusRefresh: Instant? = null

    /**
     * Les stations et leur dernier état connu, réémis à chaque changement.
     *
     * Émet immédiatement le contenu du cache, y compris hors ligne. Un cache
     * vide donne une liste vide, ce que l'interface présente comme une
     * invitation à rafraîchir et non comme une erreur.
     */
    fun observeStations(): Flow<StationsSnapshot> = combine(
        dao.observeStations(),
        dao.observeAvailabilities(),
    ) { stations, availabilities ->
        StationsSnapshot(
            stations = joinStationsWithAvailability(
                stations.map(StationEntity::toDomain),
                availabilities.map(StationAvailabilityEntity::toDomain),
            ),
            fetchedAt = availabilities.maxOfOrNull { it.fetchedAtEpochSeconds }
                ?.let(Instant::ofEpochSecond),
        )
    }

    /**
     * Met à jour les données depuis le réseau si la politique l'autorise.
     *
     * @param force ignore le délai minimal entre deux états. Réservé au geste
     *   de tirer-pour-rafraîchir : l'utilisateur qui le demande explicitement
     *   ne doit pas se voir opposer un cache.
     * @return succès si les données ont été mises à jour ou étaient déjà
     *   fraîches, sinon la cause de l'échec.
     */
    suspend fun refresh(force: Boolean = false): Outcome<Unit> = refreshLock.withLock {
        val now = clock.instant()

        if (!force && !statusRefreshIsDue(now)) {
            return@withLock Outcome.Success(Unit)
        }

        val discovery = when (val outcome = discovery()) {
            is Outcome.Failure -> return@withLock outcome
            is Outcome.Success -> outcome.value
        }

        // Les données stables d'abord : sans elles, un état temps réel n'a
        // aucune station à décrire.
        if (stationInformationRefreshIsDue(now)) {
            when (val outcome = remote.fetchStationInformation(discovery)) {
                is Outcome.Failure -> {
                    // Un échec ici n'est fatal que si le cache est vide : sinon
                    // les stations connues suffisent à afficher un état frais.
                    if (dao.stationCount() == 0) return@withLock outcome
                }

                is Outcome.Success -> {
                    dao.replaceStations(outcome.value.stations.map(Station::toEntity))
                    refreshTimestamps.setStationInformationFetchedAt(now)
                }
            }
        }

        when (val outcome = remote.fetchStationStatus(discovery)) {
            is Outcome.Failure -> return@withLock outcome
            is Outcome.Success -> {
                dao.replaceAvailabilities(
                    outcome.value.availabilities.map { it.toEntity(fetchedAt = now) },
                )
                lastStatusRefresh = now
            }
        }

        Outcome.Success(Unit)
    }

    /**
     * Le document d'auto-découverte, relu seulement si l'URL a changé.
     */
    private suspend fun discovery(): Outcome<GbfsDiscovery> {
        val url = discoveryUrlProvider()
        cachedDiscovery?.let { cached ->
            if (cachedDiscoveryUrl == url) return Outcome.Success(cached)
        }
        return when (val outcome = remote.fetchDiscovery(url)) {
            is Outcome.Failure -> outcome
            is Outcome.Success -> {
                cachedDiscovery = outcome.value
                cachedDiscoveryUrl = url
                outcome
            }
        }
    }

    private fun statusRefreshIsDue(now: Instant): Boolean {
        val last = lastStatusRefresh ?: return true
        return Duration.between(last, now) >= STATUS_MINIMUM_INTERVAL
    }

    private suspend fun stationInformationRefreshIsDue(now: Instant): Boolean {
        if (dao.stationCount() == 0) return true
        val last = refreshTimestamps.stationInformationFetchedAt() ?: return true
        return Duration.between(last, now) >= STATION_INFORMATION_MAXIMUM_AGE
    }

    /** Âge à partir duquel l'état affiché doit être signalé comme périmé. */
    fun isStale(fetchedAt: Instant?): Boolean {
        if (fetchedAt == null) return true
        return Duration.between(fetchedAt, clock.instant()) > STALE_THRESHOLD
    }

    private companion object {
        /**
         * Le flux est produit toutes les minutes ; demander plus souvent ne
         * rapporterait aucune donnée nouvelle et ne ferait que charger le
         * serveur du producteur (SPEC §4.1).
         */
        val STATUS_MINIMUM_INTERVAL: Duration = Duration.ofSeconds(60)

        /**
         * Les données stables ne changent qu'à l'ouverture ou à la fermeture
         * d'une station, soit quelques fois par an.
         */
        val STATION_INFORMATION_MAXIMUM_AGE: Duration = Duration.ofDays(1)

        /**
         * Au-delà, l'utilisateur regarde une photographie et non un état :
         * il faut le lui dire. Cinq minutes laissent passer un rafraîchissement
         * manqué sans crier au loup.
         */
        val STALE_THRESHOLD: Duration = Duration.ofMinutes(5)
    }
}

/**
 * Un instantané de ce que l'application sait des stations.
 *
 * @property stations les stations connues et leur dernier état.
 * @property fetchedAt date à laquelle cet état a été récupéré, ou `null` si
 *   aucun état n'a jamais été reçu.
 */
data class StationsSnapshot(val stations: List<StationWithAvailability>, val fetchedAt: Instant?)

private fun StationEntity.toDomain() = Station(
    id = id,
    name = name,
    position = Coordinates(latitude, longitude),
    capacity = capacity,
    postalCode = postalCode,
)

private fun Station.toEntity() = StationEntity(
    id = id,
    name = name,
    latitude = position.latitude,
    longitude = position.longitude,
    capacity = capacity,
    postalCode = postalCode,
)

private fun StationAvailabilityEntity.toDomain() = StationAvailability(
    stationId = stationId,
    bikesAvailable = bikesAvailable,
    docksAvailable = docksAvailable,
    isInstalled = isInstalled,
    isRenting = isRenting,
    isReturning = isReturning,
    reportedAt = reportedAtEpochSeconds?.let(Instant::ofEpochSecond),
)

private fun StationAvailability.toEntity(fetchedAt: Instant) = StationAvailabilityEntity(
    stationId = stationId,
    bikesAvailable = bikesAvailable,
    docksAvailable = docksAvailable,
    isInstalled = isInstalled,
    isRenting = isRenting,
    isReturning = isReturning,
    reportedAtEpochSeconds = reportedAt?.epochSecond,
    fetchedAtEpochSeconds = fetchedAt.epochSecond,
)
