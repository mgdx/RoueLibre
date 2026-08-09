package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import java.time.Instant

/**
 * Une station du réseau, dans ses données stables.
 *
 * Le vocabulaire reste générique — « station », « vélo », « réseau » — et ne
 * nomme jamais un réseau particulier : c'est une exigence de portabilité
 * (SPEC §15).
 *
 * @property id identifiant du producteur, stable d'une actualisation à l'autre.
 * @property name nom tel que publié par le producteur.
 * @property position emplacement de la station.
 * @property capacity nombre total de points d'attache, si le flux le publie.
 * @property postalCode code postal, si le flux le publie.
 */
public data class Station(
    public val id: String,
    public val name: String,
    public val position: Coordinates,
    public val capacity: Int?,
    public val postalCode: String?,
)

/**
 * L'état d'une station à un instant donné.
 *
 * @property stationId identifiant de la station décrite.
 * @property bikesAvailable vélos empruntables.
 * @property docksAvailable places libres pour rendre un vélo.
 * @property isInstalled la station est déployée sur le terrain.
 * @property isRenting la station accepte les emprunts.
 * @property isReturning la station accepte les retours.
 * @property reportedAt date de la mesure telle que déclarée par le producteur.
 */
public data class StationAvailability(
    public val stationId: String,
    public val bikesAvailable: Int,
    public val docksAvailable: Int,
    public val isInstalled: Boolean,
    public val isRenting: Boolean,
    public val isReturning: Boolean,
    public val reportedAt: Instant?,
) {
    /** Vrai si la station peut effectivement prêter un vélo maintenant. */
    public val canLendBike: Boolean
        get() = isInstalled && isRenting && bikesAvailable > 0

    /** Vrai si la station peut effectivement accepter un vélo maintenant. */
    public val canAcceptBike: Boolean
        get() = isInstalled && isReturning && docksAvailable > 0
}

/**
 * Une station et son dernier état connu.
 *
 * L'état est facultatif à dessein : les deux flux GBFS ne sont pas
 * nécessairement synchronisés. Le flux observé pour le réseau lillois publiait
 * 268 stations dans `station_information` et 267 dans `station_status`. Une
 * station sans état doit s'afficher comme telle, jamais disparaître ni être
 * confondue avec une station vide.
 */
public data class StationWithAvailability(
    public val station: Station,
    public val availability: StationAvailability?,
) {
    /** Niveau de service, qui décide de la façon dont la station est présentée. */
    public val serviceState: ServiceState
        get() = when {
            availability == null -> ServiceState.Unknown
            !availability.isInstalled -> ServiceState.OutOfService
            !availability.isRenting && !availability.isReturning -> ServiceState.OutOfService
            else -> ServiceState.InService
        }
}

/** État de service d'une station, du point de vue de l'utilisateur. */
public enum class ServiceState {
    /** La station fonctionne. */
    InService,

    /** La station est hors service ou pas encore déployée. */
    OutOfService,

    /** Le flux temps réel ne dit rien de cette station. */
    Unknown,
}

/**
 * Rapproche les données stables des stations de leur état temps réel.
 *
 * La jointure est volontairement tolérante : un identifiant présent d'un seul
 * côté ne doit ni faire disparaître la station, ni faire échouer la lecture du
 * flux. Un état orphelin — décrivant une station absente de
 * `station_information` — est ignoré, faute de savoir où le placer sur la
 * carte.
 *
 * @return une entrée par station connue, dans l'ordre reçu.
 */
public fun joinStationsWithAvailability(
    stations: List<Station>,
    availabilities: List<StationAvailability>,
): List<StationWithAvailability> {
    val byStationId = availabilities.associateBy { it.stationId }
    return stations.map { station ->
        StationWithAvailability(station, byStationId[station.id])
    }
}
