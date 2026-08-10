package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * A journey drawn on the map (SPEC §7.4).
 *
 * Three **visually distinct** legs, as the specification requires: both walks
 * are thin and dotted, the bike leg is solid and wide. The difference therefore
 * rests on the stroke's shape as much as on its colour — a colour-blind reader
 * takes the same meaning from it as anyone else.
 *
 * Both walks share one style: they are the same effort at the same speed, only
 * their place in the journey tells them apart, and the map shows that well
 * enough.
 */
object JourneyLines {

    /** The identifier of the source carrying the walking legs. */
    const val WALK_SOURCE_ID: String = "trajet-marche"

    /** The identifier of the source carrying the bike leg. */
    const val RIDE_SOURCE_ID: String = "trajet-velo"

    /** The walking legs' layer. */
    const val WALK_LAYER_ID: String = "trajet-marche-trait"

    /** The bike leg's layer. */
    const val RIDE_LAYER_ID: String = "trajet-velo-trait"

    /** The bike's stroke: solid, wide, in the signal hue. */
    fun rideLayer(context: Context): LineLayer = LineLayer(RIDE_LAYER_ID, RIDE_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(ContextCompat.getColor(context, R.color.signal)),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    /** The walks' stroke: thin and dotted, like one step after another. */
    fun walkLayer(context: Context): LineLayer = LineLayer(WALK_LAYER_ID, WALK_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(ContextCompat.getColor(context, R.color.ink)),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            // A tight dot pattern: on a phone screen, wide dashes get confused
            // with a road on the map.
            PropertyFactory.lineDasharray(arrayOf(0.6f, 1.6f)),
        )

    /** An option's two walking legs. */
    fun walkFeatures(option: JourneyOption): FeatureCollection = FeatureCollection.fromFeatures(
        listOf(option.walkToStation, option.walkToDestination).mapNotNull(::toFeature),
    )

    /** An option's bike leg. */
    fun rideFeatures(option: JourneyOption): FeatureCollection = FeatureCollection.fromFeatures(
        listOfNotNull(toFeature(option.ride)),
    )

    /** A lone track — the direct walk, when that is what is being shown. */
    fun featuresOf(leg: RouteLeg?): FeatureCollection = FeatureCollection.fromFeatures(
        listOfNotNull(leg?.let(::toFeature)),
    )

    private fun toFeature(leg: RouteLeg): Feature? {
        // A leg of a single point is not a line: MapLibre rejects the geometry
        // rather than drawing nothing.
        if (leg.geometry.size < 2) return null
        return Feature.fromGeometry(
            LineString.fromLngLats(
                leg.geometry.map { Point.fromLngLat(it.longitude, it.latitude) },
            ),
        )
    }
}
