package io.github.mgdx.rouelibre.core.address

import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlin.math.abs

/**
 * Places a house number along a street (SPEC §4.3).
 *
 * Some Lille thoroughfares run over a kilometre: falling back on the middle of
 * the street when the requested number is absent from the index would produce
 * an error of several hundred metres — enough to designate the wrong departure
 * station, and therefore to compute a wrong journey. Hence the interpolation,
 * which brings the error down to the length of a few buildings.
 *
 * @param requestedNumber the number looked for.
 * @param requestedSuffix its repetition mark — "bis", "a" — or an empty string.
 * @param knownNumbers the numbers the index attaches to this street, in any
 *   order.
 * @param streetPosition the street's representative point, the last resort.
 * @return the position retained and how it was obtained.
 */
public fun resolveHouseNumber(
    requestedNumber: Int,
    requestedSuffix: String,
    knownNumbers: List<KnownHouseNumber>,
    streetPosition: Coordinates,
): ResolvedPosition {
    if (knownNumbers.isEmpty()) {
        return ResolvedPosition(streetPosition, PositionPrecision.StreetOnly)
    }

    exactMatch(requestedNumber, requestedSuffix, knownNumbers)?.let { match ->
        return ResolvedPosition(match.position, PositionPrecision.Exact)
    }

    // Even and odd numbers face each other across the roadway: interpolating 13
    // between 12 and 14 would put it on the opposite pavement, and at a
    // junction, in the perpendicular street. Neighbours of the same parity are
    // therefore looked for first.
    val sameParity = knownNumbers.filter { it.number % 2 == requestedNumber % 2 }
    return interpolateAmong(requestedNumber, sameParity)
        ?: interpolateAmong(requestedNumber, knownNumbers)
        ?: ResolvedPosition(streetPosition, PositionPrecision.StreetOnly)
}

/**
 * The requested number if it appears as such.
 *
 * A number typed without a repetition mark accepts the first known one:
 * somebody typing "12" in a street that only has a "12 bis" is looking for that
 * building, not for the middle of the street.
 */
private fun exactMatch(
    number: Int,
    suffix: String,
    knownNumbers: List<KnownHouseNumber>,
): KnownHouseNumber? {
    val sameNumber = knownNumbers.filter { it.number == number }
    if (sameNumber.isEmpty()) return null
    return sameNumber.firstOrNull { it.suffix == suffix }
        ?: sameNumber.minByOrNull { it.suffix }
}

/**
 * Interpolates between the two nearest neighbours, if they bracket the number.
 *
 * @return `null` if the list provides no usable neighbour.
 */
private fun interpolateAmong(
    requestedNumber: Int,
    candidates: List<KnownHouseNumber>,
): ResolvedPosition? {
    if (candidates.isEmpty()) return null
    val below = candidates.filter { it.number < requestedNumber }.maxByOrNull { it.number }
    val above = candidates.filter { it.number > requestedNumber }.minByOrNull { it.number }

    if (below != null && above != null) {
        val span = (above.number - below.number).toDouble()
        val progress = (requestedNumber - below.number) / span
        return ResolvedPosition(
            Coordinates(
                latitude = below.position.latitude +
                    progress * (above.position.latitude - below.position.latitude),
                longitude = below.position.longitude +
                    progress * (above.position.longitude - below.position.longitude),
            ),
            PositionPrecision.Interpolated,
        )
    }

    // Only one side known: the line is not extended past the last number, for
    // want of knowing where the street continues — or even whether it does. The
    // nearest neighbour is an honest approximation; an extrapolation would be
    // an invention.
    val nearest = (below ?: above) ?: return null
    return ResolvedPosition(nearest.position, PositionPrecision.NearestKnown)
        .takeIf { abs(nearest.number - requestedNumber) <= FAR_NEIGHBOUR_LIMIT }
}

/**
 * The gap beyond which a lone neighbour no longer says anything.
 *
 * Forty numbers amount to roughly two hundred metres of frontage. Past that,
 * returning that neighbour's position would suggest a precision that does not
 * exist; the street's representative point is then more honest.
 */
private const val FAR_NEIGHBOUR_LIMIT = 40
