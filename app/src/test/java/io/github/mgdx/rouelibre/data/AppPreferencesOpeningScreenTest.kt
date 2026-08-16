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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Which screen the application opens on (SPEC §7.0, §7.6).
 *
 * The rule under test is that **the map is the answer whenever nothing legible
 * says otherwise** — on a fresh installation, and again whenever the word on
 * disk was written by another version or by a hand. Anything else would send
 * somebody to a screen they never asked for, and — the list being the one that
 * shows no position — would decide on their behalf whether the location
 * permission is asked for at all (SPEC §10).
 */
class AppPreferencesOpeningScreenTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = stringPreferencesKey("opening_screen")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation opens on the map`() = runTest {
        val preferences = AppPreferences(newStore())

        assertEquals(OpeningScreen.Map, preferences.openingScreen.first())
    }

    @Test
    fun `the list chosen is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setOpeningScreen(OpeningScreen.StationList)

        assertEquals(OpeningScreen.StationList, preferences.openingScreen.first())
    }

    @Test
    fun `the map chosen back is found again`() = runTest {
        val preferences = AppPreferences(newStore())
        preferences.setOpeningScreen(OpeningScreen.StationList)

        preferences.setOpeningScreen(OpeningScreen.Map)

        assertEquals(OpeningScreen.Map, preferences.openingScreen.first())
    }

    @Test
    fun `a word this build cannot read opens on the map`() = runTest {
        val store = newStore()

        store.edit { it[key] = "favourites" }

        assertEquals(OpeningScreen.Map, AppPreferences(store).openingScreen.first())
    }

    @Test
    fun `every choice writes a word this build reads back`() {
        // The identifiers are what sits on the disk from one release to the
        // next: a rename here would silently take every user back to the map.
        assertEquals(
            OpeningScreen.entries,
            OpeningScreen.entries.map { OpeningScreen.fromId(it.id) },
        )
    }
}
