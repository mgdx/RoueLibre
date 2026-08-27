package io.github.mgdx.rouelibre.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.PositionFix
import io.github.mgdx.rouelibre.core.geo.improvesOn
import io.github.mgdx.rouelibre.core.geo.isPreciseEnough
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * The device's position, obtained from the system (SPEC §10).
 *
 * **The system provider, never Google's fused location services**: those are
 * part of Play Services, which constraint C2 forbids. `LocationManager` is the
 * AOSP API, present on any Android OS without Google services.
 *
 * Every provider the device has is listened to at once, and their answers are
 * arbitrated on accuracy rather than taken in the order they arrive — see
 * [PositionFix]. Without that, the point shown to the user stops moving: the
 * network provider answers first, always, with the same wifi-derived position
 * whatever street one has walked to since.
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
    fun isAvailable(): Boolean = locationManager
        ?.let { enabledProviders(it).isNotEmpty() }
        ?: false

    /**
     * The last known position, if it is still fresh.
     *
     * Immediate and free of energy cost: it is what the system already holds. A
     * position from an hour ago no longer says where we are, hence the
     * freshness limit.
     *
     * Used to frame a map or to name the city one is in — never to answer "and
     * where am I now?", which is [current]'s business.
     *
     * @return the fix, or `null` if none is known, recent enough, or
     *   permitted. The fix rather than its coordinates: how wide it is decides
     *   what the map draws around the point (SPEC §7.1).
     */
    fun lastKnown(): PositionFix? = lastKnownFix(MAXIMUM_AGE)

    /**
     * A fresh position, waiting for a fix if need be.
     *
     * **What the system already holds is not an answer here**, unless it was
     * obtained seconds ago and precisely: on a press of "locate me" (SPEC
     * §7.1), returning a two-minute-old cache leaves the point exactly where
     * the user has just stopped believing it — a walker is a hundred metres
     * further on by then, a cyclist six hundred.
     *
     * @param timeout the delay past which we give up rather than leave the user
     *   waiting indefinitely under a building or in a car park. The position
     *   already known then serves as a fallback: an old point beats none.
     * @return the fix, or `null` if it could not be obtained.
     */
    suspend fun current(timeout: Duration = REQUEST_TIMEOUT): PositionFix? {
        lastKnownFix(CACHE_MAXIMUM_AGE)
            ?.takeIf { it.isPreciseEnough }
            ?.let { return it }
        val manager = locationManager?.takeIf { isPermitted() } ?: return null
        val providers = enabledProviders(manager)
        if (providers.isEmpty()) return null
        return bestFix(manager, providers, timeout) ?: lastKnown()
    }

    /**
     * The position as it moves, for as long as somebody collects it.
     *
     * Used by the screens that draw the user on a map: a point frozen where it
     * was when the screen opened says less than no point at all, since one
     * believes it.
     *
     * The subscription lives and dies with the collection: nothing runs in the
     * background, and nothing is written down (SPEC §2, C3). The flow completes
     * without emitting when the permission is missing — the caller then simply
     * shows nothing. Providers switched off do not end it: it stays subscribed
     * to them, silently and at no cost, so the point appears the moment
     * location is switched on rather than at the next rebuild of the screen.
     *
     * @return the last known fix first, when there is a fresh one, then every
     *   fix that improves on the one being shown. The fixes whole: the map
     *   draws the accuracy they announce around the point (SPEC §7.1).
     */
    fun positions(): Flow<PositionFix> = flow {
        val manager = locationManager?.takeIf { isPermitted() } ?: return@flow
        val providers = presentProviders(manager)
        if (providers.isEmpty()) return@flow

        // What the system already holds, straight away: waiting for the first
        // fix would leave the map without a point for several seconds while
        // one is perfectly well known.
        var shown: PositionFix? = lastKnownFix(MAXIMUM_AGE)
        shown?.let { emit(it) }

        fixes(manager, providers, FOLLOW_INTERVAL, FOLLOW_DISTANCE_METRES).collect { fix ->
            if (!fix.improvesOn(shown)) return@collect
            shown = fix
            emit(fix)
        }
    }

    /**
     * Waits for a position, and returns the most accurate one offered.
     *
     * **Every available provider is queried at once**: the satellites are the
     * most accurate but stay silent indoors, where the network answers in a
     * second. Proven on a device — indoors, the version that queried the
     * satellites alone waited ten seconds to return nothing.
     *
     * But the first to answer must not win, which is what it used to do: that
     * first answer is nearly always the network's, several hundred metres wide
     * and identical from one street to the next. It is kept, and the satellites
     * are given [REFINEMENT] to beat it; a fix that is already precise ends the
     * wait on the spot.
     *
     * `getCurrentLocation` would do this in one line, but only exists from
     * API 30; the application targets API 26.
     */
    private suspend fun bestFix(
        manager: LocationManager,
        providers: List<String>,
        timeout: Duration,
    ): PositionFix? = coroutineScope {
        var best: PositionFix? = null
        val listening = launch {
            val subscription = this
            var refinement: Job? = null
            fixes(manager, providers, NO_INTERVAL, ANY_DISTANCE_METRES).collect { fix ->
                if (!fix.improvesOn(best)) return@collect
                best = fix
                if (fix.isPreciseEnough) {
                    subscription.cancel()
                    return@collect
                }
                if (refinement == null) {
                    refinement = launch {
                        delay(REFINEMENT.toMillis())
                        subscription.cancel()
                    }
                }
            }
        }
        withTimeoutOrNull(timeout.toMillis()) { listening.join() }
        // Joined as well as cancelled: this is what makes the fixes written by
        // the subscription's thread visible to this one.
        listening.cancelAndJoin()
        best
    }

    /**
     * The fixes reported by [providers], as they come and unsorted.
     *
     * A listener per subscription, and one subscription per provider: the
     * caller arbitrates between them, this only carries them.
     *
     * @param minimumInterval the shortest delay between two fixes of the same
     *   provider, which is what keeps the radios from being asked for more than
     *   the display can use.
     * @param minimumDistanceMetres how far one must move for a fix to be
     *   reported at all.
     */
    private fun fixes(
        manager: LocationManager,
        providers: List<String>,
        minimumInterval: Duration,
        minimumDistanceMetres: Float,
    ): Flow<PositionFix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
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
                manager.requestLocationUpdates(
                    provider,
                    minimumInterval.toMillis(),
                    minimumDistanceMetres,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        } catch (_: SecurityException) {
            // The permission may have been revoked between the check and the call.
            manager.removeUpdates(listener)
            close()
            return@callbackFlow
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    /**
     * The best of the positions the system already holds, if fresh enough.
     *
     * The same arbitration as everywhere else: the most recent of the providers
     * is not necessarily the one that says where we are.
     */
    private fun lastKnownFix(maximumAge: Duration): PositionFix? {
        if (!isPermitted()) return null
        val manager = locationManager ?: return null
        val now = SystemClock.elapsedRealtime()
        return USABLE_PROVIDERS
            .mapNotNull { provider -> lastKnownFrom(manager, provider) }
            .map { it.toFix() }
            .filter { now - it.takenAtMillis <= maximumAge.toMillis() }
            .sortedBy { it.takenAtMillis }
            .fold(null as PositionFix?) { best, fix -> if (fix.improvesOn(best)) fix else best }
    }

    private fun enabledProviders(manager: LocationManager): List<String> =
        USABLE_PROVIDERS.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

    /**
     * The usable providers this device has at all, enabled or not.
     *
     * What a long-lived subscription must enumerate, where a one-shot request
     * wants [enabledProviders]: subscribing to a disabled provider costs
     * nothing and delivers nothing until the user switches location on, at
     * which point the fixes simply start. Enumerating the enabled ones instead
     * froze the subscription's world at its first instant — location switched
     * on while the map was up delivered no point until the screen was rebuilt,
     * because the flow had ended on an empty list that was no longer true.
     */
    private fun presentProviders(manager: LocationManager): List<String> =
        USABLE_PROVIDERS.filter { provider ->
            runCatching { provider in manager.allProviders }.getOrDefault(false)
        }

    private fun lastKnownFrom(manager: LocationManager, provider: String): Location? = try {
        if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        // A provider this device does not have.
        null
    }

    private fun Location.toFix() = PositionFix(
        coordinates = Coordinates(latitude, longitude),
        // A provider that measures no radius reports zero, which would pass for
        // a perfect fix and shut out every other provider for good.
        accuracyMetres = if (hasAccuracy()) accuracy.toDouble() else null,
        takenAtMillis = takenAtUptimeMillis(),
    )

    /**
     * When the fix was taken, on the clock that only goes forward.
     *
     * The uptime clock when the provider stamped one — it is immune to a clock
     * correction, which the wall clock is not, and a position judged to be from
     * the future would be shown for ever. Failing that, the wall-clock stamp is
     * carried over onto that same scale: some mock providers fill in only one
     * of the two, and a fix without a date passes for infinitely old.
     */
    private fun Location.takenAtUptimeMillis(): Long = if (elapsedRealtimeNanos > 0L) {
        elapsedRealtimeNanos / NANOSECONDS_PER_MILLISECOND
    } else {
        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - time)
    }

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

        /**
         * Past this, a position already known is no answer to "where am I?".
         *
         * Ten seconds: enough for two presses of the button in a row to spare
         * the radios a second fix, too short for the user to have gone
         * anywhere in between.
         */
        private val CACHE_MAXIMUM_AGE: Duration = Duration.ofSeconds(10)

        /** The delay past which we give up on obtaining a fix. */
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

        /**
         * How long a coarse first answer is given to be beaten.
         *
         * Four seconds: what a warm GPS needs to report, measured on a device.
         * Beyond that the user is waiting on a button, and a position to within
         * a few hundred metres, shown at once, serves them better.
         */
        private val REFINEMENT: Duration = Duration.ofSeconds(4)

        /**
         * How often at most a followed position is refreshed.
         *
         * Two seconds: a point that moves under the eye without the radio
         * being asked for more than a walker's pace produces.
         */
        private val FOLLOW_INTERVAL: Duration = Duration.ofSeconds(2)

        /** No throttling at all: a single fix is being waited for. */
        private val NO_INTERVAL: Duration = Duration.ZERO

        /**
         * How far one must move for a new fix to be reported, in metres.
         *
         * Five: below that the point would jitter on the spot, GPS accuracy in
         * a street being what it is.
         */
        private const val FOLLOW_DISTANCE_METRES = 5f

        /** And no threshold at all while waiting for that single fix. */
        private const val ANY_DISTANCE_METRES = 0f

        private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
    }
}
