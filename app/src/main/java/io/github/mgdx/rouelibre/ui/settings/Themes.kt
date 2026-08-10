package io.github.mgdx.rouelibre.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import io.github.mgdx.rouelibre.data.AppTheme

/**
 * Applies the chosen theme to the whole application (SPEC §7.6).
 *
 * `AppCompatDelegate` recreates the activities concerned: the change shows
 * immediately, without restarting the application. That is what allows judging
 * a theme at the moment one chooses it.
 */
fun applyTheme(theme: AppTheme) {
    AppCompatDelegate.setDefaultNightMode(
        when (theme) {
            AppTheme.Light -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        },
    )
}
