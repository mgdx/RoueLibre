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
 * The rule under test is that **the mechanical bike is the answer at rest** —
 * on a fresh installation and whenever the word on disk cannot be read. It is
 * the ride, the drawing and the sentence of the version before this choice
 * existed, and since 19 August 2026 it is also what an installation that
 * declared nothing is read as: the setting used to carry a third state saying
 * nothing, and that state rode exactly as this one does.
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
    fun `a fresh installation rides the mechanical bike`() = runTest {
        val preferences = AppPreferences(newStore())

        assertEquals(OwnBikeKind.Mechanical, preferences.ownBikeKind.first())
    }

    @Test
    fun `the kind declared is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setOwnBikeKind(OwnBikeKind.Electric)

        assertEquals(OwnBikeKind.Electric, preferences.ownBikeKind.first())
    }

    @Test
    fun `coming back to the mechanical bike is found again too`() = runTest {
        // The way back from the declared electric bike, which the removal of
        // the third state made the only way back there is.
        val store = newStore()
        val preferences = AppPreferences(store)
        preferences.setOwnBikeKind(OwnBikeKind.Electric)

        preferences.setOwnBikeKind(OwnBikeKind.Mechanical)

        assertEquals(OwnBikeKind.Mechanical, preferences.ownBikeKind.first())
        assertEquals(OwnBikeKind.Mechanical.id, store.data.first()[key])
    }

    @Test
    fun `a word this build cannot read rides the mechanical bike`() = runTest {
        // Written by a version that knew another word, or by a hand. Guessing
        // the other way would put a bolt on the bike of somebody who never
        // claimed one, and time their journey as though it had a motor.
        val store = newStore()

        store.edit { it[key] = "cargo" }

        assertEquals(OwnBikeKind.Mechanical, AppPreferences(store).ownBikeKind.first())
    }

    @Test
    fun `an installation that declared nothing rides the mechanical bike`() = runTest {
        // The state left behind by the build that offered "not specified": no
        // word at all on the disk. It rode as a mechanical bike then and it is
        // read as one now, which is why nothing had to be migrated.
        val store = newStore()

        assertNull(store.data.first()[key])
        assertEquals(OwnBikeKind.Mechanical, AppPreferences(store).ownBikeKind.first())
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
