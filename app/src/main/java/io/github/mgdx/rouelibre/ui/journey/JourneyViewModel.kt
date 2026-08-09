package io.github.mgdx.rouelibre.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.core.journey.JourneyPlanner
import io.github.mgdx.rouelibre.data.StationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran de résultat d'itinéraire.
 *
 * @property plan ce que l'algorithme a composé, ou `null` tant qu'il calcule.
 * @property isComputing un calcul est en cours.
 * @property chosenIndex la proposition affichée : 0 pour la meilleure, puis
 *   les alternatives dans l'ordre.
 * @property hasStations faux quand aucune station n'est connue : il faut
 *   d'abord récupérer le réseau, ce qui n'est pas la même chose qu'un trajet
 *   impossible.
 */
data class JourneyUiState(
    val plan: JourneyPlan? = null,
    val isComputing: Boolean = true,
    val chosenIndex: Int = 0,
    val hasStations: Boolean = true,
) {
    /** Les propositions, la meilleure d'abord. */
    val options: List<JourneyOption>
        get() = when (val current = plan) {
            is JourneyPlan.Found -> listOf(current.best) + current.alternatives
            else -> emptyList()
        }

    /** La proposition actuellement montrée. */
    val chosen: JourneyOption?
        get() = options.getOrNull(chosenIndex)
}

/**
 * Compose le trajet demandé et le tient à jour (SPEC §6, §7.4).
 *
 * Le calcul a lieu hors du fil principal, dans l'algorithme du module métier,
 * et il est annulable : quitter l'écran pendant qu'il tourne l'interrompt avec
 * le modèle.
 *
 * Rien n'est conservé : ni le trajet, ni ses points. Le SPEC §8 veut que les
 * itinéraires calculés vivent en mémoire, le temps de la session.
 */
class JourneyViewModel(
    private val planner: JourneyPlanner,
    private val repository: StationRepository,
    private val origin: Coordinates,
    private val destination: Coordinates,
) : ViewModel() {

    private val mutableState = MutableStateFlow(JourneyUiState())

    /** L'état courant de l'écran. */
    val state: StateFlow<JourneyUiState> = mutableState.asStateFlow()

    init {
        compute()
    }

    /**
     * Recalcule le trajet (SPEC §7.4).
     *
     * Le bouton de recalcul existe parce que les disponibilités changent : la
     * station retenue il y a cinq minutes peut s'être vidée. Le calcul repart
     * donc de l'état des stations le plus récent qu'ait le dépôt.
     */
    fun compute() {
        viewModelScope.launch {
            mutableState.update { it.copy(isComputing = true, chosenIndex = 0) }
            val stations = repository.observeStations().first().stations
            if (stations.isEmpty()) {
                mutableState.update {
                    it.copy(isComputing = false, hasStations = false, plan = null)
                }
                return@launch
            }
            val plan = planner.plan(origin, destination, stations)
            mutableState.update {
                it.copy(plan = plan, isComputing = false, hasStations = true)
            }
        }
    }

    /** Montre une autre proposition, sans rien recalculer. */
    fun choose(index: Int) {
        mutableState.update { current ->
            if (index in current.options.indices) current.copy(chosenIndex = index) else current
        }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(
        private val planner: JourneyPlanner,
        private val repository: StationRepository,
        private val origin: Coordinates,
        private val destination: Coordinates,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(JourneyViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return JourneyViewModel(planner, repository, origin, destination) as T
        }
    }
}
