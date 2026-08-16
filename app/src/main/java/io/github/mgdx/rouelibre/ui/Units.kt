package io.github.mgdx.rouelibre.ui

import android.content.res.Resources
import android.icu.util.LocaleData
import android.icu.util.LocaleData.MeasurementSystem
import android.icu.util.ULocale
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.ConfigurationCompat
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import io.github.mgdx.rouelibre.core.measure.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The units the application writes its distances in, for the whole process
 * (SPEC §7.6, §9).
 *
 * Read at the moment a figure becomes text, from every screen, and therefore
 * held here rather than passed down: the twenty places that write a distance ask
 * this and nothing else, and a value threaded through them would be a unit
 * system travelling with figures that are all in metres.
 *
 * It is a `StateFlow` so the interface can be rebuilt when it changes — a
 * setting one changes must show at once, on every screen, without waiting for
 * the next launch (see `MainActivity`).
 */
object DisplayedUnits {

    private val state = MutableStateFlow(regionUnitSystem())

    /** What is being written in right now, and its changes. */
    val system: StateFlow<UnitSystem> = state.asStateFlow()

    /** What is being written in right now. */
    fun current(): UnitSystem = state.value

    /**
     * Applies a choice read from the settings.
     *
     * The region is read again on each call rather than kept: a device whose
     * region changes while the application is running must be followed by
     * whoever asked to follow it.
     */
    fun follow(choice: UnitChoice) {
        state.value = choice.resolve(regionUnitSystem())
    }
}

/**
 * What the device's region measures in (SPEC §9).
 *
 * Asked of ICU, which ships with Android — no dependency, no Google service
 * (SPEC §2, C2) — and which answers with the very distinction needed here,
 * the British case included: the United Kingdom counts short distances in yards
 * where the United States counts them in feet.
 *
 * **The device's own locale, not the language the interface is speaking.**
 * Somebody reading English in Lyon wants kilometres, and their device says so
 * through its region: what the application's own resources resolved to is a
 * question about words, not about measurements.
 *
 * Everything that is neither American nor British reads metric.
 *
 * **A locale with no region reads metric too, and that is ours, not ICU's.**
 * Read on a Fairphone 3 on 16 August 2026, ICU answers `US` for a bare `en` and
 * for `und` — the undetermined locale — while answering `SI` for a region it
 * does not know, such as `xx-ZZ`. That is a default standing in for an answer:
 * nothing about a locale carrying no country says feet, and taking it for the
 * United States would put a French reader who forces the interface into English
 * onto miles. The same reading confirmed the rest of what this function rests
 * on: `en-GB` gives `UK`, `en-US` gives `US`, and `en-FR` gives `SI`.
 */
fun regionUnitSystem(): UnitSystem {
    val locale = deviceLocale()
    val region = locale.country
    if (region.isNullOrEmpty()) return UnitSystem.Metric
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return icuUnitSystem(ULocale.forLocale(locale))
    }
    return unitSystemOfRegion(region)
}

/**
 * The locale of the **device**, which the language chosen in the application
 * does not touch (SPEC §7.6, §9).
 *
 * This read `ULocale.getDefault(ULocale.Category.FORMAT)` until the interface
 * gained a language of its own, and that would now be a bug. Choosing a
 * language puts it at the head of the process's locale list — before the
 * device's own, which is kept behind it — and a language carries no country:
 * the tag stored is `fr`, never `fr-FR`. Read there, somebody in Boston who
 * puts the interface into French would find their miles turned into kilometres
 * by a setting about words. `Resources.getSystem()` carries the device's
 * configuration, which no per-application language overrides, on every release
 * served.
 *
 * With no language chosen the two answer the same thing, which is why nothing
 * changes for whoever never opens that setting.
 */
private fun deviceLocale(): Locale =
    ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        ?: Locale.getDefault()

/**
 * What a region measures in, asked of the table rather than of ICU.
 *
 * Split out from [regionUnitSystem] so that a test on the JVM can pin what the
 * units are read from — a region, never a language — where neither ICU nor the
 * device's configuration can be reached.
 */
internal fun unitSystemOfRegion(region: String): UnitSystem = when (region) {
    in REGIONS_MEASURING_IN_FEET -> UnitSystem.UnitedStates
    in REGIONS_MEASURING_IN_YARDS -> UnitSystem.UnitedKingdom
    else -> UnitSystem.Metric
}

/** What ICU says of a locale, where the platform is new enough to be asked. */
@RequiresApi(Build.VERSION_CODES.P)
private fun icuUnitSystem(locale: ULocale): UnitSystem =
    when (LocaleData.getMeasurementSystem(locale)) {
        MeasurementSystem.US -> UnitSystem.UnitedStates
        MeasurementSystem.UK -> UnitSystem.UnitedKingdom
        else -> UnitSystem.Metric
    }

/**
 * The regions ICU counts in feet, for the two releases that cannot be asked.
 *
 * `LocaleData.getMeasurementSystem` arrived in Android 9, and this application
 * serves Android 8.0 and 8.1 as well (SPEC §3). Rather than show those two
 * releases metric distances wherever they are, the answer is **copied from ICU
 * rather than assumed**: asked for all 253 regions it knows, on a Fairphone 3
 * on 16 August 2026, it named these two and no others — the American
 * territories it counts as metric are therefore left out on its authority, not
 * on ours.
 *
 * A table of regions is not a table of cities: nothing here is specific to a
 * network, and every one of them is overridden by the setting (SPEC §15).
 */
private val REGIONS_MEASURING_IN_FEET = setOf("US", "LR")

/**
 * The regions ICU counts in yards, read the same way and on the same day.
 *
 * Myanmar keeps units of its own, which CLDR files under the British system;
 * that is ICU's reading and this follows it rather than second-guessing it.
 */
private val REGIONS_MEASURING_IN_YARDS = setOf("GB", "MM")
