package io.github.mgdx.rouelibre.ui

import android.content.Intent
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import io.github.mgdx.rouelibre.core.intent.findPlaceInText
import io.github.mgdx.rouelibre.core.intent.parsePlaceUri

/**
 * What an incoming intent asks for, if anything (SPEC §7.8).
 *
 * The parsing itself lives in the business module, testable on the JVM: all
 * that remains here is knowing where to read, depending on whether the place
 * arrives through a URI or through shared text.
 *
 * @return the place requested, or `null` if this intent carries none.
 */
fun Intent.toPlaceRequest(): PlaceRequest? = when (action) {
    Intent.ACTION_VIEW -> data?.toString()?.let(::parsePlaceUri)

    Intent.ACTION_SEND -> {
        // The subject sometimes carries the place and the body a comment; we
        // take the first of the two that yields something usable.
        val body = getStringExtra(Intent.EXTRA_TEXT)
        val subject = getStringExtra(Intent.EXTRA_SUBJECT)
        listOfNotNull(body, subject)
            .firstNotNullOfOrNull { text -> findPlaceInText(text) }
    }

    else -> null
}
