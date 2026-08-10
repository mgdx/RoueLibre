package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The point designated by an address search, laid on the map.
 *
 * A **pin**, where stations are **discs**: on a map carrying two hundred and
 * sixty-eight of them, the point searched for must be recognisable by its shape
 * and not only by its colour (SPEC §7).
 */
object PickedPlaceMarker {

    /** The identifier of the GeoJSON source carrying the picked point. */
    const val SOURCE_ID: String = "picked-point"

    /** The marker's layer. */
    const val LAYER_ID: String = "picked-point-marker"

    private const val IMAGE_ID = "picked-point-pin"

    /**
     * Registers the marker's image in the style.
     *
     * MapLibre only draws raster images: the vector is therefore rendered once,
     * at the screen's density, rather than shipped in five sizes in the APK.
     */
    fun registerImage(context: Context, style: Style) {
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.ic_pin)) {
            "marqueur de point choisi introuvable"
        }
        style.addImage(IMAGE_ID, drawable.toBitmap())
    }

    /** The marker's layer, anchored by its tip on the exact point. */
    fun layer(): SymbolLayer = SymbolLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(IMAGE_ID),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            // The searched point takes precedence over everything else: it
            // must never be pushed aside by automatic label placement.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )

    /** The source's contents: one point, or nothing. */
    fun featureFor(position: Coordinates?): FeatureCollection {
        if (position == null) return FeatureCollection.fromFeatures(emptyList())
        return FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(
                    Point.fromLngLat(position.longitude, position.latitude),
                ),
            ),
        )
    }
}
