package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** A street name split into its type and its proper name. */
public data class SplitName(
    /** "rue", "boulevard"… or `null` if the name carries none. */
    public val streetType: String?,
    /** What remains of the name once the type is removed, already normalised. */
    public val properName: String,
) {
    /** The complete normalised name, type included. */
    public val full: String
        get() = if (streetType == null) properName else "$streetType $properName".trim()
}

/**
 * Reduces street names and queries to a comparable form (SPEC §4.3).
 *
 * The rules applied here are not written in this file: they live in
 * `config/address_normalization.json`, read both by the script that builds the
 * index and by the application. A divergence between the two would make streets
 * impossible to find — "boulevard" indexed on one side, "bd" searched on the
 * other — hence the single source.
 *
 * The treatment, identical at both ends:
 * ```
 * "Bd. de l'Hôpital Militaire"  →  "boulevard de l hopital militaire"
 *                               →  type "boulevard", name "de l hopital militaire"
 * ```
 */
public class AddressNormalizer internal constructor(
    private val anywhereAbbreviations: Map<String, String>,
    private val leadingAbbreviations: Map<String, String>,
    private val punctuation: Set<Char>,
    /**
     * Stop words, ignored when **ranking** and never when indexing: without
     * them "rue de la gare" would lose half its content.
     */
    public val stopWords: Set<String>,
    private val streetTypes: List<List<String>>,
) {

    /**
     * Reduces a raw text to its comparable form.
     *
     * Lowercase, accents removed, punctuation turned into word separation,
     * abbreviations expanded, whitespace collapsed to a single space.
     *
     * @param text a street name or a query, as it comes.
     * @return the normalised form, possibly empty.
     */
    public fun normalize(text: String): String {
        val folded = StringBuilder(text.length)
        for (character in stripAccents(text).lowercase()) {
            folded.append(if (character in punctuation) ' ' else character)
        }

        val words = folded.toString().split(WHITESPACE).filter { it.isNotEmpty() }
        val expanded = ArrayList<String>(words.size)
        words.forEachIndexed { position, word ->
            val replacement = anywhereAbbreviations[word]
                // A single-letter abbreviation is only expanded in leading
                // position, otherwise "Jean R Dupont" would become "Jean rue
                // Dupont".
                ?: leadingAbbreviations[word].takeIf { position == 0 }
            if (replacement == null) {
                expanded.add(word)
            } else {
                expanded.addAll(replacement.split(WHITESPACE).filter { it.isNotEmpty() })
            }
        }
        return expanded.joinToString(" ")
    }

    /**
     * Separates a leading street type from the proper name.
     *
     * Only a **leading** type is recognised: in "rue de la Place", "place" is
     * part of the name, it is not the street's type.
     *
     * @param normalized a name already passed through [normalize].
     */
    public fun splitStreetType(normalized: String): SplitName {
        val words = normalized.split(WHITESPACE).filter { it.isNotEmpty() }
        for (candidate in streetTypes) {
            if (words.size < candidate.size) continue
            if (words.subList(0, candidate.size) != candidate) continue
            val remainder = words.subList(candidate.size, words.size).joinToString(" ")
            // A name that reduces to its own type — "la Grand Place" — keeps
            // the type as its proper name, failing which it would become
            // impossible to find.
            return if (remainder.isEmpty()) {
                SplitName(null, candidate.joinToString(" "))
            } else {
                SplitName(candidate.joinToString(" "), remainder)
            }
        }
        return SplitName(null, normalized)
    }

    /** Normalises a raw name and detaches its street type, in one go. */
    public fun analyse(rawName: String): SplitName = splitStreetType(normalize(rawName))

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /**
         * Removes diacritics while keeping the base letters.
         *
         * Decomposing then dropping the marks handles the whole Latin
         * repertoire at once, which matters for the Flemish-rooted names that
         * abound around Lille.
         */
        fun stripAccents(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
            val builder = StringBuilder(decomposed.length)
            for (character in decomposed) {
                if (Character.getType(character) != Character.NON_SPACING_MARK.toInt()) {
                    builder.append(character)
                }
            }
            return builder.toString()
        }
    }
}

/**
 * Reads the rules file shared with the indexing script.
 *
 * The format is ordinary JSON, enriched with `$comment` keys documenting the
 * rules; they are ignored here like any unknown field.
 */
public object AddressNormalizerReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the contents of `address_normalization.json`.
     *
     * @param document the raw contents of the file embedded in the APK.
     * @return the normaliser, or the error preventing it from being built.
     */
    public fun read(document: String): Outcome<AddressNormalizer> = try {
        val rules = json.decodeFromString(NormalizationRulesDocument.serializer(), document)
        Outcome.Success(rules.toNormalizer())
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "unreadable normalisation rules",
            ),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "inconsistent normalisation rules",
            ),
        )
    }
}

@Serializable
private data class NormalizationRulesDocument(
    val rulesVersion: Int = 1,
    val abbreviations: AbbreviationsDocument,
    val streetTypes: StreetTypesDocument,
    val punctuationReplacedBySpace: String,
    val stopWords: StopWordsDocument,
) {
    fun toNormalizer(): AddressNormalizer = AddressNormalizer(
        anywhereAbbreviations = abbreviations.anywhere.withoutComments(),
        leadingAbbreviations = abbreviations.leadingOnly.withoutComments(),
        punctuation = punctuationReplacedBySpace.toSet(),
        stopWords = stopWords.words.toSet(),
        // Longest types first, so that "rond point" wins over "rond" and
        // "grand rue" over "rue".
        streetTypes = streetTypes.types
            .map { it.split(' ').filter(String::isNotEmpty) }
            .sortedByDescending { it.size },
    )

    /**
     * Documentation keys are not abbreviations.
     *
     * They live alongside the rules in the same JSON object, failing which the
     * file could not comment itself.
     */
    private fun Map<String, String>.withoutComments(): Map<String, String> =
        filterKeys { !it.startsWith('$') }
}

@Serializable
private data class AbbreviationsDocument(
    val anywhere: Map<String, String> = emptyMap(),
    val leadingOnly: Map<String, String> = emptyMap(),
)

@Serializable
private data class StreetTypesDocument(val types: List<String> = emptyList())

@Serializable
private data class StopWordsDocument(val words: List<String> = emptyList())
