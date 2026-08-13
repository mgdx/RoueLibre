package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * A place received from another application (SPEC §7.8).
 *
 * Two forms only, because they call for two different follow-ups: a point is
 * already located, a search still has to be resolved by the address index — on
 * the device, without a single request going out.
 */
public sealed interface PlaceRequest {

    /** The label received, if there was one. */
    public val label: String?

    /**
     * A point designated by its coordinates.
     *
     * @property coordinates the place aimed at.
     * @property label what the sender said about it, to be shown rather than
     *   raw coordinates.
     */
    public data class Point(
        public val coordinates: Coordinates,
        override val label: String? = null,
    ) : PlaceRequest

    /**
     * A place described in words, to be looked up in the index.
     *
     * The text is **finished**: it comes whole from another application, and
     * nobody is going to add a letter to it or pick from a list of guesses. The
     * index is therefore searched for the words themselves — see
     * `WordMatching.WholeWords` — failing which a sentence naming no address at
     * all would still be fitted to the street it happens to begin like.
     *
     * @property text what the sender wrote.
     */
    public data class Search(public val text: String) : PlaceRequest {
        override val label: String? get() = text
    }
}

/**
 * Parses a `geo:` or `google.navigation:` URI (SPEC §7.8).
 *
 * Every form met in practice is accepted, because none of them is rare:
 *
 * ```
 * geo:50.6371,3.0630
 * geo:50.6371,3.0630?z=17
 * geo:0,0?q=50.6371,3.0630
 * geo:0,0?q=50.6371,3.0630(Grand-Place)
 * geo:0,0?q=12+rue+Nationale+Lille
 * google.navigation:q=50.6371,3.0630
 * ```
 *
 * The leading `geo:0,0` is an established convention: it means "the point is in
 * the query, not here". Taking it literally would send the user out into the
 * Gulf of Guinea.
 *
 * @param uri the URI received, as it came.
 * @return the place requested, or `null` if the URI describes nothing usable.
 */
public fun parsePlaceUri(uri: String): PlaceRequest? {
    val trimmed = uri.trim()
    val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    val body = trimmed.substringAfter(':', missingDelimiterValue = "")
    if (body.isEmpty()) return null

    return when (scheme) {
        "geo" -> parseGeoBody(body)
        "google.navigation" -> parseQuery(parametersOf(body)["q"])
        "http", "https" -> parseWebMapLink(trimmed)
        else -> null
    }
}

/**
 * Extracts a place from a web map link (SPEC §7.8).
 *
 * These links cannot be verified automatically: the domains involved do not
 * belong to the project, and since Android 12 they only reach the application
 * if the user allows it in the system settings. The procedure is explained in
 * the "about" screen and in the `README.md`, failing which the behaviour would
 * look like a defect.
 *
 * Three spellings cover most of what circulates:
 *
 * ```
 * https://www.google.com/maps/@50.6371,3.0630,17z
 * https://www.google.com/maps?q=50.6371,3.0630
 * https://www.openstreetmap.org/#map=17/50.6371/3.0630
 * ```
 *
 * Nothing is downloaded to resolve them: a shortened link, whose place only
 * appears after a redirect, is therefore not recognised. Following the redirect
 * would send a request to a third party — and tell it where the user is going,
 * which constraint C3 forbids.
 */
private fun parseWebMapLink(uri: String): PlaceRequest? {
    AT_COORDINATES.find(uri)?.let { match ->
        parseCoordinates("${match.groupValues[1]},${match.groupValues[2]}")
            ?.let { return PlaceRequest.Point(it) }
    }
    OSM_HASH_COORDINATES.find(uri)?.let { match ->
        parseCoordinates("${match.groupValues[1]},${match.groupValues[2]}")
            ?.let { return PlaceRequest.Point(it) }
    }
    // A web link's "q=" sometimes carries coordinates, sometimes an address;
    // both resolve as they do elsewhere.
    return parseQuery(parametersOf(uri.substringAfter("://"))["q"])
}

/** "/@50.6371,3.0630" — the Google Maps spelling. */
private val AT_COORDINATES = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")

/** "#map=17/50.6371/3.0630" — the OpenStreetMap one. */
private val OSM_HASH_COORDINATES =
    Regex("""#map=[\d.]+/(-?\d{1,3}\.\d+)/(-?\d{1,3}\.\d+)""")

private fun parseGeoBody(body: String): PlaceRequest? {
    val path = body.substringBefore('?')
    val parameters = parametersOf(body)

    // The query wins over the path: when both are present, the path only
    // carries the conventional "0,0".
    parseQuery(parameters["q"])?.let { return it }
    return parseCoordinates(path)?.let { PlaceRequest.Point(it) }
}

/**
 * Parses the value of a `q` parameter.
 *
 * It carries either coordinates, possibly followed by a label in parentheses,
 * or an address in words.
 */
private fun parseQuery(rawQuery: String?): PlaceRequest? {
    val query = rawQuery?.let(::decodeUriComponent)?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null

    val label = query.substringAfter('(', missingDelimiterValue = "")
        .substringBeforeLast(')')
        .trim()
        .takeIf { it.isNotEmpty() }
    val beforeLabel = query.substringBefore('(').trim()

    parseCoordinates(beforeLabel)?.let { return PlaceRequest.Point(it, label) }
    return PlaceRequest.Search(query)
}

/**
 * Reads a "latitude,longitude" pair.
 *
 * @return the coordinates, or `null` if the text is not a plausible pair. A
 *   value outside the earth's bounds is refused rather than clamped: it betrays
 *   another convention, not a position.
 */
private fun parseCoordinates(text: String): Coordinates? {
    val parts = text.split(',')
    if (parts.size != 2) return null
    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().toDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    // "geo:0,0" is the conventional header of a URI whose place is in the
    // query; it is not a destination in the middle of the Atlantic.
    if (latitude == 0.0 && longitude == 0.0) return null
    return Coordinates(latitude, longitude)
}

private fun parametersOf(body: String): Map<String, String> {
    // "geo:" separates its parameters with a question mark, but
    // "google.navigation:" puts them straight in the body: both forms occur,
    // and the second has no separator to look for.
    val query = if ('?' in body) body.substringAfter('?') else body
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { parameter ->
        val name = parameter.substringBefore('=', missingDelimiterValue = "")
        val value = parameter.substringAfter('=', missingDelimiterValue = "")
        if (name.isEmpty() || value.isEmpty()) null else name.lowercase() to value
    }.toMap()
}

/**
 * Decodes URI escapes, without depending on Android.
 *
 * The business module does not know `Uri.decode`; it must stay compilable and
 * testable on the JVM (SPEC §14). A `+` means a space, as in an HTTP query —
 * that is how mapping applications write their addresses.
 */
private fun decodeUriComponent(value: String): String {
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character == '+' -> {
                builder.append(' ')
                index++
            }

            character == '%' && index + 2 < value.length -> {
                val code = value.substring(index + 1, index + 3).toIntOrNull(radix = 16)
                if (code == null) {
                    builder.append(character)
                    index++
                } else {
                    builder.append(code.toChar())
                    index += 3
                }
            }

            else -> {
                builder.append(character)
                index++
            }
        }
    }
    return builder.toString()
}

/**
 * Looks for a place inside shared text (SPEC §7.8).
 *
 * This is the commonest case in practice: an address received over a messaging
 * application, then shared into this one. A coordinate pair and a pasted `geo:`
 * URI are picked out of the middle of a sentence; anything else is handed on as
 * it came, for the index to answer or not.
 *
 * Nothing here decides whether the text holds an address: the index alone knows
 * the streets, and it is asked the question in the terms of a finished text
 * (see [PlaceRequest.Search]). A sentence naming no address then comes back
 * empty, which is what the screen has to say.
 *
 * @param text the shared text, as it came.
 * @return the place recognised, or `null` if the text is empty.
 */
public fun findPlaceInText(text: String): PlaceRequest? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    // A URI pasted into the text is still a URI: recognising it avoids looking
    // up "geo:50.6371,3.0630" in the address index.
    URI_IN_TEXT.find(trimmed)?.let { match ->
        parsePlaceUri(match.value)?.let { return it }
    }

    COORDINATES_IN_TEXT.find(trimmed)?.let { match ->
        val latitude = match.groupValues[1].toDoubleOrNull()
        val longitude = match.groupValues[2].toDoubleOrNull()
        if (latitude != null &&
            longitude != null &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
        ) {
            return PlaceRequest.Point(Coordinates(latitude, longitude))
        }
    }

    return PlaceRequest.Search(trimmed)
}

/** A `geo:` or `google.navigation:` URI sitting in the middle of a text. */
private val URI_IN_TEXT = Regex("""(?:geo|google\.navigation):\S+""", RegexOption.IGNORE_CASE)

/**
 * A pair of decimal coordinates inside a text.
 *
 * The separator accepts a comma alone or followed by spaces: both are written,
 * and a messaging application readily adds the space. The French decimal-comma
 * notation — "50,6371" — is not recognised: it is indistinguishable from a pair
 * of two integers.
 */
private val COORDINATES_IN_TEXT = Regex("""(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")
