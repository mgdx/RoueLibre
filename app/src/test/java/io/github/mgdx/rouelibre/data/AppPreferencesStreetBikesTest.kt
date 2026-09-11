package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The setting that draws the bikes outside stations, and the explanation shown
 * once with it (SPEC §7.6).
 *
 * The rule under test is that **nothing at rest draws nothing**: on a fresh
 * installation, and again for a settings file this build cannot make sense
 * of, the setting is off — it is opt-in twice over, for the markers it draws
 * and for the feed it reads. And the explanation shown once stays shown: a
 * dialog that came back at every launch would be the insistence the section
 * refuses.
 */
class AppPreferencesStreetBikesTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very keys the preferences write under, so the contract is the real one. */
    private val showKey = booleanPreferencesKey("show_street_bikes")
    private val explainedKey = booleanPreferencesKey("street_bikes_explained")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation draws no street bike and has explained nothing`() = runTest {
        val preferences = AppPreferences(newStore())

        assertFalse(preferences.showStreetBikes.first())
        assertFalse(preferences.streetBikesExplained())
    }

    @Test
    fun `the setting asked for is found again, and off again when turned off`() = runTest {
        val store = newStore()
        AppPreferences(store).setShowStreetBikes(true)

        // A new reader over the same file: what survives a restart is the
        // point, since the map follows the stored value itself.
        assertTrue(AppPreferences(store).showStreetBikes.first())

        AppPreferences(store).setShowStreetBikes(false)

        assertFalse(AppPreferences(store).showStreetBikes.first())
    }

    @Test
    fun `an explanation shown once stays shown`() = runTest {
        val store = newStore()
        AppPreferences(store).setStreetBikesExplained()

        assertTrue(AppPreferences(store).streetBikesExplained())
    }

    @Test
    fun `a value that is not a yes or a no counts as never answered`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written: read as off and as never explained, the cost is one
        // dialog, where a map drawing bikes nobody asked for would cost a
        // feed twenty times the station feed.
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("show_street_bikes")] = "true"
            it[stringPreferencesKey("street_bikes_explained")] = "true"
        }

        assertFalse(AppPreferences(store).showStreetBikes.first())
        assertFalse(AppPreferences(store).streetBikesExplained())
    }

    @Test
    fun `the feed is read every five minutes until the user says otherwise`() = runTest {
        val store = newStore()

        assertEquals(5, AppPreferences(store).vehicleFeedRefreshMinutes.first())

        AppPreferences(store).setVehicleFeedRefreshMinutes(12)

        assertEquals(12, AppPreferences(store).vehicleFeedRefreshMinutes.first())
    }

    @Test
    fun `a cadence outside one to thirty minutes is neither kept nor read`() = runTest {
        // A zero would read the feed on every tick of the map; a value a
        // version wrote differently falls back on the default rather than
        // on a silence nobody can see.
        val store = newStore()
        val preferences = AppPreferences(store)

        preferences.setVehicleFeedRefreshMinutes(0)
        assertEquals(1, preferences.vehicleFeedRefreshMinutes.first())

        preferences.setVehicleFeedRefreshMinutes(90)
        assertEquals(30, preferences.vehicleFeedRefreshMinutes.first())

        store.edit { it[intPreferencesKey("vehicle_feed_refresh_minutes")] = 45 }
        assertEquals(5, preferences.vehicleFeedRefreshMinutes.first())

        store.edit { it[stringPreferencesKey("vehicle_feed_refresh_minutes")] = "7" }
        assertEquals(5, preferences.vehicleFeedRefreshMinutes.first())
    }

    @Test
    fun `each setting is written under its own stable identifier`() = runTest {
        // A rename would put a map back to drawing nothing, or bring the
        // dialog back, without anybody having touched a switch.
        val store = newStore()
        val preferences = AppPreferences(store)

        preferences.setShowStreetBikes(true)
        preferences.setStreetBikesExplained()

        val stored = store.data.first()
        assertEquals(true, stored[showKey])
        assertEquals(true, stored[explainedKey])
    }
}
