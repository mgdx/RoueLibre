package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.filterStations
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
 * État des écrans qui montrent les stations.
 *
 * @property stations les stations retenues par la recherche, prêtes à être
 *   affichées.
 * @property query ce que l'utilisateur a tapé dans le champ de recherche.
 * @property isRefreshing une récupération est en cours.
 * @property fetchedAt date de la dernière récupération réussie, ou `null`.
 *   L'ancienneté qui en découle est recalculée par la vue à chaque battement,
 *   sinon un état vieillissant à l'écran resterait marqué comme frais.
 * @property hasLoadedOnce vrai dès que le cache a été lu, ce qui distingue
 *   « en cours de chargement » de « réellement vide ».
 */
data class StationsUiState(
    val stations: List<StationWithAvailability> = emptyList(),
    val query: String = "",
    val isRefreshing: Boolean = false,
    val fetchedAt: Instant? = null,
    val hasLoadedOnce: Boolean = false,
) {
    /**
     * Pourquoi la liste est vide, s'il y a lieu.
     *
     * Les deux cas appellent des mots et des gestes différents : un cache vide
     * invite à rafraîchir, une recherche infructueuse invite à l'effacer.
     * Les confondre reviendrait à dire à l'utilisateur que le réseau n'a
     * aucune station parce qu'il a fait une faute de frappe.
     */
    val emptiness: Emptiness
        get() = when {
            !hasLoadedOnce || isRefreshing || stations.isNotEmpty() -> Emptiness.None
            query.isNotBlank() -> Emptiness.NoMatch
            else -> Emptiness.NothingLoaded
        }
}

/** Ce que l'écran doit dire quand la liste ne montre rien. */
enum class Emptiness {
    /** Il y a des stations à l'écran. */
    None,

    /** Le cache est vide : aucune station n'a jamais été récupérée. */
    NothingLoaded,

    /** Des stations existent, mais aucune ne répond à la recherche. */
    NoMatch,
}

/**
 * Présente les stations et pilote leur rafraîchissement.
 *
 * Partagé par la carte et par la liste : les deux écrans montrent les mêmes
 * stations, avec la même politique de fraîcheur. Seule la liste se sert du
 * champ de recherche.
 *
 * Le modèle ne connaît ni vue ni ressource : il expose un état et des
 * événements, la vue choisit comment les montrer.
 */
class StationsViewModel(private val repository: StationRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(StationsUiState())

    /** L'état courant de l'écran. */
    val state: StateFlow<StationsUiState> = mutableState.asStateFlow()

    /**
     * Les échecs à signaler, une seule fois chacun.
     *
     * Un canal plutôt qu'un champ d'état : une erreur est un événement, et la
     * réafficher à chaque rotation de l'écran serait un défaut.
     */
    private val errorChannel = Channel<DataError>(Channel.BUFFERED)

    /** Flux des échecs à présenter à l'utilisateur. */
    val errors: Flow<DataError> = errorChannel.receiveAsFlow()

    /**
     * Les stations telles que le dépôt les fournit, avant filtrage.
     *
     * Gardées à part pour que changer la recherche ne demande pas de relire le
     * cache, et pour qu'effacer le champ retrouve la liste entière.
     */
    private var allStations: List<StationWithAvailability> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeStations().collect { snapshot ->
                allStations = snapshot.stations
                mutableState.update { current ->
                    current.copy(
                        stations = filterStations(allStations, current.query),
                        fetchedAt = snapshot.fetchedAt,
                        hasLoadedOnce = true,
                    )
                }
            }
        }
    }

    /**
     * Prend en compte une nouvelle saisie de recherche.
     *
     * Le filtrage a lieu à chaque frappe, sans anti-rebond : il parcourt
     * quelques centaines d'entrées déjà en mémoire. C'est la recherche
     * d'adresses du SPEC §4.3, portant sur des centaines de milliers de
     * numéros, qui en demandera un.
     */
    fun onQueryChanged(query: String) {
        mutableState.update { current ->
            if (current.query == query) {
                current
            } else {
                current.copy(query = query, stations = filterStations(allStations, query))
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
            require(modelClass.isAssignableFrom(StationsViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StationsViewModel(repository) as T
        }
    }
}
