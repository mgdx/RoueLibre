package io.github.mgdx.rouelibre.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentWelcomeBinding
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.map.MapFragment
import kotlinx.coroutines.launch

/**
 * Écran d'accueil au tout premier démarrage (SPEC §7.9).
 *
 * Un écran et non une boîte de dialogue : le contenu est trop dense pour une
 * fenêtre modale, et il doit pouvoir être relu depuis « À propos ».
 *
 * Trois pages au maximum, chacune contournable, et la dernière enchaîne
 * directement sur le téléchargement des données — une seule séquence, pas deux
 * murs de texte successifs.
 *
 * Le ton est celui du §7 : phrases courtes, voix active, aucun jargon. On
 * explique un fonctionnement, on ne vend rien.
 */
class WelcomeFragment : Fragment() {

    private var binding: FragmentWelcomeBinding? = null

    private var page = 0

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentWelcomeBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        page = savedInstanceState?.getInt(STATE_PAGE) ?: 0
        val views = checkNotNull(binding)

        views.next.setOnClickListener { onNext() }
        views.skip.setOnClickListener { onSkip() }
        showPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun showPage() {
        val views = binding ?: return
        val current = PAGES[page]
        views.title.setText(current.title)
        views.body.setText(current.body)
        views.step.text = getString(R.string.welcome_step, page + 1, PAGES.size)
        views.next.setText(current.next)
        views.skip.setText(current.skip)
        // La toute dernière page propose le téléchargement ; les précédentes
        // n'ont rien à reporter, seulement à être passées.
        views.skip.isVisible = true
        views.bodyContainer.scrollTo(0, 0)
    }

    private fun onNext() {
        if (page < PAGES.lastIndex) {
            page++
            showPage()
            return
        }
        // Dernière page : on choisit d'abord la ville, puisque c'est elle qui
        // détermine les données à chercher. L'écran suivant enchaîne dessus.
        finish(CityFragment())
    }

    private fun onSkip() {
        if (page < PAGES.lastIndex) {
            page = PAGES.lastIndex
            showPage()
            return
        }
        // « Plus tard » : l'application reste utilisable en mode dégradé, avec
        // la liste des stations et leurs disponibilités (SPEC §4.4).
        finish(MapFragment())
    }

    /**
     * Referme l'accueil et retient qu'il a été vu.
     *
     * La version est retenue ici, et non au lancement : quelqu'un qui quitte
     * l'application au milieu de la séquence doit la revoir.
     */
    private fun finish(next: Fragment) {
        viewLifecycleOwner.lifecycleScope.launch {
            container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.content, next)
                .commit()
        }
    }

    /** Une page de l'accueil. */
    private data class Page(val title: Int, val body: Int, val next: Int, val skip: Int)

    private companion object {
        const val STATE_PAGE = "page"

        /**
         * Les trois pages, dans l'ordre : ce qu'est l'application, ce qu'elle
         * ne fait pas de vos données, ce dont elle a besoin pour fonctionner.
         */
        val PAGES = listOf(
            Page(
                title = R.string.welcome_hello_title,
                body = R.string.welcome_hello_body,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_privacy_title,
                body = R.string.welcome_privacy_body,
                next = R.string.welcome_continue,
                skip = R.string.welcome_skip,
            ),
            Page(
                title = R.string.welcome_data_title,
                body = R.string.welcome_data_body,
                next = R.string.welcome_download,
                skip = R.string.welcome_later,
            ),
        )
    }
}
