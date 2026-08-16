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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What is remembered of the rider's **own** bike (SPEC §7.3, §7.6, §2 C3).
 *
 * The rule under test is that **nothing said is the answer at rest** — on a
 * fresh installation and whenever the word on disk cannot be read — because "not
 * specified" is the state that reproduces exactly the drawings and the sentences
 * of the version before this choice existed.
 *
 * The second rule is that it is **not** `wantedBikeKind`: they are two words on
 * the same disk, one about the network's bikes and one about the rider's, and
 * writing either must leave the other exactly where it was.
 */
class AppPreferencesOwnBikeKindTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very key the preferences write under, so the contract is the real one. */
    private val key = stringPreferencesKey("own_bike_kind")

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation says nothing about the rider's bike`() = runTest {
        val preferences = AppPreferences(newStore())

        assertNull(preferences.ownBikeKind.first())
    }

    @Test
    fun `the kind declared is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setOwnBikeKind(OwnBikeKind.Electric)

        assertEquals(OwnBikeKind.Electric, preferences.ownBikeKind.first())
    }

    @Test
    fun `going back to nothing said takes the word off the disk`() = runTest {
        val store = newStore()
        val preferences = AppPreferences(store)
        preferences.setOwnBikeKind(OwnBikeKind.Mechanical)

        preferences.setOwnBikeKind(null)

        assertNull(preferences.ownBikeKind.first())
        assertNull(store.data.first()[key])
    }

    @Test
    fun `a word this build cannot read says nothing about the rider's bike`() = runTest {
        // Written by a version that knew another word, or by a hand. Guessing
        // would put a bolt on the bike of somebody who never claimed one.
        val store = newStore()

        store.edit { it[key] = "cargo" }

        assertNull(AppPreferences(store).ownBikeKind.first())
    }

    @Test
    fun `the rider's bike and the network's are two separate words`() = runTest {
        // The trap this whole setting was written to avoid: two neighbouring
        // choices, opposite rules, and one key would make each overwrite the
        // other.
        val preferences = AppPreferences(newStore())

        preferences.setOwnBikeKind(OwnBikeKind.Electric)
        preferences.setWantedBikeKind(null)

        assertEquals(OwnBikeKind.Electric, preferences.ownBikeKind.first())
        assertNull(preferences.wantedBikeKind.first())
    }

    @Test
    fun `every kind writes a word this build reads back`() {
        // The identifiers are what sits on the disk from one release to the
        // next: a rename here would silently un-declare everybody's bike.
        assertTrue(
            OwnBikeKind.entries.all { OwnBikeKind.fromId(it.id) == it },
        )
    }
}
