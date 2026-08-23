package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Tests of how long a closed station has been silent (SPEC §4.1).
 *
 * The dates are those of the Lille network's feed on 23 August 2026, the
 * relevé that showed two stations closed the same day beside one that had
 * reported nothing since 13 March.
 */
class StationSilenceTest {

    private val now: Instant = Instant.parse("2026-08-23T19:37:00Z")

    private val station = Station(
        id = "211",
        name = "Gustave Delory",
        position = Coordinates(50.63, 3.06),
        capacity = 18,
        postalCode = "59000",
    )

    private fun entry(
        installed: Boolean = true,
        renting: Boolean = true,
        returning: Boolean = true,
        reportedDaysAgo: Long? = null,
    ) = StationWithAvailability(
        station = station,
        availability = StationAvailability(
            stationId = station.id,
            bikesAvailable = 0,
            docksAvailable = 0,
            isInstalled = installed,
            isRenting = renting,
            isReturning = returning,
            reportedAt = reportedDaysAgo?.let { now.minusSeconds(it * 86_400) },
        ),
    )

    @Test
    fun `a station closed for months says how long it has been silent`() {
        assertEquals(
            Freshness.Months(5),
            entry(renting = false, returning = false, reportedDaysAgo = 164).silentClosureAge(now),
        )
    }

    @Test
    fun `a station closed for a few days says it in days`() {
        assertEquals(
            Freshness.Days(19),
            entry(renting = false, returning = false, reportedDaysAgo = 19).silentClosureAge(now),
        )
    }

    @Test
    fun `a station closed today has nothing more to say than that it is closed`() {
        val closedThisMorning = StationWithAvailability(
            station = station,
            availability = StationAvailability(
                stationId = station.id,
                bikesAvailable = 0,
                docksAvailable = 0,
                isInstalled = true,
                isRenting = false,
                isReturning = false,
                reportedAt = now.minusSeconds(6 * 3_600),
            ),
        )
        assertNull(closedThisMorning.silentClosureAge(now))
    }

    @Test
    fun `the threshold sits at the day`() {
        assertNull(
            entry(renting = false, returning = false, reportedDaysAgo = 0).silentClosureAge(now),
        )
        assertEquals(
            Freshness.Days(1),
            entry(renting = false, returning = false, reportedDaysAgo = 1).silentClosureAge(now),
        )
    }

    @Test
    fun `a station in service is never called silent however old its measurement`() {
        // The rule qualifies a closure and never decides one: a producer that
        // restamps only on change would otherwise see its quiet stations
        // marked in the small hours.
        assertNull(entry(reportedDaysAgo = 164).silentClosureAge(now))
    }

    @Test
    fun `a station that only receives bikes is in service and stays unmarked`() {
        assertNull(entry(renting = false, reportedDaysAgo = 164).silentClosureAge(now))
    }

    @Test
    fun `a closure the producer stamps no date on says nothing more`() {
        assertNull(entry(renting = false, returning = false).silentClosureAge(now))
    }

    @Test
    fun `a station the real-time feed ignores says nothing more`() {
        // 268 stations in `station_information` against 267 in
        // `station_status` on the Lille feed: the state is legitimately absent.
        assertNull(StationWithAvailability(station, availability = null).silentClosureAge(now))
    }
}
