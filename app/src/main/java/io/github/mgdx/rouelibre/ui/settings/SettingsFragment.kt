package io.github.mgdx.rouelibre.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import io.github.mgdx.rouelibre.data.AppTheme
import io.github.mgdx.rouelibre.databinding.FragmentSettingsBinding
import io.github.mgdx.rouelibre.ui.about.AboutFragment
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.launch

/**
 * Settings (SPEC §7.6).
 *
 * Written by hand rather than with `androidx.preference`: that library brings
 * its own visual grammar, which the project's design tokens would then have to
 * fight, for a dozen settings that fit on one screen.
 *
 * Every change is saved immediately. There is no "apply" button: a setting one
 * has changed is a setting one wants.
 */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    private val preferences
        get() = container.preferences

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    /** True while a field is being filled by the code, so as not to rewrite it. */
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
        views.openCity.setOnClickListener { show(CityFragment()) }
        views.openStorage.setOnClickListener { show(StorageFragment()) }
        views.openAbout.setOnClickListener { show(AboutFragment()) }

        setUpTheme(views)
        setUpUnits(views)
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
            viewLifecycleOwner.lifecycleScope.launch {
                // Written BEFORE being applied, and in that order. Applying a
                // theme has the activity rebuilt, which cancels this scope: the
                // write, started first and awaited nowhere, never reached the
                // disk, and coming back to this screen showed the old choice
                // ticked under the new theme.
                preferences.setTheme(theme)
                // Applied at once: a theme one chooses must show, not wait for
                // the next launch.
                applyTheme(theme)
            }
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

    /**
     * The units distances are written in (SPEC §7.6, §9).
     *
     * Written immediately, like the theme and for the same reason, and applied
     * without waiting for the next launch: the interface is rebuilt on the new
     * units by `MainActivity`, which watches them. Nothing else moves — the
     * journey, the stations chosen and the minutes announced are all worked out
     * in metres and are not asked again (SPEC §14).
     */
    private fun setUpUnits(views: FragmentSettingsBinding) {
        views.units.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isFilling) return@addOnButtonCheckedListener
            val choice = when (checkedId) {
                R.id.units_metric -> UnitChoice.Metric
                R.id.units_us -> UnitChoice.UnitedStates
                R.id.units_uk -> UnitChoice.UnitedKingdom
                else -> UnitChoice.FollowSystem
            }
            // The trap the theme above documents does not apply here, and it
            // is worth saying why: there the write and the rebuild were two
            // steps of one coroutine, and the second cancelled the first. Here
            // the rebuild is caused by the write having landed — the interface
            // follows the stored value — so nothing can rebuild ahead of it.
            viewLifecycleOwner.lifecycleScope.launch { preferences.setUnits(choice) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.units.collect { choice ->
                    val current = binding ?: return@collect
                    isFilling = true
                    current.units.check(
                        when (choice) {
                            UnitChoice.FollowSystem -> R.id.units_system
                            UnitChoice.Metric -> R.id.units_metric
                            UnitChoice.UnitedStates -> R.id.units_us
                            UnitChoice.UnitedKingdom -> R.id.units_uk
                        },
                    )
                    isFilling = false
                }
            }
        }
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }
}
