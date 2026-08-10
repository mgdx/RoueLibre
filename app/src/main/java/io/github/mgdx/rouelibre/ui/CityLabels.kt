package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R

/**
 * Names a network together with the conurbation it runs in.
 *
 * "V'lille" says nothing to whoever has never lived there, and the three
 * networks served are told apart by their city long before their brand. Where
 * the configuration names no city — a catalogue produced before that field
 * existed — the network name stands alone rather than trailing an empty dash.
 *
 * @param network the network's name, as the catalogue publishes it.
 * @param city the conurbation, or `null` if unknown.
 */
fun Context.cityLabel(network: String, city: String?): String =
    if (city.isNullOrBlank()) network else getString(R.string.city_label, network, city)
