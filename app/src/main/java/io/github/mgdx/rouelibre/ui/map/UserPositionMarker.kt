package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The user's position on the map.
 *
 * A disc ringed in white, the convention everyone recognises. It carries no
 * figure, unlike the station markers, and no tip, unlike the searched point:
 * the three tell themselves apart at a glance.
 *
 * This point never leaves the device and is written nowhere: it lives in the
 * GeoJSON source for as long as it is displayed (SPEC §2, C3).
 */
object UserPositionMarker {

    /** The identifier of the GeoJSON source carrying the position. */
    const val SOURCE_ID: String = "position-utilisateur"

    /** The position disc's layer. */
    const val LAYER_ID: String = "position-utilisateur-disque"

    /** The disc, laid above the stations. */
    fun layer(context: Context): CircleLayer = CircleLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(ContextCompat.getColor(context, R.color.encre)),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(ContextCompat.getColor(context, R.color.surface)),
        )

    /** The source's contents: the position, or nothing. */
    fun featureFor(position: Coordinates?): FeatureCollection {
        if (position == null) return FeatureCollection.fromFeatures(emptyList())
        return FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude)),
            ),
        )
    }
}
