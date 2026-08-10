package io.github.mgdx.rouelibre.ui

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * The languages the interface actually exists in.
 *
 * `res/values/` carries French without a language qualifier, as SPEC §9
 * requires. Android then treats those resources as a default valid for any
 * language and has no way of knowing they are French: on a device set to
 * English it therefore serves French text under a configuration announced as
 * English.
 *
 * This list restores the truth. **Add every new translation to it**, at the
 * same time as the `values-<language>/` folder and `localeFilters` in
 * `build.gradle.kts` — otherwise the new language's dates and numbers would go
 * on being formatted in French.
 */
private val TRANSLATED_LANGUAGES = setOf("fr", "en")

/** The language of `res/values/`, served when no matching translation exists. */
private val BASE_LOCALE: Locale = Locale.FRENCH

/**
 * The language the application speaks on this device.
 *
 * To be used wherever a date, a time, a distance or a duration is formatted:
 * these values must agree with the text around them, not with a system setting
 * the application does not follow.
 */
fun Context.textLocale(): Locale {
    val preferred = ConfigurationCompat.getLocales(resources.configuration)[0]
        ?: return BASE_LOCALE
    return if (preferred.language in TRANSLATED_LANGUAGES) preferred else BASE_LOCALE
}
