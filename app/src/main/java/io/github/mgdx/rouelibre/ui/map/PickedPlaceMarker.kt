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
 * Le point désigné par une recherche d'adresse, posé sur la carte.
 *
 * Une **goutte**, quand les stations sont des **disques** : sur une carte qui
 * en porte deux cent soixante-huit, le point cherché doit se reconnaître à sa
 * forme et pas seulement à sa couleur (SPEC §7).
 */
object PickedPlaceMarker {

    /** Identifiant de la source GeoJSON portant le point choisi. */
    const val SOURCE_ID: String = "point-choisi"

    /** Couche du marqueur. */
    const val LAYER_ID: String = "point-choisi-marqueur"

    private const val IMAGE_ID = "point-choisi-goutte"

    /**
     * Enregistre l'image du marqueur dans le style.
     *
     * MapLibre ne dessine que des images matricielles : le vecteur est donc
     * rendu une fois, à la densité de l'écran, plutôt que livré en cinq
     * tailles dans l'APK.
     */
    fun registerImage(context: Context, style: Style) {
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.ic_pin)) {
            "marqueur de point choisi introuvable"
        }
        style.addImage(IMAGE_ID, drawable.toBitmap())
    }

    /** La couche du marqueur, ancrée par sa pointe sur le point exact. */
    fun layer(): SymbolLayer = SymbolLayer(LAYER_ID, SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(IMAGE_ID),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            // Le point cherché prime sur tout le reste : il ne doit jamais
            // être écarté par le placement automatique des étiquettes.
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )

    /** Le contenu de la source : un point, ou rien. */
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
