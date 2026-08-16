package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What is remembered of the units distances are written in (SPEC §7.6, §9).
 *
 * The rule under test is that **nothing at rest means "follow the system"** — on
 * a fresh installation, and again whenever the word on disk cannot be read. A
 * value standing in for the region would show feet to somebody whose device
 * measures in metres, which is precisely the assumption about a country this
 * setting exists to remove.
 */
class AppPreferencesUnitsTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = stringPreferencesKey("units")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation follows the system`() = runTest {
        val preferences = AppPreferences(newStore())

        assertEquals(UnitChoice.FollowSystem, preferences.units.first())
    }

    @Test
    fun `the units chosen are found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setUnits(UnitChoice.UnitedKingdom)

        assertEquals(UnitChoice.UnitedKingdom, preferences.units.first())
    }

    @Test
    fun `a word this build cannot read follows the system`() = runTest {
        // Written by a version that knew another word, or by a hand.
        val store = newStore()

        store.edit { it[key] = "nautical" }

        assertEquals(UnitChoice.FollowSystem, AppPreferences(store).units.first())
    }

    @Test
    fun `the identifier written to disk is the stable one`() = runTest {
        // A rename would send every reader who chose their units back to their
        // region's, silently, on the release that renamed it.
        val store = newStore()

        AppPreferences(store).setUnits(UnitChoice.UnitedStates)

        assertEquals("united_states", store.data.first()[key])
    }
}
