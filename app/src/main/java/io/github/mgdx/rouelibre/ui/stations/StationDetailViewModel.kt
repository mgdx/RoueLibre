package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import io.github.mgdx.rouelibre.data.location.DeviceLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * État de la feuille de détail d'une station.
 *
 * @property entry la station et son dernier état connu, ou `null` tant que le
 *   cache n'a pas été lu.
 * @property address l'adresse de la station, déduite de l'index hors ligne, ou
 *   `null` si l'index est absent ou ne connaît rien d'assez proche.
 * @property distanceInMetres distance à vol d'oiseau depuis la position de
 *   l'utilisateur, ou `null` s'il ne l'a pas partagée.
 * @property isFavourite la station figure parmi les favoris.
 * @property fetchedAt date de la dernière récupération réussie.
 */
data class StationDetailUiState(
    val entry: StationWithAvailability? = null,
    val address: AddressResult? = null,
    val distanceInMetres: Double? = null,
    val isFavourite: Boolean = false,
    val fetchedAt: Instant? = null,
)

/**
 * Alimente la feuille de détail d'une station (SPEC §7.2).
 *
 * La feuille reste vivante tant qu'elle est ouverte : les disponibilités
 * qu'elle montre suivent le flux du dépôt, elles ne sont pas figées à
 * l'ouverture. C'est ce qui évite de proposer une station qui s'est vidée
 * pendant qu'on la regardait.
 *
 * @property stationId la station décrite.
 */
class StationDetailViewModel(
    private val repository: StationRepository,
    private val preferences: AppPreferences,
    private val addressIndex: AddressIndex,
    private val deviceLocation: DeviceLocation,
    private val stationId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StationDetailUiState())

    /** L'état courant de la feuille. */
    val state: StateFlow<StationDetailUiState> = mutableState.asStateFlow()

    private var addressResolved = false
    private var distanceResolved = false

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                val entry = snapshot.stations.firstOrNull { it.station.id == stationId }
                mutableState.update { it.copy(entry = entry, fetchedAt = snapshot.fetchedAt) }
                if (entry != null) {
                    resolveAddressOnce(entry)
                    showDistanceOnce(entry)
                }
            }
        }
        viewModelScope.launch {
            preferences.favouriteStationIds.collect { favourites ->
                mutableState.update { it.copy(isFavourite = stationId in favourites) }
            }
        }
    }

    /**
     * Cherche l'adresse de la station, une seule fois.
     *
     * Le flux du dépôt réémet à chaque rafraîchissement des disponibilités,
     * toutes les minutes ; or la position d'une station ne bouge pas, et la
     * recherche parcourt des milliers de numéros.
     */
    private suspend fun resolveAddressOnce(entry: StationWithAvailability) {
        if (addressResolved) return
        addressResolved = true
        val address = addressIndex.nearestAddress(entry.station.position)
        mutableState.update { it.copy(address = address) }
    }

    /**
     * Calcule la distance depuis la position, si elle est déjà connue.
     *
     * **Aucune permission n'est demandée ici** : ouvrir le détail d'une
     * station n'est pas le moment de réclamer la localisation, et une
     * distance manquante ne prive de rien (SPEC §10). Seule la dernière
     * position connue est lue, ce qui n'allume aucun capteur.
     */
    private fun showDistanceOnce(entry: StationWithAvailability) {
        if (distanceResolved) return
        distanceResolved = true
        val here = deviceLocation.lastKnown() ?: return
        val distance = here.distanceInMetresTo(entry.station.position)
        mutableState.update { it.copy(distanceInMetres = distance) }
    }

    /** Met la station en favori, ou l'en retire (SPEC §7.2). */
    fun toggleFavourite() {
        viewModelScope.launch { preferences.toggleFavourite(stationId) }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(
        private val repository: StationRepository,
        private val preferences: AppPreferences,
        private val addressIndex: AddressIndex,
        private val deviceLocation: DeviceLocation,
        private val stationId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationDetailViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StationDetailViewModel(
                repository,
                preferences,
                addressIndex,
                deviceLocation,
                stationId,
            ) as T
        }
    }
}
