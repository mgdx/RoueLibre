package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Les stations mises en favori, avec leur disponibilité en direct (SPEC §7.5).
 *
 * L'ordre est celui que l'utilisateur a choisi, pas celui du réseau : c'est ce
 * que la réorganisation du §7.5 veut dire. Une station favorite que le flux ne
 * publie plus disparaît de la liste sans faire de bruit — le réseau en retire
 * de temps à autre.
 */
class FavouriteStationsViewModel(
    repository: StationRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val mutableFavourites = MutableStateFlow<List<StationWithAvailability>>(emptyList())

    /** Les stations favorites connues, dans l'ordre d'affichage. */
    val favourites: StateFlow<List<StationWithAvailability>> = mutableFavourites.asStateFlow()

    private val mutableHasLoaded = MutableStateFlow(false)

    /** Vrai dès que le cache a été lu, ce qui distingue « vide » de « en cours ». */
    val hasLoaded: StateFlow<Boolean> = mutableHasLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeStations(),
                preferences.favouriteStationIds,
            ) { snapshot, favouriteIds ->
                val known = snapshot.stations.associateBy { it.station.id }
                favouriteIds.mapNotNull(known::get)
            }.collect { stations ->
                mutableFavourites.value = stations
                mutableHasLoaded.value = true
            }
        }
    }

    /**
     * Enregistre l'ordre après un déplacement à la main (SPEC §7.5).
     *
     * L'ordre porte sur les identifiants et non sur les stations affichées :
     * une station absente du flux au moment du glissement ne doit pas être
     * retirée des favoris pour autant.
     */
    fun reorder(stationIds: List<String>) {
        viewModelScope.launch { preferences.setFavouriteOrder(stationIds) }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(
        private val repository: StationRepository,
        private val preferences: AppPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FavouriteStationsViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return FavouriteStationsViewModel(repository, preferences) as T
        }
    }
}
