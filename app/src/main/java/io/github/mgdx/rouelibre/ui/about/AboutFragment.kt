package io.github.mgdx.rouelibre.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.data.NEVER_LAUNCHED
import io.github.mgdx.rouelibre.databinding.FragmentAboutBinding
import io.github.mgdx.rouelibre.ui.welcome.WelcomeFragment
import io.github.mgdx.rouelibre.ui.welcome.WhatsNewFragment
import kotlinx.coroutines.launch

/**
 * The "about" screen (SPEC §7.7).
 *
 * Three things are **owed** here: the attributions of §4.5, the privacy policy
 * in plain words, and the application's licence. They are not there out of
 * politeness — the data shown comes from producers who require them, and so do
 * the libraries used.
 *
 * The network's attribution comes from the city configuration: serving another
 * conurbation must not require touching this screen (SPEC §15).
 */
class AboutFragment : Fragment() {

    private var binding: FragmentAboutBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentAboutBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        val container = (requireActivity().application as RoueLibreApplication).container

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        // The attribution is the feed producer's, hence the active city's:
        // without a city there is nobody to credit, and the line is hidden
        // rather than left blank.
        viewLifecycleOwner.lifecycleScope.launch {
            val attribution = container.activeCity()?.gbfs?.attribution
            views.networkAttribution.isVisible = !attribution.isNullOrBlank()
            views.networkAttribution.text = attribution.orEmpty()
        }
        views.openSources.setOnClickListener { show(DataSourcesFragment()) }
        views.openRepository.setOnClickListener { openRepository() }
        views.openLicences.setOnClickListener { show(LicencesFragment()) }
        // Both first-launch screens stay readable afterwards: SPEC §7.9 and
        // §7.10 each require it.
        views.openWelcome.setOnClickListener { show(WelcomeFragment()) }
        views.openWhatsNew.setOnClickListener {
            show(WhatsNewFragment.since(NEVER_LAUNCHED))
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /**
     * Opens the repository in the browser.
     *
     * It is this whole screen's only way out to the network, and it is
     * explicit: nothing leaves without the user having pressed.
     */
    private fun openRepository() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri()))
        } catch (_: ActivityNotFoundException) {
            val views = binding ?: return
            Snackbar.make(views.root, R.string.about_no_browser, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun show(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        const val REPOSITORY_URL = "https://github.com/mgdx/RoueLibre"
    }
}
