package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.DeparturePoint
import io.github.mgdx.rouelibre.core.journey.JourneyOption
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
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

    /** A station of the journey: the filled disc bearing a bike. */
    const val KIND_STATION: String = "station"

    /** An end of the journey: the outlined disc bearing a walking figure. */
    const val KIND_ENDPOINT: String = "endpoint"

    /** An end of a journey ridden from one end to the other (SPEC §7.3). */
    const val KIND_ENDPOINT_OWN_BIKE: String = "endpoint-own-bike"

    /**
     * The bike a journey sets off on, standing outside the stations
     * (SPEC §7.2.1, §7.4).
     *
     * Two of them, and the bike's own kind decides which: the bolt here comes
     * from the feed, where a station's comes from the fleet and a ride on one's
     * own bike from the rider (SPEC §4.1). It is the only badge such a marker
     * ever takes — a bike is one bike, so there is no rack to bear a cog.
     */
    const val KIND_STREET_BIKE: String = "street-bike"
    const val KIND_STREET_BIKE_ELECTRIC: String = "street-bike-electric"

    private const val STATION_IMAGE_ID = "journey-station-marker"
    private const val ENDPOINT_IMAGE_ID = "journey-endpoint-marker"
    private const val ENDPOINT_OWN_BIKE_IMAGE_ID = "journey-endpoint-own-bike-marker"
    private const val STREET_BIKE_IMAGE_ID = "journey-street-bike-marker"
    private const val STREET_BIKE_ELECTRIC_IMAGE_ID = "journey-street-bike-electric-marker"

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
     * @param ownBikeKind what the rider said their own bike is, which the two
     *   ends of a ride on it then say with a bolt (SPEC §7.6). Read from the
     *   settings, so it arrives late in the same way and for the same reason.
     */
    fun registerImages(
        context: Context,
        style: Style,
        fleet: BikeFleet,
        ownBikeKind: OwnBikeKind,
    ) {
        style.addImage(
            STATION_IMAGE_ID,
            imageOf(context, BikeGlyphs.stationMarker(fleet)),
        )
        style.addImage(ENDPOINT_IMAGE_ID, imageOf(context, R.drawable.marker_journey_endpoint))
        // The ends of a ride on one's own bike (SPEC §7.3): the filled bike
        // disc already drawn elsewhere. It never takes the cog, and it takes
        // the bolt from the rider and not from the network — what a network
        // lends says nothing about a bike that is not its own.
        style.addImage(
            ENDPOINT_OWN_BIKE_IMAGE_ID,
            imageOf(context, OwnBikeGlyphs.endpointMarker(ownBikeKind)),
        )
        // The bike a journey may set off on, outside the stations (SPEC §7.4).
        // Both drawings are registered whatever the journey shown: which of
        // them is used is read off that bike's own kind, feature by feature,
        // and neither depends on an answer arriving from disk.
        style.addImage(
            STREET_BIKE_IMAGE_ID,
            imageOf(context, R.drawable.marker_journey_street_bike),
        )
        style.addImage(
            STREET_BIKE_ELECTRIC_IMAGE_ID,
            imageOf(context, R.drawable.marker_journey_street_bike_electric),
        )
    }

    /**
     * The disc a journey's departure takes, for the drawings the map does not
     * hold — the shape under the summary, on the result screen and on its
     * detail (SPEC §7.4).
     *
     * `null` for a station, which those drawings already draw for themselves:
     * only a bike outside the stations replaces the first disc, and it is the
     * same glyph the map lays on that same point.
     */
    @DrawableRes
    fun departureMarkerOf(departure: DeparturePoint): Int? = when (departure) {
        is DeparturePoint.AtStation -> null
        is DeparturePoint.AtStreetBike -> when (departure.kind) {
            VehicleKind.Electric -> R.drawable.marker_journey_street_bike_electric
            VehicleKind.Mechanical, VehicleKind.Other -> R.drawable.marker_journey_street_bike
        }
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
                    Expression.stop(KIND_STREET_BIKE, STREET_BIKE_IMAGE_ID),
                    Expression.stop(KIND_STREET_BIKE_ELECTRIC, STREET_BIKE_ELECTRIC_IMAGE_ID),
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
     * @param origin where the user sets off from, if it is known. It is left
     *   undrawn on a journey that sets off on a bike outside the stations,
     *   where the departure marker below stands on that very point.
     * @param destination where they are going.
     * @param option the journey shown, or `null` when it comes down to a
     *   single leg — the two ends are then still worth drawing. Its departure
     *   carries its own drawing: a station's disc, or the bike glyph where the
     *   journey sets off on a bike outside the stations (SPEC §7.4), which is
     *   why such a journey puts three points on the map and not four.
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
        // A journey that sets off on a bike outside the stations has that bike
        // for its origin: the search screen locked the field on it, so the two
        // points coincide exactly. Emitting both would lay the walking disc and
        // the bike glyph on the very same spot — and overlap being allowed
        // here, both are drawn, the walker over the bike, which is the one
        // thing this marker had to say. The bike stays and the walker goes:
        // nothing of that departure is walked, and the map carries three points
        // and not four (SPEC §7.4).
        val setsOffOnABike = option?.departure is DeparturePoint.AtStreetBike
        return FeatureCollection.fromFeatures(
            listOfNotNull(
                origin.takeUnless { setsOffOnABike }?.let { pointAt(it, end) },
                option?.let { pointAt(it.departure.position, departureKindOf(it.departure)) },
                option?.let { pointAt(it.arrivalStation.position, KIND_STATION) },
                destination?.let { pointAt(it, end) },
            ),
        )
    }

    /**
     * The drawing the point a journey sets off from takes.
     *
     * The station disc where it is a station; the bike glyph, smaller and
     * countless, where the rider chose a bike outside them — bearing the bolt
     * only where the network's own type table reads that bike as electric
     * (SPEC §4.1, §7.4).
     */
    private fun departureKindOf(departure: DeparturePoint): String = when (departure) {
        is DeparturePoint.AtStation -> KIND_STATION
        is DeparturePoint.AtStreetBike -> when (departure.kind) {
            VehicleKind.Electric -> KIND_STREET_BIKE_ELECTRIC
            VehicleKind.Mechanical, VehicleKind.Other -> KIND_STREET_BIKE
        }
    }

    private fun pointAt(position: Coordinates, kind: String): Feature =
        Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude))
            .apply { addStringProperty(KIND_PROPERTY, kind) }

    private fun imageOf(context: Context, drawable: Int) =
        checkNotNull(AppCompatResources.getDrawable(context, drawable)) {
            "journey marker missing from the APK"
        }.toBitmap()
}
