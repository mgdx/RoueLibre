package io.github.mgdx.rouelibre.ui.address

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.inServedDigits

/**
 * Puts a found address into words.
 *
 * The business module returns fields — a number, a name, a municipality — and
 * never a sentence: here is where they are composed, through string resources
 * with positional placeholders, because the word order of an address changes
 * from one language to another (SPEC §9).
 */

/**
 * The query as the "no address found" message quotes it back.
 *
 * Quoting it back is what makes that message about *this* search rather than
 * about searching in general. But the field takes as much text as one cares to
 * paste into it, and four hundred characters wrote a ten-line quotation that
 * pushed the advice following it out of the panel — off the screen at the
 * larger text sizes. Past [QUERY_ECHO_LIMIT] the quotation is cut short: long
 * enough that no address anybody types is ever shortened, short enough that the
 * sentence saying what to try next stays in sight.
 */
fun boundedQuery(query: String): String = if (query.length <= QUERY_ECHO_LIMIT) {
    query
} else {
    query.take(QUERY_ECHO_LIMIT).trimEnd() + Typography.ellipsis
}

/**
 * The longest quotation the "no address found" message carries, in characters.
 *
 * Sixty is above the longest address the index holds — street name, number and
 * municipality together — so a real search is never quoted back shortened.
 */
private const val QUERY_ECHO_LIMIT = 60

/** The main line: "12 bis Rue Nationale", or the name alone. */
fun AddressResult.toTitle(context: Context): String {
    val number = houseNumber ?: return streetName
    val written = if (houseNumberSuffix.isEmpty()) {
        // The suffixed form has the number as a `%d`, which Android already
        // writes in the digits of the locale served; alone, it went through
        // `toString()` and stayed Latin beside it (SPEC §9).
        context.inServedDigits(number)
    } else {
        context.getString(R.string.address_number_with_suffix, number, houseNumberSuffix)
    }
    return context.getString(R.string.address_with_number, written, streetName)
}

/**
 * The supporting line: municipality, distance, and the admission of an
 * approximate position.
 *
 * SPEC §7 asks that a screen say what it knows; announcing a number placed by
 * estimate without flagging it would be a promise the position does not keep.
 */
fun AddressResult.toDetail(context: Context): String {
    val place = if (postcode.isNullOrBlank()) {
        city
    } else {
        // A postcode is a label made of figures: its digits are moved to
        // the numeration served, never run through a number format, which
        // would group them into "59 260" (SPEC §9).
        context.getString(
            R.string.address_locality,
            context.inServedDigits(postcode.orEmpty()),
            city,
        )
    }
    val withDistance = distanceInMetres
        ?.let { context.getString(R.string.address_detail, place, context.formatDistance(it)) }
        ?: place

    return if (precision == PositionPrecision.NearestKnown) {
        context.getString(R.string.address_detail_approximate, withDistance)
    } else {
        withDistance
    }
}
