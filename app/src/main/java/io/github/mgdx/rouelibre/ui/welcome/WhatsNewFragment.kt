package io.github.mgdx.rouelibre.ui.welcome

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentWhatsNewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ce qui a changé depuis la version précédemment installée (SPEC §7.10).
 *
 * Les notes sont **embarquées dans l'APK**, jamais téléchargées : cet écran ne
 * doit déclencher aucune requête.
 *
 * Leur source unique est `fastlane/metadata/android/fr/changelogs/`, converti
 * en ressource au moment du build. F-Droid et l'application montrent ainsi
 * exactement le même texte, sans double saisie — et sans risque que la seconde
 * copie finisse par mentir.
 */
class WhatsNewFragment : Fragment() {

    private var binding: FragmentWhatsNewBinding? = null

    private val container
        get() = (requireActivity().application as RoueLibreApplication).container

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentWhatsNewBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { close() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.done.setOnClickListener { close() }

        viewLifecycleOwner.lifecycleScope.launch {
            val since = requireArguments().getInt(ARGUMENT_SINCE)
            val notes = withContext(Dispatchers.IO) {
                readNotes(requireContext(), since, BuildConfig.VERSION_CODE)
            }
            binding?.notes?.text = notes.ifEmpty { getString(R.string.whats_new_nothing) }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /**
     * Referme l'écran et retient la version vue.
     *
     * Retenue ici, à la fermeture : quelqu'un qui quitte l'application sans
     * lire doit retrouver les notes au lancement suivant.
     */
    private fun close() {
        viewLifecycleOwner.lifecycleScope.launch {
            container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val ARGUMENT_SINCE = "depuis-version"
        private const val NOTES_DIRECTORY = "changelogs"

        /**
         * Ouvre l'écran des nouveautés.
         *
         * @param since dernier code de version vu. Toutes les notes publiées
         *   depuis sont montrées, de la plus récente à la plus ancienne : une
         *   mise à jour peut couvrir plusieurs versions, et les sauter
         *   reviendrait à cacher ce qui a changé.
         */
        fun since(since: Int): WhatsNewFragment = WhatsNewFragment().apply {
            arguments = Bundle().apply { putInt(ARGUMENT_SINCE, since) }
        }

        /**
         * Vrai s'il y a des notes à montrer pour cet intervalle.
         *
         * Consulté avant d'ouvrir l'écran : une version publiée sans note ne
         * doit pas produire un écran vide au lancement.
         */
        fun hasNotes(context: Context, since: Int, until: Int): Boolean =
            versionsToShow(context, since, until).isNotEmpty()

        private fun versionsToShow(context: Context, since: Int, until: Int): List<Int> =
            context.assets.list(NOTES_DIRECTORY).orEmpty()
                .mapNotNull { it.removeSuffix(".txt").toIntOrNull() }
                .filter { it in (since + 1)..until }
                .sortedDescending()

        /** Les notes des versions concernées, de la plus récente à la plus ancienne. */
        private fun readNotes(context: Context, since: Int, until: Int): String =
            versionsToShow(context, since, until).joinToString(separator = "\n\n") { version ->
                val text = context.assets.open("$NOTES_DIRECTORY/$version.txt")
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
                text
            }
    }
}
