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
import io.github.mgdx.rouelibre.core.data.ReconnectionWatch
import io.github.mgdx.rouelibre.core.data.compareWithInstalled
import io.github.mgdx.rouelibre.data.datasets.DatasetDownloader
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import io.github.mgdx.rouelibre.data.datasets.DatasetTransfer
import io.github.mgdx.rouelibre.data.datasets.DownloadProgress
import io.github.mgdx.rouelibre.data.datasets.TransferEvent
import io.github.mgdx.rouelibre.data.datasets.TransferFailure
import io.github.mgdx.rouelibre.data.network.ConnectionCost
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the storage screen.
 *
 * @property kind the set described.
 * @property installed what is installed, or `null`.
 * @property update what the manifest says about it, once consulted.
 * @property publishedSizeBytes the size the manifest announces, shown before
 *   asking for confirmation (SPEC §4.4).
 * @property failure why the last transfer of this set did not arrive, kept on
 *   the row rather than only announced in passing: a snackbar goes away after
 *   a few seconds and left the row saying "Not installed", which is word for
 *   word what a set nobody ever asked for says.
 * @property connectionBack the [failure] was the connection, and it has come
 *   back since: the row offers the resumption instead of repeating that the
 *   device is offline. An offer only — the press stays the user's (SPEC §4.4).
 */
data class DatasetRow(
    val kind: DatasetKind,
    val installed: InstalledDataset?,
    val update: DatasetUpdate? = null,
    val publishedSizeBytes: Long? = null,
    val failure: DataError? = null,
    val connectionBack: Boolean = false,
)

/**
 * The state of the storage screen.
 *
 * @property totalBytes the space occupied, or `null` if nothing is installed.
 * @property isChecking a manifest check is under way.
 * @property manifest the announced release, once checked.
 * @property downloading the transfer in progress, if there is one. It is
 *   `null` until the first byte arrives, which on a connection that has gone
 *   away is never.
 * @property isDownloading a transfer has been asked for and has not ended.
 *   Distinct from [downloading]: the ten seconds a request may take to give up
 *   are part of the transfer, and the screen has to show them as such — a press
 *   answered by nothing at all reads as a press that was lost.
 * @property unmeteredOnly what the setting says about billed connections
 *   (SPEC §7.6).
 * @property isMetered whether the connection in use bills what goes over it.
 * @property heldBackByMetering a transfer is waiting for a connection nobody is
 *   billed for — refused before starting, or stopped in the middle.
 */
data class StorageUiState(
    val datasets: List<DatasetRow> = DatasetKind.entries.map { DatasetRow(it, null) },
    val isImporting: Boolean = false,
    val totalBytes: Long? = null,
    val isChecking: Boolean = false,
    val manifest: DataManifest? = null,
    val downloading: DownloadProgress? = null,
    val isDownloading: Boolean = false,
    val unmeteredOnly: Boolean = true,
    val isMetered: Boolean = false,
    val heldBackByMetering: Boolean = false,
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

    /**
     * The manifest describes another network's data (SPEC §15).
     *
     * Its own message rather than the format's: nothing here asks for an
     * application that can read more, and inviting an update would send the
     * reader after a fault that is not theirs and not ours.
     *
     * @param announced the network the manifest names.
     * @param served the network in service, whose address was asked.
     */
    data class OtherNetwork(val announced: String, val served: String) : StorageMessage

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

    /**
     * Nothing is being downloaded because the connection bills (SPEC §4.4).
     *
     * Never a dead end: whoever reads this is offered the transfer anyway, this
     * once, with what it weighs named in the question.
     *
     * @param pendingBytes what there is to download, announced before anything
     *   starts (SPEC §11.9).
     * @param wasUnderWay the transfer had begun and has just been stopped,
     *   rather than being refused before its first byte.
     */
    data class HeldBackByMetering(val pendingBytes: Long, val wasUnderWay: Boolean) : StorageMessage

    /** The connection no longer bills: what was held back can start again. */
    data object CanResumeOnUnmetered : StorageMessage

    /**
     * The connection a transfer failed for want of has come back: it can start
     * again, from where it stopped, on a press (SPEC §4.4).
     */
    data object CanResumeOnReconnection : StorageMessage
}

/** What the line above the button is saying, if anything. */
enum class TransferLine {
    /** Nothing is under way: the line is not shown. */
    None,

    /** The manifest is being read. */
    Checking,

    /**
     * A transfer has been asked for and nothing has come back yet.
     *
     * The state that was missing. A press on "Download 9.1 MB" with no network
     * showed nothing whatsoever — no line, no bar, no change of state — for as
     * long as the request took to give up, which is ten seconds where the
     * connection is gone rather than refusing.
     */
    Starting,

    /** Bytes are coming down. */
    UnderWay,

    /** Nothing goes out while the connection bills (SPEC §7.6). */
    WaitingForUnmetered,
}

/**
 * What the screen must say about the transfer, read from the state alone.
 *
 * Kept out of the fragment so that the gap between the press and the first byte
 * is something a test can hold.
 */
fun StorageUiState.transferLine(): TransferLine = when {
    isChecking -> TransferLine.Checking
    downloading != null -> TransferLine.UnderWay
    isDownloading -> TransferLine.Starting
    heldBackByMetering -> TransferLine.WaitingForUnmetered
    else -> TransferLine.None
}

/** The same state, with why a set did not arrive written on its row. */
fun StorageUiState.withFailure(kind: DatasetKind, error: DataError): StorageUiState =
    copy(datasets = datasets.map { if (it.kind == kind) it.copy(failure = error) else it })

/** The same state, the failures of the previous attempt forgotten. */
fun StorageUiState.withoutFailures(): StorageUiState =
    copy(datasets = datasets.map { it.copy(failure = null, connectionBack = false) })

/**
 * The same state, with the rows that failed offline told whether the connection
 * has come back since.
 *
 * Only those rows: a set refused by its server is not answered by a connection,
 * and offering it again on that ground would be offering the wrong remedy.
 */
fun StorageUiState.withConnectionBack(back: Boolean): StorageUiState = copy(
    datasets = datasets.map {
        it.copy(connectionBack = back && it.failure == DataError.Offline)
    },
)

/**
 * Why a manifest that has been read may not be acted on, or `null` to act on it.
 *
 * Two things are held against the city in service, and both before a single
 * file is asked for.
 *
 * **The format**, because SPEC §4.4 refuses a failure discovered later, when
 * opening a file.
 *
 * **The network**, because the manifest is fetched from an address the city
 * configuration holds and nothing else proved that what came back describes
 * that city: a host serving city A's address with city B's release would have
 * installed B's map and B's addresses in A's folder, leaving a map of
 * elsewhere and a search that finds nothing, with no hint of the cause. The
 * comparison is the plain one — `tools/build_manifest.py` writes the very
 * `network.id` of the configuration into the field, and a published manifest
 * reads `"network": "nextbike-stirling"` against a configuration reading
 * `"id": "nextbike-stirling"`. Case is ignored all the same: the identifiers
 * are written in lower case throughout, so no two cities differ by their case
 * alone, and refusing a release over a capital letter would be refusing it for
 * a reason that has nothing to do with what it contains.
 *
 * **A manifest naming no network at all is not refused.** The field defaults to
 * the empty string, so an older release published before it was written, or one
 * a reader produced by hand, names nobody rather than naming somebody else —
 * and an installation that works must not stop working over a missing line.
 * What is refused is a manifest that names *another* network.
 *
 * [DataManifest.boundingBox] is left out of this on purpose: the box in the
 * configuration is recomputed from the data on every regeneration
 * (`tools/compute_bbox.py`), so the two legitimately differ by the width of a
 * street, and a box is optional besides — an absent one must refuse nothing.
 * Naming the network is the check that says which city this is; the box says
 * how far it reaches.
 *
 * @param supportedFormatVersion the format this build reads.
 * @param servedNetwork the identifier of the city in service, or `null` when
 *   there is none — there is then nothing to hold the manifest against.
 */
fun DataManifest.refusalFor(supportedFormatVersion: Int, servedNetwork: String?): StorageMessage? =
    when {
        formatVersion != supportedFormatVersion ->
            StorageMessage.UnsupportedFormat(formatVersion, supportedFormatVersion)

        servedNetwork != null &&
            network.isNotEmpty() &&
            !network.equals(servedNetwork, ignoreCase = true) ->
            StorageMessage.OtherNetwork(network, servedNetwork)

        else -> null
    }

/**
 * Whether opening the storage screen is itself the check (SPEC §4.4).
 *
 * It is when the city in service has none of its three sets: the screen then
 * has nothing else to offer than installing them, and a reader sent here by
 * "the map needs its offline tiles" was left before three "Not installed" and
 * a button reading "Check for updates", with no way to guess that an update
 * was the way to a first download. Where anything at all is installed, the
 * check stays a press.
 *
 * **Once per opening, and on the store's own reading.** The screen's state
 * starts with three empty rows before the store has said anything, and
 * deciding on that would check for a city whose sets are all there; the
 * decision is therefore taken on the first inventory the store hands over,
 * and never again — a rotation, a dialogue answered or a set deleted on the
 * screen does not ask a second time. A check already asked for by whoever
 * opened the screen spends it as well.
 *
 * Kept out of the view model so that a test can hold the rule.
 */
class OpeningCheck {

    private var spent = false

    /**
     * Whether this inventory, read as the screen opens, calls for a check.
     *
     * `true` at most once, and only on the first inventory seen.
     */
    fun calledFor(installed: Map<DatasetKind, InstalledDataset>): Boolean {
        if (spent) return false
        spent = true
        return installed.isEmpty()
    }

    /** A check has been asked for: opening the screen has nothing left to ask. */
    fun checkAsked() {
        spent = true
    }
}

/**
 * Drives the installation, updating and deletion of the offline datasets
 * (SPEC §4.4).
 *
 * **The check is never automatic in the background.** It happens on an explicit
 * action, from this screen: a periodic request would draw a usage profile of
 * the application, which constraint C3 rules out. Opening this screen for a city
 * with nothing installed is such an action, and checks at once — see
 * [OpeningCheck].
 *
 * **Nothing goes out on a billed connection** while the setting asks otherwise
 * (SPEC §4.4, §7.6): a transfer is refused before its first byte, or stopped
 * where it stands if the connection starts billing in the middle of it. Both
 * are said out loud, with what the download weighs, and both offer to run it
 * anyway — a setting that could not be overridden would keep somebody in a
 * hotel with no Wi-Fi from installing their city.
 *
 * **The transfer is the application's, not this model's** — see
 * [DatasetTransfer]. Leaving the screen used to cancel it with the model; it
 * now runs on, and a screen opened again in the middle of it shows where it
 * stands. This model starts it, on a press, and shows it; nothing else does.
 */
class StorageViewModel(
    private val store: DatasetStore,
    private val downloader: DatasetDownloader,
    private val transfer: DatasetTransfer,
    private val manifestUrl: suspend () -> String?,
    private val supportedFormatVersion: suspend () -> Int?,
    private val servedNetwork: suspend () -> String?,
    connectionCost: ConnectionCost,
    unmeteredOnly: Flow<Boolean>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        // Read before anything is collected: a press arriving in the first
        // milliseconds of the screen must be answered on the real connection.
        StorageUiState(isMetered = connectionCost.isMetered()),
    )

    /** The screen's current state. */
    val state: StateFlow<StorageUiState> = mutableState.asStateFlow()

    private val messageChannel = Channel<StorageMessage>(Channel.BUFFERED)

    /** The outcomes to announce, once each. */
    val messages: Flow<StorageMessage> = messageChannel.receiveAsFlow()

    /** Whether opening the screen still has a check to ask for. */
    private val openingCheck = OpeningCheck()

    /** Whether a transfer that failed offline can now be offered again. */
    private val reconnection = ReconnectionWatch()

    init {
        viewModelScope.launch {
            unmeteredOnly.collect { only ->
                mutableState.update { it.copy(unmeteredOnly = only) }
                applyBillingRule()
            }
        }
        viewModelScope.launch {
            connectionCost.metered.collect { metered ->
                mutableState.update { it.copy(isMetered = metered) }
                applyBillingRule()
            }
        }
        viewModelScope.launch {
            connectionCost.connected.collect { connected ->
                // The return is said at once, and the transfer is only offered:
                // nothing starts again from the background (SPEC §4.1, §4.4).
                val returned = reconnection.connectionChanged(connected)
                mutableState.update { it.withConnectionBack(reconnection.isReadyToResume) }
                if (returned) messageChannel.send(StorageMessage.CanResumeOnReconnection)
            }
        }
        viewModelScope.launch {
            store.installed.collect { installed ->
                mutableState.update { current ->
                    current.copy(
                        datasets = DatasetKind.entries.map { kind ->
                            val previous = current.datasets.firstOrNull { it.kind == kind }
                            DatasetRow(
                                kind = kind,
                                installed = installed[kind],
                                update = previous?.update,
                                publishedSizeBytes = previous?.publishedSizeBytes,
                                // One set installing is no reason for another
                                // set's failure to leave its row. A set whose
                                // own state has just changed — fetched again,
                                // imported by hand, deleted — has answered what
                                // its failure said, and holding on to it there
                                // would be showing a refusal over a file that
                                // arrived.
                                failure = previous?.failure
                                    ?.takeIf { installed[kind] == previous.installed },
                                connectionBack = previous?.connectionBack == true &&
                                    installed[kind] == previous.installed,
                            )
                        },
                        totalBytes = installed.values
                            .sumOf { it.sizeBytes }
                            .takeIf { it > 0 },
                    ).withManifestApplied()
                }
                if (openingCheck.calledFor(installed)) readManifest(announceMissingCity = false)
            }
        }
        // After the inventory: a failure the transfer met while nobody was
        // looking is written on its row once the row knows what is installed.
        viewModelScope.launch {
            var shownFailure: TransferFailure? = null
            transfer.state.collect { transferState ->
                val failure = transferState.failure
                // By identity: two transfers failing the same way are two
                // failures, and the second must be followed like the first.
                if (failure != null && failure !== shownFailure) {
                    reconnection.transferFailed(failure.error)
                    mutableState.update { it.withFailure(failure.kind, failure.error) }
                }
                shownFailure = failure
                mutableState.update { current ->
                    current.copy(
                        downloading = transferState.progress,
                        isDownloading = transferState.isRunning,
                        heldBackByMetering = transferState.heldBackByMetering,
                        // A screen opened during a transfer has read no manifest
                        // of its own; the one being carried out says what is
                        // still to come and what it weighs.
                        manifest = current.manifest ?: transferState.manifest,
                    ).withManifestApplied()
                }
            }
        }
        viewModelScope.launch {
            transfer.events.collect { event -> messageChannel.send(event.toMessage()) }
        }
    }

    private fun TransferEvent.toMessage(): StorageMessage = when (this) {
        is TransferEvent.Installed -> StorageMessage.Installed(kind)
        is TransferEvent.Rejected -> StorageMessage.Rejected(kind, reason)
        is TransferEvent.Failed -> StorageMessage.DownloadFailed(kind, error)
        is TransferEvent.HeldBack ->
            StorageMessage.HeldBackByMetering(mutableState.value.pendingBytes, wasUnderWay)
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
        readManifest(announceMissingCity = true)
    }

    /**
     * Reads the manifest.
     *
     * @param announceMissingCity whether to say that there is no city to check
     *   for. A press is answered; a check asked by opening the screen is not —
     *   the screen already says no city is chosen, and a message nobody asked
     *   for would only repeat it.
     */
    private fun readManifest(announceMissingCity: Boolean) {
        openingCheck.checkAsked()
        if (mutableState.value.isChecking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isChecking = true) }
            // Without an active city there is no manifest to check: saying so
            // beats querying an address picked at random.
            val url = manifestUrl()
            if (url == null) {
                mutableState.update { it.copy(isChecking = false) }
                if (announceMissingCity) {
                    messageChannel.send(StorageMessage.CheckFailed(DataError.NoCityChosen))
                }
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
     * A refused manifest is announced and goes no further: it is never put in
     * the state, and [startDownload] has nothing to act on — nothing is fetched
     * on the strength of a release this application will not have. See
     * [refusalFor] for what is held against it and why.
     */
    private suspend fun acceptManifest(manifest: DataManifest) {
        val supported = supportedFormatVersion() ?: return
        val refusal = manifest.refusalFor(supported, servedNetwork())
        if (refusal != null) {
            messageChannel.send(refusal)
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
     * comparing digests. Nothing starts on a billed connection while the
     * setting asks otherwise — the screen is told why, and offered [downloadAnyway].
     */
    fun downloadPending() {
        startDownload()
    }

    /**
     * Downloads it anyway, on this connection, this once (SPEC §4.4).
     *
     * The setting is left as it is: what is being agreed to is this transfer,
     * not a rule. Somebody in a hotel with no Wi-Fi must be able to install
     * their city without giving up the protection for good.
     */
    fun downloadAnyway() {
        transfer.exemptOneTransfer()
        startDownload()
    }

    private fun startDownload() {
        val manifest = mutableState.value.manifest ?: return
        if (transfer.state.value.isRunning) return
        val current = mutableState.value
        if (!transfer.mayRun(current.unmeteredOnly, current.isMetered)) {
            transfer.holdBack()
            return
        }
        // Before the first request goes out, and on the pressing thread: the
        // screen has to change on the press itself, whatever the network then
        // does with the ten seconds it has to answer in.
        reconnection.transferStarted()
        mutableState.update {
            it.copy(heldBackByMetering = false, isDownloading = true).withoutFailures()
        }
        transfer.start(manifest, current.outdated.map { it.kind })
    }

    /**
     * Offers the transfer again when the connection or the setting stops
     * holding it back.
     *
     * Stopping a transfer under way is the transfer's own business, since it
     * must happen whether or not this screen is open. What is left here is the
     * other half of the rule: **nothing starts again on its own.** SPEC §4.1
     * refuses background work, so the return of an unbilled connection is
     * announced to whoever is looking at the screen, and the press that resumes
     * the transfer is theirs.
     */
    private fun applyBillingRule() {
        val current = mutableState.value
        val transferState = transfer.state.value
        if (transferState.heldBackByMetering &&
            !transferState.isRunning &&
            transfer.mayRun(current.unmeteredOnly, current.isMetered)
        ) {
            transfer.releaseHoldBack()
            mutableState.update { it.copy(heldBackByMetering = false) }
            messageChannel.trySend(StorageMessage.CanResumeOnUnmetered)
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
        private val transfer: DatasetTransfer,
        private val manifestUrl: suspend () -> String?,
        private val supportedFormatVersion: suspend () -> Int?,
        private val servedNetwork: suspend () -> String?,
        private val connectionCost: ConnectionCost,
        private val unmeteredOnly: Flow<Boolean>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StorageViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return StorageViewModel(
                store,
                downloader,
                transfer,
                manifestUrl,
                supportedFormatVersion,
                servedNetwork,
                connectionCost,
                unmeteredOnly,
            ) as T
        }
    }
}
