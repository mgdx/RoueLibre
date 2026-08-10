package io.github.mgdx.rouelibre.core.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A geographic point, in WGS 84 decimal degrees. */
public data class Coordinates(public val latitude: Double, public val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of bounds: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of bounds: $longitude" }
    }
}

/** The earth's mean radius, in metres. */
private const val EARTH_RADIUS_METRES = 6_371_008.8

/**
 * The great-circle distance between two points, in metres.
 *
 * The haversine formula. Over the few kilometres separating two stations, it
 * and an ellipsoidal formula differ by less than a metre, for a fraction of the
 * cost — and this distance is called thousands of times while choosing the
 * candidate stations (SPEC §6).
 */
public fun Coordinates.distanceInMetresTo(other: Coordinates): Double {
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)

    val chordHalf = sin(latitudeDelta / 2).let { it * it } +
        cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).let { it * it }
    // `min` guards against the rounding error that, on two coincident points,
    // would push the argument just past 1 and make this return NaN.
    return 2 * EARTH_RADIUS_METRES * asin(min(1.0, sqrt(chordHalf)))
}

/**
 * A geographic rectangle, in WGS 84 decimal degrees.
 *
 * The application's reference bounding box (SPEC §4) is never written in the
 * code: it is read from the city configuration and from the data manifest. This
 * type merely carries it.
 */
public data class BoundingBox(
    public val south: Double,
    public val west: Double,
    public val north: Double,
    public val east: Double,
) {
    init {
        require(south <= north) { "bounding box inverted in latitude" }
        require(west <= east) { "bounding box inverted in longitude" }
    }

    /** Tells whether [point] falls inside the rectangle, edges included. */
    public operator fun contains(point: Coordinates): Boolean =
        point.latitude in south..north && point.longitude in west..east

    /** The rectangle's centre, used when the map is first displayed. */
    public val centre: Coordinates
        get() = Coordinates((south + north) / 2, (west + east) / 2)

    /**
     * The approximate distance from [point] to the nearest edge, in metres, or
     * `0.0` if it is inside.
     *
     * Used to shade the message shown for a point outside the box: "just
     * outside" and "at the other end of the country" do not call for the same
     * answer.
     */
    public fun distanceOutsideInMetres(point: Coordinates): Double {
        if (point in this) return 0.0
        val nearest = Coordinates(
            latitude = point.latitude.coerceIn(south, north),
            longitude = point.longitude.coerceIn(west, east),
        )
        return point.distanceInMetresTo(nearest)
    }

    /** True if the rectangle has a non-zero area and plausible bounds. */
    public val isUsable: Boolean
        get() = abs(north - south) > 0.0 && abs(east - west) > 0.0
}
