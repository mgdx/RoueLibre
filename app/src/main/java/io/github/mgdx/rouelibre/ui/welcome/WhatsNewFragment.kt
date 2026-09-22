package io.github.mgdx.rouelibre.ui.welcome

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.BuildConfig
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.databinding.FragmentWhatsNewBinding
import io.github.mgdx.rouelibre.ui.textLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

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
 *
 * The store files are named after the version **code** they belong to, which
 * is no name to show anybody, so the build writes the version names beside
 * them and each note is headed by its own.
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
            binding?.notes?.text =
                if (notes.isEmpty()) getString(R.string.whats_new_nothing) else notes
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

        /** The locale read when the device's language publishes no notes. */
        private const val DEFAULT_NOTES_LOCALE = "en-US"

        /** The version name of each note, written into the assets by the build. */
        private const val VERSION_NAMES = "changelog-versions.properties"

        /**
         * How many versions of notes the screen shows at most.
         *
         * An update can span several versions, and the notes of all of them
         * used to be shown so that nothing that changed was hidden. Three is
         * where that stops: past them a note describes an application nobody
         * has been running for months, and it arrives under a heap that is
         * scrolled past rather than read. The versions left out are on the
         * F-Droid page, which keeps one note per version, and in
         * `CHANGELOG.md`.
         *
         * The APK carries exactly these three as well — the build applies the
         * same limit, `publishedNotesKept` in `app/build.gradle.kts` — so
         * reading further would find nothing. The limit is written here too
         * rather than deduced from what was copied, so that what the screen
         * shows is a decision of its own.
         */
        private const val RELEASES_SHOWN = 3

        /**
         * Opens the what's-new screen.
         *
         * @param since the last version code seen. The notes published since
         *   are shown, from the most recent to the oldest, up to
         *   [RELEASES_SHOWN] of them.
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
            context.assets.list(notesDirectory(context)).orEmpty()
                .mapNotNull { it.removeSuffix(".txt").toIntOrNull() }
                .filter { it in (since + 1)..until }
                .sortedDescending()
                .take(RELEASES_SHOWN)

        /**
         * The folder of notes to read, in the language the interface speaks.
         *
         * The notes come from the store's metadata, whose folders are named
         * after its own locales — `en-US`, `fr`. A device set to French reads
         * the French notes; anything else falls back on the default folder,
         * which is the one the application's own strings default to.
         */
        private fun notesDirectory(context: Context): String {
            val published = context.assets.list(NOTES_DIRECTORY).orEmpty()
            val language = context.textLocale().language
            val match = published.firstOrNull { it.substringBefore('-') == language }
            return "$NOTES_DIRECTORY/${match ?: DEFAULT_NOTES_LOCALE}"
        }

        /**
         * The version name each note belongs to, keyed by version code.
         *
         * Only the versions shipped are named. An absent or unreadable file
         * leaves the notes unheaded rather than showing a version code, which
         * would name nothing anybody recognises.
         */
        private fun versionNames(context: Context): Map<Int, String> {
            val read = runCatching {
                context.assets.open(VERSION_NAMES).use { stream ->
                    Properties().apply { load(stream) }
                }
            }.getOrNull() ?: return emptyMap()
            return read.stringPropertyNames()
                .mapNotNull { name ->
                    val version = name.toIntOrNull() ?: return@mapNotNull null
                    read.getProperty(name)?.let { version to it }
                }
                .toMap()
        }

        /**
         * The notes of the versions concerned, most recent first, each headed
         * by the version it belongs to.
         *
         * The heading is the wording of the "about" screen, "Version 1.4.0",
         * and deliberately the same resource: one phrase, translated once, and
         * two screens that cannot come to name the same thing differently.
         */
        private fun readNotes(context: Context, since: Int, until: Int): CharSequence {
            val directory = notesDirectory(context)
            val names = versionNames(context)
            val notes = SpannableStringBuilder()
            versionsToShow(context, since, until).forEach { version ->
                if (notes.isNotEmpty()) notes.append("\n\n")
                names[version]?.let { name ->
                    notes.append(
                        context.getString(R.string.about_version, name),
                        StyleSpan(Typeface.BOLD),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    notes.append("\n")
                }
                notes.append(
                    context.assets.open("$directory/$version.txt")
                        .bufferedReader()
                        .use { it.readText() }
                        .trim(),
                )
            }
            return notes
        }
    }
}
