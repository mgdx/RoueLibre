package io.github.mgdx.rouelibre.ui.storage

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

    private val viewModel: StorageViewModel by viewModels {
        StorageViewModel.Factory(
            (requireActivity().application as RoueLibreApplication).container.datasetStore,
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
        views.datasets.layoutManager = LinearLayoutManager(requireContext())
        views.datasets.adapter = adapter
        views.datasets.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        observeState()
        observeMessages()
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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val views = binding ?: return@collect
                    adapter.submitList(state.datasets)
                    views.importing.isVisible = state.isImporting
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
