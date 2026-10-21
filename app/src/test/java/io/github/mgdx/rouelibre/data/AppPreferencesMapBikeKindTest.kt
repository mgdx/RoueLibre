package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What is remembered of the kind of bike the map counts (SPEC §7.1).
 *
 * Two rules: all bikes is the answer at rest — on a fresh installation and for
 * a word this build cannot read — and the map's kind and the journey's are two
 * answers, neither of which ever moves the other.
 */
class AppPreferencesMapBikeKindTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = stringPreferencesKey("map_bike_kind")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation shows every bike`() = runTest {
        assertNull(AppPreferences(newStore()).mapBikeKind.first())
    }

    @Test
    fun `the kind chosen on the map is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setMapBikeKind(WantedBikeKind.Electric)

        assertEquals(WantedBikeKind.Electric, preferences.mapBikeKind.first())
    }

    @Test
    fun `going back to every bike takes the word off the disk`() = runTest {
        val store = newStore()
        val preferences = AppPreferences(store)
        preferences.setMapBikeKind(WantedBikeKind.Mechanical)

        preferences.setMapBikeKind(null)

        assertNull(preferences.mapBikeKind.first())
        assertNull(store.data.first()[key])
    }

    @Test
    fun `a word this build cannot read shows every bike`() = runTest {
        // Guessing a kind would hide bikes from somebody who never asked.
        val store = newStore()

        store.edit { it[key] = "moped" }

        assertNull(AppPreferences(store).mapBikeKind.first())
    }

    /** SPEC §7.1: the map's filter is its own, and changes no journey. */
    @Test
    fun `the map's kind and the journey's never move each other`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setMapBikeKind(WantedBikeKind.Electric)
        assertNull(preferences.wantedBikeKind.first())

        preferences.setWantedBikeKind(WantedBikeKind.Mechanical)
        assertEquals(WantedBikeKind.Electric, preferences.mapBikeKind.first())
    }
}
