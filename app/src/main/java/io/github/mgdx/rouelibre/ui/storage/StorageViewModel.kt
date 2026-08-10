package io.github.mgdx.rouelibre.ui.storage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetRejection
import io.github.mgdx.rouelibre.core.data.DatasetUpdate
import io.github.mgdx.rouelibre.core.data.InstalledDataset
import io.github.mgdx.rouelibre.core.data.compareWithInstalled
import io.github.mgdx.rouelibre.data.datasets.DatasetDownloader
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.datasets.DownloadProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Une ligne de l'écran stockage.
 *
 * @property kind le jeu décrit.
 * @property installed ce qui est installé, ou `null`.
 * @property update ce que le manifeste en dit, une fois consulté.
 * @property publishedSizeBytes taille annoncée par le manifeste, montrée avant
 *   de demander confirmation (SPEC §4.4).
 */
data class DatasetRow(
    val kind: DatasetKind,
    val installed: InstalledDataset?,
    val update: DatasetUpdate? = null,
    val publishedSizeBytes: Long? = null,
)

/**
 * État de l'écran stockage.
 *
 * @property totalBytes place occupée, ou `null` si rien n'est installé.
 * @property isChecking une consultation du manifeste est en cours.
 * @property manifest la publication annoncée, une fois consultée.
 * @property downloading le transfert en cours, s'il y en a un.
 */
data class StorageUiState(
    val datasets: List<DatasetRow> = DatasetKind.entries.map { DatasetRow(it, null) },
    val isImporting: Boolean = false,
    val totalBytes: Long? = null,
    val isChecking: Boolean = false,
    val manifest: DataManifest? = null,
    val downloading: DownloadProgress? = null,
) {
    /** Les jeux que le manifeste annonce comme absents ou périmés. */
    val outdated: List<DatasetRow>
        get() = datasets.filter {
            it.update == DatasetUpdate.Missing ||
                it.update == DatasetUpdate.Outdated
        }

    /** Ce qu'il y aurait à télécharger, en octets. */
    val pendingBytes: Long
        get() = outdated.sumOf { it.publishedSizeBytes ?: 0L }
}

/** Ce que l'écran doit annoncer après une action. */
sealed interface StorageMessage {
    /** La consultation du manifeste a échoué. */
    data class CheckFailed(val error: DataError) : StorageMessage

    /** Le manifeste annonce un format que cette version ne sait pas lire. */
    data class UnsupportedFormat(val found: Int, val supported: Int) : StorageMessage

    /** Tout est déjà à jour. */
    data object AlreadyUpToDate : StorageMessage

    /** Un téléchargement a échoué. */
    data class DownloadFailed(val kind: DatasetKind, val error: DataError) : StorageMessage

    /** Un jeu vient d'être installé. */
    data class Installed(val kind: DatasetKind) : StorageMessage

    /** Un jeu vient d'être supprimé. */
    data class Deleted(val kind: DatasetKind) : StorageMessage

    /** Le fichier proposé a été refusé, pour la raison donnée. */
    data class Rejected(val kind: DatasetKind, val reason: DatasetRejection) : StorageMessage
}

/**
 * Pilote l'installation, la mise à jour et la suppression des jeux de données
 * hors ligne (SPEC §4.4).
 *
 * **La vérification n'est jamais automatique.** Elle a lieu sur action
 * explicite, depuis cet écran : une requête périodique dessinerait un profil
 * d'usage de l'application, ce que la contrainte C3 exclut.
 */
class StorageViewModel(
    private val store: DatasetStore,
    private val downloader: DatasetDownloader,
    private val manifestUrl: suspend () -> String,
    private val workDirectory: File,
    private val supportedFormatVersion: Int,
) : ViewModel() {

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
                        datasets = DatasetKind.entries.map { kind ->
                            DatasetRow(
                                kind = kind,
                                installed = installed[kind],
                                update = current.datasets.firstOrNull { it.kind == kind }?.update,
                                publishedSizeBytes = current.datasets
                                    .firstOrNull { it.kind == kind }?.publishedSizeBytes,
                            )
                        },
                        totalBytes = installed.values
                            .sumOf { it.sizeBytes }
                            .takeIf { it > 0 },
                    ).withManifestApplied()
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

    /**
     * Consulte le manifeste publié (SPEC §4.4).
     *
     * Une seule requête, sur appui, et rien n'est téléchargé à cette occasion :
     * l'utilisateur voit d'abord ce qui a changé et ce que cela pèse.
     */
    fun checkForUpdates() {
        if (mutableState.value.isChecking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isChecking = true) }
            val outcome = downloader.fetchManifest(manifestUrl())
            mutableState.update { it.copy(isChecking = false) }
            when (outcome) {
                is Outcome.Failure -> messageChannel.send(StorageMessage.CheckFailed(outcome.error))
                is Outcome.Success -> acceptManifest(outcome.value)
            }
        }
    }

    /**
     * Prend acte d'un manifeste lu.
     *
     * Un format que l'application ne sait pas lire est dit tel quel, avec une
     * invitation à mettre à jour : le SPEC §4.4 refuse qu'on échoue plus tard,
     * à l'ouverture d'un fichier.
     */
    private suspend fun acceptManifest(manifest: DataManifest) {
        if (manifest.formatVersion != supportedFormatVersion) {
            messageChannel.send(
                StorageMessage.UnsupportedFormat(manifest.formatVersion, supportedFormatVersion),
            )
            return
        }
        mutableState.update { current -> current.copy(manifest = manifest).withManifestApplied() }
        if (mutableState.value.outdated.isEmpty()) {
            messageChannel.send(StorageMessage.AlreadyUpToDate)
        }
    }

    /**
     * Télécharge et installe ce que le manifeste annonce de neuf.
     *
     * Les jeux déjà à jour ne sont pas repris : c'est tout l'intérêt de
     * comparer les empreintes.
     */
    fun downloadPending() {
        val manifest = mutableState.value.manifest ?: return
        if (mutableState.value.downloading != null) return
        viewModelScope.launch {
            for (row in mutableState.value.outdated) {
                val dataset = manifest.datasetFor(row.kind) ?: continue
                val outcome = downloader.download(
                    dataset,
                    File(workDirectory, row.kind.id),
                ) { progress ->
                    mutableState.update { it.copy(downloading = progress) }
                }
                when (outcome) {
                    is Outcome.Failure -> {
                        mutableState.update { it.copy(downloading = null) }
                        messageChannel.send(
                            StorageMessage.DownloadFailed(row.kind, outcome.error),
                        )
                        return@launch
                    }

                    is Outcome.Success -> {
                        val installed = store.install(
                            kind = row.kind,
                            files = outcome.value,
                            fingerprint = dataset.fingerprint,
                        )
                        messageChannel.send(
                            when (installed) {
                                is DatasetImportResult.Installed ->
                                    StorageMessage.Installed(row.kind)

                                is DatasetImportResult.Rejected ->
                                    StorageMessage.Rejected(row.kind, installed.reason)
                            },
                        )
                    }
                }
            }
            mutableState.update { it.copy(downloading = null).withManifestApplied() }
        }
    }

    /** Recroise l'état installé avec le manifeste consulté. */
    private fun StorageUiState.withManifestApplied(): StorageUiState {
        val manifest = manifest ?: return this
        val states = compareWithInstalled(
            manifest,
            installedFingerprints = datasets.mapNotNull { row ->
                row.installed?.let { row.kind to it.sha256 }
            }.toMap(),
        )
        return copy(
            datasets = datasets.map { row ->
                row.copy(
                    update = states[row.kind],
                    publishedSizeBytes = manifest.datasetFor(row.kind)?.sizeBytes,
                )
            },
        )
    }

    /** Supprime un jeu installé. */
    fun delete(kind: DatasetKind) {
        viewModelScope.launch {
            store.delete(kind)
            messageChannel.send(StorageMessage.Deleted(kind))
        }
    }

    /** Fabrique le modèle avec ses dépendances, sans framework d'injection. */
    class Factory(
        private val store: DatasetStore,
        private val downloader: DatasetDownloader,
        private val manifestUrl: suspend () -> String,
        private val workDirectory: File,
        private val supportedFormatVersion: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StorageViewModel::class.java)) {
                "modèle inattendu : ${modelClass.name}"
            }
            return StorageViewModel(
                store,
                downloader,
                manifestUrl,
                workDirectory,
                supportedFormatVersion,
            ) as T
        }
    }
}
