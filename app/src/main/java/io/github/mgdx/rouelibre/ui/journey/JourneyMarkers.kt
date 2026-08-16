package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.BikeGlyphs
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The four points of a journey, laid on the map (SPEC §7.4).
 *
 * The track alone says the shape of the journey but not where it changes mode:
 * where the bike is picked up, where it goes back. These markers say it, in the
 * same drawing as the illustration of the search screen — a filled disc bearing
 * a bike for a station, an outlined disc bearing a walking figure for either end
 * of the journey.
 *
 * The shape carries the meaning as much as the colour does (SPEC §7): filled
 * against outlined, bike against walker.
 */
object JourneyMarkers {

    /** The identifier of the GeoJSON source carrying the journey's points. */
    const val SOURCE_ID: String = "journey-points"

    /** The markers' layer. */
    const val LAYER_ID: String = "journey-points-marker"

    /** The property saying which of the two drawings a point takes. */
    const val KIND_PROPERTY: String = "kind"

    private const val KIND_STATION = "station"
    private const val KIND_ENDPOINT = "endpoint"

    /** An end of a journey ridden from one end to the other (SPEC §7.3). */
    private const val KIND_ENDPOINT_OWN_BIKE = "endpoint-own-bike"

    private const val STATION_IMAGE_ID = "journey-station-marker"
    private const val ENDPOINT_IMAGE_ID = "journey-endpoint-marker"
    private const val ENDPOINT_OWN_BIKE_IMAGE_ID = "journey-endpoint-own-bike-marker"

    /**
     * Registers the two drawings in the style.
     *
     * MapLibre only draws raster images: the vectors are rendered once, at the
     * screen's density, rather than shipped in five sizes in the APK.
     *
     * @param fleet what the network served lends,
     *   which its stations' discs then say with a bolt (SPEC §15). Registering
     *   the image again under the same name replaces it, so the answer may
     *   arrive after the style has loaded — which it does, being read from the
     *   city's configuration on disk.
     */
    fun registerImages(context: Context, style: Style, fleet: BikeFleet) {
        style.addImage(
            STATION_IMAGE_ID,
            imageOf(context, BikeGlyphs.stationMarker(fleet)),
        )
        style.addImage(ENDPOINT_IMAGE_ID, imageOf(context, R.drawable.marker_journey_endpoint))
        // The ends of a ride on one's own bike (SPEC §7.3): the filled bike
        // disc already drawn elsewhere, and the plain one — the bolt and the
        // cog say what waits at a station, and this bike is the rider's own.
        style.addImage(
            ENDPOINT_OWN_BIKE_IMAGE_ID,
            imageOf(context, R.drawable.marker_journey_station),
        )
    }

    /** The markers' layer, each disc centred on its point. */
    fun layer(): SymbolLayer = SymbolLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(
                Expression.match(
                    Expression.get(KIND_PROPERTY),
                    Expression.literal(ENDPOINT_IMAGE_ID),
                    Expression.stop(KIND_STATION, STATION_IMAGE_ID),
                    Expression.stop(KIND_ENDPOINT, ENDPOINT_IMAGE_ID),
                    Expression.stop(KIND_ENDPOINT_OWN_BIKE, ENDPOINT_OWN_BIKE_IMAGE_ID),
                ),
            ),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            // The four points of the journey are what the screen is about: they
            // must never be pushed aside by automatic label placement, nor by
            // one another when two of them nearly coincide.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )

    /**
     * The points to show for the journey being displayed.
     *
     * @param origin where the user sets off from, if it is known.
     * @param destination where they are going.
     * @param option the station pair shown, or `null` when the journey comes
     *   down to a single leg — the two ends are then still worth drawing.
     * @param isRidden true when that single leg is ridden on the user's own
     *   bike (SPEC §7.3): the two ends then bear a bike rather than a walking
     *   figure, since nothing of that journey is walked.
     */
    fun featuresFor(
        origin: Coordinates?,
        destination: Coordinates?,
        option: JourneyOption?,
        isRidden: Boolean = false,
    ): FeatureCollection {
        val end = if (isRidden) KIND_ENDPOINT_OWN_BIKE else KIND_ENDPOINT
        return FeatureCollection.fromFeatures(
            listOfNotNull(
                origin?.let { pointAt(it, end) },
                option?.let { pointAt(it.departureStation.position, KIND_STATION) },
                option?.let { pointAt(it.arrivalStation.position, KIND_STATION) },
                destination?.let { pointAt(it, end) },
            ),
        )
    }

    private fun pointAt(position: Coordinates, kind: String): Feature =
        Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude))
            .apply { addStringProperty(KIND_PROPERTY, kind) }

    private fun imageOf(context: Context, drawable: Int) =
        checkNotNull(AppCompatResources.getDrawable(context, drawable)) {
            "journey marker missing from the APK"
        }.toBitmap()
}
