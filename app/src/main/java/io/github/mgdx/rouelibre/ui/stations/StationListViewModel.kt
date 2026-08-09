package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * État de l'écran listant les stations.
 *
 * @property stations les stations connues et leur dernier état.
 * @property isRefreshing une récupération est en cours.
 * @property fetchedAt date de la dernière récupération réussie, ou `null`.
 *   L'ancienneté qui en découle est recalculée par la vue à chaque battement,
 *   sinon un état vieillissant à l'écran resterait marqué comme frais.
 * @property hasLoadedOnce vrai dès que le cache a été lu, ce qui distingue
 *   « en cours de chargement » de « réellement vide ».
 */
data class StationListUiState(
    val stations: List<StationWithAvailability> = emptyList(),
    val isRefreshing: Boolean = false,
    val fetchedAt: Instant? = null,
    val hasLoadedOnce: Boolean = false,
) {
    /** Vrai quand il n'y a rien à montrer et rien à attendre. */
    val isEmpty: Boolean
        get() = hasLoadedOnce && stations.isEmpty() && !isRefreshing
}

/**
 * Présente la liste des stations et pilote son rafraîchissement.
 *
 * Le modèle ne connaît ni vue ni ressource : il expose un état et des
 * événements, la vue choisit comment les montrer.
 */
class StationListViewModel(private val repository: StationRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(StationListUiState())

    /** L'état courant de l'écran. */
    val state: StateFlow<StationListUiState> = mutableState.asStateFlow()

    /**
     * Les échecs à signaler, une seule fois chacun.
     *
     * Un canal plutôt qu'un champ d'état : une erreur est un événement, et la
     * réafficher à chaque rotation de l'écran serait un défaut.
     */
    private val errorChannel = Channel<DataError>(Channel.BUFFERED)

    /** Flux des échecs à présenter à l'utilisateur. */
    val errors: Flow<DataError> = errorChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                mutableState.update { current ->
                    current.copy(
                        stations = snapshot.stations,
                        fetchedAt = snapshot.fetchedAt,
                        hasLoadedOnce = true,
                    )
                }
            }
        }
    }

    /**
     * Demande une mise à jour des disponibilités.
     *
     * @param force ignore le délai minimal entre deux appels. Réservé au geste
     *   de tirer-pour-rafraîchir : une demande explicite ne doit jamais se
     *   voir opposer un cache.
     */
    fun refresh(force: Boolean = false) {
        if (mutableState.value.isRefreshing) return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            val outcome = repository.refresh(force = force)
            mutableState.update { it.copy(isRefreshing = false) }
            if (outcome is Outcome.Failure) {
                errorChannel.send(outcome.error)
            }
        }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(private val repository: StationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StationListViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StationListViewModel(repository) as T
        }
    }
}
