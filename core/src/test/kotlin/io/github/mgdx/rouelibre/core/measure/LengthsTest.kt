package io.github.mgdx.rouelibre.core.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * How a length measured in metres is written to a reader (SPEC §9).
 *
 * The assertions are **written out in full**, symbol included, rather than
 * recomputed from the formulas under test: a test that reapplies the rule it is
 * checking passes whatever the rule becomes. The symbols below stand in for the
 * string resources the interface fetches, which a module with no Android import
 * cannot reach — they are the ones `res/values/strings.xml` carries.
 */
class LengthsTest {

    /** The symbols of `res/values/strings.xml`, so a test can read like a screen. */
    private fun LengthUnit.symbol(): String = when (this) {
        LengthUnit.Metre -> "m"
        LengthUnit.Kilometre -> "km"
        LengthUnit.Foot -> "ft"
        LengthUnit.Yard -> "yd"
        LengthUnit.Mile -> "mi"
    }

    private fun WrittenLength.text(): String = "$amount ${unit.symbol()}"

    private fun distance(metres: Double, system: UnitSystem, locale: Locale = Locale.ENGLISH) =
        writeDistance(metres, system, locale).text()

    private fun climb(
        metres: Int,
        overMetres: Int = 1_000,
        system: UnitSystem = UnitSystem.Metric,
    ) = writeClimb(metres, overMetres, system, Locale.ENGLISH)?.text()

    private fun altitude(metres: Double, system: UnitSystem) =
        writeAltitude(metres, system, Locale.ENGLISH).text()

    // ------------------------------------------------ metric, unchanged --

    @Test
    fun `a metric distance is written exactly as it always was`() {
        // The reference case of this whole feature: whatever the imperial
        // systems do, a metric reader must see the very text the application
        // showed before they existed.
        assertEquals("0 m", distance(0.0, UnitSystem.Metric))
        assertEquals("50 m", distance(47.0, UnitSystem.Metric))
        assertEquals("250 m", distance(250.0, UnitSystem.Metric))
        assertEquals("440 m", distance(437.0, UnitSystem.Metric))
        assertEquals("990 m", distance(994.0, UnitSystem.Metric))
        assertEquals("1.0 km", distance(1_000.0, UnitSystem.Metric))
        assertEquals("1.4 km", distance(1_420.0, UnitSystem.Metric))
        assertEquals("5.2 km", distance(5_180.0, UnitSystem.Metric))
        assertEquals("17.0 km", distance(17_000.0, UnitSystem.Metric))
    }

    @Test
    fun `a metric climb and a metric altitude are written exactly as they were`() {
        assertEquals("5 m", climb(5))
        assertEquals("45 m", climb(46))
        assertEquals("150 m", climb(148))
        assertEquals("0 m", altitude(2.0, UnitSystem.Metric))
        assertEquals("25 m", altitude(24.0, UnitSystem.Metric))
        assertEquals("1200 m", altitude(1_198.0, UnitSystem.Metric))
    }

    @Test
    fun `the language decides how the figure is written, not the units`() {
        // The French decimal comma and the English point (SPEC §9), on the same
        // measurement in the same system.
        assertEquals("1,4 km", distance(1_420.0, UnitSystem.Metric, Locale.FRENCH))
        assertEquals("2,3 mi", distance(3_700.0, UnitSystem.UnitedStates, Locale.FRENCH))
    }

    // ------------------------------------------------------- feet and miles --

    @Test
    fun `an american distance is written in feet, then in miles`() {
        assertEquals("0 ft", distance(0.0, UnitSystem.UnitedStates))
        assertEquals("150 ft", distance(47.0, UnitSystem.UnitedStates))
        assertEquals("800 ft", distance(250.0, UnitSystem.UnitedStates))
        assertEquals("2.3 mi", distance(3_700.0, UnitSystem.UnitedStates))
        assertEquals("10.6 mi", distance(17_000.0, UnitSystem.UnitedStates))
    }

    @Test
    fun `feet give way to miles at a thousand of them`() {
        // A thousand feet is 304.8 m: just under, the count is still read in
        // feet; just over, a fourth digit would appear and the mile takes over.
        assertEquals("950 ft", distance(290.0, UnitSystem.UnitedStates))
        assertEquals("0.2 mi", distance(305.0, UnitSystem.UnitedStates))
    }

    // ------------------------------------------------------ yards and miles --

    @Test
    fun `a british distance is written in yards, then in miles`() {
        assertEquals("0 yd", distance(0.0, UnitSystem.UnitedKingdom))
        assertEquals("50 yd", distance(47.0, UnitSystem.UnitedKingdom))
        assertEquals("275 yd", distance(250.0, UnitSystem.UnitedKingdom))
        assertEquals("2.3 mi", distance(3_700.0, UnitSystem.UnitedKingdom))
        assertEquals("10.6 mi", distance(17_000.0, UnitSystem.UnitedKingdom))
    }

    @Test
    fun `yards give way to miles at a thousand of them`() {
        // A thousand yards is 914.4 m.
        assertEquals("975 yd", distance(890.0, UnitSystem.UnitedKingdom))
        assertEquals("0.6 mi", distance(915.0, UnitSystem.UnitedKingdom))
    }

    @Test
    fun `the last figure before the threshold may need a fourth digit`() {
        // The threshold is read on the measurement, not on the rounded figure,
        // which is what the metric side has always done: a distance a hair
        // under a thousand metres rounds up to "1000 m" rather than becoming
        // "1.0 km". The imperial systems inherit the wart rather than differ
        // from metric over a tenth of a percent of values.
        assertEquals("1000 m", distance(999.6, UnitSystem.Metric))
        assertEquals("1000 ft", distance(304.0, UnitSystem.UnitedStates))
        assertEquals("1000 yd", distance(913.0, UnitSystem.UnitedKingdom))
    }

    // ----------------------------------------------------- climbs and heights --

    @Test
    fun `both imperial systems count a climb in feet`() {
        // A yard measures the ground one walks over, not the hill one climbs,
        // and a sentence must not mix two units (SPEC §9).
        assertEquals("140 ft", climb(45, system = UnitSystem.UnitedStates))
        assertEquals("140 ft", climb(45, system = UnitSystem.UnitedKingdom))
        assertEquals("80 ft", altitude(24.0, UnitSystem.UnitedStates))
        assertEquals("80 ft", altitude(24.0, UnitSystem.UnitedKingdom))
    }

    @Test
    fun `a climb falls silent at the same places in the three systems`() {
        // The two silences are facts about the SRTM samples, not about the
        // reader: the same three hundred metres of ground and the same five
        // metres of height, whatever unit the figure would be written in.
        for (system in UnitSystem.entries) {
            assertNull(climb(40, overMetres = 299, system = system))
            assertNull(climb(4, overMetres = 5_000, system = system))
        }
        for (system in UnitSystem.entries) {
            assertTrue(climb(40, overMetres = 300, system = system) != null)
            assertTrue(climb(5, overMetres = 5_000, system = system) != null)
        }
    }

    @Test
    fun `a climb just above the floor is never written as nothing`() {
        // Five metres is 16.4 ft, which the twenty-foot step must round up: a
        // climb named in one system and written "0 ft" in another would be a
        // silence the rules above did not choose.
        assertEquals("20 ft", climb(5, system = UnitSystem.UnitedStates))
        assertEquals("20 ft", climb(5, system = UnitSystem.UnitedKingdom))
    }

    @Test
    fun `the relief is worth drawing at the same places in the three systems`() {
        // It takes no unit system at all, and that is the assertion: whether a
        // drawing shows the ground or the sampling does not depend on how its
        // axis is labelled (SPEC §7.4.1).
        assertTrue(isReliefWorthDrawing(overMetres = 300, rangeMetres = 5.0))
        assertTrue(!isReliefWorthDrawing(overMetres = 299, rangeMetres = 50.0))
        assertTrue(!isReliefWorthDrawing(overMetres = 3_000, rangeMetres = 4.9))
    }

    // --------------------------------------------------------- no false precision --

    @Test
    fun `no system announces a finer precision than the metric one`() {
        // The rule that governs every step chosen above, checked by counting:
        // over a stretch of ground, an imperial system must divide it into no
        // more distinct figures than metric does. It says the same thing as
        // "50 ft is coarser than 10 m" without reapplying the arithmetic under
        // test — and it catches a step made finer anywhere in the range,
        // including past the mile threshold.
        //
        // Not a claim that the two grids agree: they are offset, so a coarser
        // step can still fall between two measurements metric writes alike.
        // What must never happen is a system splitting the ground more finely.
        val sweeps = listOf(
            (0 until 1_000).map(Int::toDouble),
            (1_000 until 20_000 step 10).map(Int::toDouble),
        )
        for (sweep in sweeps) {
            val metric = sweep.map { distance(it, UnitSystem.Metric) }.distinct().size
            for (system in UnitSystem.entries) {
                val counted = sweep.map { distance(it, system) }.distinct().size
                assertTrue(
                    "$system writes $counted distinct figures where metric writes $metric",
                    counted <= metric,
                )
            }
        }
    }

    @Test
    fun `no system announces a finer height than the metric one`() {
        val sweep = (0 until 500).map(Int::toDouble)
        val metric = sweep.map { altitude(it, UnitSystem.Metric) }.distinct().size
        for (system in UnitSystem.entries) {
            val counted = sweep.map { altitude(it, system) }.distinct().size
            assertTrue(
                "$system writes $counted distinct heights over 500 m, metric $metric",
                counted <= metric,
            )
        }
    }
}
