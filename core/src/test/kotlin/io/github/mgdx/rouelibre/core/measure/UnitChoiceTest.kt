package io.github.mgdx.rouelibre.core.measure

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the application reads back from the units setting (SPEC §7.6).
 *
 * The rule under test is that **nothing at rest means "follow the system"** — on
 * a fresh installation, and again whenever the word on disk cannot be read. A
 * value standing in for the region would show somebody feet they never asked
 * for, on a device that says it measures in metres.
 */
class UnitChoiceTest {

    @Test
    fun `a fresh installation follows the system`() {
        assertEquals(UnitChoice.FollowSystem, UnitChoice.fromId(null))
    }

    @Test
    fun `a word this build cannot read follows the system`() {
        // Written by a version that knew another word, or by a hand.
        assertEquals(UnitChoice.FollowSystem, UnitChoice.fromId("nautical"))
        assertEquals(UnitChoice.FollowSystem, UnitChoice.fromId(""))
    }

    @Test
    fun `each choice is found again under its own identifier`() {
        // The identifiers are written to disk and must survive a release: a
        // rename here silently sends every reader back to their region's units.
        for (choice in UnitChoice.entries) {
            assertEquals(choice, UnitChoice.fromId(choice.id))
        }
        assertEquals(
            listOf("follow_system", "metric", "united_states", "united_kingdom"),
            UnitChoice.entries.map { it.id },
        )
    }

    @Test
    fun `following the system takes the region's answer, whatever it is`() {
        for (region in UnitSystem.entries) {
            assertEquals(region, UnitChoice.FollowSystem.resolve(region))
        }
    }

    @Test
    fun `a choice made overrides the region`() {
        // The whole point of the setting: somebody in Boston who wants
        // kilometres gets kilometres, and the other way round.
        assertEquals(UnitSystem.Metric, UnitChoice.Metric.resolve(UnitSystem.UnitedStates))
        assertEquals(
            UnitSystem.UnitedStates,
            UnitChoice.UnitedStates.resolve(UnitSystem.Metric),
        )
        assertEquals(
            UnitSystem.UnitedKingdom,
            UnitChoice.UnitedKingdom.resolve(UnitSystem.Metric),
        )
    }
}
