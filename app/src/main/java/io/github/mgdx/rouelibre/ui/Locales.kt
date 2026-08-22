package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.icu.text.NumberingSystem
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.text.Collator
import java.util.Locale

/**
 * The languages the interface actually exists in.
 *
 * `res/values/` carries English without a language qualifier, as SPEC §9
 * requires. Android then treats those resources as a default valid for any
 * language and has no way of knowing which language they are in: on a device
 * set to German it serves them under a configuration announced as German.
 *
 * This list restores the truth. **Add every new translation to it**, at the
 * same time as the `values-<language>/` folder, `localeFilters` in
 * `build.gradle.kts` and `res/xml/locales_config.xml` — otherwise the new
 * language's dates and numbers would go on being formatted in English while
 * its text is finally translated, and the language chooser would go on not
 * offering it. `CONTRIBUTING.md` lists those places in one paragraph, and a
 * test pins the XML against this list.
 *
 * The languages whose file exists but still holds the English text are
 * deliberately absent: as long as one reads English, one is owed English
 * dates — and a chooser offering "Deutsch" to hand back English would be worse
 * than one offering no German at all.
 */
internal val TRANSLATED_LANGUAGES = setOf("de", "en", "es", "fr", "ja")

/**
 * The language of `res/values/`, served when no matching translation exists.
 *
 * It is the one language with no `values-<language>/` folder of its own: the
 * file carrying no qualifier is the English one (SPEC §9).
 */
internal val BASE_LOCALE: Locale = Locale.ENGLISH

/**
 * Orders language names as a reader looks for one: by their first letter,
 * accents folded onto the letter they are drawn over rather than sorted after
 * the alphabet.
 */
private val BY_NAME: Collator = Collator.getInstance(Locale.ROOT)

/**
 * The languages the interface can be asked to speak (SPEC §7.6, §9).
 *
 * Derived from [TRANSLATED_LANGUAGES] and from nothing else, so that finishing
 * a translation is the whole of what it takes to have it offered. A second list
 * written by hand would sooner or later name a language whose file still holds
 * the English text.
 *
 * "Follow the system" is not in here: it is the absence of a choice, not a
 * language, and the screen offering these adds it at the head of its list.
 */
fun offeredLanguages(): List<Locale> = TRANSLATED_LANGUAGES
    .map(Locale::forLanguageTag)
    .sortedWith(compareBy(BY_NAME) { it.endonym() })

/**
 * The name a language is offered under: **its own name for it**.
 *
 * "Français", never "French": somebody hunting for their language down a list
 * is hunting for the word they would write themselves, and they may well not
 * read the language the list is currently in. The first letter is titlecased in
 * that same language, French writing its own name in lower case.
 */
fun Locale.endonym(): String = getDisplayLanguage(this)
    .replaceFirstChar { it.titlecase(this) }

/**
 * The language the interface has been asked to speak, `null` when it is
 * following the system's (SPEC §7.6).
 *
 * **AppCompat holds this choice, and it is the only place it is held.** It
 * stores it itself — in the framework from Android 13 on, in a file of its own
 * below that, opted into by the `autoStoreLocales` service declared in the
 * manifest — and it reads it back before the first view of a cold start. A
 * second copy in `AppPreferences` would be a second source of truth for one
 * value, and the two would diverge the first time the language was changed from
 * Android's own per-application settings, which write the framework's copy and
 * know nothing of ours.
 *
 * A stored value naming a language the interface does not speak is read as no
 * choice at all: that is what the system's per-application picker can leave
 * behind when a translation is withdrawn, and English text under a French
 * heading is not a state this screen may show.
 */
fun chosenLanguage(): Locale? = knownLanguage(AppCompatDelegate.getApplicationLocales()[0])

/**
 * Speaks [language] from now on, or hands the choice back to the system when it
 * is `null` (SPEC §7.6).
 *
 * Applied and stored in the one call, with no "apply" button. AppCompat rebuilds
 * the screens on the new language, exactly as a change of theme does and for the
 * same reason: the words are read when a screen binds its views, so a screen
 * already drawn would go on speaking the old language.
 */
fun speakLanguage(language: Locale?) {
    AppCompatDelegate.setApplicationLocales(
        if (language == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.create(language)
        },
    )
}

/**
 * The language of a stored choice, `null` when there is none or when it names a
 * language the interface does not speak.
 */
internal fun knownLanguage(stored: Locale?): Locale? =
    stored?.takeIf { it.language in TRANSLATED_LANGUAGES }

/**
 * The language the application speaks on this device.
 *
 * To be used wherever a date, a time, a distance or a duration is formatted:
 * these values must agree with the text around them, not with a system setting
 * the application does not follow — and since the language became a setting of
 * its own, not with the system's language either.
 *
 * Read from the configuration rather than from the process's default locale,
 * and that is the whole point: the configuration is what carries the chosen
 * language, on every context the interface draws from.
 */
fun Context.textLocale(): Locale {
    val served = ConfigurationCompat.getLocales(resources.configuration)[0]
    return textLocaleFor(served, served?.let { NumberingSystem.getInstance(it)?.name })
}

/**
 * The locale figures are written in, given the language the resources resolved
 * to and the digits the served locale counts in.
 *
 * A language with no translation of its own is served English text, and is owed
 * English figures with it (SPEC §9, §11.13): the words and the dates follow the
 * language, and that is what [displayed] decides.
 *
 * **The digits themselves follow the locale served, and not that language.**
 * Android writes the figures held in the resources — every `%d`, every plural —
 * with the configuration's own locale, which on a device set to Arabic means
 * Arabic-Indic digits under text that is still English. What Kotlin formats
 * beside them therefore has to count in the same digits, or one line carries two
 * numbering systems at once: "59260 · ٢٠ docks" was read that way on a device
 * set to `ar` (SPEC §9).
 *
 * Latin digits are left as the plain language: they are what English and French
 * are written with, and a locale carrying the extension would no longer be equal
 * to the one it was built from.
 *
 * @param numberingSystem the Unicode name of the served locale's digits —
 *   "latn", "arab", "deva" — or null when it could not be read.
 */
internal fun textLocaleFor(displayed: Locale?, numberingSystem: String? = null): Locale {
    val language =
        if (displayed != null && displayed.language in TRANSLATED_LANGUAGES) {
            displayed
        } else {
            BASE_LOCALE
        }
    if (numberingSystem == null || numberingSystem == LATIN_DIGITS) return language
    // Composed as a tag rather than through Locale.Builder, which throws on a
    // name it finds ill-formed: figures are not worth a crash, and a tag that
    // cannot be parsed simply leaves the language as it was.
    return Locale.forLanguageTag("${language.toLanguageTag()}-u-nu-$numberingSystem")
}

/** The digits English and French are written with. */
private const val LATIN_DIGITS = "latn"
