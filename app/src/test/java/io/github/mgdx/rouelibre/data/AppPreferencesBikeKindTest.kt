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
 * What is remembered of the bike somebody wants to ride (SPEC §7.3, §2 C3).
 *
 * Nothing here describes a journey: one word about equipment, or nothing at all.
 * The rule under test is that **nothing at all is the answer at rest** — on a
 * fresh installation, and again whenever the word on disk cannot be read.
 */
class AppPreferencesBikeKindTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = stringPreferencesKey("wanted_bike_kind")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation asks for no kind`() = runTest {
        val preferences = AppPreferences(newStore())

        assertNull(preferences.wantedBikeKind.first())
    }

    @Test
    fun `the kind chosen is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setWantedBikeKind(WantedBikeKind.Electric)

        assertEquals(WantedBikeKind.Electric, preferences.wantedBikeKind.first())
    }

    @Test
    fun `choosing no kind takes the word off the disk`() = runTest {
        val store = newStore()
        val preferences = AppPreferences(store)
        preferences.setWantedBikeKind(WantedBikeKind.Mechanical)

        preferences.setWantedBikeKind(null)

        assertNull(preferences.wantedBikeKind.first())
        assertNull(store.data.first()[key])
    }

    @Test
    fun `a word this build cannot read asks for no kind`() = runTest {
        // Written by a version that knew another word, or by a hand. Standing
        // in for it with a guess would filter the journeys of somebody who
        // never asked for anything.
        val store = newStore()

        store.edit { it[key] = "moped" }

        assertNull(AppPreferences(store).wantedBikeKind.first())
    }
}
