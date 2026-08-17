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
 *
 * **On Android 8.0 and 8.1 this writes metric wherever the device is, and that
 * is a decision rather than an oversight.** `LocaleData.getMeasurementSystem`
 * arrived in Android 9, and answering for those two releases meant carrying a
 * table of 253 regions copied out of ICU by hand — a copy nobody would reread,
 * ageing in place, for two releases. The American reader it concerns reaches
 * feet and miles in two taps through the units setting (SPEC §7.6, §9), which
 * overrides this reading whatever it says. It is the one place in the
 * application where somebody has to act to be shown what their region measures
 * in.
 */
fun regionUnitSystem(): UnitSystem {
    val locale = deviceLocale()
    if (locale.country.isNullOrEmpty()) return UnitSystem.Metric
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return icuUnitSystem(ULocale.forLocale(locale))
    }
    return UnitSystem.Metric
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

/** What ICU says of a locale, where the platform is new enough to be asked. */
@RequiresApi(Build.VERSION_CODES.P)
private fun icuUnitSystem(locale: ULocale): UnitSystem =
    when (LocaleData.getMeasurementSystem(locale)) {
        MeasurementSystem.US -> UnitSystem.UnitedStates
        MeasurementSystem.UK -> UnitSystem.UnitedKingdom
        else -> UnitSystem.Metric
    }
