package io.github.mgdx.rouelibre.ui

import android.content.Context
import androidx.annotation.StringRes
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.ActiveCity

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

/**
 * Names a network this build no longer serves, as a catalogue last named it.
 *
 * @return the label, or `null` when no catalogue on the device names it.
 */
fun Context.withdrawnCityLabel(city: ActiveCity.NoLongerServed): String? =
    city.servedByNewerVersion?.let { cityLabel(it.displayName, it.mainCity) }
        ?: city.withdrawal?.let { cityLabel(it.displayName, it.mainCity) }

/**
 * The title over a city chosen and served no more: gone, or served by a newer
 * version of the application, which is the city list's own word for the rows
 * it refuses (SPEC §15.1).
 */
@StringRes
fun noLongerServedTitle(city: ActiveCity.NoLongerServed): Int =
    if (city.servedByNewerVersion != null) {
        R.string.city_needs_newer_version
    } else {
        R.string.map_city_withdrawn_title
    }

/**
 * Tells somebody that the network they chose is served no more (SPEC §15.1).
 *
 * The sentence names it whenever a catalogue still can, says that its stations
 * are no longer shown — they are not stale, there is nothing behind them — and
 * where the data it left can be deleted.
 */
fun Context.noLongerServedMessage(city: ActiveCity.NoLongerServed): String {
    // Not gone at all: an update brings it back, and "no longer available"
    // would send its users looking for another network.
    city.servedByNewerVersion?.let { entry ->
        return getString(
            R.string.map_city_newer_version_message,
            cityLabel(entry.displayName, entry.mainCity),
        )
    }
    return withdrawnCityLabel(city)
        ?.let { getString(R.string.map_city_withdrawn_message, it) }
        ?: getString(R.string.map_city_withdrawn_message_unnamed)
}

/**
 * Names the city chosen and served no more, where a screen names the city in
 * service — "IDEcycle — Pau · no longer available" — so that it reads neither as
 * served nor as no choice at all (SPEC §15.1). A network a newer version of
 * the application serves says that instead.
 */
fun Context.noLongerServedLabel(city: ActiveCity.NoLongerServed): String = withdrawnCityLabel(city)
    ?.let {
        val label = if (city.servedByNewerVersion != null) {
            R.string.city_newer_version_label
        } else {
            R.string.city_withdrawn_label
        }
        getString(label, it)
    }
    ?: getString(R.string.map_city_withdrawn_title)
