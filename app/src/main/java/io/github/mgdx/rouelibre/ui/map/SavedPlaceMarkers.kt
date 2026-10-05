package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.data.SavedPlace
import io.github.mgdx.rouelibre.data.SavedPlaceKind
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * The home and the work the user has named, laid on the map (SPEC §7.1).
 *
 * One source for both, each point carrying the picture it is drawn with: two
 * places do not deserve two layers, and a third kind would only be a third
 * picture.
 */
object SavedPlaceMarkers {

    /** The identifier of the GeoJSON source carrying the named places. */
    const val SOURCE_ID: String = "saved-places"

    /** The markers' layer. */
    const val LAYER_ID: String = "saved-places-marker"

    private const val IMAGE_PROPERTY = "image"

    /**
     * Registers both pictures in the style, rendered once at the screen's
     * density for the reason [PickedPlaceMarker.registerImage] gives.
     */
    fun registerImages(context: Context, style: Style) {
        for (kind in SavedPlaceKind.entries) {
            val drawable = checkNotNull(
                AppCompatResources.getDrawable(context, kind.markerDrawable),
            ) { "marqueur de lieu enregistré introuvable" }
            style.addImage(kind.imageId, drawable.toBitmap())
        }
    }

    /**
     * The markers' layer, each disc centred on the exact point.
     *
     * Allowed to overlap like the point searched for: a home pushed off the
     * map by a street name is a home the user cannot find again.
     */
    fun layer(): SymbolLayer = SymbolLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(Expression.get(IMAGE_PROPERTY)),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )

    /** The source's contents: one point per place named, and nothing else. */
    fun featuresFor(places: Map<SavedPlaceKind, SavedPlace>): FeatureCollection =
        FeatureCollection.fromFeatures(
            places.map { (kind, place) ->
                Feature.fromGeometry(
                    Point.fromLngLat(place.position.longitude, place.position.latitude),
                ).apply { addStringProperty(IMAGE_PROPERTY, kind.imageId) }
            },
        )

    private val SavedPlaceKind.imageId: String
        get() = "saved-place-${name.lowercase()}"

    private val SavedPlaceKind.markerDrawable: Int
        get() = when (this) {
            SavedPlaceKind.Home -> R.drawable.marker_saved_home
            SavedPlaceKind.Work -> R.drawable.marker_saved_work
        }
}
