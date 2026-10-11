package io.github.mgdx.rouelibre.ui

/**
 * The flag of a country, as the emoji its ISO 3166-1 alpha-2 code spells.
 *
 * Unicode draws a flag from the code itself: each letter maps to a regional
 * indicator symbol, and the font pairs them into the flag. So no image ships
 * with the application, and a country the catalogue adds tomorrow has its flag
 * without a release. A pair the font does not know falls back to the two
 * letters boxed, which still names the country.
 *
 * @param countryCode the code, in either case and with stray spaces tolerated —
 *   the catalogue is downloaded and its hand is not ours.
 * @return the flag, or `null` when there is no code or it is not two letters
 *   A to Z: half a pair, or a digit, would draw a stray symbol before the name.
 */
fun countryFlag(countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase() ?: return null
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    return buildString {
        code.forEach { appendCodePoint(REGIONAL_INDICATOR_A + (it - 'A')) }
    }
}

/**
 * Puts a country's flag before a label, or leaves it alone without one.
 *
 * Joined by a no-break space: a flag wrapped onto a line of its own would read
 * as belonging to nothing.
 */
fun withCountryFlag(label: String, countryCode: String?): String =
    countryFlag(countryCode)?.let { "$it$FLAG_SEPARATOR$label" } ?: label

/**
 * The label [withCountryFlag] was given, its flag taken off again.
 *
 * This is what a screen reader is to say. A flag is decoration for the eye;
 * spoken, it becomes "flag: France" before every network in a list, and the
 * name is what the row is about.
 */
fun withoutCountryFlag(label: CharSequence): CharSequence {
    val flagLength = 2 * Character.charCount(REGIONAL_INDICATOR_A)
    val prefixLength = flagLength + 1
    if (label.length < prefixLength || label[flagLength] != FLAG_SEPARATOR) return label
    val flag = label.subSequence(0, flagLength).toString()
    val isFlag = flag.codePoints().allMatch { it in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z }
    return if (isFlag) label.subSequence(prefixLength, label.length) else label
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val REGIONAL_INDICATOR_Z = REGIONAL_INDICATOR_A + ('Z' - 'A')
private const val FLAG_SEPARATOR = ' '
