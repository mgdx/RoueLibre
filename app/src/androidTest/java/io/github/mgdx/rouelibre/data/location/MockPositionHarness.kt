// `Criteria` and `addTestProvider` only have a modern signature from API 30
// on; the application targets API 26, so it is the old one that has to be
// exercised.
@file:Suppress("DEPRECATION")

package io.github.mgdx.rouelibre.data.location

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holds a chosen position on the device, for exploring the application by hand.
 *
 * This is a tool, not a test: it asserts nothing. It exists because there is no
 * way to inject a position from `adb` alone — only an application holding the
 * `mock_location` app op can, and the instrumentation is the only one the
 * project ships. Trying out a journey in a city one is not in otherwise means
 * installing a third-party mock-location application, which the project's
 * constraints on dependencies would not accept.
 *
 * It abstains unless a latitude is given, so a plain `connectedAndroidTest`
 * run walks straight past it.
 *
 * ```
 * adb shell appops set io.github.mgdx.rouelibre.debug android:mock_location allow
 * adb shell am instrument -w \
 *     -e class io.github.mgdx.rouelibre.data.location.MockPositionHarness \
 *     -e latitude 50.633868 -e longitude 3.044314 -e minutes 60 \
 *     io.github.mgdx.rouelibre.debug.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * The position holds as long as the command runs, and disappears with it: a
 * mock provider belongs to the process that installed it. Interrupting the
 * command therefore gives the device its real GPS back — which is also why it
 * must not be left running by accident, the mock replacing the system provider
 * for every application, not just this one.
 */
@RunWith(AndroidJUnit4::class)
class MockPositionHarness {

    @Test
    fun holds_the_position_given_on_the_command_line() {
        val arguments = InstrumentationRegistry.getArguments()
        val latitude = arguments.getString("latitude")?.toDoubleOrNull()
        val longitude = arguments.getString("longitude")?.toDoubleOrNull()
        assumeTrue(
            "give -e latitude and -e longitude to hold a position",
            latitude != null && longitude != null,
        )

        val minutes = arguments.getString("minutes")?.toLongOrNull() ?: DEFAULT_MINUTES
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(LocationManager::class.java)

        val installed = PROVIDERS.filter { provider ->
            runCatching {
                manager.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE,
                )
                manager.setTestProviderEnabled(provider, true)
            }.isSuccess
        }
        assumeTrue("mock location is not permitted on this device", installed.isNotEmpty())

        try {
            val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                // Republished continuously: the application refuses a fix older
                // than two minutes, and a listener subscribed after the first
                // one would otherwise wait forever.
                installed.forEach { provider ->
                    manager.setTestProviderLocation(
                        provider,
                        fix(provider, latitude!!, longitude!!),
                    )
                }
                Thread.sleep(REFRESH_MILLIS)
            }
        } finally {
            installed.forEach { runCatching { manager.removeTestProvider(it) } }
        }
    }

    private fun fix(provider: String, latitude: Double, longitude: Double) =
        Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = ACCURACY_METRES
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

    private companion object {
        /** The providers the application reads, save the passive one. */
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        /** How long the position is held when no duration is given. */
        const val DEFAULT_MINUTES = 30L

        /** Well under the two minutes past which the application drops a fix. */
        const val REFRESH_MILLIS = 2_000L

        /** A plausible accuracy for a fix out in the open. */
        const val ACCURACY_METRES = 8f
    }
}
