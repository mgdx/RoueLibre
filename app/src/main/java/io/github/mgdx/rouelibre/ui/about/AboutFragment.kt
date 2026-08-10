package io.github.mgdx.rouelibre.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentAboutBinding

/**
 * « À propos » (SPEC §7.7).
 *
 * Trois choses y sont **dues** : les attributions du §4.5, la politique de
 * confidentialité en clair, et la licence de l'application. Elles ne sont pas
 * là par politesse — les données affichées viennent de producteurs qui
 * l'exigent, et les bibliothèques employées aussi.
 *
 * L'attribution du réseau vient de la configuration de ville : servir une
 * autre agglomération ne doit pas demander de toucher à cet écran (SPEC §15).
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
        views.networkAttribution.text = container.cityConfiguration.gbfs.attribution
        views.openRepository.setOnClickListener { openRepository() }
        views.openLicences.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, LicencesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /**
     * Ouvre le dépôt dans le navigateur.
     *
     * C'est la seule sortie vers le réseau de tout cet écran, et elle est
     * explicite : rien ne part sans que l'utilisateur ait appuyé.
     */
    private fun openRepository() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri()))
        } catch (_: ActivityNotFoundException) {
            val views = binding ?: return
            Snackbar.make(views.root, R.string.about_no_browser, Snackbar.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REPOSITORY_URL = "https://github.com/mgdx/RoueLibre"
    }
}
