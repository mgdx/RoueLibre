package io.github.mgdx.rouelibre.ui.stations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.freshnessOf
import io.github.mgdx.rouelibre.databinding.FragmentStationListBinding
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import io.github.mgdx.rouelibre.ui.toStatusLine
import io.github.mgdx.rouelibre.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Liste des stations et de leur disponibilité.
 *
 * Le rafraîchissement n'a lieu que tant que l'écran est visible, et jamais en
 * arrière-plan : c'est la contrainte C3 du SPEC §2 autant que la politique du
 * §4.1. Une boucle liée au cycle de vie s'arrête d'elle-même quand l'écran
 * passe en arrière-plan.
 */
class StationListFragment : Fragment() {

    private var binding: FragmentStationListBinding? = null

    private val viewModel: StationsViewModel by viewModels {
        StationsViewModel.Factory(
            (requireActivity().application as RoueLibreApplication)
                .container
                .stationRepository,
        )
    }

    private val adapter = StationAdapter { entry ->
        StationDetailSheet.newInstance(entry.station.id)
            .show(parentFragmentManager, StationDetailSheet.TAG)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentStationListBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = requireBinding()

        views.stations.layoutManager = LinearLayoutManager(requireContext())
        views.stations.adapter = adapter
        views.stations.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )
        // Les lignes ne changent jamais de taille : le dire épargne un calcul
        // de mise en page à chaque rafraîchissement, sur 268 stations.
        views.stations.setHasFixedSize(true)

        views.openStorage.setOnClickListener { openStorage() }
        views.swipeRefresh.setOnRefreshListener { viewModel.refresh(force = true) }

        // Filtrage à chaque frappe : quelques centaines d'entrées déjà en
        // mémoire, aucun anti-rebond n'est justifié ici.
        views.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        views.searchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // La liste est déjà filtrée ; il ne reste qu'à rendre l'écran
                // au regard en repliant le clavier.
                view.clearFocus()
                hideKeyboard(view)
                true
            } else {
                false
            }
        }

        views.modeBikes.contentDescription = getString(R.string.mode_bikes_description)
        views.modeDocks.contentDescription = getString(R.string.mode_docks_description)
        views.modeToggle.check(R.id.mode_bikes)
        views.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            adapter.mode = when (checkedId) {
                R.id.mode_docks -> AvailabilityMode.Docks
                else -> AvailabilityMode.Bikes
            }
        }

        observeState()
        observeErrors()
        keepAvailabilityFresh()
    }

    override fun onDestroyView() {
        // Le RecyclerView survit à la vue par son adaptateur ; le détacher
        // évite de retenir la vue détruite.
        binding?.stations?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    val views = binding ?: return@collectLatest
                    adapter.submitList(state.stations)
                    views.swipeRefresh.isRefreshing = state.isRefreshing
                    showEmptyState(state)
                    showFreshness(state.fetchedAt)
                }
            }
        }
    }

    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errors.collect { error ->
                    val views = binding ?: return@collect
                    Snackbar
                        .make(
                            views.root,
                            error.toUserMessage(requireContext()),
                            Snackbar.LENGTH_LONG,
                        )
                        .setAction(R.string.action_retry) { viewModel.refresh(force = true) }
                        .show()
                }
            }
        }
    }

    /**
     * Rafraîchit tant que l'écran est visible, et réécrit l'âge affiché.
     *
     * Le dépôt applique lui-même le délai minimal d'une minute ; la boucle
     * repasse plus souvent pour tenir à jour le « il y a 12 secondes », qui
     * vieillirait sinon à l'écran sans que rien ne le corrige.
     */
    private fun keepAvailabilityFresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refresh()
                    showFreshness(viewModel.state.value.fetchedAt)
                    delay(FRESHNESS_TICK_MILLIS)
                }
            }
        }
    }

    /**
     * Montre, s'il y a lieu, pourquoi la liste ne contient rien.
     *
     * Deux situations, deux gestes : un cache vide se rafraîchit, une
     * recherche infructueuse s'efface. Proposer « Rafraîchir » à quelqu'un qui
     * a fait une faute de frappe l'enverrait chercher une panne inexistante.
     */
    private fun showEmptyState(state: StationsUiState) {
        val views = binding ?: return
        views.emptyState.isVisible = state.emptiness != Emptiness.None
        when (state.emptiness) {
            Emptiness.None -> Unit

            Emptiness.NothingLoaded -> {
                views.emptyTitle.setText(R.string.stations_empty_title)
                views.emptyMessage.setText(R.string.stations_empty_message)
                views.emptyAction.setText(R.string.action_refresh)
                views.emptyAction.setOnClickListener { viewModel.refresh(force = true) }
            }

            Emptiness.NoMatch -> {
                views.emptyTitle.setText(R.string.stations_no_match_title)
                views.emptyMessage.text =
                    getString(R.string.stations_no_match_message, state.query)
                views.emptyAction.setText(R.string.action_clear_search)
                views.emptyAction.setOnClickListener { views.searchInput.text?.clear() }
            }
        }
    }

    /** Ouvre l'écran de gestion des données hors ligne (SPEC §4.4). */
    private fun openStorage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, StorageFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun hideKeyboard(view: View) {
        val manager = requireContext().getSystemService(InputMethodManager::class.java)
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Réécrit la ligne d'âge, obsolescence comprise.
     *
     * Recalculée à chaque battement et non figée dans l'état : sans cela, un
     * écran laissé ouvert continuerait d'annoncer « mis à jour à l'instant »
     * une demi-heure plus tard.
     */
    private fun showFreshness(fetchedAt: Instant?) {
        val views = binding ?: return
        val freshness = freshnessOf(fetchedAt, Instant.now())
        views.freshness.text = freshness.toStatusLine(requireContext(), freshness.isStale)
    }

    private fun requireBinding(): FragmentStationListBinding =
        checkNotNull(binding) { "vue non créée" }

    private companion object {
        /**
         * Cadence de la boucle. Assez courte pour que l'âge affiché reste
         * juste, assez longue pour ne rien coûter : le dépôt, lui, ne
         * contactera le réseau qu'une fois par minute.
         */
        const val FRESHNESS_TICK_MILLIS = 10_000L
    }
}
