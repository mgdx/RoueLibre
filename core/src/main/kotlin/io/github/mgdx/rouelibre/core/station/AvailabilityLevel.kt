package io.github.mgdx.rouelibre.core.station

/**
 * The availability level, as the indicator shows it (SPEC §7.1).
 *
 * The banding is not cosmetic: it carries over the reliability reasoning of
 * SPEC §6. A station can empty — or fill — while one walks towards it, and the
 * likelihood of that happening depends far more on the first few bikes than on
 * the ones after.
 */
public enum class AvailabilityLevel {
    /** Nothing at all. The journey proposed would be impossible. */
    None,

    /**
     * One or two. A station with a single bike may be empty on arrival: that is
     * the case SPEC §6 asks to penalise, and it must be visible.
     */
    Low,

    /** Three to five. The risk becomes small without being nil. */
    Medium,

    /** Six or more. Beyond that, one more bike no longer changes the decision. */
    Good,
}

/** The threshold below which a station is judged at risk of emptying. */
private const val LOW_THRESHOLD = 3

/** The threshold beyond which one more unit changes nothing. */
private const val GOOD_THRESHOLD = 6

/**
 * Places a count of bikes or docks on the indicator's scale.
 *
 * The same thresholds apply to bikes and to docks: the question asked is the
 * same on both sides — "will this still be true when I get there?".
 *
 * @param count the number of bikes available or of free docks.
 */
public fun availabilityLevelOf(count: Int): AvailabilityLevel = when {
    count <= 0 -> AvailabilityLevel.None
    count < LOW_THRESHOLD -> AvailabilityLevel.Low
    count < GOOD_THRESHOLD -> AvailabilityLevel.Medium
    else -> AvailabilityLevel.Good
}

/** What the user is after: borrowing a bike, or returning one. */
public enum class AvailabilityMode {
    /** Count the bikes that can be borrowed. */
    Bikes,

    /** Count the free docks. */
    Docks,
}

/**
 * What is to be shown for a station, in the requested mode.
 *
 * @property count the number to write in the indicator, or `null` if unknown.
 * @property level the matching level, or `null` if unknown.
 * @property isOutOfService the station does not provide the service asked for.
 * @property filledFraction the share of the capacity this count occupies,
 *   between 0 and 1, or `null` if the capacity is not published. It feeds the
 *   indicator's arc: the figure says how many, the arc says out of how many.
 */
public data class AvailabilityDisplay(
    public val count: Int?,
    public val level: AvailabilityLevel?,
    public val isOutOfService: Boolean,
    public val filledFraction: Float?,
)

/**
 * Turns a station's state into what the indicator must show.
 *
 * @param mode according to whether the user is after a bike or a dock.
 * @param kind the kind of bike being counted, or `null` — the default — to count
 *   them all. It applies to [AvailabilityMode.Bikes] alone: docks have no kind,
 *   and a filter has nothing to say about them (SPEC §7.1). A station whose
 *   breakdown cannot be read answers like a station with no state at all —
 *   unknown, and drawn without a figure — never as a station holding none: see
 *   [BikeKindFilter.bikesAt].
 */
public fun StationWithAvailability.displayFor(
    mode: AvailabilityMode,
    kind: BikeKindFilter? = null,
): AvailabilityDisplay {
    val current = availability
        ?: return AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = false,
            filledFraction = null,
        )

    // "Out of service" applies to the service asked for, not to the station as
    // a whole: a station that no longer takes returns can still lend.
    val serviceRefused = when (mode) {
        AvailabilityMode.Bikes -> !current.isInstalled || !current.isRenting
        AvailabilityMode.Docks -> !current.isInstalled || !current.isReturning
    }
    if (serviceRefused) {
        return AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = true,
            filledFraction = null,
        )
    }

    val count = when (mode) {
        AvailabilityMode.Bikes -> kind?.let { wanted ->
            // Unreadable breakdown: the same answer as a station the feed says
            // nothing about. Drawing a nought would claim we counted and found
            // none, which is not what happened.
            wanted.bikesAt(current) ?: return AvailabilityDisplay(
                count = null,
                level = null,
                isOutOfService = false,
                filledFraction = null,
            )
        } ?: current.bikesAvailable

        AvailabilityMode.Docks -> current.docksAvailable
    }
    // The published capacity is preferred to the sum of bikes plus docks, which
    // varies while a bike is being taken out; failing that, the sum stands in.
    val total = station.capacity?.takeIf { it > 0 }
        ?: (current.bikesAvailable + current.docksAvailable).takeIf { it > 0 }

    return AvailabilityDisplay(
        count = count,
        level = availabilityLevelOf(count),
        isOutOfService = false,
        filledFraction = total?.let { (count.toFloat() / it).coerceIn(0f, 1f) },
    )
}
