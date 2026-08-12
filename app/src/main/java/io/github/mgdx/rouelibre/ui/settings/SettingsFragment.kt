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
     * The two source addresses.
     *
     * Emptied, they restore the city configuration's own: that is what the
     * field's cross does, and the hint then shows the default value. The
     * default host must never be a single point of failure (SPEC §4.4).
     */
    private fun setUpSources(views: FragmentSettingsBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            isFilling = true
            views.gbfsUrl.setText(preferences.gbfsDiscoveryUrlOverride().orEmpty())
            views.manifestUrl.setText(preferences.dataManifestUrlOverride().orEmpty())
            isFilling = false
            // The default addresses are the active city's: without a city
            // there are none, and the field simply stays empty.
            val city = container.activeCity()
            views.gbfsField.placeholderText = city?.gbfs?.discoveryUrl
            views.manifestField.placeholderText = city?.dataRelease?.manifestUrl
        }

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

}
