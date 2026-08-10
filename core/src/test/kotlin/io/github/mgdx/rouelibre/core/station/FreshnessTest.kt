package io.github.mgdx.rouelibre.core.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Tests of the displayed age of the data (SPEC §4.1). */
class FreshnessTest {

    private val now: Instant = Instant.parse("2026-08-09T12:00:00Z")

    private fun ago(seconds: Long) = freshnessOf(now.minusSeconds(seconds), now)

    @Test
    fun `the very first seconds do not deserve a count`() {
        assertEquals(Freshness.JustNow, ago(0))
        assertEquals(Freshness.JustNow, ago(4))
    }

    @Test
    fun `the count moves from seconds to minutes then to hours`() {
        assertEquals(Freshness.Seconds(12), ago(12))
        assertEquals(Freshness.Seconds(59), ago(59))
        assertEquals(Freshness.Minutes(1), ago(60))
        assertEquals(Freshness.Minutes(59), ago(3_599))
        assertEquals(Freshness.Hours(1), ago(3_600))
        assertEquals(Freshness.Hours(23), ago(86_399))
        assertEquals(Freshness.LongAgo, ago(86_400))
    }

    @Test
    fun `the absence of data is told apart from old data`() {
        assertEquals(Freshness.Never, freshnessOf(null, now))
    }

    @Test
    fun `a clock going backwards does not produce a negative age`() {
        // An NTP correction or a clock change: "just now" beats "-3 seconds
        // ago".
        assertEquals(Freshness.JustNow, freshnessOf(now.plusSeconds(30), now))
    }

    @Test
    fun `the state is only called frozen beyond five minutes`() {
        assertTrue(!ago(0).isStale)
        assertTrue(!ago(59).isStale)
        assertTrue(!ago(4 * 60).isStale)
        assertTrue(ago(5 * 60).isStale)
        assertTrue(ago(3_600).isStale)
        assertTrue(Freshness.LongAgo.isStale)
    }

    @Test
    fun `without any data the state is frozen by definition`() {
        assertTrue(Freshness.Never.isStale)
    }
}
