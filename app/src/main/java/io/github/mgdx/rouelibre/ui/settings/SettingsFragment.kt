package io.github.mgdx.rouelibre.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.data.AppTheme
import io.github.mgdx.rouelibre.databinding.FragmentSettingsBinding
import io.github.mgdx.rouelibre.databinding.ItemDurationSettingBinding
import io.github.mgdx.rouelibre.ui.about.AboutFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Réglages (SPEC §7.6).
 *
 * Écrit à la main plutôt qu'avec `androidx.preference` : cette bibliothèque
 * apporte sa propre grammaire visuelle, que les jetons de conception du projet
 * devraient ensuite combattre, pour une dizaine de réglages qui tiennent sur
 * un écran.
 *
 * Chaque changement est enregistré immédiatement. Il n'y a pas de bouton
 * « valider » : un réglage qu'on a changé est un réglage qu'on veut.
 */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    private val preferences
        get() = (requireActivity().application as RoueLibreApplication).container.preferences

    private val cityConfiguration
        get() = (requireActivity().application as RoueLibreApplication)
            .container
            .cityConfiguration

    /** Vrai pendant qu'un champ est rempli par le code, pour ne pas le réécrire. */
    private var isFilling = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.openStorage.setOnClickListener { show(StorageFragment()) }
        views.openAbout.setOnClickListener { show(AboutFragment()) }

        setUpTheme(views)
        setUpHandlingTimes(views)
        setUpSources(views)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun setUpTheme(views: FragmentSettingsBinding) {
        views.theme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.theme_light -> AppTheme.Light
                R.id.theme_dark -> AppTheme.Dark
                else -> AppTheme.System
            }
            viewLifecycleOwner.lifecycleScope.launch { preferences.setTheme(theme) }
            // Appliqué tout de suite : un thème qu'on choisit doit se voir, pas
            // attendre le prochain lancement.
            applyTheme(theme)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.theme.collect { theme ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.theme.check(
                        when (theme) {
                            AppTheme.Light -> R.id.theme_light
                            AppTheme.Dark -> R.id.theme_dark
                            AppTheme.System -> R.id.theme_system
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    private fun setUpHandlingTimes(views: FragmentSettingsBinding) {
        views.pickup.label.setText(R.string.settings_pickup)
        views.dropoff.label.setText(R.string.settings_dropoff)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.handlingTimes.collect { times ->
                    showMinutes(binding?.pickup, times.pickupSeconds)
                    showMinutes(binding?.dropoff, times.dropoffSeconds)
                }
            }
        }

        views.pickup.decrease.setOnClickListener { changePickup(-STEP_SECONDS) }
        views.pickup.increase.setOnClickListener { changePickup(+STEP_SECONDS) }
        views.dropoff.decrease.setOnClickListener { changeDropoff(-STEP_SECONDS) }
        views.dropoff.increase.setOnClickListener { changeDropoff(+STEP_SECONDS) }
    }

    private fun showMinutes(row: ItemDurationSettingBinding?, seconds: Int) {
        row?.value?.text = getString(R.string.duration_minutes, seconds / SECONDS_PER_MINUTE)
    }

    private fun changePickup(delta: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val current = preferences.handlingTimes.first()
            preferences.setHandlingTimes(
                current.copy(pickupSeconds = current.pickupSeconds + delta),
            )
        }
    }

    private fun changeDropoff(delta: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val current = preferences.handlingTimes.first()
            preferences.setHandlingTimes(
                current.copy(dropoffSeconds = current.dropoffSeconds + delta),
            )
        }
    }

    /**
     * Les deux adresses de source.
     *
     * Vidées, elles rétablissent celles de la configuration de ville : c'est
     * ce que fait la croix du champ, et l'invite de saisie montre alors la
     * valeur par défaut. L'hébergeur par défaut ne doit jamais être un point
     * de défaillance unique (SPEC §4.4).
     */
    private fun setUpSources(views: FragmentSettingsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            isFilling = true
            views.gbfsUrl.setText(preferences.gbfsDiscoveryUrlOverride().orEmpty())
            views.manifestUrl.setText(preferences.dataManifestUrlOverride().orEmpty())
            isFilling = false
        }
        views.gbfsField.placeholderText = cityConfiguration.gbfs.discoveryUrl
        views.manifestField.placeholderText = cityConfiguration.dataRelease.manifestUrl

        views.gbfsUrl.doAfterTextChanged { text ->
            if (isFilling) return@doAfterTextChanged
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setGbfsDiscoveryUrlOverride(text?.toString())
            }
        }
        views.manifestUrl.doAfterTextChanged { text ->
            if (isFilling) return@doAfterTextChanged
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setDataManifestUrlOverride(text?.toString())
            }
        }
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val STEP_SECONDS = 60
    }
}
