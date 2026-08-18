package io.github.mgdx.rouelibre.ui

import android.content.Context
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Writing digits in the numeration of the locale served (SPEC §9).
 *
 * **The one door for every figure the resources do not write themselves.**
 * Android puts what a `%d` or a plural holds into the digits of the locale it
 * serves; a `toString()` beside it stays in Latin ones, and the row read
 * "59260 · ٢٠ docks" — two systems in one line. The rule is not a list of
 * places to remember, it is this file: whatever is about to be shown goes
 * through it, whether it starts life as a count or as a string with figures in
 * it, so that the next place a figure appears cannot quietly go back to Latin.
 *
 * **Digits are transliterated, never reformatted.** A postcode is a label made
 * of figures rather than a quantity: run through a number format it would come
 * back grouped — "59 260" — and a code beginning with a nought would lose it.
 * Counts are written the same way, with no thousands separator, which is also
 * what `core` does when it writes a length.
 */

/**
 * Writes the digits of [text] in the numeration the interface counts in.
 *
 * Everything that is not an ASCII digit is left exactly as it stands:
 * separators, letters, the middle dot between two facts of a row.
 */
fun Context.inServedDigits(text: String): String = inDigitsOf(text, zeroDigitOf(textLocale()))

/**
 * Writes a count in the numeration the interface counts in.
 *
 * The same door as the string above, and deliberately so: a count reaching the
 * screen as `toString()` is how the two systems came to share a line.
 */
fun Context.inServedDigits(count: Int): String = inServedDigits(count.toString())

/**
 * The digit a numeration writes nought with — '0', '٠', '०' — from which the
 * nine others follow, Unicode laying every decimal set out in order.
 *
 * Read once and held where a figure is written on every frame: `onDraw` is no
 * place to look a locale's symbols up.
 */
internal fun zeroDigitOf(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).zeroDigit

/**
 * [text] with its ASCII digits moved to the set [zero] opens.
 *
 * Latin digits are returned as they came, the same instance and no work done:
 * English and French must be written exactly as they were before this file
 * existed.
 */
internal fun inDigitsOf(text: String, zero: Char): String {
    if (zero == LATIN_ZERO) return text
    return buildString(text.length) {
        for (character in text) {
            append(
                if (character in LATIN_ZERO..LATIN_NINE) {
                    zero + (character - LATIN_ZERO)
                } else {
                    character
                },
            )
        }
    }
}

private const val LATIN_ZERO = '0'
private const val LATIN_NINE = '9'
