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
 * Construction des marqueurs de stations sur la carte (SPEC §7.1).
 *
 * Le marqueur reprend la logique de l'indicateur de la liste : la densité du
 * disque donne le niveau, le chiffre donne le compte exact. L'arc de
 * remplissage, lui, n'est pas repris — il demanderait une image par station,
 * soit plusieurs centaines de bitmaps pour une information de contexte qui se
 * lit très bien dans la feuille de détail.
 *
 * Comme dans la liste, la couleur ne porte jamais l'information seule : le
 * chiffre est toujours écrit par-dessus.
 */
object StationMarkers {

    /** Identifiant de la source GeoJSON portant les stations. */
    const val SOURCE_ID: String = "stations"

    /** Couche des disques de stations isolées. */
    const val STATION_CIRCLE_LAYER: String = "stations-disque"

    /** Couche des chiffres de stations isolées. */
    const val STATION_COUNT_LAYER: String = "stations-nombre"

    /** Couche des amas, aux zooms éloignés. */
    const val CLUSTER_CIRCLE_LAYER: String = "stations-amas"

    /** Couche du décompte porté par un amas. */
    const val CLUSTER_COUNT_LAYER: String = "stations-amas-nombre"

    /** Propriété portant le nombre affiché : vélos ou places selon le mode. */
    const val COUNT_PROPERTY: String = "count"

    /** Propriété portant le niveau, qui décide de la couleur du disque. */
    const val LEVEL_PROPERTY: String = "level"

    /** Propriété portant l'identifiant, pour retrouver la station au clic. */
    const val STATION_ID_PROPERTY: String = "stationId"

    /** Propriété portant le nom, lu par les lecteurs d'écran. */
    const val NAME_PROPERTY: String = "name"

    private const val LEVEL_NONE = "aucun"
    private const val LEVEL_LOW = "faible"
    private const val LEVEL_MEDIUM = "moyen"
    private const val LEVEL_GOOD = "bon"
    private const val LEVEL_OUT_OF_SERVICE = "hors-service"
    private const val LEVEL_UNKNOWN = "inconnu"

    /** Police des chiffres, la même que celle de l'indicateur de la liste. */
    private val DIGIT_FONT = arrayOf("Bricolage Grotesque Bold")

    /**
     * Traduit les stations en objets GeoJSON pour la carte.
     *
     * @param stations les stations connues et leur dernier état.
     * @param mode ce que le marqueur doit compter.
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
                // Une station hors service ou sans état n'affiche pas de
                // chiffre : écrire « 0 » laisserait croire à une station vide,
                // ce qui n'est pas la même information.
                addStringProperty(
                    COUNT_PROPERTY,
                    display.count?.takeIf { !display.isOutOfService }?.toString().orEmpty(),
                )
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    /** Le disque d'une station isolée, coloré par son niveau. */
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

    /** Le chiffre posé sur le disque. */
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
            // Le chiffre appartient au disque : il ne doit jamais être
            // écarté par le placement automatique des étiquettes.
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
        .withFilter(Expression.not(Expression.has("point_count")))

    /**
     * Le disque d'un amas de stations, aux zooms éloignés.
     *
     * CERCLÉ et non plein, à l'inverse des marqueurs de stations. Un amas
     * portant « 8 » veut dire huit stations, un marqueur portant « 8 » veut
     * dire huit vélos : les peindre pareil rendait les deux indiscernables.
     * La rampe pétrole est réservée à la disponibilité, et à elle seule.
     *
     * Le contour plutôt que l'aplat n'est pas qu'une question de lisibilité :
     * une vingtaine de disques noirs sur une carte volontairement désaturée
     * auraient été l'élément le plus criard de l'écran, alors qu'un amas n'est
     * qu'un état transitoire du zoom.
     */
    fun clusterLayer(context: Context): CircleLayer = CircleLayer(CLUSTER_CIRCLE_LAYER, SOURCE_ID)
        .withProperties(
            // La surface croît avec le nombre de stations regroupées, mais
            // moins vite que lui : un amas de cent ne doit pas manger
            // l'écran.
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

    /** Le nombre de stations d'un amas. */
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

    /** Couleur du disque en fonction du niveau, reprise des jetons du projet. */
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

    /** Couleur du chiffre, choisie pour contraster avec son disque. */
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
