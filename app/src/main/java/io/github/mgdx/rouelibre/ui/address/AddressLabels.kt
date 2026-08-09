package io.github.mgdx.rouelibre.ui.address

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.ui.formatDistance

/**
 * Met une adresse trouvée en mots.
 *
 * Le module métier rend des champs — un numéro, un nom, une commune — et
 * jamais une phrase : c'est ici qu'ils se composent, par des ressources de
 * chaînes à placeholders positionnels, parce que l'ordre des mots d'une
 * adresse change d'une langue à l'autre (SPEC §9).
 */

/** La ligne principale : « 12 bis Rue Nationale », ou le nom seul. */
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
 * La ligne d'appui : commune, distance, et l'aveu d'une position approchée.
 *
 * Le SPEC §7 demande qu'un écran dise ce qu'il sait ; annoncer un numéro placé
 * au jugé sans le signaler serait une promesse que la position ne tient pas.
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
