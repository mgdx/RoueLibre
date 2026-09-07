package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mgdx.rouelibre.core.geo.Coordinates
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
 * What is remembered of the two places the user names (SPEC §7.6, §2 C3).
 *
 * The first rule under test is that **a place exists only if its three keys
 * read**. Any one of them missing, blank or of the wrong type is read as no
 * place at all — never as a point at zero, which is a real spot in the Gulf of
 * Guinea and would have a journey drawn to it.
 *
 * The second is that **a label is stored whole**: an address carries commas,
 * apostrophes and line breaks, which is why the label has a key of its own
 * rather than sharing one with the coordinates.
 */
class AppPreferencesSavedPlacesTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** The very keys the preferences write under, so the contract is the real one. */
    private val homeLabel = stringPreferencesKey("saved_place_home_label")
    private val homeLatitude = doublePreferencesKey("saved_place_home_latitude")
    private val homeLongitude = doublePreferencesKey("saved_place_home_longitude")

    private val home = SavedPlace("12 Rue Nationale", Coordinates(50.6292, 3.0573))
    private val work = SavedPlace("Gare Lille Flandres", Coordinates(50.6365, 3.0703))

    /** A settings file of its own per test, in a folder JUnit throws away. */
    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.Unconfined),
        produceFile = { folder.newFile("settings.preferences_pb") },
    )

    @Test
    fun `a fresh installation has named no place`() = runTest {
        val preferences = AppPreferences(newStore())

        assertNull(preferences.homePlace.first())
        assertNull(preferences.workPlace.first())
    }

    @Test
    fun `the place declared is found again`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setSavedPlace(SavedPlaceKind.Home, home)

        assertEquals(home, preferences.homePlace.first())
        assertEquals(home, preferences.savedPlace(SavedPlaceKind.Home).first())
    }

    @Test
    fun `declaring a place again replaces the one before`() = runTest {
        // Moving house must not leave two homes on the disk, one of them wrong.
        val preferences = AppPreferences(newStore())
        preferences.setSavedPlace(SavedPlaceKind.Home, home)

        preferences.setSavedPlace(SavedPlaceKind.Home, work)

        assertEquals(work, preferences.homePlace.first())
    }

    @Test
    fun `a place forgotten is gone from the disk`() = runTest {
        // Erasable with one press is the whole reason this may be stored at all
        // (SPEC §2, C3), so nothing of it may survive the press.
        val store = newStore()
        val preferences = AppPreferences(store)
        preferences.setSavedPlace(SavedPlaceKind.Home, home)

        preferences.clearSavedPlace(SavedPlaceKind.Home)

        assertNull(preferences.homePlace.first())
        assertNull(store.data.first()[homeLabel])
        assertNull(store.data.first()[homeLatitude])
        assertNull(store.data.first()[homeLongitude])
    }

    @Test
    fun `the two places are independent of one another`() = runTest {
        val preferences = AppPreferences(newStore())

        preferences.setSavedPlace(SavedPlaceKind.Home, home)
        preferences.setSavedPlace(SavedPlaceKind.Work, work)
        preferences.clearSavedPlace(SavedPlaceKind.Work)

        assertEquals(home, preferences.homePlace.first())
        assertNull(preferences.workPlace.first())
    }

    @Test
    fun `a place without its label is no place`() = runTest {
        val store = newStore()
        AppPreferences(store).setSavedPlace(SavedPlaceKind.Home, home)

        store.edit { it.remove(homeLabel) }

        assertNull(AppPreferences(store).homePlace.first())
    }

    @Test
    fun `a place without its latitude is no place, not a point at zero`() = runTest {
        val store = newStore()
        AppPreferences(store).setSavedPlace(SavedPlaceKind.Home, home)

        store.edit { it.remove(homeLatitude) }

        assertNull(AppPreferences(store).homePlace.first())
    }

    @Test
    fun `a place without its longitude is no place, not a point at zero`() = runTest {
        val store = newStore()
        AppPreferences(store).setSavedPlace(SavedPlaceKind.Home, home)

        store.edit { it.remove(homeLongitude) }

        assertNull(AppPreferences(store).homePlace.first())
    }

    @Test
    fun `a blank label is no place`() = runTest {
        // A place with nothing to show is a place no screen can offer.
        val store = newStore()
        AppPreferences(store).setSavedPlace(SavedPlaceKind.Home, home.copy(label = "   "))

        assertNull(AppPreferences(store).homePlace.first())
    }

    @Test
    fun `a coordinate that cannot be read is no place`() = runTest {
        // Written by a version that stored it differently, or truncated by a
        // device out of space. Reading it as a number would throw where the
        // screens expect a place or nothing.
        val store = newStore()
        AppPreferences(store).setSavedPlace(SavedPlaceKind.Home, home)

        store.edit {
            it.remove(homeLatitude)
            it[stringPreferencesKey("saved_place_home_latitude")] = "50.6292"
        }

        assertNull(AppPreferences(store).homePlace.first())
    }

    @Test
    fun `a label with a comma, a line break and an apostrophe reads back whole`() = runTest {
        // The reason the label has a key of its own: any separator chosen would
        // eventually fall inside somebody's address and cut their home in two.
        val preferences = AppPreferences(newStore())
        val awkward = SavedPlace(
            label = "3, rue de l'Hôpital Militaire\n59800 Lille",
            position = Coordinates(50.6412, 3.0587),
        )

        preferences.setSavedPlace(SavedPlaceKind.Work, awkward)

        assertEquals(awkward, preferences.workPlace.first())
    }
}
