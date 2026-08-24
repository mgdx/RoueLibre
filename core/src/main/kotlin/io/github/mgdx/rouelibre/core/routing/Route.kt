package io.github.mgdx.rouelibre.core.routing

import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlin.time.Duration

/**
 * The travel modes of a door-to-door journey (SPEC.md §5).
 *
 * One entry, one BRouter profile: the list of profiles the application lays on
 * disk is derived from these entries, so a mode added here is a profile that
 * must exist under `assets/routing`.
 *
 * **Two of them describe a bicycle**, and that is the whole of what the pedal
 * assistance is modelled by: the ride is traced with the profile that describes
 * the bike it is ridden on, so what comes back is the engine's own estimate for
 * that bike rather than a speed added to somebody else's (SPEC §6). Which of
 * the two is used is decided by [io.github.mgdx.rouelibre.core.journey.RiddenBike].
 *
 * @property profileName the name of the BRouter profile file, without its
 *   extension.
 */
public enum class TravelMode(public val profileName: String) {
    /** Access legs, on foot. */
    Walking("urban-walk"),

    /** The main leg, on a share bike one pedals alone. */
    Cycling("city-bike"),

    /** The main leg, on a pedal-assist bike. */
    ElectricCycling("city-ebike"),
}

/**
 * One computed leg of a route.
 *
 * @property mode on foot or by bike.
 * @property distanceMetres the length of the track.
 * @property duration the duration the engine estimates for this mode.
 * @property ascentMetres the cumulative climb: every stretch of the leg that
 *   goes up, added together, the descents left out. **Neither the difference
 *   between the two ends nor the height between the lowest and the highest
 *   reading** — a leg that gains ten metres, gives them back and gains ten more
 *   has climbed twenty, and that is what a rider pedals.
 *
 *   The sum is the engine's *filtered* one (BRouter, `RoutingEngine.recalcTrack`
 *   and its `ascend`), which is why it comes out lower than adding up the steps
 *   of [elevationsMetres]: a descent is held in a buffer rather than closing the
 *   climb straight away, so a dip shallower than the elevation source's own
 *   error does not turn the rise after it into a fresh hill. The buffer is ten
 *   metres over the three-arcsecond SRTM readings most graphs carry, five where
 *   the graph carries one-arcsecond ones — the vertical error of the samples
 *   themselves, which is the only honest place to put it. Without it, the saw
 *   the sampling draws across flat ground would add up to a climb the rider
 *   never makes.
 *
 *   It follows that this figure **cannot be read off a drawing of the leg**, and
 *   is not meant to be: a filtered sum of the ups is not a height on an axis.
 * @property geometry the track, from start to finish.
 * @property elevationsMetres the height above sea level at each point of
 *   [geometry], in the same order and of the same length. `null` at a point the
 *   routing graph carries no elevation for, and the whole list empty when the
 *   engine returned none — a graph generated without elevation, or a leg of one
 *   point. It is the raw reading, unfiltered, where [ascentMetres] is the
 *   engine's filtered sum: the two answer different questions, and drawing the
 *   shape of a leg needs the readings themselves.
 */
public data class RouteLeg(
    public val mode: TravelMode,
    public val distanceMetres: Int,
    public val duration: Duration,
    public val ascentMetres: Int,
    public val geometry: List<Coordinates>,
    public val elevationsMetres: List<Double?> = emptyList(),
)

/**
 * Why a route computation did not succeed.
 *
 * Each case calls for different conduct, and therefore a distinct message: that
 * is what guided this split, not the technical nature of the failure
 * (SPEC §14).
 */
public sealed interface RoutingFailure {

    /** The routing graph is not installed on the device. */
    public data object GraphMissing : RoutingFailure

    /**
     * One of the points lies outside the area the graph covers.
     *
     * SPEC §4 requires it: outside the box, the application must say so clearly
     * and never fail in silence.
     */
    public data object OutsideCoverage : RoutingFailure

    /**
     * No usable path between the two points for this mode.
     *
     * This happens legitimately: a station on the far side of a canal with no
     * bridge nearby, or a pedestrian zone closed to bicycles.
     */
    public data object NoRouteFound : RoutingFailure

    /** The computation ran past its allotted time. */
    public data object Timeout : RoutingFailure

    /**
     * The engine failed for a reason of its own.
     *
     * @property detail the engine's message, meant for the log and the bug
     *   report, never for the screen.
     */
    public data class EngineFailure(public val detail: String) : RoutingFailure
}

/** The outcome of a route computation. */
public sealed interface RouteResult {

    /** The requested track. */
    public data class Success(public val leg: RouteLeg) : RouteResult

    /** The computation did not succeed. */
    public data class Failure(public val reason: RoutingFailure) : RouteResult
}
