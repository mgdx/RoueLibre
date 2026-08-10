package io.github.mgdx.rouelibre.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import io.github.mgdx.rouelibre.data.AppTheme

/**
 * Applique le thème choisi à toute l'application (SPEC §7.6).
 *
 * `AppCompatDelegate` recrée les activités concernées : le changement se voit
 * immédiatement, sans redémarrer l'application. C'est ce qui permet de juger
 * un thème au moment où on le choisit.
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
