package io.github.mgdx.rouelibre.ui.storage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetRejection
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Une ligne de l'écran stockage.
 *
 * @property kind le jeu décrit.
 * @property installed ce qui est installé, ou `null`.
 */
data class DatasetRow(val kind: DatasetKind, val installed: InstalledDataset?)

/**
 * État de l'écran stockage.
 *
 * @property totalBytes place occupée, ou `null` si rien n'est installé.
 */
data class StorageUiState(
    val datasets: List<DatasetRow> = DatasetKind.entries.map { DatasetRow(it, null) },
    val isImporting: Boolean = false,
    val totalBytes: Long? = null,
)

/** Ce que l'écran doit annoncer après une action. */
sealed interface StorageMessage {
    /** Un jeu vient d'être installé. */
    data class Installed(val kind: DatasetKind) : StorageMessage

    /** Un jeu vient d'être supprimé. */
    data class Deleted(val kind: DatasetKind) : StorageMessage

    /** Le fichier proposé a été refusé, pour la raison donnée. */
    data class Rejected(val kind: DatasetKind, val reason: DatasetRejection) : StorageMessage
}

/** Pilote l'installation et la suppression des jeux de données hors ligne. */
class StorageViewModel(private val store: DatasetStore) : ViewModel() {

    private val mutableState = MutableStateFlow(StorageUiState())

    /** L'état courant de l'écran. */
    val state: StateFlow<StorageUiState> = mutableState.asStateFlow()

    private val messageChannel = Channel<StorageMessage>(Channel.BUFFERED)

    /** Les issues d'action à annoncer, une seule fois chacune. */
    val messages: Flow<StorageMessage> = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            store.installed.collect { installed ->
                mutableState.update { current ->
                    current.copy(
                        datasets = DatasetKind.entries.map {
                            DatasetRow(it, installed[it])
                        },
                        totalBytes = installed.values
                            .sumOf { it.sizeBytes }
                            .takeIf { it > 0 },
                    )
                }
            }
        }
    }

    /**
     * Installe le fichier désigné comme le jeu [kind].
     *
     * @param source document choisi dans le sélecteur du système.
     */
    fun import(kind: DatasetKind, source: Uri) {
        if (mutableState.value.isImporting) return
        viewModelScope.launch {
            mutableState.update { it.copy(isImporting = true) }
            val outcome = store.importFrom(kind, source)
            mutableState.update { it.copy(isImporting = false) }
            messageChannel.send(
                when (outcome) {
                    is DatasetImportResult.Installed -> StorageMessage.Installed(kind)
                    is DatasetImportResult.Rejected ->
                        StorageMessage.Rejected(kind, outcome.reason)
                },
            )
        }
    }

    /** Supprime un jeu installé. */
    fun delete(kind: DatasetKind) {
        viewModelScope.launch {
            store.delete(kind)
            messageChannel.send(StorageMessage.Deleted(kind))
        }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(private val store: DatasetStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StorageViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StorageViewModel(store) as T
        }
    }
}
