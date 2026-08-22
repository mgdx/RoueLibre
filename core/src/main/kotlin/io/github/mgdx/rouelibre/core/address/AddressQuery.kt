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

/**
 * The length of a postcode written as a single group of digits.
 *
 * That is the form of most of the countries served — France, Spain, Germany,
 * Italy, Mexico, Turkey. The forms written in two groups are deliberately not
 * recognised here: a Dutch "1012 AB" or a Polish "00-001" reaches this point
 * as two words, and a lone group of four digits cannot be told apart from a
 * house number, which would cost more than the postcode does.
 */
private const val POSTCODE_LENGTH = 5

/**
 * Takes a query apart into a house number and search words.
 *
 * The three writing orders in use are accepted, because the application serves
 * whichever conurbation publishes its data and their languages do not write an
 * address the same way:
 *
 * ```
 * "12 bis rue Nationale"        the number opens the query
 * "rue Nationale 12 bis"        the number closes it
 * "Gran Vía 12 Madrid"          the number stands between street and town
 * ```
 *
 * The last is the ordinary order of German, Spanish, Italian, Dutch, Polish,
 * Czech and Portuguese, and it is what lets each translation write its search
 * prompt in the order of its own language.
 *
 * A number that belongs to the street's **name** must not be read as an
 * address: "Avenida 9 de Julio", "rue du 8 Mai 1945", "Straße des 17. Juni"
 * and "Calle 20 de Noviembre" are streets of cities the application serves.
 * Two things keep them whole — a number that does not **open** the query is
 * given up as soon as a second one appears, a date carrying two of them; and a
 * number is only read between the street and the town when neither of its
 * neighbours is an article, which is what a name puts around a date. See
 * [readMedianNumber].
 *
 * A postcode typed in is removed from the searched words rather than kept: the
 * index does not hold it in full text, and leaving it in the search would fail
 * an otherwise sound query.
 *
 * @param raw the query as typed.
 */
public fun AddressNormalizer.parseQuery(raw: String): AddressQuery {
    // The postcode goes before the number is looked for, and not after: it
    // stands exactly between the street and the town — "Gran Vía 12 28013
    // Madrid" — where it would hide the house number behind a second number.
    val words = normalize(raw).split(' ').filter { it.isNotEmpty() }
        .filterNot(::looksLikePostcode)
    if (words.isEmpty()) return AddressQuery(null, "", emptyList())

    val recognized = readLeadingNumber(words)
        ?: readTrailingNumber(words)
        ?: readMedianNumber(words, stopWords)

    val remaining = if (recognized == null) {
        words
    } else {
        words.subList(0, recognized.firstWord) +
            words.subList(recognized.firstWord + recognized.wordCount, words.size)
    }

    return AddressQuery(
        houseNumber = recognized?.number,
        houseNumberSuffix = recognized?.suffix.orEmpty(),
        terms = remaining,
    )
}

/** A recognised number, and the run of words it takes up in the query. */
private data class RecognizedNumber(
    val number: Int,
    val suffix: String,
    val firstWord: Int,
    val wordCount: Int,
)

/** "12 bis rue Nationale": the number opens the query. */
private fun readLeadingNumber(words: List<String>): RecognizedNumber? {
    val number = words.first().toHouseNumberOrNull() ?: return null
    // A bare number designates no street: better to treat it as an ordinary
    // word, even if that finds nothing, than to search for "nothing".
    if (words.size == 1) return null
    val suffix = words.getOrNull(1)?.takeIf { isSuffix(it) && words.size > 2 }
    return RecognizedNumber(number, suffix.orEmpty(), 0, if (suffix == null) 1 else 2)
}

/** "rue Nationale 12 bis": the number closes the query. */
private fun readTrailingNumber(words: List<String>): RecognizedNumber? {
    if (holdsSeveralNumbers(words)) return null
    val last = words.last()
    if (isSuffix(last) && words.size > 2) {
        val number = words[words.size - 2].toHouseNumberOrNull() ?: return null
        return RecognizedNumber(number, last, words.size - 2, 2)
    }
    if (words.size == 1) return null
    val number = last.toHouseNumberOrNull() ?: return null
    return RecognizedNumber(number, "", words.size - 1, 1)
}

/**
 * "Gran Vía 12 Madrid": the number stands between the street and the town.
 *
 * That is the ordinary order of half the languages the application is
 * translated into, and the only one of the three that cannot be told from a
 * street whose **name** holds a number by the position of the number alone.
 * Two conditions separate them, and both are needed:
 *
 * - **the query holds a single number**, checked by [holdsSeveralNumbers]: a
 *   date written in full brings two — "rue du 8 Mai 1945" — and a house number
 *   never travels with a second one;
 * - **neither neighbour of the number is an article**. A name puts one before
 *   its date, "rue **du** 8 Mai", "Straße **des** 17. Juni", or after it,
 *   "Avenida 9 **de** Julio", "Calle 20 **de** Noviembre", where a town simply
 *   follows the number. The words meant are the stop words already written
 *   down per language for ranking (SPEC §4.3), not a second list to keep.
 *
 * What that does not catch is a date written without a preposition, as Polish
 * and Czech write theirs — "Aleja 3 Maja", "náměstí 28. října" — which reads
 * exactly like a street, a number and a town. Telling those apart needs the
 * month names of the language, which belong in its normalisation rules rather
 * than here; the cost meanwhile is bounded, the words left over still naming
 * the street, and only the point inside it being taken from a number that was
 * never one.
 */
private fun readMedianNumber(words: List<String>, stopWords: Set<String>): RecognizedNumber? {
    if (holdsSeveralNumbers(words)) return null
    val position = words.indexOfFirst { it.isDigitsOnly() }
    // The first and the last word are the two orders already examined.
    if (position <= 0 || position >= words.size - 1) return null
    val number = words[position].toHouseNumberOrNull() ?: return null

    if (words[position - 1] in stopWords) return null
    // Tested before the repetition mark, and not after: a lone "a" is a mark in
    // German and an article in Italian and Spanish, and reading "via Roma 12 a
    // Milano" the first way would eat the preposition. Giving up the mark costs
    // a doorway; giving up the article would cost the address.
    val next = words[position + 1]
    if (next in stopWords) return null

    val suffix = next.takeIf { isSuffix(it) && position + 2 < words.size }
    return RecognizedNumber(number, suffix.orEmpty(), position, if (suffix == null) 1 else 2)
}

/**
 * True if the query holds more than one number.
 *
 * A house number comes alone. Two numbers are the mark of a date the street
 * carries in its own name — "rue du 8 Mai 1945" — where reading the last one
 * as an address searches a street that does not exist. Giving the number up in
 * that case is the cheaper mistake: the whole name stays in the search words,
 * so the street is still found, and only its doorway is missing.
 *
 * The number that **opens** the query escapes this: nothing stands before it
 * to make it part of a name, and "12 rue du 8 Mai 1945" is an address like any
 * other.
 */
private fun holdsSeveralNumbers(words: List<String>): Boolean =
    words.count { it.isDigitsOnly() } > 1

private fun String.isDigitsOnly(): Boolean = isNotEmpty() && all(Char::isDigit)

private fun String.toHouseNumberOrNull(): Int? {
    if (!isDigitsOnly()) return null
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
