package io.github.mgdx.rouelibre.core.address

/**
 * What a query means, once taken apart.
 *
 * @property houseNumber the house number recognised in the query, or `null`.
 * @property houseNumberSuffix the repetition mark that goes with it — "bis",
 *   "ter", "a" — or an empty string.
 * @property terms the remaining words, normalised, that designate the street.
 *   Empty if the query holds nothing searchable.
 */
public data class AddressQuery(
    public val houseNumber: Int?,
    public val houseNumberSuffix: String,
    public val terms: List<String>,
) {
    /** True if there is nothing to search for: empty field, or a bare number. */
    public val isEmpty: Boolean
        get() = terms.isEmpty()
}

/**
 * Repetition marks spelled out in full.
 *
 * A lone letter is accepted as a mark too; this list only covers the forms that
 * cannot be guessed from their length.
 */
private val WRITTEN_SUFFIXES = setOf("bis", "ter", "quater", "quinquies")

/**
 * The highest house number we agree to recognise.
 *
 * Every number in the Base Adresse Nationale falls below it; past that, a
 * number typed is almost always something else — a postcode, a year, a bus
 * route — and taking it for a house number would send us looking for an address
 * that does not exist.
 */
private const val HIGHEST_PLAUSIBLE_HOUSE_NUMBER = 9_999

/** The length of a French postcode. */
private const val POSTCODE_LENGTH = 5

/**
 * Takes a query apart into a house number and search words.
 *
 * Both writing orders are accepted, because both are used: "12 bis rue
 * Nationale" as well as "rue Nationale 12 bis".
 *
 * A postcode typed in is removed from the searched words rather than kept: the
 * index does not hold it in full text, and leaving it in the search would fail
 * an otherwise sound query.
 *
 * @param raw the query as typed.
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

/** A recognised number and how many words it consumed. */
private data class RecognizedNumber(val number: Int, val suffix: String, val consumedWords: Int)

/** "12 bis rue Nationale": the number opens the query. */
private fun readLeadingNumber(words: List<String>): RecognizedNumber? {
    val number = words.first().toHouseNumberOrNull() ?: return null
    // A bare number designates no street: better to treat it as an ordinary
    // word, even if that finds nothing, than to search for "nothing".
    if (words.size == 1) return null
    val suffix = words.getOrNull(1)?.takeIf { isSuffix(it) && words.size > 2 }
    return RecognizedNumber(number, suffix.orEmpty(), if (suffix == null) 1 else 2)
}

/** "rue Nationale 12 bis": the number closes the query. */
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
 * A word is a repetition mark if it is spelled out in full, or reduced to a
 * single letter — "12 A", "3 b".
 */
private fun isSuffix(word: String): Boolean =
    word in WRITTEN_SUFFIXES || (word.length == 1 && word[0].isLetter())

private fun looksLikePostcode(word: String): Boolean =
    word.length == POSTCODE_LENGTH && word.all(Char::isDigit)
