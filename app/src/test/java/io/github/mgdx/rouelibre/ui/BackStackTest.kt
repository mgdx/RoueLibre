package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that keeps the map and the station list from stacking (SPEC §7.6).
 *
 * The two screens lead to each other, so a press that always builds the screen
 * asked for stacks map over list over map without end — and, before the way to
 * the map was made permanent, it also hid that way, its visibility having been
 * read off the depth of the stack. The names on the back stack are all that is
 * needed to tell where the wanted screen already is, which is what keeps this
 * decision on the JVM: no Android runtime is involved (SPEC §14).
 */
class BackStackTest {

    @Test
    fun `a map put up over the list is returned to, not built again`() {
        // The list one comes back to from the map: the map's transaction is
        // still on the stack, under the list's.
        assertEquals(
            ScreenBehind.Stacked,
            screenBehind(
                MAP_BACK_STACK_ENTRY,
                STATION_LIST_BACK_STACK_ENTRY,
                listOf(MAP_BACK_STACK_ENTRY, STATION_LIST_BACK_STACK_ENTRY),
            ),
        )
    }

    @Test
    fun `the map opened on is found under the stack`() {
        // The application opened on the map, so the map is not on the stack at
        // all: the list's own transaction is what stands over it.
        assertEquals(
            ScreenBehind.Underneath,
            screenBehind(
                MAP_BACK_STACK_ENTRY,
                STATION_LIST_BACK_STACK_ENTRY,
                listOf(STATION_LIST_BACK_STACK_ENTRY),
            ),
        )
    }

    @Test
    fun `a list opened on has no map behind it`() {
        assertEquals(
            ScreenBehind.Nowhere,
            screenBehind(
                MAP_BACK_STACK_ENTRY,
                STATION_LIST_BACK_STACK_ENTRY,
                emptyList(),
            ),
        )
    }

    @Test
    fun `screens the pair knows nothing of are no map`() {
        // Favourites, settings, a station's sheet: all pushed unnamed, and none
        // of them is a map to come back to.
        assertEquals(
            ScreenBehind.Nowhere,
            screenBehind(
                MAP_BACK_STACK_ENTRY,
                STATION_LIST_BACK_STACK_ENTRY,
                listOf(null, null),
            ),
        )
    }

    @Test
    fun `the nearer of the two wins`() {
        // Map opened on, list, map, list: both names are there, and the map to
        // come back to is the one just below, not the one under the whole stack.
        assertEquals(
            ScreenBehind.Stacked,
            screenBehind(
                MAP_BACK_STACK_ENTRY,
                STATION_LIST_BACK_STACK_ENTRY,
                listOf(
                    STATION_LIST_BACK_STACK_ENTRY,
                    MAP_BACK_STACK_ENTRY,
                    STATION_LIST_BACK_STACK_ENTRY,
                ),
            ),
        )
    }

    @Test
    fun `the rule reads the same way round from the map`() {
        // The map's way to the list, with a list opened on underneath.
        assertEquals(
            ScreenBehind.Underneath,
            screenBehind(
                STATION_LIST_BACK_STACK_ENTRY,
                MAP_BACK_STACK_ENTRY,
                listOf(MAP_BACK_STACK_ENTRY),
            ),
        )
    }

    @Test
    fun `the two names are told apart`() {
        // They are written into transactions and read back out of them: one
        // being a prefix or a copy of the other would make either screen answer
        // for the other.
        assertEquals(2, setOf(MAP_BACK_STACK_ENTRY, STATION_LIST_BACK_STACK_ENTRY).size)
    }
}
