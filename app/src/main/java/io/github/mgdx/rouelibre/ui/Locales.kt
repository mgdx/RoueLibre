package io.github.mgdx.rouelibre.ui

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * Langues dans lesquelles l'interface existe réellement.
 *
 * `res/values/` porte le français sans qualificatif de langue, comme l'exige
 * le SPEC §9. Android considère alors ces ressources comme un défaut valable
 * pour n'importe quelle langue et n'a aucun moyen de savoir qu'elles sont
 * françaises : sur un appareil configuré en anglais, il sert donc des textes
 * français dans une configuration annoncée anglaise.
 *
 * Cette liste rétablit la vérité. **Y ajouter chaque nouvelle traduction**, en
 * même temps que le dossier `values-<langue>/` et que `localeFilters` dans
 * `build.gradle.kts` — sinon les dates et les nombres de la nouvelle langue
 * continueraient d'être formatés en français.
 */
private val TRANSLATED_LANGUAGES = setOf("fr", "en")

/** Langue de `res/values/`, servie à défaut de traduction correspondante. */
private val BASE_LOCALE: Locale = Locale.FRENCH

/**
 * La langue dans laquelle l'application s'exprime pour cet appareil.
 *
 * À utiliser partout où l'on formate une date, une heure, une distance ou une
 * durée : ces valeurs doivent s'accorder au texte qui les entoure, pas à un
 * réglage système que l'application ne suit pas.
 */
fun Context.textLocale(): Locale {
    val preferred = ConfigurationCompat.getLocales(resources.configuration)[0]
        ?: return BASE_LOCALE
    return if (preferred.language in TRANSLATED_LANGUAGES) preferred else BASE_LOCALE
}
