package io.github.mgdx.rouelibre.core.geo

import kotlin.math.log2
import kotlin.math.max

/**
 * Where the centre of a viewport may sit for that viewport to stay inside the
 * box.
 *
 * The offline data stops at the reference bounding box (SPEC §4): past its edge
 * the map has nothing left to draw. Holding the *centre* inside the box is not
 * enough to keep that emptiness off the screen — most of the viewport still
 * hangs over it — so the box is shrunk by how far the viewport reaches from its
 * own centre.
 *
 * The four reaches are asked for one by one rather than as two half-spans
 * because on a Mercator map they are not equal in pairs: the same number of
 * pixels covers more degrees towards the pole than towards the equator, and
 * halving the span would leave a sliver of nothing showing along the poleward
 * edge.
 *
 * @param northwardReach the degrees of latitude between the centre of the
 *   visible region and its northern edge, and so on for the three others.
 * @return the rectangle the centre must stay within. It collapses onto the
 *   box's own middle along whichever axis the viewport is the wider of the two:
 *   the edge then cannot be hidden, and the best left to do is to keep it as
 *   far off as possible.
 */
public fun BoundingBox.centresKeepingViewportInside(
    northwardReach: Double,
    southwardReach: Double,
    eastwardReach: Double,
    westwardReach: Double,
): BoundingBox {
    val middle = centre
    val southmost = south + southwardReach
    val northmost = north - northwardReach
    val westmost = west + westwardReach
    val eastmost = east - eastwardReach
    val latitudeFits = southmost <= northmost
    val longitudeFits = westmost <= eastmost
    return BoundingBox(
        south = if (latitudeFits) southmost else middle.latitude,
        west = if (longitudeFits) westmost else middle.longitude,
        north = if (latitudeFits) northmost else middle.latitude,
        east = if (longitudeFits) eastmost else middle.longitude,
    )
}

/**
 * The widest zoom at which a viewport still fits inside the box.
 *
 * Measured rather than computed: the caller hands in the visible region it has
 * at [zoom], and the answer follows from how zoom and span relate — one step
 * out doubles both spans, so the shortfall is read as a base-2 logarithm. That
 * keeps this free of the tile size and of the screen density, which the map
 * engine already accounted for in the region it reported.
 *
 * @param zoom the zoom the visible region was measured at.
 * @param visibleLatitudeSpan the height of that region, in degrees.
 * @param visibleLongitudeSpan its width, in degrees.
 * @return the smallest zoom that keeps the box covering the screen; [zoom]
 *   itself if the box has no extent to speak of.
 */
public fun BoundingBox.zoomKeepingViewportInside(
    zoom: Double,
    visibleLatitudeSpan: Double,
    visibleLongitudeSpan: Double,
): Double {
    if (!isUsable) return zoom
    val overflow = max(
        visibleLatitudeSpan / (north - south),
        visibleLongitudeSpan / (east - west),
    )
    if (overflow <= 0.0) return zoom
    return zoom + log2(overflow)
}
