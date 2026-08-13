package io.github.mgdx.rouelibre.core.routing

import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo

/**
 * One reading of the ground under a leg.
 *
 * @property distanceMetres how far along the leg it stands, from its start.
 * @property elevationMetres how high above sea level it stands.
 */
public data class ElevationPoint(
    public val distanceMetres: Double,
    public val elevationMetres: Double,
)

/**
 * The shape of the ground a leg runs over, from its start to its end.
 *
 * The leg carries its elevations point by point (see [RouteLeg]); what a
 * drawing of them needs is how far along each one stands, which is the
 * distance walked or ridden to reach it — not the straight line from the
 * start. It is therefore accumulated from one point to the next.
 *
 * A point whose elevation the graph does not carry is left out rather than
 * guessed at: a hole in the readings is a hole in what we know, and joining
 * the two sides of it is the drawing's business, not this function's. A leg
 * with no elevation at all returns an empty profile, which is what a city
 * whose graph was generated without elevation produces (SPEC §7.4).
 *
 * @return the readings in order, possibly empty, never guessed.
 */
public fun RouteLeg.elevationProfile(): List<ElevationPoint> {
    if (elevationsMetres.isEmpty()) return emptyList()
    val profile = mutableListOf<ElevationPoint>()
    var travelled = 0.0
    geometry.forEachIndexed { index, point ->
        if (index > 0) travelled += geometry[index - 1].distanceInMetresTo(point)
        val elevation = elevationsMetres.getOrNull(index) ?: return@forEachIndexed
        profile += ElevationPoint(travelled, elevation)
    }
    return profile
}

/**
 * The same profile, with the sampling's own noise taken out of it.
 *
 * The elevations come from SRTM samples some thirty metres apart, whose
 * vertical error runs to several metres. Drawn as they come, a ride across flat
 * ground is a saw: two metres up, two metres down, every fifty metres, none of
 * it on the ground. That is the very reason a climb is not named under three
 * hundred metres of ground (SPEC §7.4), and a drawing has to answer it too —
 * with the difference that it cannot simply keep quiet, since the same curve
 * carries the real hill further on.
 *
 * Each reading is therefore replaced by the mean of those within half a window
 * of it, measured along the leg rather than in points: readings crowd together
 * where the track turns, and a window counted in points would smooth a
 * hairpin's climb away while leaving a straight one untouched. A window of
 * [windowMetres] leaves a rise of that length standing and flattens what is
 * shorter, which is the split the section already draws.
 *
 * @param windowMetres the ground a reading is averaged over, centred on it.
 * @return a profile of the same length, at the same distances.
 */
public fun List<ElevationPoint>.smoothedOver(windowMetres: Double): List<ElevationPoint> {
    if (size < 3 || windowMetres <= 0.0) return this
    val half = windowMetres / 2
    var first = 0
    var last = 0
    var sum = 0.0
    return mapIndexed { index, point ->
        // The two ends of the window only ever move forwards, so the whole
        // sweep costs one pass however many readings a thirty-kilometre ride
        // brings back.
        while (last < size && this[last].distanceMetres <= point.distanceMetres + half) {
            sum += this[last].elevationMetres
            last++
        }
        while (this[first].distanceMetres < point.distanceMetres - half) {
            sum -= this[first].elevationMetres
            first++
        }
        val count = last - first
        if (count <= 0) this[index] else point.copy(elevationMetres = sum / count)
    }
}
