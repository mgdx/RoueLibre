package io.github.mgdx.rouelibre.ui.storage

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.databinding.FragmentStorageBinding
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.textLocale
import kotlinx.coroutines.launch

/**
 * The storage screen (SPEC §4.4).
 *
 * Lists the three offline datasets with their size and their date, and allows
 * installing or deleting them. The user must always know what the application
 * occupies and be able to reclaim it.
 *
 * Importing goes through the system's document chooser, which requires no
 * storage permission and leaves the user in charge of the file they designate.
 */
class StorageFragment : Fragment() {

    private var binding: FragmentStorageBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    private val viewModel: StorageViewModel by viewModels {
        StorageViewModel.Factory(
            store = container.datasetStore,
            downloader = container.datasetDownloader,
            manifestUrl = { container.dataManifestUrl() },
            workDirectory = container.downloadWorkDirectory,
            supportedFormatVersion = { container.activeCity()?.dataRelease?.formatVersion },
            connectionCost = container.connectionCost,
            unmeteredOnly = container.preferences.downloadOnUnmeteredOnly,
        )
    }

    /** The set awaiting a file, between the press and the chooser's return. */
    private var awaitingImportFor: DatasetKind? = null

    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val kind = awaitingImportFor
        awaitingImportFor = null
        if (uri != null && kind != null) {
            viewModel.import(kind, uri)
        }
    }

    private val adapter = DatasetAdapter(
        onImport = ::requestImport,
        onDelete = ::confirmDelete,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentStorageBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        showServedCity(views)
        views.datasets.layoutManager = LinearLayoutManager(requireContext())
        views.datasets.adapter = adapter
        views.datasets.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        views.checkUpdates.setOnClickListener { onUpdateButtonClicked() }

        // Opened from the welcome screen, this one checks straight away: the
        // user has just pressed the download button, and asking them to confirm
        // it again here would be one more door.
        if (savedInstanceState == null && arguments?.getBoolean(ARGUMENT_CHECK_ON_OPEN) == true) {
            viewModel.checkForUpdates()
        }

        observeState()
        observeMessages()
    }

    /**
     * The same button checks, then downloads.
     *
     * Two separate buttons would force the user to understand the difference
     * before acting. Here the first press asks what is published, and the
     * second — whose label then announces the size — fetches it.
     */
    private fun onUpdateButtonClicked() {
        val state = viewModel.state.value
        if (state.manifest != null && state.outdated.isNotEmpty()) {
            // The old warning belongs to the setting being off: with it on, the
            // model refuses the transfer and offers it anyway, and two words
            // about the same connection would only muddle the screen.
            if (!state.unmeteredOnly) warnIfNotOnWifi()
            viewModel.downloadPending()
        } else {
            viewModel.checkForUpdates()
        }
    }

    /**
     * Warns if we are not on Wi-Fi (SPEC §4.4).
     *
     * A warning, not an obstacle: somebody on a generous data plan does not
     * have to ask their application for permission. Shown only when the setting
     * of §7.6 is off, which is exactly the case it was written for — with the
     * setting on, a billed connection is answered by a question rather than by
     * a remark in passing.
     */
    private fun warnIfNotOnWifi() {
        val manager = requireContext().getSystemService(ConnectivityManager::class.java) ?: return
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (onWifi) return
        showMessage(getString(R.string.storage_wifi_warning))
    }

    /**
     * Says what has just happened, above the button rather than over it.
     *
     * The screen ends on a button pinned to the bottom edge, which is exactly
     * where a snackbar rises: left to itself it covered the label — "Download
     * 967 kB" read through "Base map deleted" — and neither could be read. The
     * bar is therefore anchored to the button, which pushes it up by its
     * height.
     *
     * **This screen's messages need more than the two lines a snackbar gives.**
     * They are the only ones carrying a dataset's name in front of them —
     * "Routing data: …" — and what follows has to say both what failed and what
     * happens next, which measured 188 px of a full two lines at sixty-four
     * characters on a 1080-wide screen. A third line was therefore missing
     * before the sentence was, and a fourth is the margin somebody reading at
     * twice the text size needs. Not more: past four a snackbar stops being a
     * remark in passing and becomes a wall over the screen it comments on.
     */
    private fun showMessage(message: CharSequence) {
        val views = binding ?: return
        val bar = Snackbar.make(views.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(views.checkUpdates)
        bar.view
            .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            .maxLines = MESSAGE_LINES
        bar.show()
    }

    override fun onDestroyView() {
        binding?.datasets?.adapter = null
        binding = null
        super.onDestroyView()
    }

    /**
     * Asks for confirmation before erasing an installed set (SPEC §4.4).
     *
     * The same question the city screen asks before erasing a whole city, for
     * the same reason: up to a hundred megabytes that only the network can put
     * back, behind a button that sits one press away in a list one scrolls. The
     * size is named in the question — what is at stake here is the download it
     * would take to undo, and that is what the figure says.
     */
    private fun confirmDelete(kind: DatasetKind) {
        val installed = viewModel.state.value.datasets
            .firstOrNull { it.kind == kind }
            ?.installed
            ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dataset_delete_title)
            .setMessage(
                getString(
                    R.string.dataset_delete_body,
                    getString(kind.nameResource()),
                    formatBytes(requireContext(), installed.sizeBytes),
                ),
            )
            .setPositiveButton(R.string.dataset_delete) { _, _ -> viewModel.delete(kind) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Says why nothing is coming down, and offers to download anyway
     * (SPEC §4.4).
     *
     * **Never a dead end**: somebody in a hotel with no Wi-Fi must be able to
     * install their city. The question names what the transfer weighs — that is
     * what is at stake on a connection billed by the megabyte — and tells what
     * to do about it rather than stating a fact. Answering yes covers this
     * transfer only: the setting is not touched, and the next download asks
     * again.
     */
    private fun offerToDownloadAnyway(held: StorageMessage.HeldBackByMetering) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (held.wasUnderWay) {
                    R.string.download_stopped_title
                } else {
                    R.string.download_held_back_title
                },
            )
            .setMessage(
                getString(
                    if (held.wasUnderWay) {
                        R.string.download_stopped_body
                    } else {
                        R.string.download_held_back_body
                    },
                    formatBytes(requireContext(), held.pendingBytes),
                ),
            )
            .setPositiveButton(R.string.download_anyway) { _, _ -> viewModel.downloadAnyway() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /**
     * Opens the document chooser.
     *
     * The MIME type stays generic: neither MBTiles, nor rd5, nor SQLite has a
     * recognised one, and filtering on the extension would hide legitimate
     * files instead of helping. Validation happens on import.
     */
    private fun requestImport(kind: DatasetKind) {
        awaitingImportFor = kind
        pickDocument.launch(arrayOf("*/*"))
    }

    companion object {
        private const val ARGUMENT_CHECK_ON_OPEN = "check-on-open"

        /** What [showMessage] lets a snackbar grow to, and no further. */
        private const val MESSAGE_LINES = 4

        /**
         * Opens the screen and checks the manifest immediately (SPEC §7.9).
         *
         * Reserved for the sequence coming from the welcome screen: elsewhere,
         * the check stays triggered by a press.
         */
        fun checkingForUpdates(): StorageFragment = StorageFragment().apply {
            arguments = Bundle().apply { putBoolean(ARGUMENT_CHECK_ON_OPEN, true) }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val views = binding ?: return@collect
                    adapter.submitList(state.datasets)
                    views.importing.isVisible = state.isImporting || state.isChecking
                    showDownload(state)
                    views.checkUpdates.setText(
                        if (state.manifest != null && state.outdated.isNotEmpty()) {
                            R.string.storage_download_pending
                        } else {
                            R.string.storage_check_updates
                        },
                    )
                    if (state.manifest != null && state.outdated.isNotEmpty()) {
                        views.checkUpdates.text = getString(
                            R.string.storage_download_pending,
                            formatBytes(requireContext(), state.pendingBytes),
                        )
                    }
                    views.checkUpdates.isEnabled =
                        !state.isChecking &&
                        state.downloading == null
                    views.storageTotal.text = state.totalBytes
                        ?.let {
                            getString(
                                R.string.storage_total,
                                formatBytes(requireContext(), it),
                            )
                        }
                        ?: getString(R.string.storage_nothing_installed)
                }
            }
        }
    }

    /**
     * Names the city whose data this screen manages.
     *
     * The sets are stored per city: without this subtitle, "42.5 MB occupied"
     * would suggest that is all the application occupies, when other cities may
     * occupy as much beside it (SPEC §11.9).
     */
    private fun showServedCity(views: FragmentStorageBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            views.toolbar.subtitle = container.activeCity()?.network?.let {
                requireContext().cityLabel(it.displayName, it.city)
            }
                ?: getString(R.string.storage_no_city)
        }
    }

    /** Shows what the transfer in progress has already received. */
    private fun showDownload(state: StorageUiState) {
        val views = binding ?: return
        val progress = state.downloading
        views.downloadState.isVisible =
            progress != null ||
            state.isChecking ||
            state.heldBackByMetering
        views.downloadProgress.isVisible = progress != null
        if (state.isChecking) {
            // A check lasts only a moment, but it goes over the network:
            // saying so avoids the impression that the press was lost.
            views.downloadState.setText(R.string.storage_checking)
        } else if (progress == null && state.heldBackByMetering) {
            // The line stays for as long as the wait does: a bar that has gone
            // away leaves a screen that looks idle for no stated reason.
            views.downloadState.text = getString(
                R.string.download_waiting_for_unmetered,
                formatBytes(requireContext(), state.pendingBytes),
            )
        }
        if (progress == null) return

        val locale = requireContext().textLocale()
        views.downloadState.text = getString(
            R.string.storage_downloading,
            progress.fileName,
            formatBytes(requireContext(), progress.downloadedBytes),
            formatBytes(requireContext(), progress.totalBytes),
        )
        views.downloadProgress.isIndeterminate = progress.totalBytes <= 0
        if (progress.totalBytes > 0) {
            views.downloadProgress.setProgressCompat(
                ((progress.downloadedBytes * 100) / progress.totalBytes).toInt(),
                true,
            )
        }
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { message ->
                    // A refusal asks a question — it is the one message here
                    // that expects an answer, and a bar that goes away by
                    // itself would take the way out with it.
                    if (message is StorageMessage.HeldBackByMetering) {
                        offerToDownloadAnyway(message)
                    } else {
                        showMessage(message.toText(requireContext()))
                    }
                }
            }
        }
    }
}
