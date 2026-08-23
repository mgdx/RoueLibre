package io.github.mgdx.rouelibre.core.station

import io.github.mgdx.rouelibre.core.geo.Coordinates
import java.time.Instant

/**
 * A station of the network, in its static data.
 *
 * The vocabulary stays generic — "station", "bike", "network" — and never names
 * a particular network: that is a portability requirement (SPEC §15).
 *
 * @property id the producer's identifier, stable from one refresh to the next.
 * @property name the name as published by the producer.
 * @property position where the station stands.
 * @property capacity the total number of docking points, if the feed publishes
 *   it.
 * @property postalCode the postcode, if the feed publishes it.
 */
public data class Station(
    public val id: String,
    public val name: String,
    public val position: Coordinates,
    public val capacity: Int?,
    public val postalCode: String?,
)

/**
 * The state of a station at a given instant.
 *
 * @property stationId the identifier of the station described.
 * @property bikesAvailable bikes that can be borrowed.
 * @property bikesByVehicleType how those bikes divide between the network's
 *   vehicle types, as the feed publishes it, and empty when it publishes
 *   nothing. The identifiers are the producer's own and mean nothing here: it
 *   takes the network's table to turn them into mechanical and electric, which
 *   is what [splitByKind] does.
 * @property docksAvailable free docks to return a bike to.
 * @property isInstalled the station is deployed on the ground.
 * @property isRenting the station accepts rentals.
 * @property isReturning the station accepts returns.
 * @property reportedAt when the measurement was taken, as declared by the
 *   producer.
 */
public data class StationAvailability(
    public val stationId: String,
    public val bikesAvailable: Int,
    public val docksAvailable: Int,
    public val bikesByVehicleType: Map<String, Int> = emptyMap(),
    public val isInstalled: Boolean,
    public val isRenting: Boolean,
    public val isReturning: Boolean,
    public val reportedAt: Instant?,
) {
    /** True if the station can actually lend a bike right now. */
    public val canLendBike: Boolean
        get() = isInstalled && isRenting && bikesAvailable > 0

    /** True if the station can actually take a bike right now. */
    public val canAcceptBike: Boolean
        get() = isInstalled && isReturning && docksAvailable > 0
}

/**
 * A station and its last known state.
 *
 * The state is optional on purpose: the two GBFS feeds are not necessarily in
 * step. The feed observed for the Lille network published 268 stations in
 * `station_information` and 267 in `station_status`. A station without a state
 * must be shown as such, never vanish nor be mistaken for an empty one.
 */
public data class StationWithAvailability(
    public val station: Station,
    public val availability: StationAvailability?,
) {
    /** The level of service, which decides how the station is presented. */
    public val serviceState: ServiceState
        get() = when {
            availability == null -> ServiceState.Unknown
            !availability.isInstalled -> ServiceState.OutOfService
            !availability.isRenting && !availability.isReturning -> ServiceState.OutOfService
            else -> ServiceState.InService
        }

    /**
     * How long a station out of service has been silent, when the silence has
     * lasted long enough to be worth saying; `null` in every other case.
     *
     * A station out of service is out of service whether it closed this
     * morning or five months ago, and until now the application said the same
     * word for both. The `last_reported` field of `station_status` — the
     * instant the producer stamped the measurement — separates them, and it
     * was being stored and never read. On the Lille network on 23 August 2026,
     * two of the eight closed stations had reported that same day and one had
     * reported nothing since 13 March: the first will likely reopen, the last
     * is off the pavement.
     *
     * **The silence qualifies a closure, it never decides one.** The service
     * state is settled by `is_installed`, `is_renting` and `is_returning`
     * alone, which is why a station lending bikes gets no mark here however
     * old its measurement is.
     *
     * **The threshold is a day**, expressed as the age having left the hours
     * behind. The five minutes [Freshness.isStale] applies to the feed cannot
     * apply to a station: GBFS obliges no producer to restamp a station whose
     * count has not moved, so a network reporting only on change would see
     * every one of its stations called silent at four in the morning. A day
     * contradicts no producer — the Lille feed's 259 stations in service had a
     * median age of 2.2 minutes — and it is the step at which the two cases
     * above fall on either side.
     *
     * @param now the reference instant, injected to keep the rule testable.
     */
    public fun silentClosureAge(now: Instant): Freshness? {
        if (serviceState != ServiceState.OutOfService) return null
        val reportedAt = availability?.reportedAt ?: return null
        return when (val age = freshnessOf(reportedAt, now)) {
            is Freshness.Days, is Freshness.Months -> age
            else -> null
        }
    }
}

/** A station's service state, from the user's point of view. */
public enum class ServiceState {
    /** The station is working. */
    InService,

    /** The station is out of service, or not deployed yet. */
    OutOfService,

    /** The real-time feed says nothing about this station. */
    Unknown,
}

/**
 * Brings the stations' static data together with their real-time state.
 *
 * The join is deliberately forgiving: an identifier present on only one side
 * must neither make the station vanish nor make reading the feed fail. An
 * orphan state — describing a station absent from `station_information` — is
 * ignored, for want of knowing where to place it on the map.
 *
 * @return one entry per known station, in the order received.
 */
public fun joinStationsWithAvailability(
    stations: List<Station>,
    availabilities: List<StationAvailability>,
): List<StationWithAvailability> {
    val byStationId = availabilities.associateBy { it.stationId }
    return stations.map { station ->
        StationWithAvailability(station, byStationId[station.id])
    }
}
