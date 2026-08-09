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
 * La position de l'utilisateur sur la carte.
 *
 * Un disque cerclé de blanc, la convention que tout le monde reconnaît. Il ne
 * porte ni chiffre, contrairement aux marqueurs de stations, ni pointe,
 * contrairement au point cherché : les trois se distinguent d'un coup d'œil.
 *
 * Ce point ne quitte jamais l'appareil et n'est écrit nulle part : il vit dans
 * la source GeoJSON le temps de l'affichage (SPEC §2, C3).
 */
object UserPositionMarker {

    /** Identifiant de la source GeoJSON portant la position. */
    const val SOURCE_ID: String = "position-utilisateur"

    /** Couche du disque de position. */
    const val LAYER_ID: String = "position-utilisateur-disque"

    /** Le disque, posé au-dessus des stations. */
    fun layer(context: Context): CircleLayer = CircleLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(ContextCompat.getColor(context, R.color.encre)),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(ContextCompat.getColor(context, R.color.surface)),
        )

    /** Le contenu de la source : la position, ou rien. */
    fun featureFor(position: Coordinates?): FeatureCollection {
        if (position == null) return FeatureCollection.fromFeatures(emptyList())
        return FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude)),
            ),
        )
    }
}
