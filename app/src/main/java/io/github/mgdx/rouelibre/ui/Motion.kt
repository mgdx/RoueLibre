package io.github.mgdx.rouelibre.ui

import android.content.Context
import android.provider.Settings

/**
 * Tells whether the device asks for animations to be reduced.
 *
 * SPEC §7 makes it a non-negotiable constraint. Android exposes no "reduce
 * animations" preference as such: it is the animation duration scale, set to
 * zero, that carries the request — whether from the developer options or from
 * the manufacturer's accessibility settings.
 *
 * @return true if every movement must be replaced by an immediate change.
 */
fun Context.prefersReducedMotion(): Boolean {
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
