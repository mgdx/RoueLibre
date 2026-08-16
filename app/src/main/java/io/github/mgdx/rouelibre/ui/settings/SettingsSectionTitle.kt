package io.github.mgdx.rouelibre.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat

/**
 * A section title on the settings screen (SPEC §7.6).
 *
 * It exists for a single line, [ViewCompat.setAccessibilityHeading]. A screen
 * reader offers the sections as stops one can jump between only if each title
 * says it is a heading, and the attribute that says so in a layout,
 * `android:accessibilityHeading`, arrived in API 28 — two releases above this
 * application's floor. Set from here, the heading holds on every device the
 * application runs on.
 *
 * A view of its own rather than a line in [SettingsFragment] because settings
 * are due to arrive in those sections one at a time and from different hands: a
 * title that had to be registered somewhere else to be a heading would one day
 * be added without it, and nothing would show that it had been. Placing the
 * view is the whole of what a section costs.
 *
 * Its face, its size and the air above it come from the style it is given,
 * `Widget.RoueLibre.SettingsSection`.
 */
class SettingsSectionTitle @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attributes, defaultStyle) {

    init {
        ViewCompat.setAccessibilityHeading(this, true)
    }
}
