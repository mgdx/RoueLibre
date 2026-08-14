package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.FleetReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the city in service lends, and what the interface therefore draws.
 *
 * The single answer to "plain bike, bolt, or bolt and cog" (SPEC §7). It is
 * built in two movements:
 *
 * - the **seed**, from the city configuration, which `tools/read_fleet.py`
 *   counted when the city was added. It is what a first launch has to go on,
 *   and what a launch with no connection keeps;
 * - the **count**, redone from the live feeds every time the stations refresh
 *   (SPEC §4.1), which is what makes a network that has changed since the last
 *   survey show the right bike without waiting for a release.
 *
 * **A reading only ever adds.** Merging rather than replacing is what keeps the
 * glyph still: a network whose stations happen to be empty of one kind at four
 * in the morning, or a feed served in part, would otherwise turn a mixed city
 * into an electric one under the user's eyes, and back again a minute later.
 * The consequence is accepted and it is the safe one — a kind seen once at a
 * station is a kind the network lends, and the only way to unlearn it is to
 * leave the city, or a new survey shipped with a release.
 *
 * @property store where a reading is remembered across restarts.
 * @property activeCityFleet the city served and the seed it carries, read on
 *   every call rather than captured: changing city must take effect without a
 *   restart.
 */
class FleetRepository(
    private val store: MeasuredFleetStore,
    private val activeCityFleet: suspend () -> CityFleet?,
) {

    private val state = MutableStateFlow<FleetDescription?>(null)

    /** The city [state] was filled for, so a change of city is noticed. */
    private var loadedCityId: String? = null

    /** Serialises the loading and the merging: screens and refreshes both come. */
    private val lock = Mutex()

    /**
     * What the city in service lends, re-emitted whenever the reading changes.
     *
     * `null` until a city has been chosen, and for as long as the configuration
     * is being read from disk — every screen draws the plain bike until then,
     * which promises the least.
     *
     * The seed is loaded on the first collection rather than in a constructor:
     * this repository is built with the container, long before any screen wants
     * an answer, and reading a file then would delay the launch for a glyph.
     */
    val fleet: Flow<FleetDescription?> = flow {
        lock.withLock { loadInsideLock() }
        emitAll(state)
    }.distinctUntilChanged()

    /**
     * Takes what the bikes just fetched say the network lends.
     *
     * A reading resting on nothing counted is dropped rather than merged: it
     * carries the declaration's answer, not the network's, and the seed already
     * holds a better one.
     */
    suspend fun record(reading: FleetReading) {
        if (reading.bikesCounted == 0) return
        lock.withLock {
            val city = loadInsideLock() ?: return@withLock
            val known = state.value ?: return@withLock
            val merged = merge(known, reading)
            if (merged == known) return@withLock
            state.value = merged
            store.setMeasuredFleet(city.id, merged)
        }
    }

    /**
     * Forgets the reading, the city served having changed.
     *
     * One conurbation's fleet says nothing about another's: leaving a mixed
     * city for a mechanical one must not carry the cog over.
     */
    suspend fun forget() {
        lock.withLock {
            state.value = null
            loadedCityId = null
            store.clearMeasuredFleet()
        }
    }

    /**
     * Puts the city's seed and its remembered reading into [state], once.
     *
     * @return the city served, or `null` if there is none.
     */
    private suspend fun loadInsideLock(): CityFleet? {
        val city = activeCityFleet()
        if (city == null) {
            state.value = null
            loadedCityId = null
            return null
        }
        if (loadedCityId == city.id) return city
        val remembered = store.measuredFleet(city.id)
        state.value = remembered?.let { merge(city.configured, it) } ?: city.configured
        loadedCityId = city.id
        return city
    }

    private fun merge(known: FleetDescription, reading: FleetReading): FleetDescription = merge(
        known,
        FleetDescription(
            hasElectricBikes = reading.hasElectricBikes,
            isMixed = reading.isMixed,
            vehicleTypes = reading.vehicleTypes,
        ),
    )

    /**
     * Puts two readings together, keeping everything either of them saw.
     *
     * The vehicle type table is the one part where the fresh reading wins
     * outright on the identifiers it names: an operator that reassigns an
     * identifier to another kind is describing its own fleet, and the feed is
     * more recent than the survey. Identifiers it does not name are kept, since
     * losing one silences a station's split.
     */
    private fun merge(known: FleetDescription, fresh: FleetDescription) = FleetDescription(
        hasElectricBikes = known.hasElectricBikes || fresh.hasElectricBikes,
        isMixed = known.isMixed || fresh.isMixed,
        vehicleTypes = known.vehicleTypes + fresh.vehicleTypes,
    )
}

/**
 * The city served, as this repository needs to know it.
 *
 * @property id the network's identifier, which keys the remembered reading.
 * @property configured what the configuration was seeded with (SPEC §15).
 */
data class CityFleet(val id: String, val configured: FleetDescription)
