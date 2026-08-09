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
 * L'ordre suit celui des stations du réseau, pas celui des ajouts : les
 * identifiants sont conservés dans un ensemble, qui n'a pas d'ordre. La
 * réorganisation demandée par le §7.5 viendra avec l'écran dédié.
 */
class FavouriteStationsViewModel(repository: StationRepository, preferences: AppPreferences) :
    ViewModel() {

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
                snapshot.stations.filter { it.station.id in favouriteIds }
            }.collect { stations ->
                mutableFavourites.value = stations
                mutableHasLoaded.value = true
            }
        }
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
