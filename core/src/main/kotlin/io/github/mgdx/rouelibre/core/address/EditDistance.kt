package io.github.mgdx.rouelibre.core.address

import kotlin.math.abs
import kotlin.math.min

/**
 * Nombre de fautes admises pour un mot de cette longueur (SPEC §4.3).
 *
 * Une faute en dessous de huit caractères, deux au-delà : sur un mot court,
 * deux fautes changent tellement le mot que le rattrapage ramènerait plus de
 * bruit que de service — « gare » est à deux fautes de « gard », « care »,
 * « gaz », et d'une bonne partie du dictionnaire.
 */
public fun toleratedMistakes(word: String): Int = if (word.length < 8) 1 else 2

/**
 * Distance d'édition entre deux mots, plafonnée.
 *
 * Variante de **Damerau-Levenshtein** : aux insertions, suppressions et
 * substitutions elle ajoute l'**interversion de deux lettres voisines**, faute
 * la plus courante au clavier tactile, que la distance de Levenshtein simple
 * compterait pour deux.
 *
 * C'est la variante dite « par alignement optimal » : elle n'autorise pas une
 * lettre déjà intervertie à subir une autre modification. Le cas est
 * pathologique sur des noms de rues, et l'écarter tient l'algorithme en deux
 * lignes de tableau au lieu d'une matrice complète et d'un dictionnaire de
 * dernières occurrences.
 *
 * Le plafond n'est pas un confort : il permet d'abandonner une comparaison dès
 * que la ligne courante dépasse le seuil, ce qui écarte l'écrasante majorité
 * des vingt mille entrées de l'index en quelques caractères.
 *
 * @param source premier mot, déjà normalisé.
 * @param target second mot, déjà normalisé.
 * @param maximum distance au-delà de laquelle le résultat n'a plus d'intérêt.
 * @return la distance exacte si elle vaut au plus [maximum], sinon une valeur
 *   strictement supérieure à [maximum] dont seule cette propriété est garantie.
 */
public fun boundedDamerauLevenshteinDistance(source: String, target: String, maximum: Int): Int {
    val beyond = maximum + 1
    if (abs(source.length - target.length) > maximum) return beyond
    if (source == target) return 0
    if (source.isEmpty()) return target.length.coerceAtMost(beyond)
    if (target.isEmpty()) return source.length.coerceAtMost(beyond)

    // Trois lignes suffisent : la courante, la précédente, et celle d'avant —
    // la seule dont l'interversion a besoin.
    var beforePrevious = IntArray(target.length + 1)
    var previous = IntArray(target.length + 1) { it }
    var current = IntArray(target.length + 1)

    for (sourceIndex in 1..source.length) {
        current[0] = sourceIndex
        var rowMinimum = current[0]
        for (targetIndex in 1..target.length) {
            val substitutionCost =
                if (source[sourceIndex - 1] == target[targetIndex - 1]) 0 else 1
            var best = min(
                current[targetIndex - 1] + 1, // insertion
                previous[targetIndex] + 1, // suppression
            )
            best = min(best, previous[targetIndex - 1] + substitutionCost)
            val isTransposition = sourceIndex > 1 &&
                targetIndex > 1 &&
                source[sourceIndex - 1] == target[targetIndex - 2] &&
                source[sourceIndex - 2] == target[targetIndex - 1]
            if (isTransposition) {
                best = min(best, beforePrevious[targetIndex - 2] + 1)
            }
            current[targetIndex] = best
            rowMinimum = min(rowMinimum, best)
        }
        // Une ligne entièrement au-dessus du plafond ne peut plus redescendre :
        // les lignes suivantes ne font que croître.
        if (rowMinimum > maximum) return beyond

        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
    }
    return previous[target.length]
}
