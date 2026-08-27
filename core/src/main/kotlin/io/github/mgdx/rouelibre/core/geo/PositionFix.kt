package io.github.mgdx.rouelibre.core.geo

/**
 * A position as a provider reported it: where, how well, and when.
 *
 * The application listens to every provider the device has at once (SPEC §10),
 * and they do not agree: the satellites answer in a few seconds to within ten
 * metres, the network answers in one to within several hundred. A fix
 * therefore carries what is needed to arbitrate between two of them, and never
 * travels as bare coordinates.
 *
 * @property coordinates where the provider believes the device is.
 * @property accuracyMetres the radius the provider gives itself, when it gives
 *   one at all.
 * @property takenAtMillis when the fix was taken, on a clock that only goes
 *   forward — the device's uptime, never the wall clock, which a time-zone
 *   change or a clock correction can move backwards under our feet.
 */
public data class PositionFix(
    public val coordinates: Coordinates,
    public val accuracyMetres: Double?,
    public val takenAtMillis: Long,
)

/**
 * True when this fix deserves to replace [shown].
 *
 * **The most recent fix is not the best one.** Without this arbitration the
 * displayed point freezes where it does not belong: the network provider
 * answers every couple of seconds with the same position, deduced from the
 * wifi networks in sight, a few hundred metres wide and unchanged from one
 * street to the next — and it lands on top of the satellite fix that was
 * actually following the walker.
 */
public fun PositionFix.improvesOn(shown: PositionFix?): Boolean {
    if (shown == null) return true

    val gain = takenAtMillis - shown.takenAtMillis
    // Not newer than what is displayed: it says nothing about where we are.
    if (gain <= 0) return false
    // Past this, the displayed point has had time to become false — a walker
    // covers some forty metres in half a minute. A coarse fix then beats a
    // precise one that has aged, and this is what unfreezes the point when the
    // satellites go silent under a roof.
    if (gain >= SUPERSEDING_AGE_MILLIS) return true

    val shownAccuracy = shown.accuracyMetres ?: return true
    val accuracy = accuracyMetres ?: return true
    // A fix that follows a movement is never accurate to the very same metre
    // as the one before it: demanding an improvement would pin the point to
    // the best fix of the session. Only a collapse in accuracy is turned down
    // — the kilometre-wide answer landing on top of a ten-metre one.
    return accuracy <= shownAccuracy * TOLERATED_WORSENING + TOLERATED_WORSENING_METRES
}

/**
 * Where the point stands, [fraction] of the way from this fix to [target].
 *
 * What the marker glides along between two fixes: fixes arrive a couple of
 * seconds and a few metres apart, and a point redrawn at each arrival
 * teleports from one to the next instead of walking. The screens draw the
 * intermediate steps this computes, so the point moves the way the person
 * carrying it does.
 *
 * Straight lines in degrees: over the tens of metres separating two fixes
 * they are indistinguishable from the geodesic. The one place they are not —
 * two fixes astride the antimeridian — no served city straddles, and the
 * glide would merely take the long way round for a second.
 *
 * The width of the doubt travels too, so the uncertainty circle shrinks or
 * grows with the point instead of jumping after it; a fix that announces no
 * accuracy hands the question to the other one. The timestamp is [target]'s:
 * an intermediate step is a way of drawing that fix, not a third measurement.
 */
public fun PositionFix.interpolatedTowards(target: PositionFix, fraction: Double): PositionFix {
    val walked = fraction.coerceIn(0.0, 1.0)
    return PositionFix(
        coordinates = Coordinates(
            latitude = coordinates.latitude +
                (target.coordinates.latitude - coordinates.latitude) * walked,
            longitude = coordinates.longitude +
                (target.coordinates.longitude - coordinates.longitude) * walked,
        ),
        accuracyMetres = accuracyMetres?.let { from ->
            target.accuracyMetres?.let { to -> from + (to - from) * walked }
        } ?: target.accuracyMetres,
        takenAtMillis = target.takenAtMillis,
    )
}

/**
 * True when the fix is accurate enough to stop waiting for a better one.
 *
 * Twenty-five metres: the width of a boulevard with its pavements. Below that,
 * the point lands on the right side of the street, which is all "locate me"
 * has to answer (SPEC §7.1); above it, a few more seconds of listening are
 * worth more to the user than an immediate answer.
 */
public val PositionFix.isPreciseEnough: Boolean
    get() = accuracyMetres?.let { it <= PRECISE_ENOUGH_METRES } == true

/** How old the displayed point has to be for any fresh fix to replace it. */
private const val SUPERSEDING_AGE_MILLIS = 30_000L

/** How much worse than the displayed fix a newer one may be, as a factor. */
private const val TOLERATED_WORSENING = 1.5

/** And by how many metres on top, so that precise fixes keep succeeding. */
private const val TOLERATED_WORSENING_METRES = 10.0

/** The accuracy past which waiting longer no longer serves the user. */
private const val PRECISE_ENOUGH_METRES = 25.0
