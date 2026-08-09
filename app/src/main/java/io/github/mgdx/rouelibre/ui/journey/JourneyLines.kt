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
 * Le tracé d'un trajet sur la carte (SPEC §7.4).
 *
 * Trois segments **visuellement distincts**, comme l'exige le SPEC : les deux
 * marches sont pointillées et fines, le trajet à vélo est plein et large. La
 * différence tient donc à la forme du trait autant qu'à sa couleur — un
 * daltonien lit la même chose que tout le monde.
 *
 * Les deux marches partagent le même style : ce sont le même effort et la même
 * vitesse, seule leur place dans le trajet les distingue, et la carte le montre
 * assez.
 */
object JourneyLines {

    /** Identifiant de la source portant les segments à pied. */
    const val WALK_SOURCE_ID: String = "trajet-marche"

    /** Identifiant de la source portant le segment à vélo. */
    const val RIDE_SOURCE_ID: String = "trajet-velo"

    /** Couche des segments à pied. */
    const val WALK_LAYER_ID: String = "trajet-marche-trait"

    /** Couche du segment à vélo. */
    const val RIDE_LAYER_ID: String = "trajet-velo-trait"

    /** Le trait du vélo : plein, large, dans la teinte de signal. */
    fun rideLayer(context: Context): LineLayer = LineLayer(RIDE_LAYER_ID, RIDE_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(ContextCompat.getColor(context, R.color.signal)),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    /** Le trait des marches : fin et pointillé, comme un pas après l'autre. */
    fun walkLayer(context: Context): LineLayer = LineLayer(WALK_LAYER_ID, WALK_SOURCE_ID)
        .withProperties(
            PropertyFactory.lineColor(ContextCompat.getColor(context, R.color.encre)),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            // Un pointillé serré : sur un écran de téléphone, un tireté large
            // se confond avec une route de la carte.
            PropertyFactory.lineDasharray(arrayOf(0.6f, 1.6f)),
        )

    /** Les deux marches d'une proposition. */
    fun walkFeatures(option: JourneyOption): FeatureCollection = FeatureCollection.fromFeatures(
        listOf(option.walkToStation, option.walkToDestination).mapNotNull(::toFeature),
    )

    /** Le trajet à vélo d'une proposition. */
    fun rideFeatures(option: JourneyOption): FeatureCollection = FeatureCollection.fromFeatures(
        listOfNotNull(toFeature(option.ride)),
    )

    /** Un tracé isolé — la marche directe, quand c'est elle que l'on montre. */
    fun featuresOf(leg: RouteLeg?): FeatureCollection = FeatureCollection.fromFeatures(
        listOfNotNull(leg?.let(::toFeature)),
    )

    private fun toFeature(leg: RouteLeg): Feature? {
        // Un segment d'un seul point n'est pas une ligne : MapLibre rejette la
        // géométrie, plutôt que de ne rien dessiner.
        if (leg.geometry.size < 2) return null
        return Feature.fromGeometry(
            LineString.fromLngLats(
                leg.geometry.map { Point.fromLngLat(it.longitude, it.latitude) },
            ),
        )
    }
}
