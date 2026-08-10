package io.github.mgdx.rouelibre.data

import java.time.Instant

/**
 * Remembers when the stations' static data was fetched.
 *
 * That date must survive a restart of the application, otherwise the "at most
 * once a day" rule of SPEC §4.1 would not hold: every launch would download the
 * complete station list again.
 *
 * The interface exists so the refresh policy can be tested on the JVM without
 * DataStore or a device. `AppPreferences` is the only implementation shipped.
 */
interface RefreshTimestampStore {

    /** When the last successful refresh happened, or `null` if there was none. */
    suspend fun stationInformationFetchedAt(): Instant?

    /** Stores the date of a successful refresh. */
    suspend fun setStationInformationFetchedAt(instant: Instant)
}
