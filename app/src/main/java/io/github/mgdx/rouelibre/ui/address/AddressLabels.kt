package io.github.mgdx.rouelibre.ui.address

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.ui.formatDistance

/**
 * Puts a found address into words.
 *
 * The business module returns fields — a number, a name, a municipality — and
 * never a sentence: here is where they are composed, through string resources
 * with positional placeholders, because the word order of an address changes
 * from one language to another (SPEC §9).
 */

/** The main line: "12 bis Rue Nationale", or the name alone. */
fun AddressResult.toTitle(context: Context): String {
    val number = houseNumber ?: return streetName
    val written = if (houseNumberSuffix.isEmpty()) {
        number.toString()
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
        context.getString(R.string.address_locality, postcode, city)
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
