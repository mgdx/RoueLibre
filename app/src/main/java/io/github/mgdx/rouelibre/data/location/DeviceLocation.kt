package io.github.mgdx.rouelibre.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.coroutines.resume

/**
 * The device's position, obtained from the system (SPEC §10).
 *
 * **The system provider, never Google's fused location services**: those are
 * part of Play Services, which constraint C2 forbids. `LocationManager` is the
 * AOSP API, present on a LineageOS without GApps.
 *
 * The position is neither written to disk, nor sent anywhere, nor kept from one
 * session to the next: it lives in memory for the duration of the computation
 * that asks for it (SPEC §2, C3).
 *
 * No function here asks for a permission: that falls to the screen triggering
 * the use, at the moment the user understands why.
 */
class DeviceLocation(private val context: Context) {

    private val locationManager: LocationManager?
        get() = ContextCompat.getSystemService(context, LocationManager::class.java)

    /** True if either of the two location permissions is granted. */
    fun isPermitted(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True if the device can provide a position at all.
     *
     * This case must be told apart from a denied permission: "location is
     * turned off" and "the application is not allowed to use it" call for
     * different actions from the user.
     */
    fun isAvailable(): Boolean = locationManager?.let { manager ->
        USABLE_PROVIDERS.any { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    } ?: false

    /**
     * The last known position, if it is still fresh.
     *
     * Immediate and free of energy cost: it is what the system already holds. A
     * position from an hour ago no longer says where we are, hence the
     * freshness limit.
     *
     * @return the position, or `null` if none is known, recent enough, or
     *   permitted.
     */
    fun lastKnown(): Coordinates? {
        if (!isPermitted()) return null
        val manager = locationManager ?: return null
        val now = System.currentTimeMillis()
        return USABLE_PROVIDERS
            .mapNotNull { provider -> lastKnownFrom(manager, provider) }
            .filter { now - it.time <= MAXIMUM_AGE.toMillis() }
            .maxByOrNull { it.time }
            ?.toCoordinates()
    }

    /**
     * A fresh position, waiting for a fix if need be.
     *
     * @param timeout the delay past which we give up rather than leave the user
     *   waiting indefinitely under a building or in a car park.
     * @return the position, or `null` if it could not be obtained.
     */
    suspend fun current(timeout: Duration = REQUEST_TIMEOUT): Coordinates? {
        lastKnown()?.let { return it }
        if (!isPermitted()) return null
        val manager = locationManager ?: return null
        val providers = USABLE_PROVIDERS.filter { candidate ->
            runCatching { manager.isProviderEnabled(candidate) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return null

        return try {
            withTimeout(timeout.toMillis()) {
                awaitFirstFix(manager, providers)
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: SecurityException) {
            // The permission may have been revoked between the check and the call.
            null
        }
    }

    /**
     * Waits for the first fix to arrive, and unsubscribes whatever happens.
     *
     * **Every available provider is queried at once**, and the first to answer
     * wins. Querying only one does not work: GPS is the most accurate but stays
     * silent indoors, where the network provider answers in a second. Proven on
     * a device — indoors, the version that queried GPS alone waited ten seconds
     * to return nothing.
     *
     * For what the application does with it — framing a map, measuring a
     * distance to a station — the first fix is amply enough.
     *
     * `getCurrentLocation` would do this in one line, but only exists from
     * API 30; the application targets API 26.
     */
    private suspend fun awaitFirstFix(
        manager: LocationManager,
        providers: List<String>,
    ): Coordinates? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (continuation.isActive) continuation.resume(location.toCoordinates())
            }

            // Mandatory before API 30, where their default implementations do
            // not exist yet.
            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Removed in API 29, but required by the interface before it.")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: android.os.Bundle?,
            ) = Unit
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            manager.removeUpdates(listener)
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    }

    private fun lastKnownFrom(manager: LocationManager, provider: String): Location? = try {
        if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        // A provider this device does not have.
        null
    }

    private fun Location.toCoordinates() = Coordinates(latitude, longitude)

    companion object {
        /** The two permissions, in the order they will be requested. */
        val PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /**
         * The providers queried, from the most accurate to the least hungry.
         * None of them belongs to the Google services.
         */
        private val USABLE_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        /** Past this, a position no longer says where we are. */
        private val MAXIMUM_AGE: Duration = Duration.ofMinutes(2)

        /** The delay past which we give up on obtaining a fix. */
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
