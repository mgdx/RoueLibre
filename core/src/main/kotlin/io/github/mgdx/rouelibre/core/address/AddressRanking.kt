package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import kotlin.math.floor

/**
 * Classement des voies candidates (SPEC §4.3).
 *
 * Deux critères, dans cet ordre : **la qualité de la correspondance d'abord,
 * la proximité ensuite**. À égalité de correspondance, la rue la plus proche
 * passe devant.
 *
 * « À égalité » se prend au sens large, sinon le critère de proximité ne
 * servirait jamais : deux correspondances qui ne diffèrent que par la longueur
 * d'un préfixe sont, pour l'utilisateur, la même correspondance. Les scores
 * sont donc rangés par paliers, et la distance départage à l'intérieur d'un
 * palier — jamais entre deux paliers, où la correspondance garde le dernier
 * mot.
 */

/**
 * Largeur d'un palier de correspondance.
 *
 * Un vingtième sépare des correspondances réellement différentes — un mot
 * trouvé contre un mot manquant — sans distinguer ce qui ne se distingue pas à
 * l'usage, comme deux préfixes de longueurs voisines.
 */
private const val QUALITY_TIER = 0.05

/**
 * Poids des champs dans la correspondance.
 *
 * Le nom propre identifie la voie ; le type — « rue », « avenue » — ne réduit
 * presque rien, la moitié de l'index étant des rues ; la commune réduit
 * beaucoup, mais ne désigne pas la voie elle-même.
 */
private const val PROPER_NAME_WEIGHT = 1.0
private const val STREET_TYPE_WEIGHT = 0.6
private const val CITY_WEIGHT = 0.8

/**
 * Poids d'un mot faible dans la saisie : mot vide, ou fragment très court.
 *
 * « de », « la », « des » portent peu de sens mais ne sont pas du bruit :
 * quelqu'un qui tape « rue de la gare » a écrit ce qu'il voit sur la plaque.
 * Ils comptent donc, faiblement.
 */
private const val WEAK_TERM_WEIGHT = 0.2

/**
 * Longueur en dessous de laquelle un mot ne peut pas, à lui seul, écarter une
 * voie. Deux lettres, c'est la taille d'un mot vide ou d'un « rue » amputé.
 */
private const val SHORT_TERM_LENGTH = 2

/** Un mot de la saisie retrouvé tel quel. */
private const val EXACT_WORD_SCORE = 1.0

/**
 * Un mot de la saisie qui préfixe un mot de l'entrée.
 *
 * La note monte avec la part du mot couverte : « gamb » sur « gambetta » est
 * une frappe en cours, « gambett » est presque le mot entier. Le plancher de
 * 0,72 reste au-dessus de tout rattrapage par faute de frappe : un préfixe
 * exact est toujours un meilleur indice qu'une lettre corrigée.
 */
private const val PREFIX_FLOOR = 0.72
private const val PREFIX_RANGE = 0.28

/** Un mot retrouvé à une faute près, puis à deux. */
private const val ONE_MISTAKE_SCORE = 0.55
private const val TWO_MISTAKES_SCORE = 0.35

/**
 * Part de la note tenant à ce que l'entrée n'a **pas** de mots en trop.
 *
 * Sans elle, « Rue Gambetta » et « Rue Gambetta Prolongée » seraient à égalité
 * pour la saisie « gambetta », alors que la première est ce qui a été demandé.
 */
private const val COVERAGE_WEIGHT = 0.15

/**
 * Classe les voies candidates pour une saisie.
 *
 * @param candidates les voies à départager, telles que l'index les a rendues.
 * @param query la saisie démontée par [parseQuery].
 * @param stopWords les mots vides, qui pèsent moins dans la correspondance.
 * @param origin point de référence pour la proximité — la position de
 *   l'utilisateur, ou le centre de la carte. `null` si aucun n'est connu.
 * @param limit nombre de résultats rendus.
 * @return les voies retenues, les meilleures d'abord.
 */
public fun rankStreets(
    candidates: Iterable<SearchableStreet>,
    query: AddressQuery,
    stopWords: Set<String>,
    origin: Coordinates?,
    limit: Int,
): List<ScoredStreet> {
    if (query.isEmpty || limit <= 0) return emptyList()

    val scored = candidates.mapNotNull { street ->
        val quality = matchQualityOf(street, query.terms, stopWords)
        if (quality <= 0.0) {
            null
        } else {
            ScoredStreet(
                street = street,
                matchQuality = quality,
                distanceInMetres = origin?.distanceInMetresTo(street.position),
            )
        }
    }

    return scored
        .sortedWith(
            compareByDescending<ScoredStreet> { qualityTierOf(it.matchQuality) }
                .thenBy { it.distanceInMetres ?: 0.0 }
                // Dernier départage, pour que deux exécutions sur les mêmes
                // données rendent toujours le même ordre : sans lui, l'ordre
                // de deux entrées équivalentes dépendrait de celui de l'index.
                .thenBy { it.street.id },
        )
        .take(limit)
}

/** Le palier auquel appartient une note de correspondance. */
private fun qualityTierOf(quality: Double): Int = floor(quality / QUALITY_TIER).toInt()

/**
 * Note la correspondance entre une entrée et les mots saisis.
 *
 * @return une note de 0 à 1, ou 0 si un mot porteur de sens reste introuvable :
 *   une saisie dont un mot ne correspond à rien ne décrit pas cette voie.
 */
private fun matchQualityOf(
    street: SearchableStreet,
    terms: List<String>,
    stopWords: Set<String>,
): Double {
    val nameWords = street.normalizedName.split(' ').filter { it.isNotEmpty() }
    val typeWords = street.normalizedType?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
    // La commune absorbée compte autant que la commune actuelle : quelqu'un
    // qui habite Lomme écrit « Lomme », pas « Lille ».
    val cityWords = listOfNotNull(street.normalizedCity, street.normalizedFormerCity)
        .flatMap { it.split(' ') }
        .filter { it.isNotEmpty() }

    var weightedScore = 0.0
    var totalWeight = 0.0
    val coveredNameWords = HashSet<String>()

    for (term in terms) {
        val againstName = bestScoreAmong(term, nameWords)
        val best = maxOf(
            againstName.score * PROPER_NAME_WEIGHT,
            bestScoreAmong(term, typeWords).score * STREET_TYPE_WEIGHT,
            bestScoreAmong(term, cityWords).score * CITY_WEIGHT,
        )
        // Un mot vide, ou un fragment de deux lettres, ne porte pas assez de
        // sens pour écarter une voie à lui seul. Mesuré sur l'index réel :
        // une lettre perdue dans « rue » — « Re de la Paix » — ne laissait
        // aucun résultat, alors que le reste de la saisie désignait la voie
        // sans ambiguïté.
        val isWeak = term in stopWords || term.length <= SHORT_TERM_LENGTH
        if (best <= 0.0 && !isWeak) return 0.0
        againstName.word?.let(coveredNameWords::add)

        val weight = if (isWeak) WEAK_TERM_WEIGHT else 1.0
        weightedScore += best * weight
        totalWeight += weight
    }
    if (totalWeight == 0.0) return 0.0

    val termScore = weightedScore / totalWeight
    // Part des mots du nom que la saisie a effectivement demandés : elle
    // récompense le nom qui s'arrête là où la saisie s'arrête.
    val coverage = if (nameWords.isEmpty()) {
        1.0
    } else {
        coveredNameWords.size.toDouble() / nameWords.size
    }
    return (1 - COVERAGE_WEIGHT) * termScore + COVERAGE_WEIGHT * coverage
}

/** La meilleure note d'un mot saisi parmi les mots d'un champ, et le mot visé. */
private fun bestScoreAmong(term: String, words: List<String>): WordMatch {
    var best = WordMatch(0.0, null)
    for (word in words) {
        val score = scoreWord(term, word)
        if (score > best.score) best = WordMatch(score, word)
    }
    return best
}

private data class WordMatch(val score: Double, val word: String?)

/**
 * Note un mot saisi contre un mot indexé.
 *
 * Trois cas, du plus sûr au moins sûr : le mot entier, le préfixe — qui
 * couvre la frappe en cours — puis le rattrapage par distance d'édition, qui
 * n'intervient que si les deux premiers échouent.
 */
private fun scoreWord(term: String, word: String): Double {
    if (term == word) return EXACT_WORD_SCORE
    if (word.startsWith(term)) {
        return PREFIX_FLOOR + PREFIX_RANGE * (term.length.toDouble() / word.length)
    }
    // Rattraper un mot d'une seule lettre n'a pas de sens : à une faute près,
    // il vaut n'importe quelle autre lettre. À partir de deux, en revanche,
    // le rattrapage sert — « ed » pour « de », « re » pour « rue » — et le
    // faible poids accordé à ces fragments contient le bruit qui en découle.
    if (term.length < MINIMUM_FUZZY_LENGTH) return 0.0

    val tolerance = toleratedMistakes(term)
    // Au-delà du plafond, la distance rendue n'est pas la vraie : elle dit
    // seulement « plus loin que ce qui nous intéresse ». La comparer au seuil
    // est donc la seule lecture juste.
    val distance = boundedDamerauLevenshteinDistance(term, word, tolerance)
    return when {
        distance > tolerance -> 0.0
        distance <= 1 -> ONE_MISTAKE_SCORE
        else -> TWO_MISTAKES_SCORE
    }
}

/** En dessous de deux lettres, toute correction rapproche n'importe quoi. */
private const val MINIMUM_FUZZY_LENGTH = 2
