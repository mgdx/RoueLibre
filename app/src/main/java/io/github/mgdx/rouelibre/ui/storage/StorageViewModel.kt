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
 * One row of the storage screen.
 *
 * @property kind the set described.
 * @property installed what is installed, or `null`.
 * @property update what the manifest says about it, once consulted.
 * @property publishedSizeBytes the size the manifest announces, shown before
 *   asking for confirmation (SPEC §4.4).
 */
data class DatasetRow(
    val kind: DatasetKind,
    val installed: InstalledDataset?,
    val update: DatasetUpdate? = null,
    val publishedSizeBytes: Long? = null,
)

/**
 * The state of the storage screen.
 *
 * @property totalBytes the space occupied, or `null` if nothing is installed.
 * @property isChecking a manifest check is under way.
 * @property manifest the announced release, once checked.
 * @property downloading the transfer in progress, if there is one.
 */
data class StorageUiState(
    val datasets: List<DatasetRow> = DatasetKind.entries.map { DatasetRow(it, null) },
    val isImporting: Boolean = false,
    val totalBytes: Long? = null,
    val isChecking: Boolean = false,
    val manifest: DataManifest? = null,
    val downloading: DownloadProgress? = null,
) {
    /** The sets the manifest announces as absent or out of date. */
    val outdated: List<DatasetRow>
        get() = datasets.filter {
            it.update == DatasetUpdate.Missing ||
                it.update == DatasetUpdate.Outdated
        }

    /** What there would be to download, in bytes. */
    val pendingBytes: Long
        get() = outdated.sumOf { it.publishedSizeBytes ?: 0L }
}

/** What the screen must announce after an action. */
sealed interface StorageMessage {
    /** Checking the manifest failed. */
    data class CheckFailed(val error: DataError) : StorageMessage

    /** The manifest announces a format this build cannot read. */
    data class UnsupportedFormat(val found: Int, val supported: Int) : StorageMessage

    /** Everything is already up to date. */
    data object AlreadyUpToDate : StorageMessage

    /** A download failed. */
    data class DownloadFailed(val kind: DatasetKind, val error: DataError) : StorageMessage

    /** A set has just been installed. */
    data class Installed(val kind: DatasetKind) : StorageMessage

    /** A set has just been deleted. */
    data class Deleted(val kind: DatasetKind) : StorageMessage

    /** The file offered was refused, for the reason given. */
    data class Rejected(val kind: DatasetKind, val reason: DatasetRejection) : StorageMessage
}

/**
 * Drives the installation, updating and deletion of the offline datasets
 * (SPEC §4.4).
 *
 * **The check is never automatic.** It happens on an explicit action, from this
 * screen: a periodic request would draw a usage profile of the application,
 * which constraint C3 rules out.
 */
class StorageViewModel(
    private val store: DatasetStore,
    private val downloader: DatasetDownloader,
    private val manifestUrl: suspend () -> String?,
    private val workDirectory: File,
    private val supportedFormatVersion: suspend () -> Int?,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StorageUiState())

    /** The screen's current state. */
    val state: StateFlow<StorageUiState> = mutableState.asStateFlow()

    private val messageChannel = Channel<StorageMessage>(Channel.BUFFERED)

    /** The outcomes to announce, once each. */
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
     * Installs the designated file as the [kind] set.
     *
     * @param source the document picked from the system chooser.
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
     * Checks the published manifest (SPEC §4.4).
     *
     * A single request, on a press, and nothing is downloaded on that occasion:
     * the user first sees what changed and what it weighs.
     */
    fun checkForUpdates() {
        if (mutableState.value.isChecking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isChecking = true) }
            // Without an active city there is no manifest to check: saying so
            // beats querying an address picked at random.
            val url = manifestUrl()
            if (url == null) {
                mutableState.update { it.copy(isChecking = false) }
                messageChannel.send(StorageMessage.CheckFailed(DataError.NoCityChosen))
                return@launch
            }
            val outcome = downloader.fetchManifest(url)
            mutableState.update { it.copy(isChecking = false) }
            when (outcome) {
                is Outcome.Failure -> messageChannel.send(StorageMessage.CheckFailed(outcome.error))
                is Outcome.Success -> acceptManifest(outcome.value)
            }
        }
    }

    /**
     * Takes note of a manifest that has been read.
     *
     * A format the application cannot read is said as much, with an invitation
     * to update: SPEC §4.4 refuses a failure later, when opening a file.
     */
    private suspend fun acceptManifest(manifest: DataManifest) {
        val supported = supportedFormatVersion() ?: return
        if (manifest.formatVersion != supported) {
            messageChannel.send(
                StorageMessage.UnsupportedFormat(manifest.formatVersion, supported),
            )
            return
        }
        mutableState.update { current -> current.copy(manifest = manifest).withManifestApplied() }
        if (mutableState.value.outdated.isEmpty()) {
            messageChannel.send(StorageMessage.AlreadyUpToDate)
        }
    }

    /**
     * Downloads and installs whatever the manifest announces as new.
     *
     * Sets already up to date are not fetched again: that is the whole point of
     * comparing digests.
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

    /** Cross-checks the installed state against the manifest read. */
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

    /** Deletes an installed set. */
    fun delete(kind: DatasetKind) {
        viewModelScope.launch {
            store.delete(kind)
            messageChannel.send(StorageMessage.Deleted(kind))
        }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val store: DatasetStore,
        private val downloader: DatasetDownloader,
        private val manifestUrl: suspend () -> String?,
        private val workDirectory: File,
        private val supportedFormatVersion: suspend () -> Int?,
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
