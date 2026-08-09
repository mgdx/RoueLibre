package io.github.mgdx.rouelibre.ui.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.databinding.FragmentAddressSearchBinding
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Recherche d'une adresse dans l'index hors ligne (SPEC §4.3, §7.3).
 *
 * L'écran rend l'adresse choisie à celui qui l'a ouvert, plutôt que d'agir
 * lui-même : la carte y centre son point, et la recherche d'itinéraire y
 * prendra son départ ou son arrivée.
 *
 * **Aucun appel réseau n'a lieu ici, y compris pendant la frappe.** C'est la
 * donnée la plus sensible de l'application : ce que quelqu'un cherche dit où
 * il va.
 */
class AddressSearchFragment : Fragment() {

    private var binding: FragmentAddressSearchBinding? = null

    private val viewModel: AddressSearchViewModel by viewModels {
        AddressSearchViewModel.Factory(
            (requireActivity().application as RoueLibreApplication).container.addressIndex,
            origin = readOrigin(),
        )
    }

    private val adapter = AddressAdapter(::pick)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentAddressSearchBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)

        views.results.layoutManager = LinearLayoutManager(requireContext())
        views.results.adapter = adapter
        views.results.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        views.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        views.searchInput.setOnEditorActionListener { field, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // La liste se met à jour d'elle-même ; valider ne fait que
                // rendre l'écran au regard en repliant le clavier.
                field.clearFocus()
                hideKeyboard(field)
                true
            } else {
                false
            }
        }

        // Le clavier s'ouvre avec l'écran : on n'arrive ici que pour taper.
        if (savedInstanceState == null) {
            views.searchInput.requestFocus()
            views.searchInput.post { insetsController(views.searchInput).show(ime()) }
        }

        observeState()
    }

    override fun onDestroyView() {
        binding?.results?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    val views = binding ?: return@collectLatest
                    adapter.submitList(state.results)
                    views.results.isVisible = state.results.isNotEmpty()
                    showEmptyState(state)
                }
            }
        }
    }

    /**
     * Montre ce qu'il y a à dire quand la liste est vide.
     *
     * Quatre situations, quatre gestes différents : installer l'index, effacer
     * une saisie infructueuse, taper quelque chose, ou réimporter un fichier
     * illisible. Les confondre enverrait chercher une panne inexistante.
     */
    private fun showEmptyState(state: AddressSearchUiState) {
        val views = binding ?: return
        views.emptyState.isVisible = state.results.isEmpty()
        if (state.results.isNotEmpty()) return
        // Pendant une recherche, le message précédent reste : le remplacer par
        // « Où vas-tu ? » à chaque frappe ferait clignoter l'écran entre deux
        // saisies qui, elles, se suivent sans à-coup.
        if (state.isSearching && state.query.isNotBlank()) return
        views.emptyAction.isVisible = false

        when {
            !state.isIndexInstalled -> {
                views.emptyTitle.setText(R.string.address_needs_index_title)
                views.emptyMessage.setText(R.string.address_needs_index_message)
                views.emptyAction.setText(R.string.storage_open)
                views.emptyAction.isVisible = true
                views.emptyAction.setOnClickListener { openStorage() }
            }

            state.error != null -> {
                views.emptyTitle.setText(R.string.address_unreadable_title)
                views.emptyMessage.text = state.error.toUserMessage(requireContext())
                views.emptyAction.setText(R.string.storage_open)
                views.emptyAction.isVisible = true
                views.emptyAction.setOnClickListener { openStorage() }
            }

            state.hasNoMatch -> {
                views.emptyTitle.setText(R.string.address_no_match_title)
                views.emptyMessage.text =
                    getString(R.string.address_no_match_message, state.query)
            }

            else -> {
                views.emptyTitle.setText(R.string.address_search_prompt_title)
                views.emptyMessage.setText(R.string.address_search_prompt_message)
            }
        }
    }

    /** Rend l'adresse choisie à l'écran qui a ouvert celui-ci. */
    private fun pick(result: AddressResult) {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putDouble(RESULT_LATITUDE, result.position.latitude)
                putDouble(RESULT_LONGITUDE, result.position.longitude)
                putString(RESULT_LABEL, result.toTitle(requireContext()))
            },
        )
        binding?.searchInput?.let(::hideKeyboard)
        parentFragmentManager.popBackStack()
    }

    private fun openStorage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, StorageFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun hideKeyboard(view: View) {
        insetsController(view).hide(ime())
    }

    /**
     * De quoi ouvrir et replier le clavier.
     *
     * Passe par la fenêtre plutôt que par la vue : le raccourci qui ne prend
     * qu'une vue est déprécié, et lui seul sait retrouver la fenêtre à coup
     * sûr.
     */
    private fun insetsController(view: View): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(requireActivity().window, view)

    private fun ime(): Int = WindowInsetsCompat.Type.ime()

    /** Le point de référence du classement, s'il en a été fourni un. */
    private fun readOrigin(): Coordinates? {
        val arguments = arguments ?: return null
        if (!arguments.containsKey(ARGUMENT_ORIGIN_LATITUDE)) return null
        return Coordinates(
            latitude = arguments.getDouble(ARGUMENT_ORIGIN_LATITUDE),
            longitude = arguments.getDouble(ARGUMENT_ORIGIN_LONGITUDE),
        )
    }

    companion object {
        /** Clé sous laquelle l'adresse choisie est rendue. */
        const val REQUEST_KEY = "adresse-choisie"

        /** Latitude du point choisi, en degrés décimaux. */
        const val RESULT_LATITUDE = "latitude"

        /** Longitude du point choisi, en degrés décimaux. */
        const val RESULT_LONGITUDE = "longitude"

        /** Libellé à afficher pour ce point, déjà mis en mots. */
        const val RESULT_LABEL = "libelle"

        private const val ARGUMENT_ORIGIN_LATITUDE = "origine-latitude"
        private const val ARGUMENT_ORIGIN_LONGITUDE = "origine-longitude"

        /**
         * Ouvre la recherche.
         *
         * @param origin point de référence pour classer les résultats par
         *   proximité — le centre de la carte, faute de position connue. Aucune
         *   permission n'est demandée pour l'obtenir (SPEC §10).
         */
        fun newInstance(origin: Coordinates?): AddressSearchFragment =
            AddressSearchFragment().apply {
                arguments = origin?.let {
                    Bundle().apply {
                        putDouble(ARGUMENT_ORIGIN_LATITUDE, it.latitude)
                        putDouble(ARGUMENT_ORIGIN_LONGITUDE, it.longitude)
                    }
                }
            }
    }
}
