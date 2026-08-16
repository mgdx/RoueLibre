package io.github.mgdx.rouelibre.core.measure

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turning a length measured in metres into a figure a reader can take in.
 *
 * **This file is the only place in the application where a metre becomes a foot,
 * a yard or a mile.** Everything upstream of it — the routing engine, the
 * algorithm of §6, the bounding boxes, the dataset manifests, the GBFS feeds,
 * the distances between stations, the elevation profile — measures in metres and
 * knows nothing of the reader's units. If a conversion is ever needed elsewhere,
 * the boundary has been drawn in the wrong place: move the call, do not add a
 * second conversion (SPEC §14).
 *
 * What crosses this boundary is a [WrittenLength]: a figure already rounded and
 * already written in the reader's language, plus the unit it is in. The symbol
 * itself is a string like any other and lives in the resources (SPEC §9), which
 * a module with no Android import cannot reach — and that is the whole of what
 * the interface still has to do.
 */

/** The unit a [WrittenLength] is counted in. */
public enum class LengthUnit {
    /** Metres. */
    Metre,

    /** Kilometres. */
    Kilometre,

    /** Feet, used for distance and for height alike in both imperial systems. */
    Foot,

    /** Yards. */
    Yard,

    /** Miles, the same statute mile in both imperial systems. */
    Mile,
}

/**
 * A length ready to be shown: the figure, and the unit it counts.
 *
 * @property amount the figure, rounded and formatted in the reader's language —
 *   the French decimal comma and the English point are not written the same way
 *   (SPEC §9).
 * @property unit the unit whose symbol the interface is to fetch.
 */
public data class WrittenLength(public val amount: String, public val unit: LengthUnit)

/**
 * Puts a distance into words.
 *
 * Values are rounded to what the display can honestly promise: an address's
 * position is known to within a few metres, the user's to far less. Writing
 * "437 m" would suggest a precision that does not exist — and every imperial
 * step below is chosen to be at least as coarse as its metric counterpart, so
 * that changing units never claims to have measured better.
 *
 * The unit changes at a thousand of the smaller one, in all three systems: past
 * that the figure needs four digits and stops being read at a glance. The
 * comparison is made on the measured value rather than the rounded one, which is
 * what the metric side has always done — a distance a hair under the threshold
 * therefore rounds up to a four-digit "1000 m", and making the imperial systems
 * differ from metric on that hair would cost more than the wart.
 *
 * @param metres the distance measured.
 * @param system the units the reader is being written to in.
 * @param locale the language the figure is written in.
 * @return a distance ready to show: "250 m", "1.4 km", "850 ft" or "2.3 mi".
 */
public fun writeDistance(metres: Double, system: UnitSystem, locale: Locale): WrittenLength =
    when (system) {
        UnitSystem.Metric ->
            if (metres < METRES_PER_KILOMETRE) {
                whole(metres, METRE_ROUNDING, LengthUnit.Metre, locale)
            } else {
                fractional(metres / METRES_PER_KILOMETRE, LengthUnit.Kilometre, locale)
            }

        UnitSystem.UnitedStates -> {
            val feet = metres / METRES_PER_FOOT
            if (feet < FEET_BEFORE_MILES) {
                whole(feet, FOOT_ROUNDING, LengthUnit.Foot, locale)
            } else {
                fractional(metres / METRES_PER_MILE, LengthUnit.Mile, locale)
            }
        }

        UnitSystem.UnitedKingdom -> {
            val yards = metres / METRES_PER_YARD
            if (yards < YARDS_BEFORE_MILES) {
                whole(yards, YARD_ROUNDING, LengthUnit.Yard, locale)
            } else {
                fractional(metres / METRES_PER_MILE, LengthUnit.Mile, locale)
            }
        }
    }

/**
 * Puts a climb into words, or says there is none worth naming.
 *
 * A climb is read in the small unit however big it gets — a hill is counted in
 * metres, or in feet, by everyone who rides up one — so this is not
 * [writeDistance] applied to a vertical figure: no kilometre and no mile ever
 * appears here.
 *
 * **The two silences are measured in metres and stay there**, whatever the
 * reader's units. They are facts about the data rather than about the reader:
 * the elevation of the routing graph comes from SRTM samples some thirty metres
 * apart, whose vertical error runs to several metres, and a climb too small or
 * too short to be real is just as unsayable in feet as in metres.
 *
 * - **A stretch too short to be described by them says nothing.** Forty metres
 *   of pavement announced five metres of climb — a twelve per cent grade on a
 *   street that has none — because two samples and the error between them were
 *   the whole of what the engine had to go on. Under [CLIMB_MEASURABLE_OVER]
 *   the figure is not the ground, it is the sampling.
 * - **Under five metres nothing is named either**, and the figure is written to
 *   five above it, which is as fine as those samples can honestly promise.
 *
 * What is *not* used here is a floor high enough to hide real relief: a ride
 * across flat country that gains seven metres has gained them, and the ten
 * metres tried first — the dip the engine's own filter forgives — silenced the
 * bike leg of half the journeys in a flat conurbation while the total, summing
 * three legs, still named one.
 *
 * @param metres the climb the routing engine measured.
 * @param overMetres the ground it was gained over: one leg's length, or the
 *   whole journey's.
 * @param system the units the reader is being written to in.
 * @param locale the language the figure is written in.
 * @return the climb ready to show, "45 m" or "150 ft" for instance, or null when
 *   the ground is flat enough, or short enough, that saying anything would be
 *   saying too much.
 */
public fun writeClimb(
    metres: Int,
    overMetres: Int,
    system: UnitSystem,
    locale: Locale,
): WrittenLength? {
    if (overMetres < CLIMB_MEASURABLE_OVER) return null
    if (metres < CLIMB_ROUNDING) return null
    return writeHeight(metres.toDouble(), system, locale)
}

/**
 * Writes a height above sea level.
 *
 * Rounded as a climb is and for the same reason: the readings come from samples
 * whose vertical error runs to several metres, and a figure written to the metre
 * — or to the foot — would promise what they cannot.
 *
 * @param metres the height measured.
 * @param system the units the reader is being written to in.
 * @param locale the language the figure is written in.
 */
public fun writeAltitude(metres: Double, system: UnitSystem, locale: Locale): WrittenLength =
    writeHeight(metres, system, locale)

/**
 * Whether a leg's relief has a shape worth drawing (SPEC §7.4.1).
 *
 * The same two thresholds that silence [writeClimb], for the same reason: what
 * the graph holds under three hundred metres of ground, or inside five metres
 * of height, is the sampling of SRTM and its error rather than the ground. A
 * profile drawn from it would stretch that error across the width of the
 * screen, and read as a hill.
 *
 * Both are in metres and neither moves with the reader's units: the drawing is
 * either of the ground or of the sampling, and which of the two it is does not
 * depend on how its axis happens to be labelled.
 *
 * @param overMetres the length of the leg.
 * @param rangeMetres the height between its lowest and its highest reading —
 *   not its climb, which can add up over ups and downs that a drawing would
 *   have to amplify to show at all.
 */
public fun isReliefWorthDrawing(overMetres: Int, rangeMetres: Double): Boolean =
    overMetres >= CLIMB_MEASURABLE_OVER && rangeMetres >= CLIMB_ROUNDING

/**
 * A height, in the reader's units.
 *
 * Both imperial systems count height in feet: yards measure the ground one
 * walks over, not the hill one climbs, and a sentence naming a climb beside two
 * altitudes must not mix two units (SPEC §9).
 */
private fun writeHeight(metres: Double, system: UnitSystem, locale: Locale): WrittenLength =
    when (system) {
        UnitSystem.Metric -> whole(metres, CLIMB_ROUNDING, LengthUnit.Metre, locale)
        UnitSystem.UnitedStates, UnitSystem.UnitedKingdom ->
            whole(metres / METRES_PER_FOOT, CLIMB_FOOT_ROUNDING, LengthUnit.Foot, locale)
    }

/**
 * A figure rounded to a step of its unit, with no decimal.
 *
 * **Digits are not grouped.** Nothing written here reaches a size where a
 * thousands separator helps — a distance past a thousand small units is written
 * in the large one — and the two places it could show up are an altitude in feet
 * and the boundary case [writeDistance] describes, neither of which is worth
 * changing what the metric side has always printed.
 */
private fun whole(amount: Double, step: Int, unit: LengthUnit, locale: Locale): WrittenLength {
    val rounded = (amount / step).roundToInt() * step
    val format = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    return WrittenLength(format.format(rounded), unit)
}

/** A figure written to one decimal, which is what the large units are read to. */
private fun fractional(amount: Double, unit: LengthUnit, locale: Locale): WrittenLength {
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
    }
    return WrittenLength(format.format(amount), unit)
}

private const val METRES_PER_KILOMETRE = 1_000.0

/** The international mile, exactly. */
private const val METRES_PER_MILE = 1_609.344

/** The international foot, exactly. */
private const val METRES_PER_FOOT = 0.3048

/** The international yard, exactly. */
private const val METRES_PER_YARD = 0.9144

/** Below a kilometre, the distance rounds to the nearest ten metres. */
private const val METRE_ROUNDING = 10

/**
 * Below the threshold, a distance in feet rounds to fifty of them.
 *
 * Ten metres is 32.8 ft, so fifty feet — 15.2 m — is the first round step
 * coarser than the metric one. Twenty-five would be finer than the metres this
 * figure is converted from, and would announce a position to eight metres that
 * is known to a good deal less than that.
 */
private const val FOOT_ROUNDING = 50

/**
 * Below the threshold, a distance in yards rounds to twenty-five of them.
 *
 * Ten metres is 10.9 yd, so a step of ten yards — 9.1 m — would be finer than
 * the metric rule and would promise more than the measurement holds.
 * Twenty-five yards is 22.9 m, the round step just above it.
 */
private const val YARD_ROUNDING = 25

/** A climb is written to five metres, and under one step it is not written. */
private const val CLIMB_ROUNDING = 5

/**
 * A climb or an altitude in feet is written to twenty of them.
 *
 * Five metres is 16.4 ft: ten feet would be finer than what the SRTM samples
 * can promise, twenty — 6.1 m — is the round step just above. Twenty-five would
 * do no harm either, and loses a third more of a figure that is small to begin
 * with.
 */
private const val CLIMB_FOOT_ROUNDING = 20

/**
 * The ground a climb needs to be gained over before it is worth naming.
 *
 * Three hundred metres is some ten SRTM samples: enough for the stretch to have
 * a shape of its own rather than to be one reading and its error. Shorter than
 * that, the engine's elevation filter has not had the length to work either —
 * it forgives dips as it goes, but the first rise of a leg always counts, and on
 * a leg of forty metres that first rise is the whole figure.
 */
private const val CLIMB_MEASURABLE_OVER = 300

/**
 * Where a distance in feet gives way to miles.
 *
 * One rule for the three systems: **past a thousand of the small unit the count
 * needs four digits**, and a figure one has to read digit by digit is no longer
 * read at a glance. It is the metric threshold — a thousand metres — applied to
 * another unit, and nothing else was invented for the occasion.
 *
 * A thousand feet is 304.8 m, so an American reader reaches miles early, and a
 * six-hundred-metre walk reads "0.4 mi" where a metric one reads "600 m". That
 * is the cost of a unit a third of a yard long, and the way out is worse: two
 * thousand feet would print "1950 ft", which is exactly what the rule exists to
 * prevent.
 */
private const val FEET_BEFORE_MILES = 1_000

/**
 * Where a distance in yards gives way to miles.
 *
 * The rule of [FEET_BEFORE_MILES], which for yards happens to fall almost where
 * the metric one does: a thousand yards is 914.4 m. Five hundred was weighed —
 * half a mile is 880 yd, and British road signs count in yards up to about that
 * — and dropped: it would send a walk of six hundred metres to "0.4 mi", whose
 * decimal is worth 161 m, where "650 yd" says the same thing seven times more
 * closely and reads just as easily.
 */
private const val YARDS_BEFORE_MILES = 1_000
