package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The refusal of a location request, as it is written down (SPEC §10).
 *
 * The rule under test is that a refusal outlives the session — it is what
 * keeps the map from asking unprompted at the next launch — while a fresh
 * installation, or a settings file this build cannot make sense of, still
 * counts as never refused: the map keeps its one unprompted ask.
 */
class AppPreferencesLocationRequestTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation has refused nothing`() = runTest {
        val preferences = AppPreferences(newStore())

        assertFalse(preferences.locationRequestDeclined())
    }

    @Test
    fun `a refusal recorded is found again`() = runTest {
        val store = newStore()
        AppPreferences(store).setLocationRequestDeclined()

        // A new reader over the same file: what survives a restart is the
        // point — a refusal held in memory alone was the bug being fixed.
        assertTrue(AppPreferences(store).locationRequestDeclined())
    }

    @Test
    fun `a value that is not a yes or a no counts as never refused`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written: read as "never refused", the cost is one dialog, where
        // a crash would cost the screen.
        val store = newStore()
        store.edit { it[stringPreferencesKey("location_request_declined")] = "true" }

        assertFalse(AppPreferences(store).locationRequestDeclined())
    }
}
