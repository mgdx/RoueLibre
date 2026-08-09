package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.provider.Settings

/**
 * Indique si l'appareil demande à ce que les animations soient réduites.
 *
 * Le SPEC §7 en fait une contrainte non négociable. Android n'expose pas de
 * préférence « réduire les animations » en tant que telle : c'est l'échelle de
 * durée des animations, mise à zéro, qui porte cette demande — que ce soit
 * depuis les options pour développeurs ou depuis les réglages d'accessibilité
 * du constructeur.
 *
 * @return vrai si tout mouvement doit être remplacé par un changement immédiat.
 */
fun Context.prefersReducedMotion(): Boolean {
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
