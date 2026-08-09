package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * Un bout de trajet désigné par l'utilisateur : départ ou arrivée.
 *
 * Porte son libellé avec lui, parce que c'est ce que l'écran affiche : « ma
 * position », « 12 Rue Nationale », le nom d'une station favorite. Des
 * coordonnées brutes ne se relisent pas.
 *
 * Ce type ne va jamais sur le disque. Le SPEC §8 interdit de conserver une
 * destination : ces valeurs vivent dans l'état d'un écran, et disparaissent
 * avec lui.
 *
 * @property label ce que l'utilisateur lit.
 * @property position l'endroit désigné.
 */
data class JourneyEndpoint(val label: String, val position: Coordinates) {

    /** Écrit le point dans un paquet, sous des clés préfixées. */
    fun writeTo(bundle: Bundle, prefix: String) {
        bundle.putString("$prefix$LABEL_KEY", label)
        bundle.putDouble("$prefix$LATITUDE_KEY", position.latitude)
        bundle.putDouble("$prefix$LONGITUDE_KEY", position.longitude)
    }

    companion object {
        private const val LABEL_KEY = "-libelle"
        private const val LATITUDE_KEY = "-latitude"
        private const val LONGITUDE_KEY = "-longitude"

        /** Relit un point écrit par [writeTo], ou `null` s'il n'y en a pas. */
        fun readFrom(bundle: Bundle?, prefix: String): JourneyEndpoint? {
            if (bundle == null || !bundle.containsKey("$prefix$LATITUDE_KEY")) return null
            return JourneyEndpoint(
                label = bundle.getString("$prefix$LABEL_KEY").orEmpty(),
                position = Coordinates(
                    latitude = bundle.getDouble("$prefix$LATITUDE_KEY"),
                    longitude = bundle.getDouble("$prefix$LONGITUDE_KEY"),
                ),
            )
        }
    }
}
