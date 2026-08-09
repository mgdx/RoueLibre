package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** Un nom de voie séparé en son type et son nom propre. */
public data class SplitName(
    /** « rue », « boulevard »… ou `null` si le nom n'en porte pas. */
    public val streetType: String?,
    /** Ce qui reste du nom une fois le type retiré, déjà normalisé. */
    public val properName: String,
) {
    /** Le nom normalisé complet, type compris. */
    public val full: String
        get() = if (streetType == null) properName else "$streetType $properName".trim()
}

/**
 * Réduit noms de voies et saisies à une forme comparable (SPEC §4.3).
 *
 * Les règles appliquées ici ne sont pas écrites dans ce fichier : elles vivent
 * dans `config/address_normalization.json`, lu à la fois par le script qui
 * construit l'index et par l'application. Une divergence entre les deux
 * rendrait des rues introuvables — « boulevard » indexé d'un côté, « bd »
 * cherché de l'autre — d'où la source unique.
 *
 * Le traitement, identique aux deux bouts :
 * ```
 * "Bd. de l'Hôpital Militaire"  →  "boulevard de l hopital militaire"
 *                               →  type « boulevard », nom « de l hopital militaire »
 * ```
 */
public class AddressNormalizer internal constructor(
    private val anywhereAbbreviations: Map<String, String>,
    private val leadingAbbreviations: Map<String, String>,
    private val punctuation: Set<Char>,
    /**
     * Mots vides, ignorés au **classement** et jamais à l'indexation : sans
     * eux « rue de la gare » perdrait la moitié de son contenu.
     */
    public val stopWords: Set<String>,
    private val streetTypes: List<List<String>>,
) {

    /**
     * Réduit un texte brut à sa forme comparable.
     *
     * Minuscules, accents retirés, ponctuation transformée en séparation de
     * mots, abréviations développées, espaces réduits à un seul.
     *
     * @param text un nom de voie ou une saisie, tel qu'il se présente.
     * @return la forme normalisée, éventuellement vide.
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
                // Une abréviation d'une seule lettre n'est développée qu'en
                // tête, sinon « Jean R Dupont » deviendrait « Jean rue Dupont ».
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
     * Sépare un type de voie en tête du nom propre.
     *
     * Seul un type **en tête** est reconnu : dans « rue de la Place »,
     * « place » fait partie du nom, ce n'est pas le type de la voie.
     *
     * @param normalized un nom déjà passé par [normalize].
     */
    public fun splitStreetType(normalized: String): SplitName {
        val words = normalized.split(WHITESPACE).filter { it.isNotEmpty() }
        for (candidate in streetTypes) {
            if (words.size < candidate.size) continue
            if (words.subList(0, candidate.size) != candidate) continue
            val remainder = words.subList(candidate.size, words.size).joinToString(" ")
            // Un nom qui se réduit à son type — « la Grand Place » — garde le
            // type pour nom propre, faute de quoi il deviendrait introuvable.
            return if (remainder.isEmpty()) {
                SplitName(null, candidate.joinToString(" "))
            } else {
                SplitName(candidate.joinToString(" "), remainder)
            }
        }
        return SplitName(null, normalized)
    }

    /** Normalise un nom brut et en détache le type de voie, en une fois. */
    public fun analyse(rawName: String): SplitName = splitStreetType(normalize(rawName))

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /**
         * Retire les diacritiques en gardant les lettres de base.
         *
         * Décomposer puis écarter les marques traite d'un coup tout le
         * répertoire latin, ce qui compte pour les noms d'origine flamande
         * fréquents autour de Lille.
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
 * Lit le fichier de règles partagé avec le script d'indexation.
 *
 * Le format est du JSON ordinaire, enrichi de clés `$comment` qui documentent
 * les règles ; elles sont ignorées ici comme tout champ inconnu.
 */
public object AddressNormalizerReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Analyse le contenu de `address_normalization.json`.
     *
     * @param document contenu brut du fichier embarqué dans l'APK.
     * @return le normalisateur, ou l'erreur qui empêche de le construire.
     */
    public fun read(document: String): Outcome<AddressNormalizer> = try {
        val rules = json.decodeFromString(NormalizationRulesDocument.serializer(), document)
        Outcome.Success(rules.toNormalizer())
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "règles de normalisation illisibles",
            ),
        )
    } catch (error: IllegalArgumentException) {
        Outcome.Failure(
            DataError.MalformedResponse(
                error.message ?: "règles de normalisation incohérentes",
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
        // Les types les plus longs d'abord, pour que « rond point » l'emporte
        // sur « rond » et « grand rue » sur « rue ».
        streetTypes = streetTypes.types
            .map { it.split(' ').filter(String::isNotEmpty) }
            .sortedByDescending { it.size },
    )

    /**
     * Les clés de documentation ne sont pas des abréviations.
     *
     * Elles cohabitent avec les règles dans le même objet JSON, faute de quoi
     * le fichier ne pourrait pas se commenter lui-même.
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
