package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.core.geo.uncertaintyCircle
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The user's position on the map.
 *
 * A disc ringed in white, the convention everyone recognises. It carries no
 * figure, unlike the station markers, and no tip, unlike the searched point:
 * the three tell themselves apart at a glance.
 *
 * Under the disc, a translucent grey circle as wide as the fix's announced
 * accuracy, drawn only when that accuracy is too coarse to be trusted on its
 * own (SPEC §7.1). The disc alone has the same size at eight metres as at
 * eight hundred, and reads as a certainty in both cases.
 *
 * This point never leaves the device and is written nowhere: it lives in the
 * GeoJSON sources for as long as it is displayed (SPEC §2, C3).
 */
object UserPositionMarker {

    /** The identifier of the GeoJSON source carrying the position. */
    const val SOURCE_ID: String = "user-position"

    /** The identifier of the source carrying the circle of uncertainty. */
    const val ACCURACY_SOURCE_ID: String = "user-position-accuracy"

    /** The position disc's layer. */
    const val LAYER_ID: String = "user-position-disc"

    /** The circle of uncertainty's layer. */
    const val ACCURACY_LAYER_ID: String = "user-position-accuracy"

    /** The property that says the fix has aged past being believed. */
    const val STALE_PROPERTY: String = "stale"

    /**
     * The disc, laid above the stations.
     *
     * Its ink drains to grey when the fix goes stale: a disc still drawn full
     * a minute after the last fix asserts a position nobody is measuring any
     * more, and grey is how it says "last seen here" instead (SPEC §7.1).
     */
    fun layer(context: Context): CircleLayer = CircleLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(
                Expression.switchCase(
                    Expression.toBool(Expression.get(STALE_PROPERTY)),
                    Expression.color(ContextCompat.getColor(context, R.color.ink_soft)),
                    Expression.color(ContextCompat.getColor(context, R.color.ink)),
                ),
            ),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(ContextCompat.getColor(context, R.color.surface)),
        )

    /**
     * The circle of uncertainty, laid under the disc.
     *
     * A filled polygon and not a `circleRadius`, which is a count of screen
     * pixels: a radius in pixels keeps its size as one zooms in, so it would
     * cover less and less ground while claiming to cover the same — the exact
     * opposite of what this circle is for.
     *
     * Faint on purpose: it is the doubt around the answer, not the answer.
     */
    fun accuracyLayer(context: Context): FillLayer =
        FillLayer(ACCURACY_LAYER_ID, ACCURACY_SOURCE_ID)
            .withProperties(
                PropertyFactory.fillColor(ContextCompat.getColor(context, R.color.ink_soft)),
                PropertyFactory.fillOpacity(ACCURACY_OPACITY),
            )

    /**
     * The source's contents: the position, or nothing.
     *
     * @param stale whether the fix is only where the device was last seen —
     *   the disc then greys, see [layer].
     */
    fun featureFor(fix: PositionFix?, stale: Boolean = false): FeatureCollection {
        if (fix == null) return FeatureCollection.fromFeatures(emptyList())
        val position = fix.coordinates
        val point = Feature.fromGeometry(
            Point.fromLngLat(position.longitude, position.latitude),
        )
        point.addBooleanProperty(STALE_PROPERTY, stale)
        return FeatureCollection.fromFeatures(listOf(point))
    }

    /** The circle's contents: the ring around the position, or nothing. */
    fun accuracyFeatureFor(fix: PositionFix?): FeatureCollection {
        val circle = fix?.uncertaintyCircle() ?: return FeatureCollection.fromFeatures(emptyList())
        val ring = circle.map { Point.fromLngLat(it.longitude, it.latitude) }
        return FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))),
        )
    }

    /**
     * How opaque the circle is.
     *
     * Fifteen per cent: enough for the tint to read over the pale land and the
     * dark one alike, little enough for the streets under it to stay legible —
     * the circle says how far the doubt goes, it does not hide the map inside
     * it.
     */
    private const val ACCURACY_OPACITY = 0.15f
}
