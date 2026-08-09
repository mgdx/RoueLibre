package io.github.mgdx.rouelibre.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran de recherche d'adresses.
 *
 * @property query ce qui est tapé, brut.
 * @property results les adresses trouvées, la meilleure d'abord.
 * @property isSearching une recherche est en cours.
 * @property isIndexInstalled faux tant que l'index d'adresses n'est pas sur
 *   l'appareil : l'écran explique alors quoi faire au lieu de ne rien trouver.
 * @property error l'échec de lecture à signaler, s'il y en a un.
 */
data class AddressSearchUiState(
    val query: String = "",
    val results: List<AddressResult> = emptyList(),
    val isSearching: Boolean = false,
    val isIndexInstalled: Boolean = true,
    val error: DataError? = null,
) {
    /** Vrai quand la saisie ne ramène rien, une fois la recherche terminée. */
    val hasNoMatch: Boolean
        get() = isIndexInstalled &&
            error == null &&
            !isSearching &&
            query.isNotBlank() &&
            results.isEmpty()
}

/**
 * Pilote la recherche d'adresses hors ligne (SPEC §4.3).
 *
 * Deux règles gouvernent ce modèle.
 *
 * **Aucune recherche sur le fil principal.** Le parcours flou porte sur des
 * dizaines de milliers d'entrées ; il vit dans [AddressIndex], sur le
 * répartiteur des entrées-sorties.
 *
 * **Chaque frappe annule la précédente.** Un anti-rebond laisse passer une
 * pause de frappe avant de chercher, et `collectLatest` abandonne le calcul en
 * cours dès qu'une lettre s'ajoute. Sans quoi une saisie de quinze caractères
 * lancerait quinze parcours complets dont quatorze seraient jetés.
 *
 * @property index l'index hors ligne interrogé.
 * @property origin point de référence du classement par proximité — le centre
 *   de la carte au moment où l'écran s'ouvre. Aucune permission de localisation
 *   n'est demandée pour cela (SPEC §10).
 */
class AddressSearchViewModel(private val index: AddressIndex, private val origin: Coordinates?) :
    ViewModel() {

    private val mutableState = MutableStateFlow(
        AddressSearchUiState(isIndexInstalled = index.isInstalled()),
    )

    /** L'état courant de l'écran. */
    val state: StateFlow<AddressSearchUiState> = mutableState.asStateFlow()

    private val typed = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // `debounce` reste marqué en aperçu par kotlinx.coroutines alors
            // qu'il est stable d'usage depuis des années ; l'anti-rebond de
            // 150 ms est une exigence du SPEC §4.3, pas un choix de confort.
            @OptIn(FlowPreview::class)
            typed.debounce(TYPING_PAUSE_MILLIS).collectLatest(::search)
        }
    }

    /** Prend en compte une nouvelle saisie. */
    fun onQueryChanged(query: String) {
        if (mutableState.value.query == query) return
        mutableState.update { it.copy(query = query, error = null) }
        typed.value = query
    }

    private suspend fun search(query: String) {
        if (query.isBlank()) {
            mutableState.update {
                it.copy(results = emptyList(), isSearching = false, error = null)
            }
            return
        }
        if (!index.isInstalled()) {
            mutableState.update { it.copy(isIndexInstalled = false, isSearching = false) }
            return
        }

        mutableState.update { it.copy(isSearching = true, isIndexInstalled = true) }
        when (val outcome = index.search(query, origin)) {
            is Outcome.Success -> mutableState.update {
                it.copy(results = outcome.value, isSearching = false, error = null)
            }

            is Outcome.Failure -> mutableState.update {
                it.copy(results = emptyList(), isSearching = false, error = outcome.error)
            }
        }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(private val index: AddressIndex, private val origin: Coordinates?) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AddressSearchViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return AddressSearchViewModel(index, origin) as T
        }
    }

    private companion object {
        /**
         * Pause de frappe avant de chercher, en millisecondes.
         *
         * Cent cinquante millisecondes, comme le demande le SPEC §4.3 : assez
         * court pour que la liste suive la frappe, assez long pour qu'une
         * saisie continue ne déclenche qu'une recherche.
         */
        const val TYPING_PAUSE_MILLIS = 150L
    }
}
