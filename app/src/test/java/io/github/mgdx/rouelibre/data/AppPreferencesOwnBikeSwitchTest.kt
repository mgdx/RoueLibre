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
 * Whether journeys are worked out for the rider's own bike (SPEC §7.3).
 *
 * The rule under test is that **nothing at rest is the journey the application
 * is for** — the walk → bike → walk of §6 — on a fresh installation and again
 * for a settings file this build cannot make sense of. Read the other way
 * round, somebody who never touched the switch would be answered with a ride
 * on a bike they may not own.
 */
class AppPreferencesOwnBikeSwitchTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation goes through the stations`() = runTest {
        val preferences = AppPreferences(newStore())

        assertFalse(preferences.usesOwnBike.first())
    }

    @Test
    fun `the switch turned on is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setUsesOwnBike(true)

        assertTrue(preferences.usesOwnBike.first())
    }

    @Test
    fun `a value that is not a yes or a no goes through the stations`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written: the journey screen collects this, and a value read
        // through its typed key would take that screen down rather than offer
        // the journey the application is for.
        val store = newStore()
        store.edit { it[stringPreferencesKey("uses_own_bike")] = "true" }

        assertFalse(AppPreferences(store).usesOwnBike.first())
    }
}
