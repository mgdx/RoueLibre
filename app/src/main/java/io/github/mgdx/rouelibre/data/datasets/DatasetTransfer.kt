package io.github.mgdx.rouelibre.data.datasets

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.data.DataManifest
import io.github.mgdx.rouelibre.core.data.DatasetImportResult
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.data.DatasetRejection
import io.github.mgdx.rouelibre.core.data.ManifestDataset
import io.github.mgdx.rouelibre.core.data.MeteredTransferGate
import io.github.mgdx.rouelibre.data.network.ConnectionCost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Why the last transfer stopped short of a set.
 *
 * @property kind the set that did not arrive.
 * @property error what the connection or the server answered.
 */
data class TransferFailure(val kind: DatasetKind, val error: DataError)

/**
 * Where the application's dataset transfer stands.
 *
 * @property isRunning a transfer has been asked for and has not ended.
 * @property progress the file coming down, once its first byte has arrived.
 * @property manifest the release the transfer, running or last run, carries
 *   out: a storage screen opened in the middle of it reads the sizes and the
 *   outcome against the very manifest that was pressed on.
 * @property failure why the last transfer stopped, until another one starts.
 * @property heldBackByMetering the transfer is waiting for a connection nobody
 *   is billed for — refused before starting, or stopped in the middle.
 */
data class TransferState(
    val isRunning: Boolean = false,
    val progress: DownloadProgress? = null,
    val manifest: DataManifest? = null,
    val failure: TransferFailure? = null,
    val heldBackByMetering: Boolean = false,
)

/** What a transfer has to announce to whoever is looking, once each. */
sealed interface TransferEvent {
    /** A set has been received, verified and put in place. */
    data class Installed(val kind: DatasetKind) : TransferEvent

    /** A set was received and refused, for the reason given. */
    data class Rejected(val kind: DatasetKind, val reason: DatasetRejection) : TransferEvent

    /** A set did not arrive; the sets after it were not attempted. */
    data class Failed(val kind: DatasetKind, val error: DataError) : TransferEvent

    /**
     * Nothing is coming down because the connection bills (SPEC §4.4).
     *
     * @param wasUnderWay the transfer had begun and has just been stopped,
     *   rather than being refused before its first byte.
     */
    data class HeldBack(val wasUnderWay: Boolean) : TransferEvent
}

/**
 * Carries out the dataset transfer the storage screen asks for (SPEC §4.4).
 *
 * **It belongs to the application, not to the screen.** The transfer used to
 * run in the storage screen's own scope, so pressing Back a second after
 * "Download 7.6 MB" cancelled it without a word: the partial file stayed where
 * it stopped, and the city just chosen became the one in service with no map,
 * no journeys and no addresses. A transfer started by a press and carried on
 * after the screen has gone is not a resumption from the background — the press
 * was the user's, and nothing here ever starts without one. The screen remains
 * the only place that shows the transfer and the only one that starts it.
 *
 * **The billing rule goes with the transfer.** A Wi-Fi lost for a mobile plan
 * while nobody is looking at the storage screen must stop the transfer just the
 * same, so the watch on billing runs for as long as the transfer does, here,
 * rather than in the screen. The exemption granted by "Download anyway" is held
 * here for the same reason, and spent when the transfer ends.
 *
 * **It lives as long as the process does**, and no longer: a process killed in
 * the background takes the transfer with it, and the partial file left behind
 * is resumed from its offset on the next press, as after any interruption.
 *
 * @param scope the application's scope, which no screen cancels. Its
 *   dispatcher is the main one, so that the state and the gate are only ever
 *   touched from one thread.
 * @param download fetches one set into a working directory — see
 *   [DatasetDownloader.download].
 * @param install puts the files received in place — see [DatasetStore.install].
 * @param workDirectory where to drop what is being downloaded, one directory
 *   per set.
 * @param connectionCost what the connection in use bills.
 * @param unmeteredOnly what the setting says about billed connections
 *   (SPEC §7.6).
 */
class DatasetTransfer(
    private val scope: CoroutineScope,
    private val download: suspend (
        dataset: ManifestDataset,
        directory: File,
        onProgress: (DownloadProgress) -> Unit,
    ) -> Outcome<List<File>>,
    private val install: suspend (
        kind: DatasetKind,
        files: List<File>,
        fingerprint: String,
    ) -> DatasetImportResult,
    private val workDirectory: File,
    private val connectionCost: ConnectionCost,
    private val unmeteredOnly: Flow<Boolean>,
) {

    private val mutableState = MutableStateFlow(TransferState())

    /** Where the transfer stands, for whichever screen is looking. */
    val state: StateFlow<TransferState> = mutableState.asStateFlow()

    // Not replayed: an outcome nobody was there to hear is carried by [state]
    // and by the store's inventory, which is what a screen opened later reads.
    private val mutableEvents = MutableSharedFlow<TransferEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** The outcomes to announce, to the screens listening when they happen. */
    val events: SharedFlow<TransferEvent> = mutableEvents.asSharedFlow()

    /** The rule on billed connections, and the exemption that lifts it once. */
    private val gate = MeteredTransferGate()

    private var job: Job? = null

    /**
     * Whether a transfer may run right now.
     *
     * @param unmeteredOnly what the setting says (SPEC §7.6).
     * @param metered whether the connection in use bills what goes over it.
     */
    fun mayRun(unmeteredOnly: Boolean, metered: Boolean): Boolean =
        gate.mayRun(unmeteredOnly, metered)

    /** Lets the next transfer run whatever the connection bills, this once. */
    fun exemptOneTransfer() {
        gate.exemptOneTransfer()
    }

    /** Refuses the transfer before its first byte, and says so. */
    fun holdBack() {
        mutableState.update { it.copy(heldBackByMetering = true, progress = null) }
        mutableEvents.tryEmit(TransferEvent.HeldBack(wasUnderWay = false))
    }

    /**
     * The connection no longer bills: the transfer held back is no longer
     * waiting for anything but a press.
     */
    fun releaseHoldBack() {
        mutableState.update { it.copy(heldBackByMetering = false) }
    }

    /**
     * Downloads and installs the [kinds] the [manifest] announces, in order.
     *
     * Only ever called on a press. Does nothing while a transfer is running.
     * The sets after one that fails are not attempted: a connection that has
     * just failed would fail them too, ten seconds at a time.
     */
    fun start(manifest: DataManifest, kinds: List<DatasetKind>) {
        if (job?.isActive == true) return
        mutableState.value = TransferState(isRunning = true, manifest = manifest)
        job = scope.launch {
            val transfer = coroutineContext.job
            val billing = launch { stopWhenBilled(transfer) }
            try {
                for (kind in kinds) {
                    val dataset = manifest.datasetFor(kind) ?: continue
                    if (!transferOne(dataset)) return@launch
                }
            } finally {
                billing.cancel()
                // Also on the way out of a cancellation, which is how a
                // connection that starts billing ends a transfer: the exemption
                // is spent with the transfer that carried it, so the next one
                // asks again.
                gate.transferEnded()
                mutableState.update { it.copy(isRunning = false, progress = null) }
            }
        }
    }

    /** Fetches and installs one set, and answers whether to go on to the next. */
    private suspend fun transferOne(dataset: ManifestDataset): Boolean {
        val kind = dataset.kind
        val outcome = download(dataset, File(workDirectory, kind.id)) { progress ->
            mutableState.update { it.copy(progress = progress) }
        }
        return when (outcome) {
            is Outcome.Failure -> {
                mutableState.update { it.copy(failure = TransferFailure(kind, outcome.error)) }
                mutableEvents.emit(TransferEvent.Failed(kind, outcome.error))
                false
            }

            is Outcome.Success -> {
                val installed = install(kind, outcome.value, dataset.fingerprint)
                mutableEvents.emit(
                    when (installed) {
                        is DatasetImportResult.Installed -> TransferEvent.Installed(kind)
                        is DatasetImportResult.Rejected ->
                            TransferEvent.Rejected(kind, installed.reason)
                    },
                )
                true
            }
        }
    }

    /**
     * Stops [transfer] where it stands the moment the connection starts billing.
     *
     * A gigabyte that carries on in silence over a mobile plan is precisely what
     * the setting promises to avoid; what has arrived stays on disk, and the
     * next attempt asks the server for the rest.
     */
    private suspend fun stopWhenBilled(transfer: Job) {
        combine(unmeteredOnly, connectionCost.metered) { only, metered ->
            gate.mayRun(only, metered)
        }.first { allowed -> !allowed }
        mutableState.update { it.copy(heldBackByMetering = true, progress = null) }
        mutableEvents.emit(TransferEvent.HeldBack(wasUnderWay = true))
        transfer.cancel()
    }

    /**
     * Abandons whatever transfer there is, because the city in service is
     * changing.
     *
     * The store installs into the directory of the city in service at the
     * moment a set arrives: a transfer left to run past the change would put
     * one city's map into another's folder. Joined, so that nothing of it is
     * still writing when the new city is put into service. What has already
     * arrived stays in the working directory, as after any interruption.
     */
    suspend fun abandon() {
        job?.cancelAndJoin()
        job = null
        mutableState.value = TransferState()
    }

    private companion object {
        /** Room for every announcement a transfer of three sets can make. */
        const val EVENT_BUFFER = 8
    }
}
