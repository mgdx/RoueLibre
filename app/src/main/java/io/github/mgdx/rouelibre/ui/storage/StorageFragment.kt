package io.github.mgdx.rouelibre.ui.storage

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
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
        onDelete = { viewModel.delete(it) },
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
            warnIfNotOnWifi()
            viewModel.downloadPending()
        } else {
            viewModel.checkForUpdates()
        }
    }

    /**
     * Warns if we are not on Wi-Fi (SPEC §4.4).
     *
     * A warning, not an obstacle: somebody on a generous data plan does not
     * have to ask their application for permission.
     */
    private fun warnIfNotOnWifi() {
        val manager = requireContext().getSystemService(ConnectivityManager::class.java) ?: return
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (onWifi) return
        val views = binding ?: return
        Snackbar.make(views.root, R.string.storage_wifi_warning, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        binding?.datasets?.adapter = null
        binding = null
        super.onDestroyView()
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
        views.downloadState.isVisible = progress != null || state.isChecking
        views.downloadProgress.isVisible = progress != null
        if (state.isChecking) {
            // A check lasts only a moment, but it goes over the network:
            // saying so avoids the impression that the press was lost.
            views.downloadState.setText(R.string.storage_checking)
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
                    val views = binding ?: return@collect
                    Snackbar
                        .make(views.root, message.toText(requireContext()), Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}
