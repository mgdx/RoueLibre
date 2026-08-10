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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Exercises reading the position with a mock provider.
 *
 * A test provider, and not the real GPS: a test cannot depend on where the
 * device happens to be, nor wait for it to see the sky. The injected position
 * is in the centre of Lille, inside the data's bounding box.
 *
 * Mocking a position needs two permissions, and both are conditions of the
 * device rather than of the code:
 *
 * ```
 * adb shell appops set io.github.mgdx.rouelibre.debug android:mock_location allow
 * ```
 *
 * then, in the developer options, **"Mock location app" → Roue Libre**. Proven
 * on an Android 16: without that second setting the system accepts
 * `addTestProvider` and `setTestProviderLocation` without error, but delivers
 * the position to nobody. The test then abstains rather than failing.
 */
@RunWith(AndroidJUnit4::class)
class DeviceLocationTest {

    private lateinit var manager: LocationManager
    private lateinit var deviceLocation: DeviceLocation
    private var providerAdded = false

    /** The Grand-Place in Lille. */
    private val lille = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 50.6371
        longitude = 3.0630
        accuracy = 12f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Before
    fun installTestProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(LocationManager::class.java)
        deviceLocation = DeviceLocation(context)

        providerAdded = try {
            manager.addTestProvider(
                LocationManager.GPS_PROVIDER,
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
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            true
        } catch (_: SecurityException) {
            false
        }
        assumeTrue("mock location is not permitted on this device", providerAdded)

        // The system may accept the provider without delivering anything from
        // it: that is the case until the application is named as the "mock
        // location app" in the developer options.
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)
        assumeTrue(
            "désigne Roue Libre comme application de position fictive " +
                "dans les options pour développeurs",
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) != null,
        )
    }

    @After
    fun removeTestProvider() {
        if (!providerAdded) return
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    @Test
    fun returns_the_mock_position() {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)

        val position = deviceLocation.lastKnown()
        assertNotNull("mock position not read", position)

        assertEquals(lille.latitude, position!!.latitude, TOLERANCE)
        assertEquals(lille.longitude, position.longitude, TOLERANCE)
    }

    @Test
    fun waits_for_a_fix_when_no_position_is_known() = runBlocking {
        // No fix has been published yet: `current` must ask for one, and the
        // first provider to answer wins.
        val awaited = async { deviceLocation.current(Duration.ofSeconds(10)) }
        delay(FIX_DELAY_MILLIS)
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, lille)

        val position = awaited.await()
        assertNotNull("no fix obtained", position)
        assertEquals(lille.latitude, position!!.latitude, TOLERANCE)
    }

    @Test
    fun returns_nothing_when_the_provider_is_off() {
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)

        // The device's other providers may well stay active: what is verified
        // here is that a provider switched off is not read.
        assertNull(
            "un fournisseur éteint ne doit rien rendre",
            runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        )
    }

    private companion object {
        /** A hundred-thousandth of a degree: about a metre. */
        const val TOLERANCE = 1e-5

        /** The delay before publishing the fix, so the wait actually happens. */
        const val FIX_DELAY_MILLIS = 500L
    }
}
