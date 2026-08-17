package io.github.mgdx.rouelibre.data

import io.github.mgdx.rouelibre.core.journey.RiddenBike
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The translation from what the rider was asked to what the algorithm rides.
 *
 * Two questions converge here and nowhere else (SPEC §7.6), so this is where
 * the pessimistic reading of "any bike" is pinned down: it is a rule of §6, not
 * an implementation detail, and it is the one a refactoring is most likely to
 * lose by treating `null` as "whatever is at the station".
 */
class RiddenBikesTest {

    @Test
    fun `asking for no kind rides the plain bike`() {
        // The default state of the journey screen, and by far the commonest.
        // A station lending both kinds may hand over either; announcing minutes
        // that assumed an assistance would be promising what nobody promised.
        assertEquals(RiddenBike.Mechanical, (null as WantedBikeKind?).asRiddenBike())
    }

    @Test
    fun `the kind asked of the network decides the bike ridden`() {
        assertEquals(RiddenBike.Mechanical, WantedBikeKind.Mechanical.asRiddenBike())
        assertEquals(RiddenBike.ElectricallyAssisted, WantedBikeKind.Electric.asRiddenBike())
    }

    @Test
    fun `an undeclared own bike rides exactly as a mechanical one`() {
        // SPEC §7.6: two wordings and not three. A bike declared mechanical and
        // a bike nobody declared are the same plain bike, on the drawing, in
        // the summary and now on the track as well.
        assertEquals(RiddenBike.Mechanical, (null as OwnBikeKind?).asRiddenBike())
        assertEquals(
            (null as OwnBikeKind?).asRiddenBike(),
            OwnBikeKind.Mechanical.asRiddenBike(),
        )
    }

    @Test
    fun `a declared pedal-assist bike is the only own bike that changes the ride`() {
        assertEquals(RiddenBike.ElectricallyAssisted, OwnBikeKind.Electric.asRiddenBike())
        // And it is the only one: everything else in the enum, plus the absent
        // value, must land on the plain bike. Written as a sweep so a third
        // state added to the setting cannot quietly inherit the assistance.
        val assisted = (OwnBikeKind.entries + null).filter {
            it.asRiddenBike() == RiddenBike.ElectricallyAssisted
        }
        assertEquals(listOf(OwnBikeKind.Electric), assisted)
    }
}
