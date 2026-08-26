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
 * The licences of the works embedded in the application.
 *
 * This is not a courtesy: SPEC §5 requires **keeping BRouter's copyright notice
 * and MIT licence text** in the legal notices; the fonts are under the SIL
 * Open Font License, MapLibre Native under BSD-2-Clause and the Public Suffix
 * List embedded by OkHttp under MPL-2.0, all of which ask the same. The texts
 * live in the APK's resources and are read as they are — rewording them would
 * alter them.
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
     * Reads the embedded licence texts, in the alphabetical order of their
     * files.
     *
     * The folder is walked rather than enumerated in the code: adding a
     * dependency and its licence must not require remembering to edit this
     * screen, because nobody would remember.
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
