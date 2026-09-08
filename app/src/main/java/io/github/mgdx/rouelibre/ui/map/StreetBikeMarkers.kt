package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.kind
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Building the markers of the bikes a network reports outside its stations
 * (SPEC §7.1, §4.1).
 *
 * Everything here is said against [StationMarkers], which is the marker the
 * user already knows:
 *
 *  * **smaller**, about two thirds of a station's disc at every zoom, because
 *    what it stands for is smaller — one bike against a rack of them;
 *  * **without a figure**, because a bike is one bike and a "1" written on
 *    every one of them would say nothing while making the map shout;
 *  * **bearing the bolt** where the network's type table reads the bike as
 *    electric, which is the mark the rest of the interface already carries;
 *  * **clustered by the same mechanism, at the same zooms**, so that levélo's
 *    145 bikes in one spot of the Arnavaux industrial zone read as a hundred
 *    and forty-five and not as a blot. Depots are drawn as they are: no
 *    heuristic tells one from a bike left at a kerb, and any that did would be
 *    a coefficient nobody measured (SPEC §7.1, §14).
 *
 * The colours are the availability scale's strongest step and the ink it lays
 * on it — the tokens a well-stocked station already wears, which invert
 * between the light and the dark theme by themselves. A bike outside stations
 * is a bike one may find there, and giving it a hue of its own would be a
 * seventh colour on a map whose palette is spent (SPEC §7).
 *
 * Nothing drawn here says whether the bike may be taken where it stands: the
 * network's rules decide that, GBFS does not carry them, and the sheet and the
 * explanation dialog are where it is said (SPEC §7.6).
 */
object StreetBikeMarkers {

    /** The identifier of the GeoJSON source carrying the bikes. */
    const val SOURCE_ID: String = "street-bikes"

    /** The layer of the discs of individual bikes. */
    const val CIRCLE_LAYER: String = "street-bikes-disc"

    /** The layer of the bolt a bike read as electric carries. */
    const val BOLT_LAYER: String = "street-bikes-bolt"

    /** The cluster layer, at distant zooms. */
    const val CLUSTER_CIRCLE_LAYER: String = "street-bikes-cluster"

    /** The layer of the count a cluster carries. */
    const val CLUSTER_COUNT_LAYER: String = "street-bikes-cluster-count"

    /** The property carrying the identifier, to find the bike on a tap. */
    const val BIKE_ID_PROPERTY: String = "bikeId"

    /** The property saying whether the bike bears the bolt. */
    const val ELECTRIC_PROPERTY: String = "electric"

    /** The figures' typeface, the same as the station clusters'. */
    private val DIGIT_FONT = arrayOf("Bricolage Grotesque Bold")

    private const val BOLT_IMAGE_ID = "street-bike-bolt"

    /**
     * Turns the bikes into GeoJSON features for the map.
     *
     * Every bike given is drawn, wherever the feed puts it: what may be shown
     * at all was settled upstream, at parse time — a bike at a station, a
     * disabled one, a reserved one and a scooter never reach this list (SPEC
     * §4.1).
     *
     * @param bikes the bikes outside stations, as the last read holds them.
     * @param vehicleTypes the kind of each vehicle type identifier of this
     *   network, without which no identifier can be read as a kind. Empty
     *   until the type feed has been reached, which draws every bike plain —
     *   the drawing that promises the least.
     */
    fun toFeatureCollection(
        bikes: List<StreetBike>,
        vehicleTypes: Map<String, VehicleKind>,
    ): FeatureCollection {
        val features = bikes.map { bike ->
            Feature.fromGeometry(
                Point.fromLngLat(bike.position.longitude, bike.position.latitude),
            ).apply {
                addStringProperty(BIKE_ID_PROPERTY, bike.id)
                addBooleanProperty(
                    ELECTRIC_PROPERTY,
                    bike.kind(vehicleTypes) == VehicleKind.Electric,
                )
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Registers the bolt's image in the style.
     *
     * MapLibre draws raster images only: the vector is rendered once, at the
     * screen's density, rather than shipped in five sizes in the APK — the
     * path [PickedPlaceMarker] already follows.
     */
    fun registerImage(context: Context, style: Style) {
        val bolt = checkNotNull(
            AppCompatResources.getDrawable(context, R.drawable.marker_street_bike_bolt),
        ) {
            "bolt of the bikes outside stations not found"
        }
        style.addImage(BOLT_IMAGE_ID, bolt.toBitmap())
    }

    /**
     * An individual bike's disc.
     *
     * Two thirds of a station's radius at both ends of the ramp — 7 and 15
     * become 4.7 and 10 — so the difference holds at every zoom rather than
     * only where the stops were written. It keeps the station's stroke, which
     * is what separates two markers standing on the same pavement.
     */
    fun circleLayer(context: Context): CircleLayer = CircleLayer(CIRCLE_LAYER, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(11, 4.7f),
                    Expression.stop(16, 10f),
                ),
            ),
            PropertyFactory.circleColor(colour(context, R.color.availability_good)),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(colour(context, R.color.surface)),
        )
        .withFilter(Expression.not(Expression.has("point_count")))

    /**
     * The bolt, on the bikes the type table reads as electric.
     *
     * It follows the disc's own ramp rather than being drawn at one size: a
     * bolt as wide at the widest zoom as it is at the closest would be a
     * smudge over a disc of four pixels.
     */
    fun boltLayer(): SymbolLayer = SymbolLayer(BOLT_LAYER, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(BOLT_IMAGE_ID),
            PropertyFactory.iconSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(11, 0.5f),
                    Expression.stop(16, 1f),
                ),
            ),
            // The bolt belongs to its disc: it must never be pushed aside by
            // automatic label placement.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
        .withFilter(
            Expression.all(
                Expression.not(Expression.has("point_count")),
                Expression.toBool(Expression.get(ELECTRIC_PROPERTY)),
            ),
        )

    /**
     * The disc of a cluster of bikes, at distant zooms.
     *
     * Outlined and smaller, on the station cluster's pattern and for its
     * reasons — a cluster is a transient state of the zoom, and twenty filled
     * discs would be the loudest thing on a deliberately desaturated map. What
     * tells the two clusters apart is the ink of the outline: a station
     * cluster is drawn in the ink of the interface, this one in the green of
     * the discs it collapses, so a count of bikes is never read as a count of
     * stations.
     */
    fun clusterLayer(context: Context): CircleLayer = CircleLayer(CLUSTER_CIRCLE_LAYER, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("point_count"),
                    Expression.stop(2, 8f),
                    Expression.stop(30, 13f),
                    Expression.stop(120, 17f),
                ),
            ),
            PropertyFactory.circleColor(colour(context, R.color.surface)),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(colour(context, R.color.availability_good)),
            PropertyFactory.circleOpacity(0.92f),
        )
        .withFilter(Expression.has("point_count"))

    /**
     * The number of bikes in a cluster.
     *
     * **Written in Latin digits**, as the station counts are and for the very
     * same reason: MapLibre writes `point_count_abbreviated` itself, out of
     * reach of any locale, and the digit stack baked into the assets carries
     * the first range of 256 characters alone. A range asked for and not
     * served empties the tile that needed it, streets included — see
     * `MapGlyphsTest` and the note in [StationMarkers].
     */
    fun clusterCountLayer(context: Context): SymbolLayer =
        SymbolLayer(CLUSTER_COUNT_LAYER, SOURCE_ID)
            .withProperties(
                PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                PropertyFactory.textFont(DIGIT_FONT),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor(colour(context, R.color.ink)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
            .withFilter(Expression.has("point_count"))

    private fun colour(context: Context, resource: Int) = ContextCompat.getColor(context, resource)
}
