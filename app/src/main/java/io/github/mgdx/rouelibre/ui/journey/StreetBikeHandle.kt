package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind

/**
 * The bike a journey was asked from, as it travels between screens
 * (SPEC §7.2.1, §7.3).
 *
 * The counterpart of [JourneyEndpoint] for the one departure that is not a
 * point one names: what the search screen was opened with, what the result
 * screen is asked for, and what either of them has to find again after a
 * rotation or a killed process. It carries what the journey is worked out from
 * and nothing more — the bike itself, and the kind the network's table read it
 * as, resolved once on the map where that table is at hand (SPEC §4.1, §15).
 *
 * **It never reaches the disk**, exactly as the two ends do not (SPEC §8): a
 * bike's identifier is rotated after every rental, so a handle written down
 * today names nothing tomorrow, and there is nothing here worth surviving the
 * next read of the feed.
 *
 * Flat and dull on purpose: five values a `Bundle` already knows how to carry,
 * so that what crosses a screen boundary needs no parcelling of its own.
 *
 * @property id the producer's identifier for the bike.
 * @property position where the feed put it, which is where the journey begins.
 * @property kind what the network's vehicle type table reads it as, which is
 *   the profile its ride is traced with (SPEC §6).
 * @property chargeRatio its charge as a ratio from 0 to 1, where the feed
 *   publishes one.
 * @property rangeMetres how far it can still go, where the feed publishes a
 *   figure above zero.
 */
data class StreetBikeHandle(
    val id: String,
    val position: Coordinates,
    val kind: VehicleKind,
    val chargeRatio: Double?,
    val rangeMetres: Int?,
) {

    /** Writes the bike into a bundle, under prefixed keys. */
    fun writeTo(bundle: Bundle, prefix: String) {
        bundle.putString("$prefix$ID_KEY", id)
        bundle.putDouble("$prefix$LATITUDE_KEY", position.latitude)
        bundle.putDouble("$prefix$LONGITUDE_KEY", position.longitude)
        bundle.putString("$prefix$KIND_KEY", kind.wireName)
        // A ratio and a range are both absent oftener than not, and a bundle
        // has no null double: the key itself carries the answer, and an absent
        // key reads back as nothing rather than as zero — which on a charge
        // would say the bike is flat when it is not (SPEC §7.2.1).
        chargeRatio?.let { bundle.putDouble("$prefix$CHARGE_KEY", it) }
        rangeMetres?.let { bundle.putInt("$prefix$RANGE_KEY", it) }
    }

    /** The bike as the business core knows it (SPEC §4.1). */
    fun toStreetBike(): StreetBike = StreetBike(
        id = id,
        position = position,
        // The type identifier is the producer's and stays with the feed: what
        // crosses a screen boundary is the kind it was already read as, the
        // table that translates it belonging to the screen that holds the feed
        // (SPEC §15). Nothing downstream asks for the word again — the ride's
        // profile and every drawing are settled by [kind].
        vehicleTypeId = null,
        chargeRatio = chargeRatio,
        rangeMetres = rangeMetres,
    )

    companion object {
        private const val ID_KEY = "-bike-id"
        private const val LATITUDE_KEY = "-bike-latitude"
        private const val LONGITUDE_KEY = "-bike-longitude"
        private const val KIND_KEY = "-bike-kind"
        private const val CHARGE_KEY = "-bike-charge"
        private const val RANGE_KEY = "-bike-range"

        /** Reads back a bike written by [writeTo], or `null` if there is none. */
        fun readFrom(bundle: Bundle?, prefix: String): StreetBikeHandle? {
            if (bundle == null || !bundle.containsKey("$prefix$ID_KEY")) return null
            return StreetBikeHandle(
                id = bundle.getString("$prefix$ID_KEY").orEmpty(),
                position = Coordinates(
                    latitude = bundle.getDouble("$prefix$LATITUDE_KEY"),
                    longitude = bundle.getDouble("$prefix$LONGITUDE_KEY"),
                ),
                // A word that cannot be read comes back as `Other`, which
                // every drawing and the ride's own profile treat as the plain
                // bike: the bolt is what has to be earned (SPEC §7.1).
                kind = VehicleKind.ofWireName(bundle.getString("$prefix$KIND_KEY")),
                chargeRatio = bundle.takeIf { it.containsKey("$prefix$CHARGE_KEY") }
                    ?.getDouble("$prefix$CHARGE_KEY"),
                rangeMetres = bundle.takeIf { it.containsKey("$prefix$RANGE_KEY") }
                    ?.getInt("$prefix$RANGE_KEY"),
            )
        }
    }
}
