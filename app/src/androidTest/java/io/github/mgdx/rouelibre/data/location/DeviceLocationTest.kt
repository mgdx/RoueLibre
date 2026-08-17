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
import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Mocking a position is a condition of the device rather than of the code, and
 * it takes three things — verified on a Fairphone 5 under Android 16:
 *
 * ```
 * adb shell appops set io.github.mgdx.rouelibre.debug android:mock_location allow
 * adb shell pm grant io.github.mgdx.rouelibre.debug android.permission.ACCESS_FINE_LOCATION
 * adb shell am instrument -w -e class …DeviceLocationTest \
 *     io.github.mgdx.rouelibre.debug.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * The last line matters as much as the first two: `connectedAndroidTest`
 * reinstalls the application before every run, and installing resets the app
 * op. Run through Gradle, these tests will keep abstaining however carefully
 * the device was prepared.
 *
 * Without the app op, `addTestProvider` throws and the test abstains rather
 * than failing: it is the environment that is missing something, not the code.
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
            "name Roue Libre as the mock location app " +
                "in the developer options",
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

        assertEquals(lille.latitude, position!!.coordinates.latitude, TOLERANCE)
        assertEquals(lille.longitude, position.coordinates.longitude, TOLERANCE)
    }

    @Test
    fun waits_for_a_fix_rather_than_serve_a_position_already_known() = runBlocking {
        // "Locate me" asks where one IS. A position from a minute ago says
        // where one WAS, and serving it is what left the point standing still
        // while the user walked on: `current` must go and ask for a fix.
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, aged(lille))
        val awaited = async { deviceLocation.current(Duration.ofSeconds(10)) }
        delay(FIX_DELAY_MILLIS)
        val moved = movedNorthBy(TRACK_STEP_METRES)
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, moved)

        val position = awaited.await()
        assertNotNull("no fix obtained", position)
        assertEquals(
            "the position already known was served instead of a fresh fix",
            moved.latitude,
            position!!.coordinates.latitude,
            TOLERANCE,
        )
    }

    @Test
    fun the_followed_position_moves_with_the_device() = runBlocking {
        // The regression this whole file exists for: the point stayed put
        // while the device went on, every fix after the first being turned
        // down. Three points fifty metres apart, and all three must be seen.
        val track = List(TRACK_POINTS) { step -> movedNorthBy(step * TRACK_STEP_METRES) }
        val seen = mutableListOf<Coordinates>()
        val following = launch { deviceLocation.positions().collect { seen += it.coordinates } }

        track.forEach { point ->
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, point)
            // Longer than the two seconds the subscription throttles at, so
            // that no fix is dropped for having come too soon after another.
            delay(FOLLOW_STEP_MILLIS)
        }
        following.cancel()

        val last = seen.lastOrNull()
        assertNotNull("no position followed at all", last)
        assertEquals(
            "the followed point stopped moving",
            track.last().latitude,
            last!!.latitude,
            TOLERANCE,
        )
        assertEquals(
            "one fix per point of the track was expected",
            TRACK_POINTS,
            seen.distinct().size,
        )
    }

    /** The same point, stamped as taken long enough ago to be worth nothing. */
    private fun aged(point: Location) = Location(point).apply {
        time = System.currentTimeMillis() - STALE_AGE_MILLIS
        elapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos() - STALE_AGE_MILLIS * NANOSECONDS_PER_MILLISECOND
    }

    /** The Grand-Place, moved [metres] due north. */
    private fun movedNorthBy(metres: Double) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = lille.latitude + metres / METRES_PER_DEGREE_OF_LATITUDE
        longitude = lille.longitude
        accuracy = 12f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    @Test
    fun returns_nothing_when_the_provider_is_off() {
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)

        // The device's other providers may well stay active: what is verified
        // here is that a provider switched off is not read.
        assertNull(
            "a provider switched off must return nothing",
            runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        )
    }

    private companion object {
        /** A hundred-thousandth of a degree: about a metre. */
        const val TOLERANCE = 1e-5

        /** The delay before publishing the fix, so the wait actually happens. */
        const val FIX_DELAY_MILLIS = 500L

        /** How many points of the followed track are published. */
        const val TRACK_POINTS = 3

        /** How far apart they are, in metres: well past the five-metre floor. */
        const val TRACK_STEP_METRES = 50.0

        /** And how far apart in time: past the two seconds of the throttle. */
        const val FOLLOW_STEP_MILLIS = 2_500L

        /** A degree of latitude, in metres. Constant, unlike a longitude's. */
        const val METRES_PER_DEGREE_OF_LATITUDE = 111_320.0

        /** How old a position has to be for the application to disown it. */
        const val STALE_AGE_MILLIS = 60_000L

        const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
    }
}
