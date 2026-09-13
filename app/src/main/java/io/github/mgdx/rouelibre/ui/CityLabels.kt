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
 * The other way round holds too: a catalogue naming no network shows the
 * conurbation on its own. The generation scripts never publish an empty name —
 * a network whose producer states none is listed under its identifier spelled
 * out — but the catalogue is downloaded, and a row arriving without a name must
 * not be shown as a dash with nothing before it.
 *
 * @param network the network's name, as the catalogue publishes it.
 * @param city the conurbation, or `null` if unknown.
 */
fun Context.cityLabel(network: String, city: String?): String = when {
    city.isNullOrBlank() -> network
    network.isBlank() -> city
    else -> getString(R.string.city_label, network, city)
}
