package io.github.mgdx.rouelibre.core.station

/**
 * Which stations the map leaves out (SPEC §7.1).
 *
 * The mode toggle beside it chooses **what a marker counts**; this chooses
 * **which markers there are**. In a dense conurbation the map carries several
 * hundred discs, and a good part of them answer nothing to somebody looking for
 * a bike right now: a station out of service will lend none, a station at zero
 * will lend none either.
 *
 * **Both are off by default**, here as in the screen that holds them: a map
 * nobody has asked anything of shows the network as it stands.
 *
 * There is deliberately **no adjustable threshold** — no "at least three bikes".
 * It would be one more figure to justify, one more control on a screen SPEC §7
 * asks to keep calm, and the marker already says how many there are.
 *
 * @property hideOutOfService leave out the stations that refuse the service
 *   asked for.
 * @property hideEmpty leave out the stations whose count was read as zero.
 */
public data class StationFilter(
    public val hideOutOfService: Boolean = false,
    public val hideEmpty: Boolean = false,
) {

    /** True while the filter can take nothing away, which is its resting state. */
    public val hidesNothing: Boolean get() = !hideOutOfService && !hideEmpty

    /**
     * Whether a station showing this stays on the map.
     *
     * The test is made on what the marker **shows** rather than on the raw feed,
     * and that is the whole of the design: [AvailabilityDisplay] is already
     * settled per mode and per kind of bike, so the filter hides exactly what
     * the user can see, and cannot fall out of step with it.
     *
     * **What is unknown is not what is known to be absent.** An
     * [AvailabilityDisplay.count] of `null` — the real-time feed says nothing of
     * this station, or its breakdown by kind could not be read — survives
     * [hideEmpty], because hiding it would assert on the strength of a silence
     * something the application has not read. The same silence is not
     * [AvailabilityDisplay.isOutOfService] either, so it survives
     * [hideOutOfService] as well.
     */
    public fun keeps(display: AvailabilityDisplay): Boolean = when {
        hideOutOfService && display.isOutOfService -> false
        // `== 0` and not `<= 0` by intent: a count is either read or absent, and
        // `null` is the absent one. See the KDoc above.
        hideEmpty && display.count == 0 -> false
        else -> true
    }
}

/**
 * The stations the map is to draw, in the order it received them.
 *
 * Filtering happens **before** the markers are built rather than by redrawing
 * them all: the list runs to several hundred entries and is replayed at every
 * refresh, once a minute at most.
 *
 * What lies beyond the reference box (§4, [Station.isBeyondCoveredArea]) is
 * untouched by this: these filters speak of state and of counts, and being
 * outside the data is neither.
 *
 * @param stations the known stations and their last state.
 * @param filter what is to be left out.
 * @param mode what the markers count, which is what "empty" means: no bike in
 *   [AvailabilityMode.Bikes], no free dock in [AvailabilityMode.Docks]. The
 *   filter therefore turns round with the toggle, and the same station can be
 *   hidden under one and drawn under the other.
 * @param kind the kind of bike being counted, or `null` for all of them. A
 *   station whose breakdown cannot be read counts as unknown, not as empty.
 * @param alwaysShown stations that survive whatever the filter says. A journey
 *   drawn on the map keeps the stations it just proposed: making them vanish
 *   would be the screen contradicting itself one line further down.
 */
public fun stationsShownOnMap(
    stations: List<StationWithAvailability>,
    filter: StationFilter,
    mode: AvailabilityMode,
    kind: BikeKindFilter? = null,
    alwaysShown: Set<String> = emptySet(),
): List<StationWithAvailability> {
    if (filter.hidesNothing) return stations
    return stations.filter { entry ->
        entry.station.id in alwaysShown || filter.keeps(entry.displayFor(mode, kind))
    }
}
