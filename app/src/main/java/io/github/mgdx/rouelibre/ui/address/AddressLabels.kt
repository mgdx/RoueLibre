package io.github.mgdx.rouelibre.ui.address

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.address.addressLayoutOf
import io.github.mgdx.rouelibre.ui.formatDistance
import io.github.mgdx.rouelibre.ui.inServedDigits

/**
 * Puts a found address into words.
 *
 * The business module returns fields — a number, a name, a municipality — and
 * never a sentence: here is where they are composed, through string resources
 * with positional placeholders, because the word order of the sentence around
 * an address changes from one language to another (SPEC §9).
 *
 * The address itself is the exception: number before or after the street,
 * comma or no comma, are settled by the country the address is in rather than
 * by the reader's language, and come from [addressLayoutOf] (SPEC §4.3).
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

/** The main line: "12 bis rue Nationale", or the name alone. */
fun AddressResult.toTitle(context: Context): String {
    val number = houseNumber ?: return streetName
    // The layout comes from the address's own base and not from the string
    // resources: it belongs to the country the address is in, and a French
    // address stays "12 rue Nationale" for a Polish reader (SPEC §4.3). The
    // digits it is written in are the reader's, which is the other rule
    // (SPEC §9), and the two are settled in two different places on purpose.
    return addressLayoutOf(language).write(
        streetName = streetName,
        houseNumber = context.inServedDigits(number),
        houseNumberSuffix = houseNumberSuffix,
    )
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

/**
 * The one row a chooser offers for an address (SPEC §7.8).
 *
 * The address search shows a found address on two lines — what it is called,
 * then what tells it from another of the same name — and a dialog's list has
 * room for one. Both lines go into it all the same, and that is the whole
 * point: the conurbation holds a "12 rue Nationale" in several of its
 * municipalities, and a list of five rows all reading "12 Rue Nationale" is
 * not a choice, it is a draw.
 *
 * @param title the address as it is written — [toTitle].
 * @param detail what tells it apart — [toDetail]; empty where the index knows
 *   nothing more, and the row is then the title alone rather than a title
 *   trailing an empty half.
 * @param write joins the two, through the string resource that owns their
 *   order and their punctuation (SPEC §9). Taken as a function rather than
 *   read here, so that the rule this holds — that a row carries what tells it
 *   apart — is pinned on the JVM, where no `Context` writes anything.
 */
fun addressChoiceRow(title: String, detail: String, write: (String, String) -> String): String =
    if (detail.isBlank()) title else write(title, detail)

/** The same row, as the device writes it. */
fun AddressResult.toChoiceRow(context: Context): String =
    addressChoiceRow(toTitle(context), toDetail(context)) { title, detail ->
        context.getString(R.string.incoming_address_choice, title, detail)
    }
