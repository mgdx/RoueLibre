package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.config.FleetDescription

/**
 * Remembers what the network was last counted to lend.
 *
 * The reading has to survive a restart, otherwise a launch with no connection —
 * a car park, a metro platform, the very moment one wants a station — would fall
 * back on the configuration's seed and draw a bike the user has already been
 * shown to be the wrong one. It is what makes the counting worth doing at all
 * (SPEC §4.1).
 *
 * One slot, for the city in service: the application serves one conurbation at a
 * time, and a reading is meaningless anywhere else. Nothing personal is written
 * here — how many bikes a public network lends is a fact about the network
 * (SPEC §2, C3).
 *
 * The interface exists so the fleet repository can be tested on the JVM without
 * DataStore or a device. `AppPreferences` is the only implementation shipped.
 */
interface MeasuredFleetStore {

    /**
     * What was counted for [cityId], or `null` if nothing ever was — or if what
     * is stored belongs to another city, which is the same thing here.
     */
    suspend fun measuredFleet(cityId: String): FleetDescription?

    /** Stores a reading, replacing whatever the slot held. */
    suspend fun setMeasuredFleet(cityId: String, fleet: FleetDescription)

    /** Empties the slot, the city served having changed. */
    suspend fun clearMeasuredFleet()
}
