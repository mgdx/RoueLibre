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
import io.github.mgdx.rouelibre.ui.textLocale
import kotlinx.coroutines.launch

/**
 * Écran « stockage » (SPEC §4.4).
 *
 * Liste les trois jeux de données hors ligne avec leur taille et leur date, et
 * permet de les installer ou de les supprimer. L'utilisateur doit toujours
 * savoir ce que l'application occupe et pouvoir le reprendre.
 *
 * L'import se fait par le sélecteur de documents du système, ce qui ne demande
 * aucune permission de stockage et laisse l'utilisateur maître du fichier
 * qu'il désigne.
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

    /** Le jeu dont on attend un fichier, entre l'appui et le retour du sélecteur. */
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

        // Ouvert depuis l'accueil, l'écran consulte d'emblée : l'utilisateur
        // vient d'appuyer sur « Télécharger les données », lui redemander de
        // le confirmer ici serait une porte de plus.
        if (savedInstanceState == null && arguments?.getBoolean(ARGUMENT_CHECK_ON_OPEN) == true) {
            viewModel.checkForUpdates()
        }

        observeState()
        observeMessages()
    }

    /**
     * Le même bouton consulte puis télécharge.
     *
     * Deux boutons distincts obligeraient à comprendre la différence avant
     * d'agir. Ici, la première pression demande ce qui est publié, la seconde
     * — dont le libellé annonce alors la taille — le prend.
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
     * Avertit si l'on n'est pas en Wi-Fi (SPEC §4.4).
     *
     * Un avertissement, pas un obstacle : quelqu'un qui a un forfait généreux
     * n'a pas à demander la permission à son application.
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
     * Ouvre le sélecteur de documents.
     *
     * Le type MIME reste générique : ni MBTiles, ni rd5, ni SQLite n'en ont un
     * qui soit reconnu, et restreindre sur l'extension masquerait les fichiers
     * légitimes au lieu d'aider. La validation a lieu à l'import.
     */
    private fun requestImport(kind: DatasetKind) {
        awaitingImportFor = kind
        pickDocument.launch(arrayOf("*/*"))
    }

    companion object {
        private const val ARGUMENT_CHECK_ON_OPEN = "consulter-a-l-ouverture"

        /**
         * Ouvre l'écran en consultant aussitôt le manifeste (SPEC §7.9).
         *
         * Réservé à l'enchaînement depuis l'écran d'accueil : ailleurs, la
         * consultation reste déclenchée par un appui.
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
                            formatBytes(state.pendingBytes, requireContext().textLocale()),
                        )
                    }
                    views.checkUpdates.isEnabled =
                        !state.isChecking &&
                        state.downloading == null
                    views.storageTotal.text = state.totalBytes
                        ?.let {
                            getString(
                                R.string.storage_total,
                                formatBytes(it, requireContext().textLocale()),
                            )
                        }
                        ?: getString(R.string.storage_nothing_installed)
                }
            }
        }
    }

    /**
     * Nomme la ville dont cet écran gère les données.
     *
     * Les jeux sont rangés par ville : sans ce sous-titre, « 42,5 Mo occupés »
     * laisserait croire que c'est tout ce que l'application occupe, alors que
     * d'autres villes peuvent en occuper autant à côté (SPEC §11.9).
     */
    private fun showServedCity(views: FragmentStorageBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            views.toolbar.subtitle = container.activeCity()?.network?.displayName
                ?: getString(R.string.storage_no_city)
        }
    }

    /** Montre ce que le transfert en cours a déjà reçu. */
    private fun showDownload(state: StorageUiState) {
        val views = binding ?: return
        val progress = state.downloading
        views.downloadState.isVisible = progress != null || state.isChecking
        views.downloadProgress.isVisible = progress != null
        if (state.isChecking) {
            // Une consultation ne dure qu'un instant, mais elle passe par le
            // réseau : le dire évite de croire que l'appui s'est perdu.
            views.downloadState.setText(R.string.storage_checking)
        }
        if (progress == null) return

        val locale = requireContext().textLocale()
        views.downloadState.text = getString(
            R.string.storage_downloading,
            progress.fileName,
            formatBytes(progress.downloadedBytes, locale),
            formatBytes(progress.totalBytes, locale),
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
