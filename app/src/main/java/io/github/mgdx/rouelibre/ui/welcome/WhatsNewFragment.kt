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
 * What has changed since the previously installed version (SPEC §7.10).
 *
 * The notes are **embedded in the APK**, never downloaded: this screen must
 * trigger no request at all.
 *
 * Their single source is `fastlane/metadata/android/fr/changelogs/`, converted
 * into a resource at build time. F-Droid and the application therefore show
 * exactly the same text, without double entry — and without the risk of the
 * second copy ending up lying.
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
     * Closes the screen and remembers the version seen.
     *
     * Remembered here, on closing: somebody who leaves the application without
     * reading must find the notes again on the next launch.
     */
    private fun close() {
        viewLifecycleOwner.lifecycleScope.launch {
            container.preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val ARGUMENT_SINCE = "since-version"
        private const val NOTES_DIRECTORY = "changelogs"

        /**
         * Opens the what's-new screen.
         *
         * @param since the last version code seen. Every note published since
         *   is shown, from the most recent to the oldest: an update can span
         *   several versions, and skipping them would amount to hiding what
         *   changed.
         */
        fun since(since: Int): WhatsNewFragment = WhatsNewFragment().apply {
            arguments = Bundle().apply { putInt(ARGUMENT_SINCE, since) }
        }

        /**
         * True if there are notes to show for this range.
         *
         * Checked before opening the screen: a release published without notes
         * must not produce an empty screen on launch.
         */
        fun hasNotes(context: Context, since: Int, until: Int): Boolean =
            versionsToShow(context, since, until).isNotEmpty()

        private fun versionsToShow(context: Context, since: Int, until: Int): List<Int> =
            context.assets.list(NOTES_DIRECTORY).orEmpty()
                .mapNotNull { it.removeSuffix(".txt").toIntOrNull() }
                .filter { it in (since + 1)..until }
                .sortedDescending()

        /** The notes of the versions concerned, most recent first. */
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
