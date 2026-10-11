package io.github.mgdx.rouelibre.core.intent

import io.github.mgdx.rouelibre.core.geo.Coordinates
import java.util.Locale

/**
 * Writes the `geo:` URI that hands a named point over to another application.
 *
 * The point is written twice — in the path and in `q=` — because receiving
 * applications disagree on which of the two they read, and the label only
 * travels in the second (SPEC §7.4).
 *
 * @param position where the point stands.
 * @param label what the point is called; any character is accepted.
 * @return the URI, ready to be parsed by `Uri.parse`.
 */
public fun geoUri(position: Coordinates, label: String): String {
    val point = "${geoDegrees(position.latitude)},${geoDegrees(position.longitude)}"
    return "geo:$point?q=$point(${encodeGeoLabel(label)})"
}

/**
 * Writes a coordinate in fixed decimal notation.
 *
 * `Double.toString` switches to scientific notation below 0.001 in absolute
 * value, and `1.0E-4` is not a number to the `geo:` readers: OsmAnd ran a text
 * search for it instead of aiming at the point, for anything within about a
 * hundred metres of the Greenwich meridian or the equator. Six decimals are
 * 0.11 m at most, finer than any position the application holds, and keep the
 * text short. `Locale.ROOT`, because a French or German locale would write a
 * decimal comma — the very separator between latitude and longitude.
 */
internal fun geoDegrees(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

/**
 * Percent-encodes a label, leaving only RFC 3986's unreserved characters bare.
 *
 * `Uri.encode` leaves `(`, `)`, `'`, `!` and `*` alone, and a label holding a
 * parenthesis then closed `q=lat,lon(label)` early in some readers. Encoding
 * everything else as UTF-8 bytes also keeps `&` from cutting the query and `+`
 * from being read back as a space.
 */
internal fun encodeGeoLabel(label: String): String {
    val encoded = StringBuilder(label.length)
    for (byte in label.toByteArray(Charsets.UTF_8)) {
        val code = byte.toInt() and 0xFF
        val character = code.toChar()
        if (character in UNRESERVED_SYMBOLS || character.isAsciiLetterOrDigit()) {
            encoded.append(character)
        } else {
            encoded.append('%')
                .append(HEX_DIGITS[code shr 4])
                .append(HEX_DIGITS[code and 0xF])
        }
    }
    return encoded.toString()
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

private const val UNRESERVED_SYMBOLS = "-._~"

private const val HEX_DIGITS = "0123456789ABCDEF"
