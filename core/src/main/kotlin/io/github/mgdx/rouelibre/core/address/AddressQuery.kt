package io.github.mgdx.rouelibre.core.address

/**
 * Ce qu'une saisie veut dire, une fois démontée.
 *
 * @property houseNumber le numéro de voirie reconnu dans la saisie, ou `null`.
 * @property houseNumberSuffix l'indice qui l'accompagne — « bis », « ter »,
 *   « a » — ou une chaîne vide.
 * @property terms les mots restants, normalisés, qui désignent la voie. Vides
 *   si la saisie ne contient rien de cherchable.
 */
public data class AddressQuery(
    public val houseNumber: Int?,
    public val houseNumberSuffix: String,
    public val terms: List<String>,
) {
    /** Vrai s'il n'y a rien à chercher : champ vide, ou numéro seul. */
    public val isEmpty: Boolean
        get() = terms.isEmpty()
}

/**
 * Indices de répétition écrits en toutes lettres.
 *
 * Une lettre isolée est également acceptée comme indice ; la liste ne sert
 * qu'aux formes que l'on ne peut pas deviner à leur longueur.
 */
private val WRITTEN_SUFFIXES = setOf("bis", "ter", "quater", "quinquies")

/**
 * Numéro de voirie le plus élevé que l'on accepte de reconnaître.
 *
 * Les numéros de la Base Adresse Nationale tiennent tous en dessous ; au-delà,
 * un nombre saisi est presque toujours autre chose — un code postal, une
 * année, une ligne de bus — et le prendre pour un numéro ferait chercher une
 * adresse qui n'existe pas.
 */
private const val HIGHEST_PLAUSIBLE_HOUSE_NUMBER = 9_999

/** Longueur d'un code postal français. */
private const val POSTCODE_LENGTH = 5

/**
 * Démonte une saisie en numéro de voirie et mots de recherche.
 *
 * Les deux ordres d'écriture sont acceptés, parce que les deux se pratiquent :
 * « 12 bis rue Nationale » comme « rue Nationale 12 bis ».
 *
 * Un code postal saisi est retiré des mots cherchés plutôt que gardé : l'index
 * ne l'indexe pas en texte intégral, et le laisser dans la recherche ferait
 * échouer une saisie par ailleurs juste.
 *
 * @param raw la saisie brute, telle que tapée.
 */
public fun AddressNormalizer.parseQuery(raw: String): AddressQuery {
    val words = normalize(raw).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return AddressQuery(null, "", emptyList())

    val leading = readLeadingNumber(words)
    val trailing = if (leading == null) readTrailingNumber(words) else null
    val recognized = leading ?: trailing

    val remaining = when {
        leading != null -> words.subList(leading.consumedWords, words.size)
        trailing != null -> words.subList(0, words.size - trailing.consumedWords)
        else -> words
    }

    return AddressQuery(
        houseNumber = recognized?.number,
        houseNumberSuffix = recognized?.suffix.orEmpty(),
        terms = remaining.filterNot(::looksLikePostcode),
    )
}

/** Un numéro reconnu et le nombre de mots qu'il a consommés. */
private data class RecognizedNumber(val number: Int, val suffix: String, val consumedWords: Int)

/** « 12 bis rue Nationale » : le numéro ouvre la saisie. */
private fun readLeadingNumber(words: List<String>): RecognizedNumber? {
    val number = words.first().toHouseNumberOrNull() ?: return null
    // Un numéro seul ne désigne aucune voie : il vaut mieux le traiter comme
    // un mot ordinaire, quitte à ne rien trouver, que de chercher « rien ».
    if (words.size == 1) return null
    val suffix = words.getOrNull(1)?.takeIf { isSuffix(it) && words.size > 2 }
    return RecognizedNumber(number, suffix.orEmpty(), if (suffix == null) 1 else 2)
}

/** « rue Nationale 12 bis » : le numéro ferme la saisie. */
private fun readTrailingNumber(words: List<String>): RecognizedNumber? {
    val last = words.last()
    if (isSuffix(last) && words.size > 2) {
        val number = words[words.size - 2].toHouseNumberOrNull() ?: return null
        return RecognizedNumber(number, last, 2)
    }
    if (words.size == 1) return null
    val number = last.toHouseNumberOrNull() ?: return null
    return RecognizedNumber(number, "", 1)
}

private fun String.toHouseNumberOrNull(): Int? {
    if (isEmpty() || !all(Char::isDigit)) return null
    val value = toIntOrNull() ?: return null
    return value.takeIf { it in 1..HIGHEST_PLAUSIBLE_HOUSE_NUMBER }
}

/**
 * Un mot est un indice de répétition s'il est écrit en toutes lettres, ou
 * réduit à une seule lettre — « 12 A », « 3 b ».
 */
private fun isSuffix(word: String): Boolean =
    word in WRITTEN_SUFFIXES || (word.length == 1 && word[0].isLetter())

private fun looksLikePostcode(word: String): Boolean =
    word.length == POSTCODE_LENGTH && word.all(Char::isDigit)
