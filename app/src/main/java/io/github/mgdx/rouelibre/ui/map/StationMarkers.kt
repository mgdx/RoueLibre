package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.AvailabilityLevel
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.displayFor
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Building the station markers on the map (SPEC §7.1).
 *
 * The marker follows the same logic as the list's indicator: the disc's density
 * gives the level, the figure gives the exact count. The filling arc, however,
 * is not carried over — it would need one image per station, hundreds of
 * bitmaps for a piece of context that reads perfectly well in the detail sheet.
 *
 * As in the list, colour never carries the information alone: the figure is
 * always written on top.
 */
object StationMarkers {

    /** The identifier of the GeoJSON source carrying the stations. */
    const val SOURCE_ID: String = "stations"

    /** The layer of the discs of individual stations. */
    const val STATION_CIRCLE_LAYER: String = "stations-disque"

    /** The layer of the figures of individual stations. */
    const val STATION_COUNT_LAYER: String = "stations-nombre"

    /** The cluster layer, at distant zooms. */
    const val CLUSTER_CIRCLE_LAYER: String = "stations-amas"

    /** The layer of the count a cluster carries. */
    const val CLUSTER_COUNT_LAYER: String = "stations-amas-nombre"

    /** The property carrying the number shown: bikes or docks per the mode. */
    const val COUNT_PROPERTY: String = "count"

    /** The property carrying the level, which decides the disc's colour. */
    const val LEVEL_PROPERTY: String = "level"

    /** The property carrying the identifier, to find the station on a tap. */
    const val STATION_ID_PROPERTY: String = "stationId"

    /** The property carrying the name, spoken by screen readers. */
    const val NAME_PROPERTY: String = "name"

    private const val LEVEL_NONE = "aucun"
    private const val LEVEL_LOW = "faible"
    private const val LEVEL_MEDIUM = "moyen"
    private const val LEVEL_GOOD = "bon"
    private const val LEVEL_OUT_OF_SERVICE = "hors-service"
    private const val LEVEL_UNKNOWN = "inconnu"

    /** The figures' typeface, the same as the list indicator's. */
    private val DIGIT_FONT = arrayOf("Bricolage Grotesque Bold")

    /**
     * Turns the stations into GeoJSON features for the map.
     *
     * @param stations the known stations and their last state.
     * @param mode what the marker is to count.
     */
    fun toFeatureCollection(
        stations: List<StationWithAvailability>,
        mode: AvailabilityMode,
    ): FeatureCollection {
        val features = stations.map { entry ->
            val display = entry.displayFor(mode)
            Feature.fromGeometry(
                Point.fromLngLat(
                    entry.station.position.longitude,
                    entry.station.position.latitude,
                ),
            ).apply {
                addStringProperty(STATION_ID_PROPERTY, entry.station.id)
                addStringProperty(NAME_PROPERTY, entry.station.name)
                addStringProperty(
                    LEVEL_PROPERTY,
                    when {
                        display.isOutOfService -> LEVEL_OUT_OF_SERVICE
                        display.level == null -> LEVEL_UNKNOWN
                        display.level == AvailabilityLevel.None -> LEVEL_NONE
                        display.level == AvailabilityLevel.Low -> LEVEL_LOW
                        display.level == AvailabilityLevel.Medium -> LEVEL_MEDIUM
                        else -> LEVEL_GOOD
                    },
                )
                // A station out of service or without a state shows no figure:
                // writing "0" would suggest an empty station, which is not the
                // same information.
                addStringProperty(
                    COUNT_PROPERTY,
                    display.count?.takeIf { !display.isOutOfService }?.toString().orEmpty(),
                )
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /** An individual station's disc, coloured by its level. */
    fun circleLayer(context: Context): CircleLayer = CircleLayer(STATION_CIRCLE_LAYER, SOURCE_ID)
        .withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(11, 7f),
                    Expression.stop(16, 15f),
                ),
            ),
            PropertyFactory.circleColor(levelExpression(context)),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(colour(context, R.color.surface)),
        )
        .withFilter(Expression.not(Expression.has("point_count")))

    /** The figure laid on the disc. */
    fun countLayer(context: Context): SymbolLayer = SymbolLayer(STATION_COUNT_LAYER, SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.get(COUNT_PROPERTY)),
            PropertyFactory.textFont(DIGIT_FONT),
            PropertyFactory.textSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(11, 9f),
                    Expression.stop(16, 15f),
                ),
            ),
            PropertyFactory.textColor(inkExpression(context)),
            // The figure belongs to the disc: it must never be pushed aside by
            // automatic label placement.
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
        .withFilter(Expression.not(Expression.has("point_count")))

    /**
     * The disc of a cluster of stations, at distant zooms.
     *
     * OUTLINED rather than filled, unlike the station markers. A cluster
     * showing "8" means eight stations, a marker showing "8" means eight bikes:
     * painting them alike made the two indistinguishable. The teal ramp is
     * reserved for availability, and for it alone.
     *
     * The outline rather than the flat fill is not only a matter of legibility:
     * twenty black discs on a deliberately desaturated map would have been the
     * loudest thing on the screen, when a cluster is only a transient state of
     * the zoom.
     */
    fun clusterLayer(context: Context): CircleLayer = CircleLayer(CLUSTER_CIRCLE_LAYER, SOURCE_ID)
        .withProperties(
            // The area grows with the number of stations clustered, but more
            // slowly than it does: a cluster of a hundred must not eat the
            // screen.
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("point_count"),
                    Expression.stop(2, 12f),
                    Expression.stop(30, 19f),
                    Expression.stop(120, 25f),
                ),
            ),
            PropertyFactory.circleColor(colour(context, R.color.surface)),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(colour(context, R.color.encre)),
            PropertyFactory.circleOpacity(0.92f),
        )
        .withFilter(Expression.has("point_count"))

    /** The number of stations in a cluster. */
    fun clusterCountLayer(context: Context): SymbolLayer =
        SymbolLayer(CLUSTER_COUNT_LAYER, SOURCE_ID)
            .withProperties(
                PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                PropertyFactory.textFont(DIGIT_FONT),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor(colour(context, R.color.encre)),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
            .withFilter(Expression.has("point_count"))

    /** The disc's colour by level, taken from the project's design tokens. */
    private fun levelExpression(context: Context): Expression = Expression.match(
        Expression.get(LEVEL_PROPERTY),
        Expression.color(colour(context, R.color.map_marker_minor)),
        Expression.stop(LEVEL_NONE, Expression.color(colour(context, R.color.surface))),
        Expression.stop(LEVEL_LOW, Expression.color(colour(context, R.color.dispo_faible))),
        Expression.stop(LEVEL_MEDIUM, Expression.color(colour(context, R.color.dispo_moyenne))),
        Expression.stop(LEVEL_GOOD, Expression.color(colour(context, R.color.dispo_bonne))),
        Expression.stop(
            LEVEL_OUT_OF_SERVICE,
            Expression.color(colour(context, R.color.map_marker_minor)),
        ),
        Expression.stop(
            LEVEL_UNKNOWN,
            Expression.color(colour(context, R.color.map_marker_minor)),
        ),
    )

    /** The figure's colour, chosen to contrast with its disc. */
    private fun inkExpression(context: Context): Expression = Expression.match(
        Expression.get(LEVEL_PROPERTY),
        Expression.color(colour(context, R.color.encre)),
        Expression.stop(LEVEL_NONE, Expression.color(colour(context, R.color.alerte))),
        Expression.stop(
            LEVEL_LOW,
            Expression.color(colour(context, R.color.dispo_faible_encre)),
        ),
        Expression.stop(
            LEVEL_MEDIUM,
            Expression.color(colour(context, R.color.dispo_moyenne_encre)),
        ),
        Expression.stop(
            LEVEL_GOOD,
            Expression.color(colour(context, R.color.dispo_bonne_encre)),
        ),
    )

    private fun colour(context: Context, resource: Int) = ContextCompat.getColor(context, resource)
}
