package io.github.mgdx.rouelibre.data.addresses

import android.content.Context
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressNormalizer
import io.github.mgdx.rouelibre.core.address.AddressNormalizerReader
import io.github.mgdx.rouelibre.core.address.searchLetterFolds
import java.io.IOException

/**
 * The street-name normalisation rules, one set per language (SPEC §4.3, §15.1).
 *
 * A street type is a word of a language: "rue" and "boulevard" say nothing
 * about a Warsaw address, where the word is "ulica" and the abbreviation "ul.".
 * The rules therefore travel with a city's data rather than being frozen into
 * the application, and the language meant is the one the **address base** is
 * written in — not the one the interface speaks. An index built over Antwerp is
 * searched in Dutch whatever the phone is set to.
 *
 * Which language applies is never decided here: the index says so itself
 * (`normalizationLanguage` in its metadata), because it is the file that was
 * built with those rules. Indexing with one set and searching with another
 * would make streets unfindable, which is the failure this whole arrangement
 * exists to prevent.
 *
 * The files are copied into the APK's assets at build time from
 * `config/address-normalization/`, the single source shared with the indexing
 * script.
 */
class AddressNormalizers(private val context: Context) {

    private val loaded = HashMap<String, AddressNormalizer>()

    private var folds: Map<Char, String>? = null

    /**
     * The rules of [language], or English where that language has none.
     *
     * Falling back rather than failing is deliberate: a network appears in a
     * country before anybody has written that country's street vocabulary, and
     * an index built with the plainest rules is still searchable — a street
     * found by its whole name, without the type/name split. The indexing script
     * falls back the same way, and writes down which language it settled on, so
     * the two ends cannot disagree.
     *
     * @throws IllegalStateException if even the English rules are missing — a
     *   manufacturing defect of the APK, not a user situation.
     */
    @Synchronized
    fun of(language: String?): AddressNormalizer {
        val wanted = language?.takeIf { it.isNotBlank() } ?: FALLBACK_LANGUAGE
        loaded[wanted]?.let { return it }
        val normalizer = read(wanted) ?: read(FALLBACK_LANGUAGE)
            ?: error("No normalisation rules in the APK for \"$wanted\" nor English")
        loaded[wanted] = normalizer
        return normalizer
    }

    /**
     * The letters to fold in the searches that carry no language (SPEC §4.3).
     *
     * The station list and the city catalogue are searched without knowing what
     * language anything is written in, so they take every rule set shipped
     * rather than one. Gathered from the same files as the rest, which is the
     * point: "ł → l" is written down once, in `config/address-normalization/`,
     * and the indexing script reads it from there too.
     *
     * Read once and kept: the files are a hundred and seventy kilobytes all
     * told, and the search field they serve filters on every keystroke.
     */
    @Synchronized
    fun searchLetterFolds(): Map<Char, String> {
        folds?.let { return it }
        val languages = try {
            context.assets.list(RULES_DIRECTORY)?.asList().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
        val gathered = searchLetterFolds(
            languages.mapNotNull { file -> read(file.removeSuffix(RULES_SUFFIX)) },
        )
        folds = gathered
        return gathered
    }

    private fun read(language: String): AddressNormalizer? {
        val document = try {
            context.assets.open("$RULES_DIRECTORY/$language.json")
                .bufferedReader()
                .use { it.readText() }
        } catch (_: IOException) {
            return null
        }
        return when (val outcome = AddressNormalizerReader.read(document)) {
            is Outcome.Success -> outcome.value
            // A rules file that ships but cannot be read is a defect of the
            // build, and saying which language it is saves the next reader the
            // hunt.
            is Outcome.Failure -> error(
                "Normalisation rules unreadable for \"$language\": ${outcome.error}",
            )
        }
    }

    private companion object {
        const val RULES_DIRECTORY = "address-normalization"

        const val RULES_SUFFIX = ".json"

        /** The language of the interface itself (SPEC §9), and of last resort. */
        const val FALLBACK_LANGUAGE = "en"
    }
}
