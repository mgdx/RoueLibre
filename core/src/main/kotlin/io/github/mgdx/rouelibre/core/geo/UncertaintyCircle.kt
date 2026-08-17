package io.github.mgdx.rouelibre.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ring drawn around a fix to show how wide its uncertainty is, or `null`
 * when there is nothing worth drawing.
 *
 * A disc alone claims a position the device does not have: a fix deduced from
 * the wifi networks in sight is given to within several hundred metres, and it
 * is drawn with the same assurance as a satellite fix to within eight. The ring
 * says what the disc leaves out.
 *
 * Drawn only above [isPreciseEnough]'s threshold, and that threshold rather
 * than one of its own: below twenty-five metres the point already lands on the
 * right side of the street, so the ring would circle a position that is not in
 * doubt. A fix that reports no accuracy at all gets no ring — an uncertainty we
 * have not measured is not one we may draw.
 *
 * @return the ring's vertices, the last repeating the first so it closes, in
 *   the order a polygon expects.
 */
public fun PositionFix.uncertaintyCircle(): List<Coordinates>? {
    val radiusMetres = accuracyMetres ?: return null
    if (isPreciseEnough) return null
    return coordinates.circleOfRadius(radiusMetres)
}

/**
 * The geodesic circle of radius [radiusMetres] around this point.
 *
 * Vertices placed by walking a bearing over the sphere rather than by adding
 * degrees to the coordinates: a degree of longitude is not a degree of latitude
 * anywhere but the equator, and the second method draws an ellipse that
 * flattens the further north the city is.
 */
private fun Coordinates.circleOfRadius(radiusMetres: Double): List<Coordinates> {
    val angularRadius = radiusMetres / EARTH_RADIUS_METRES
    val startLatitude = Math.toRadians(latitude)
    val startLongitude = Math.toRadians(longitude)

    return (0..CIRCLE_VERTICES).map { vertex ->
        val bearing = TURN * (vertex % CIRCLE_VERTICES) / CIRCLE_VERTICES
        val vertexLatitude = asin(
            sin(startLatitude) * cos(angularRadius) +
                cos(startLatitude) * sin(angularRadius) * cos(bearing),
        )
        val vertexLongitude = startLongitude + atan2(
            sin(bearing) * sin(angularRadius) * cos(startLatitude),
            cos(angularRadius) - sin(startLatitude) * sin(vertexLatitude),
        )
        Coordinates(
            latitude = Math.toDegrees(vertexLatitude),
            longitude = Math.toDegrees(vertexLongitude).normalisedLongitude(),
        )
    }
}

/**
 * Brings a longitude back inside [-180, 180].
 *
 * A circle drawn on a point next to the antimeridian runs past it, and
 * [Coordinates] refuses a longitude outside those bounds.
 */
private fun Double.normalisedLongitude(): Double = ((this + 540.0) % 360.0) - 180.0

/**
 * How many vertices approximate the circle.
 *
 * Sixty-four: the chord between two of them departs from the true arc by
 * 1 − cos(π/64), about one part in eight hundred of the radius. On a circle
 * three hundred metres wide that is under half a metre, far below the pixel it
 * would take for a corner to show.
 */
private const val CIRCLE_VERTICES = 64

/** A full turn, in radians. */
private const val TURN = 2 * Math.PI
