package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Whether the availability figures are drawn larger (SPEC §7, §7.6).
 *
 * The rule under test is that **a preference nobody wrote draws the interface
 * exactly as it has always been drawn**: this setting adds a way of reading, it
 * does not change what everybody gets by default.
 */
class AppPreferencesLargeNumbersTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation draws the figures as it always has`() = runTest {
        val preferences = AppPreferences(newStore())

        assertFalse(preferences.largeAvailabilityNumbers.first())
    }

    @Test
    fun `the larger figures asked for are found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setLargeAvailabilityNumbers(true)

        assertTrue(preferences.largeAvailabilityNumbers.first())
    }

    @Test
    fun `the ordinary figures asked back for are found again`() = runTest {
        val preferences = AppPreferences(newStore())
        preferences.setLargeAvailabilityNumbers(true)

        preferences.setLargeAvailabilityNumbers(false)

        assertFalse(preferences.largeAvailabilityNumbers.first())
    }

    @Test
    fun `a value that is not a yes or a no draws the ordinary figures`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written: every screen showing an indicator collects this, so a
        // value read through its typed key would take them all down at once.
        val store = newStore()
        store.edit { it[stringPreferencesKey("large_availability_numbers")] = "true" }

        assertFalse(AppPreferences(store).largeAvailabilityNumbers.first())
    }
}
