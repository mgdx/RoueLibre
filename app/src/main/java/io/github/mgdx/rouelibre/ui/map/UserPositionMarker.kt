package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.core.geo.uncertaintyCircle
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
 * Between the two, a cone peeking out from under the disc says which way the
 * walker is moving, and only while the satellites measure it: standing
 * still, the disc is bare (SPEC §7.1).
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

    /** The direction of travel, in degrees clockwise from north, when measured. */
    const val BEARING_PROPERTY: String = "bearing"

    /** The direction cone's layer, between the circle and the disc. */
    const val BEARING_LAYER_ID: String = "user-position-bearing"

    private const val BEARING_IMAGE_ID = "user-position-bearing-cone"

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
     * Registers the direction cone's image in the style.
     *
     * MapLibre only draws raster images: the vector is rendered once, at the
     * screen's density, rather than shipped in five sizes in the APK.
     */
    fun registerImage(context: Context, style: Style) {
        val drawable = checkNotNull(
            AppCompatResources.getDrawable(context, R.drawable.ic_position_bearing),
        ) { "direction cone drawable missing" }
        style.addImage(BEARING_IMAGE_ID, drawable.toBitmap())
    }

    /**
     * The direction of travel, a cone peeking out from under the disc.
     *
     * Drawn only where the feature carries a bearing at all: the satellites
     * measure one from the movement itself, so a walker who stops loses it
     * and the disc goes back to bare — a direction nobody measured is not one
     * to draw, the uncertainty circle's own rule (SPEC §7.1).
     *
     * Rotated in map space, not screen space: the cone points along the
     * street it says, however the camera is turned.
     */
    fun bearingLayer(): SymbolLayer = SymbolLayer(BEARING_LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(BEARING_IMAGE_ID),
            PropertyFactory.iconRotate(Expression.toNumber(Expression.get(BEARING_PROPERTY))),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            // The user's own direction must never be pushed aside by label
            // placement, exactly as the point itself never is.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
        .withFilter(Expression.has(BEARING_PROPERTY))

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
     *   the disc then greys, see [layer], and the cone is withheld: a stale
     *   fix's direction is where the device was going, not where it goes.
     */
    fun featureFor(fix: PositionFix?, stale: Boolean = false): FeatureCollection {
        if (fix == null) return FeatureCollection.fromFeatures(emptyList())
        val position = fix.coordinates
        val point = Feature.fromGeometry(
            Point.fromLngLat(position.longitude, position.latitude),
        )
        point.addBooleanProperty(STALE_PROPERTY, stale)
        if (!stale) {
            fix.bearingDegrees?.let { point.addNumberProperty(BEARING_PROPERTY, it) }
        }
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
