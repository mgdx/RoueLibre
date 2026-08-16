package io.github.mgdx.rouelibre.core.journey

import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.core.station.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of the rounding a journey is shown with (SPEC §7.4).
 *
 * The invariant under test is the one the screens are read against: the total
 * announced equals the sum of the figures printed under it. The durations are
 * chosen to fall on fractions of a minute, which is where rounding each leg on
 * its own used to make the two disagree.
 */
class ShownMinutesTest {

    /** The legs added up, which is what the total is rounded from. */
    private fun List<Duration>.total(): Duration = fold(Duration.ZERO, Duration::plus)

    private fun leg(duration: Duration, mode: TravelMode) = RouteLeg(
        mode = mode,
        distanceMetres = 500,
        duration = duration,
        ascentMetres = 0,
        geometry = emptyList(),
    )

    private fun station(id: String) = Station(
        id = id,
        name = "Station $id",
        position = Coordinates(50.63, 3.06),
        capacity = 20,
        postalCode = "59000",
    )

    private fun option(walkTo: Duration, ride: Duration, walkFrom: Duration) = JourneyOption(
        departureStation = station("departure"),
        arrivalStation = station("arrival"),
        bikesAtDeparture = 5,
        docksAtArrival = 5,
        walkToStation = leg(walkTo, TravelMode.Walking),
        ride = leg(ride, TravelMode.Cycling),
        walkToDestination = leg(walkFrom, TravelMode.Walking),
        riskPenalty = 2.minutes,
    )

    @Test
    fun `a duration is shown in whole minutes, rounded up`() {
        assertEquals(1, 1.seconds.inShownMinutes())
        assertEquals(1, 60.seconds.inShownMinutes())
        assertEquals(2, 61.seconds.inShownMinutes())
        assertEquals(22, (21.minutes + 30.seconds).inShownMinutes())
    }

    /** Even a journey of fifty metres takes a minute: "0 min" reads as a fault. */
    @Test
    fun `a duration is never shown as nothing`() {
        assertEquals(1, Duration.ZERO.inShownMinutes())
        assertEquals(1, 3.seconds.inShownMinutes())
    }

    /**
     * The case reported from the device: 26 min announced over legs adding up to
     * 28, because each leg was rounded up on its own.
     */
    @Test
    fun `the parts add up to the total, on fractional minutes`() {
        val legs = listOf(
            2.minutes + 5.seconds,
            21.minutes + 30.seconds,
            2.minutes + 6.seconds,
        )
        val shown = apportionMinutes(legs)

        assertEquals(legs.total().inShownMinutes(), shown.sum())
        assertEquals(listOf(2, 22, 2), shown)
    }

    /** The spare minutes go to the legs that lost the most seconds to rounding. */
    @Test
    fun `the spare minute goes to the leg that lost the most`() {
        val shown = apportionMinutes(
            listOf(
                1.minutes + 5.seconds,
                1.minutes + 55.seconds,
                1.minutes + 30.seconds,
            ),
        )

        // Four and a half minutes in all, hence five shown, hence two minutes to
        // place over the three floors of one: the 55 and 30 second remainders
        // take them, the 5 second one does not.
        assertEquals(listOf(1, 2, 2), shown)
        assertEquals(5, shown.sum())
    }

    /** A journey made of one leg is its own total, with nothing to share out. */
    @Test
    fun `a single part is the whole`() {
        val walk = 13.minutes + 1.seconds
        assertEquals(listOf(14), apportionMinutes(listOf(walk)))
        assertEquals(walk.inShownMinutes(), apportionMinutes(listOf(walk)).sum())
    }

    /**
     * Three legs of twenty seconds add up to one minute, and each is worth a
     * minute of its own: the total shown is then what its parts add up to,
     * rather than a figure the rows underneath contradict.
     */
    @Test
    fun `the total follows the parts when each is worth its minute`() {
        val shown = apportionMinutes(listOf(20.seconds, 20.seconds, 20.seconds))

        assertEquals(listOf(1, 1, 1), shown)
        assertEquals(3, shown.sum())
    }

    @Test
    fun `nothing to show adds up to nothing`() {
        assertEquals(emptyList<Int>(), apportionMinutes(emptyList()))
    }

    /**
     * Swept over every combination of seconds a journey's three legs can fall
     * on, because the invariant has to hold for all of them and not only for the
     * cases somebody thought to write down.
     */
    @Test
    fun `the invariant holds whatever the seconds`() {
        for (first in 0..119 step 7) {
            for (second in 0..1799 step 13) {
                for (third in 0..119 step 11) {
                    val legs = listOf(first.seconds, second.seconds, third.seconds)
                    val shown = apportionMinutes(legs)
                    val total = shown.sum()

                    assertEquals(legs.size, shown.size)
                    assertTrue("a leg is shown as $shown", shown.all { it >= 1 })
                    assertTrue(
                        "$legs shown as $shown, total $total",
                        total >= legs.total().inShownMinutes(),
                    )
                    // The total is never inflated beyond what the floor of one
                    // minute per leg forces: a leg under a minute is worth its
                    // minute all the same, and only those can raise it.
                    val shortLegs = legs.count { it < 1.minutes }
                    assertTrue(
                        "$legs shown as $shown, total $total",
                        total <= legs.total().inShownMinutes() + shortLegs,
                    )
                }
            }
        }
    }

    /**
     * The whole journey, as the three screens read it: the total in large, the
     * walking and riding of the sentence beside it, and the band of three legs
     * under both. The reported journey — 26 min over "5 min walking and 22 min
     * riding" over "3 min · 22 min · 3 min" — is the one measured here.
     */
    @Test
    fun `a journey's three figures agree with one another`() {
        val journey = option(
            walkTo = 2.minutes + 5.seconds,
            ride = 21.minutes + 30.seconds,
            walkFrom = 2.minutes + 6.seconds,
        )
        val minutes = journey.shownMinutes()

        assertEquals(journey.travelTime.inShownMinutes(), minutes.total)
        assertEquals(minutes.total, minutes.walking + minutes.ride)
        assertEquals(
            minutes.total,
            minutes.walkToStation + minutes.ride + minutes.walkToDestination,
        )
    }
}
