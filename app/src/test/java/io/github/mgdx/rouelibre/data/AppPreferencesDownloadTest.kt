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
 * What is remembered of the connection datasets may travel on (SPEC §4.4).
 *
 * The rule under test is that **nothing at rest means "wait for a connection
 * nobody is billed for"**: on a fresh installation, and again whenever the
 * setting has never been touched. Read the other way round, a gigabyte would
 * leave on a mobile plan on the very launch where nobody has yet been asked.
 */
class AppPreferencesDownloadTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = booleanPreferencesKey("download_on_unmetered_only")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation waits for an unmetered connection`() = runTest {
        val preferences = AppPreferences(newStore())

        assertTrue(preferences.downloadOnUnmeteredOnly.first())
    }

    @Test
    fun `the choice made is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setDownloadOnUnmeteredOnly(false)

        assertFalse(preferences.downloadOnUnmeteredOnly.first())
    }

    @Test
    fun `the identifier written to disk is the stable one`() = runTest {
        // A rename would put every reader who lifted the restriction back
        // behind it, silently, on the release that renamed it.
        val store = newStore()

        AppPreferences(store).setDownloadOnUnmeteredOnly(false)

        assertEquals(false, store.data.first()[key])
    }

    @Test
    fun `a value written by hand is read as written`() = runTest {
        val store = newStore()

        store.edit { it[key] = false }

        assertFalse(AppPreferences(store).downloadOnUnmeteredOnly.first())
    }

    @Test
    fun `a value that is not a yes or a no still waits for an unmetered connection`() = runTest {
        // Written by a version that stored it differently, or by a file left
        // half-written. Read as anything but "wait", a settings file nobody can
        // make sense of would send a gigabyte out on a mobile plan.
        val store = newStore()
        store.edit { it[stringPreferencesKey("download_on_unmetered_only")] = "false" }

        assertTrue(AppPreferences(store).downloadOnUnmeteredOnly.first())
    }
}
