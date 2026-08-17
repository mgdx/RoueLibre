package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 * Which stations the map is told to leave out (SPEC §7.1, §7.6).
 *
 * The rule under test is that **nothing at rest hides nothing**: on a fresh
 * installation, and again for a settings file this build cannot make sense of.
 * These two are the only settings whose effect is invisible on the screen they
 * act upon, so a value read wrongly would empty a map with nothing anywhere to
 * say why.
 */
class AppPreferencesStationFiltersTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very keys the preferences write under, so the contract is the real one. */
    private val outOfServiceKey = booleanPreferencesKey("hide_out_of_service_stations")
    private val emptyKey = booleanPreferencesKey("hide_empty_stations")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation hides no station`() = runTest {
        val preferences = AppPreferences(newStore())

        assertFalse(preferences.hideOutOfServiceStations.first())
        assertFalse(preferences.hideEmptyStations.first())
    }

    @Test
    fun `the filters asked for are found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setHideOutOfServiceStations(true)
        preferences.setHideEmptyStations(true)

        assertTrue(preferences.hideOutOfServiceStations.first())
        assertTrue(preferences.hideEmptyStations.first())
    }

    @Test
    fun `the filters turned off again are off again`() = runTest {
        val preferences = AppPreferences(newStore())
        preferences.setHideOutOfServiceStations(true)
        preferences.setHideEmptyStations(true)

        preferences.setHideOutOfServiceStations(false)
        preferences.setHideEmptyStations(false)

        assertFalse(preferences.hideOutOfServiceStations.first())
        assertFalse(preferences.hideEmptyStations.first())
    }

    @Test
    fun `each filter is written under its own stable identifier`() = runTest {
        // A rename would put a map back to hiding stations, or stop it hiding
        // them, without anybody having touched a switch.
        val store = newStore()

        AppPreferences(store).setHideEmptyStations(true)

        assertEquals(true, store.data.first()[emptyKey])
        assertEquals(null, store.data.first()[outOfServiceKey])
    }

    @Test
    fun `a value that is not a yes or a no hides nothing`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written: the map must come back whole rather than come back
        // empty, since nothing on it would say a filter is on.
        val store = newStore()
        store.edit {
            it[stringPreferencesKey("hide_out_of_service_stations")] = "true"
            it[stringPreferencesKey("hide_empty_stations")] = "true"
        }

        val preferences = AppPreferences(store)

        assertFalse(preferences.hideOutOfServiceStations.first())
        assertFalse(preferences.hideEmptyStations.first())
    }
}
