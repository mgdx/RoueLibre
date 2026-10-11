package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
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
 * Where the name stands as a label — a list row, a subtitle — the country's
 * flag goes before it: the catalogue spans several countries, and a flag is
 * read at a glance where a country name would lengthen every row. A sentence
 * that names the network, a dialogue or a snackbar, leaves [country] out: a
 * flag in the middle of a sentence is decoration the sentence does not need.
 * A view showing a flagged label is set through [flaggedText] or
 * [flaggedSubtitle], so that a screen reader says the name alone.
 *
 * @param network the network's name, as the catalogue publishes it.
 * @param city the conurbation, or `null` if unknown.
 * @param country the ISO 3166-1 alpha-2 code of its country, or `null` for no
 *   flag.
 */
fun Context.cityLabel(network: String, city: String?, country: String? = null): String =
    withCountryFlag(
        when {
            city.isNullOrBlank() -> network
            network.isBlank() -> city
            else -> getString(R.string.city_label, network, city)
        },
        country,
    )

/**
 * The text of a view that may show a label carrying a country's flag.
 *
 * The view is read aloud without the flag: TalkBack would otherwise announce
 * "flag: France" before every network of a list. A content description is what
 * every screen reader honours, where a [android.text.style.TtsSpan] is left to
 * the speech engine — and the engines found on phones without Google services
 * may ignore it.
 */
var TextView.flaggedText: CharSequence?
    get() = text
    set(value) {
        text = value
        contentDescription = spokenWithoutFlag(value)
    }

/**
 * The subtitle of a toolbar that may carry a country's flag, read aloud
 * without it for the reason [flaggedText] gives.
 *
 * A toolbar offers no handle on the view that shows its subtitle: it is found
 * among the toolbar's children by its text, once set.
 */
var Toolbar.flaggedSubtitle: CharSequence?
    get() = subtitle
    set(value) {
        subtitle = value
        children.filterIsInstance<TextView>()
            .firstOrNull { it.text.toString() == value?.toString() }
            ?.contentDescription = spokenWithoutFlag(value)
    }

/** What is to be said of [label], or `null` when it is said as it is shown. */
private fun spokenWithoutFlag(label: CharSequence?): CharSequence? =
    label?.let(::withoutCountryFlag)?.takeIf { it.length != label.length }

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
 *
 * A label and not a sentence, so it carries the country's flag when the
 * catalogue still names the country — set it through [flaggedText] or
 * [flaggedSubtitle].
 */
fun Context.noLongerServedLabel(city: ActiveCity.NoLongerServed): String = withdrawnCityLabel(city)
    ?.let {
        val label = if (city.servedByNewerVersion != null) {
            R.string.city_newer_version_label
        } else {
            R.string.city_withdrawn_label
        }
        // A withdrawal record names no country; the catalogue entry of a
        // network a newer version serves does.
        withCountryFlag(getString(label, it), city.servedByNewerVersion?.country)
    }
    ?: getString(R.string.map_city_withdrawn_title)
