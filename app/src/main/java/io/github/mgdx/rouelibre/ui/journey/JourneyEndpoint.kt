package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import io.github.mgdx.rouelibre.core.geo.Coordinates

/**
 * One end of a journey designated by the user: origin or destination.
 *
 * It carries its own label, because that is what the screen shows: "my
 * position", "12 Rue Nationale", a favourite station's name. Raw coordinates do
 * not read back.
 *
 * This type never reaches the disk. SPEC §8 forbids keeping a destination:
 * these values live in a screen's state, and vanish with it.
 *
 * @property label what the user reads.
 * @property position the place designated.
 */
data class JourneyEndpoint(val label: String, val position: Coordinates) {

    /** Writes the point into a bundle, under prefixed keys. */
    fun writeTo(bundle: Bundle, prefix: String) {
        bundle.putString("$prefix$LABEL_KEY", label)
        bundle.putDouble("$prefix$LATITUDE_KEY", position.latitude)
        bundle.putDouble("$prefix$LONGITUDE_KEY", position.longitude)
    }

    companion object {
        private const val LABEL_KEY = "-label"
        private const val LATITUDE_KEY = "-latitude"
        private const val LONGITUDE_KEY = "-longitude"

        /** Reads back a point written by [writeTo], or `null` if there is none. */
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
