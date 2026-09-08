package io.github.mgdx.rouelibre.ui

import android.content.Intent
import io.github.mgdx.rouelibre.core.intent.PlaceRequest
import io.github.mgdx.rouelibre.core.intent.findPlaceInText
import io.github.mgdx.rouelibre.core.intent.parsePlaceUri

/**
 * What an incoming intent asks for (SPEC §7.8).
 *
 * **A link nobody can read is not the same thing as no link at all**, and the
 * two used to arrive here as the same `null`: `geo:999,999`, `geo:abc,def` and
 * `geo:-91.5,181.7` opened the application on its map and said nothing, leaving
 * somebody who had just tapped a place shared from elsewhere to wonder what had
 * happened. Every `VIEW` filter the manifest declares is a map link, so a URI
 * reaching this application aims at a place: failing to read it is worth the
 * same word the neighbouring case already gets, a point that is readable but
 * outside the covered area.
 */
sealed interface IncomingRequest {

    /** A place to resolve and then open. */
    data class Place(val request: PlaceRequest) : IncomingRequest

    /** A link meant for this application whose place could not be read. */
    data object Unreadable : IncomingRequest
}

/**
 * What an incoming intent asks for, if anything (SPEC §7.8).
 *
 * The parsing itself lives in the business module, testable on the JVM: all
 * that remains here is knowing where to read, depending on whether the place
 * arrives through a URI or through shared text.
 *
 * @return the request, or `null` if this intent carries none.
 */
fun Intent.toIncomingRequest(): IncomingRequest? = when (action) {
    Intent.ACTION_VIEW -> linkReceived(data?.toString())

    Intent.ACTION_SEND -> {
        // The subject sometimes carries the place and the body a comment; we
        // take the first of the two that yields something usable.
        val body = getStringExtra(Intent.EXTRA_TEXT)
        val subject = getStringExtra(Intent.EXTRA_SUBJECT)
        listOfNotNull(body, subject)
            .firstNotNullOfOrNull { text -> findPlaceInText(text) }
            ?.let(IncomingRequest::Place)
    }

    else -> null
}

/**
 * Reads the URI a `VIEW` intent came with.
 *
 * Kept apart from the intent it was read off so that it can be exercised on the
 * JVM (SPEC §14): what it decides is the difference between a silence and a
 * sentence, and that decision is worth a test of its own.
 *
 * A shared text is deliberately not given the same reading: any text at all
 * describes a place well enough to be looked up, and one that finds nothing has
 * its own answer already, with the search field filled in (SPEC §7.8).
 *
 * @param uri the URI received, or `null` where the intent carried none.
 * @return the place asked for, [IncomingRequest.Unreadable] where the link
 *   names nothing that can be read, or `null` where there was no link at all.
 */
fun linkReceived(uri: String?): IncomingRequest? {
    val link = uri?.takeIf { it.isNotBlank() } ?: return null
    return parsePlaceUri(link)?.let(IncomingRequest::Place) ?: IncomingRequest.Unreadable
}
