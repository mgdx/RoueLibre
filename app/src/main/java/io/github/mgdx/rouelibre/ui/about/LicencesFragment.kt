package io.github.mgdx.rouelibre.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.FragmentLicencesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Les licences des œuvres embarquées dans l'application.
 *
 * Ce n'est pas une courtoisie : le SPEC §5 impose de **conserver l'avis de
 * copyright et le texte de la licence MIT de BRouter** dans les mentions
 * légales, et les polices sont sous SIL Open Font License, qui demande la même
 * chose. Les textes vivent dans les ressources de l'APK et sont lus tels
 * quels — les reformuler serait les altérer.
 */
class LicencesFragment : Fragment() {

    private var binding: FragmentLicencesBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentLicencesBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)
        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)

        viewLifecycleOwner.lifecycleScope.launch {
            val texts = withContext(Dispatchers.IO) { readLicences() }
            binding?.licences?.text = texts
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /**
     * Lit les textes de licence embarqués, dans l'ordre alphabétique de leurs
     * fichiers.
     *
     * Le dossier est parcouru plutôt qu'énuméré dans le code : ajouter une
     * dépendance et sa licence ne doit pas demander de penser à modifier cet
     * écran, car on n'y penserait pas.
     */
    private fun readLicences(): String {
        val assets = requireContext().assets
        val names = assets.list(LICENCES_DIRECTORY).orEmpty().sorted()
        return names.joinToString(separator = "\n\n") { name ->
            val text = assets.open("$LICENCES_DIRECTORY/$name")
                .bufferedReader()
                .use { it.readText() }
                .trim()
            "$name\n\n$text"
        }
    }

    private companion object {
        const val LICENCES_DIRECTORY = "licences"
    }
}
