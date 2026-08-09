package io.github.mgdx.rouelibre.core.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Un point géographique, en degrés décimaux WGS 84. */
public data class Coordinates(public val latitude: Double, public val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude hors bornes : $latitude" }
        require(longitude in -180.0..180.0) { "longitude hors bornes : $longitude" }
    }
}

/** Rayon moyen de la Terre, en mètres. */
private const val EARTH_RADIUS_METRES = 6_371_008.8

/**
 * Distance orthodromique entre deux points, en mètres.
 *
 * Formule de la haversine. Sur les quelques kilomètres qui séparent deux
 * stations, elle et une formule ellipsoïdale s'écartent de moins d'un mètre,
 * pour une fraction du coût — et cette distance est appelée des milliers de
 * fois lors du choix des stations candidates (SPEC §6).
 */
public fun Coordinates.distanceInMetresTo(other: Coordinates): Double {
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val startLatitude = Math.toRadians(latitude)
    val endLatitude = Math.toRadians(other.latitude)

    val chordHalf = sin(latitudeDelta / 2).let { it * it } +
        cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).let { it * it }
    // `min` protège de l'erreur d'arrondi qui, sur deux points confondus,
    // pousserait l'argument juste au-delà de 1 et ferait renvoyer NaN.
    return 2 * EARTH_RADIUS_METRES * asin(min(1.0, sqrt(chordHalf)))
}

/**
 * Rectangle géographique, en degrés décimaux WGS 84.
 *
 * L'emprise de référence de l'application (SPEC §4) n'est jamais écrite dans
 * le code : elle est lue dans la configuration de ville et dans le manifeste
 * des données. Ce type ne fait que la porter.
 */
public data class BoundingBox(
    public val south: Double,
    public val west: Double,
    public val north: Double,
    public val east: Double,
) {
    init {
        require(south <= north) { "emprise inversée en latitude" }
        require(west <= east) { "emprise inversée en longitude" }
    }

    /** Indique si [point] tombe à l'intérieur du rectangle, bords compris. */
    public operator fun contains(point: Coordinates): Boolean =
        point.latitude in south..north && point.longitude in west..east

    /** Centre du rectangle, utilisé au premier affichage de la carte. */
    public val centre: Coordinates
        get() = Coordinates((south + north) / 2, (west + east) / 2)

    /**
     * Distance approximative du [point] au bord le plus proche, en mètres, ou
     * `0.0` s'il est à l'intérieur.
     *
     * Sert à nuancer le message montré pour un point hors emprise : « juste en
     * dehors » et « à l'autre bout du pays » n'appellent pas la même réponse.
     */
    public fun distanceOutsideInMetres(point: Coordinates): Double {
        if (point in this) return 0.0
        val nearest = Coordinates(
            latitude = point.latitude.coerceIn(south, north),
            longitude = point.longitude.coerceIn(west, east),
        )
        return point.distanceInMetresTo(nearest)
    }

    /** Vrai si le rectangle a une surface non nulle et des bornes plausibles. */
    public val isUsable: Boolean
        get() = abs(north - south) > 0.0 && abs(east - west) > 0.0
}
