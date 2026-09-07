package io.github.mgdx.rouelibre.ui.settings

import io.github.mgdx.rouelibre.data.SavedPlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which row of "my places" survives the screen going away (SPEC §7.6, §14).
 *
 * **The defect this holds shut was found on the phone, and no test of the
 * screen's wording could have seen it.** Wrocław installed, "Set Home" pressed,
 * an address chosen, and the row still read "Set Home": nothing reached the
 * settings file, nothing crashed, nothing was logged. The memory of which row
 * had asked was being wiped on the way back in, by the very line written to
 * carry it through a rotation — the bundle was read into the field
 * unconditionally, and on the ordinary path there is no bundle.
 *
 * The two paths are what this pins, because they pull in opposite directions:
 * on one the field is the only witness, on the other it is the bundle. A test
 * that checked only the rotation would have passed on the broken code.
 */
class AwaitedPlaceTest {

    @Test
    fun `a screen only unstacked keeps the row it was holding`() {
        // Going to the address search destroys the view and keeps the instance,
        // so onSaveInstanceState is never called: the field is all there is,
        // and the view is built again with no bundle at all. This is the
        // ordinary path — press a row, choose an address, come back — and it is
        // the one that was broken.
        assertEquals(
            SavedPlaceKind.Home,
            awaitedPlace(held = SavedPlaceKind.Home, savedName = null),
        )
        assertEquals(
            SavedPlaceKind.Work,
            awaitedPlace(held = SavedPlaceKind.Work, savedName = null),
        )
    }

    @Test
    fun `a screen built afresh takes the row from the bundle`() {
        // A rotation while the address search is open, or a process the system
        // killed: the fragment is recreated, the field is back to nothing, and
        // the bundle carries everything. This is what onSaveInstanceState
        // serves, and the fix must not cost it.
        assertEquals(SavedPlaceKind.Home, awaitedPlace(held = null, savedName = "Home"))
        assertEquals(SavedPlaceKind.Work, awaitedPlace(held = null, savedName = "Work"))
    }

    @Test
    fun `no row waiting stays no row waiting`() {
        // Opening the settings and never pressing a place: an address chosen
        // elsewhere and answered under the same key must land on nothing.
        assertNull(awaitedPlace(held = null, savedName = null))
    }

    @Test
    fun `a name the bundle cannot make out leaves the field as it was`() {
        // A bundle written by a later version, or truncated by a device out of
        // space, says nothing rather than saying "no row" — the reading
        // AppPreferences already gives a place it cannot make out.
        assertEquals(
            SavedPlaceKind.Home,
            awaitedPlace(held = SavedPlaceKind.Home, savedName = "Garage"),
        )
        assertNull(awaitedPlace(held = null, savedName = "Garage"))
        // An empty string is not a kind either, and is not a wipe.
        assertEquals(SavedPlaceKind.Work, awaitedPlace(held = SavedPlaceKind.Work, savedName = ""))
    }

    @Test
    fun `every kind survives being written down and read back`() {
        // The bundle carries the name, so a kind renamed in the code would take
        // the memory with it silently.
        SavedPlaceKind.entries.forEach { kind ->
            assertEquals(kind, awaitedPlace(held = null, savedName = kind.name))
        }
    }
}
