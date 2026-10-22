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
 * @property isVirtual the station is a zone marked out for parking rather than
 *   a rack standing on the pavement — GBFS's `is_virtual_station`. Such a
 *   station has nothing to install, and its `is_installed` is no closure: see
 *   [StationAvailability.isDeployed].
 * @property providerId the operator the station belongs to, where the feed
 *   names one — an aggregator carries several (SPEC §15) — and `null` where it
 *   does not.
 */
public data class Station(
    public val id: String,
    public val name: String,
    public val position: Coordinates,
    public val capacity: Int?,
    public val postalCode: String?,
    public val isVirtual: Boolean = false,
    public val providerId: String? = null,
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
 * @property docksAvailable free docks to return a bike to, exactly as the feed
 *   counted them, and `null` where it published no count — which GBFS reserves
 *   for a station with unlimited docking. Never read alone to decide whether a
 *   bike can be returned: [hasUnlimitedDocks] says when the figure limits
 *   nothing.
 * @property isInstalled the station is deployed on the ground, exactly as the
 *   feed says it. Never read alone to decide a closure: [isDeployed] is.
 * @property isRenting the station accepts rentals.
 * @property isReturning the station accepts returns.
 * @property reportedAt when the measurement was taken, as declared by the
 *   producer.
 * @property isAtVirtualStation the station described is virtual
 *   ([Station.isVirtual]). Not a field of `station_status`: it is carried over
 *   from the station by [joinStationsWithAvailability], so that the state
 *   stored stays the one the feed sent.
 * @property isAtStationWithCapacity the station described publishes a
 *   capacity ([Station.capacity]). Carried over by the join for the same
 *   reason, and read by [hasUnlimitedDocks] alone.
 * @property isInFeedCountingWithoutCapacity the racks of the same feed that
 *   publish no capacity count their free docks: at most half of them say
 *   nought. Virtual stations are left out of it, their nought never being
 *   believed. In such a feed a nought without a capacity is a measurement.
 *   Settled by the join over the whole feed, and `true` until then: a state
 *   nobody has joined reads its nought as full, as it always did.
 * @property isInFeedRepeatingCapacity the feed's free docks are its stations'
 *   capacities written again rather than counted: most of the stations where
 *   bikes stand announce their whole capacity free, which no dock can do.
 *   Settled by the join over the whole feed, and `false` until then: a state
 *   nobody has joined keeps its count.
 */
public data class StationAvailability(
    public val stationId: String,
    public val bikesAvailable: Int,
    public val docksAvailable: Int?,
    public val bikesByVehicleType: Map<String, Int> = emptyMap(),
    public val isInstalled: Boolean,
    public val isRenting: Boolean,
    public val isReturning: Boolean,
    public val reportedAt: Instant?,
    public val isAtVirtualStation: Boolean = false,
    public val isAtStationWithCapacity: Boolean = false,
    public val isInFeedCountingWithoutCapacity: Boolean = true,
    public val isInFeedRepeatingCapacity: Boolean = false,
) {
    /**
     * True if the station is there to be used, as far as its installation
     * goes.
     *
     * A virtual station has no rack to put on the pavement, so its
     * `is_installed` says nothing about whether it works: Pony's Limoges
     * network publishes every one of its 265 stations as virtual and not
     * installed, while renting, returning and holding 269 bikes between them.
     * Read alone, the flag closed the whole city. `is_renting` and
     * `is_returning` still decide for such a station; for a physical one,
     * `is_installed` keeps its full weight (SPEC §4.1).
     */
    public val isDeployed: Boolean
        get() = isInstalled || isAtVirtualStation

    /** True if the station can actually lend a bike right now. */
    public val canLendBike: Boolean
        get() = isDeployed && isRenting && bikesAvailable > 0

    /**
     * True if no count of free docks limits what the station takes back.
     *
     * Three cases, and none of them comes from a network's name
     * (SPEC §4.1, §15):
     *
     *  · **no count at all.** GBFS requires `num_docks_available` "except for
     *    stations that have unlimited docking capacity", so a station
     *    publishing none is one of those;
     *  · **a nought measured against nothing**: a station publishing no
     *    capacity, said to have no free dock. nextbike writes the free docks as
     *    the capacity less the bikes, floored at nought, and writes nought where
     *    there is no capacity to subtract from — virtual or not. 804 of the 805
     *    stations of MyRadl in Munich on 21 October 2026, 800 of them virtual;
     *    359 of the 360 of Düsseldorf on 22 October, 345 of them racks. Read as
     *    a count, that nought made every journey in those cities end in "no
     *    free dock nearby";
     *  · **a count that is the capacity written again**, in a feed where most
     *    stations holding bikes announce their whole capacity free
     *    ([isInFeedRepeatingCapacity]). A dock holding a bike is not free, so
     *    such a figure was never measured, and the bikes have their own lock.
     *    Ecovelo's Auch-Vélo'c, on 22 October 2026: 9 of its 9 stations
     *    holding bikes read their capacity in free docks — 10 free beside 10
     *    bikes at a ten-dock station, 5 free beside 7 bikes at a five-dock one,
     *    which also shows returns going past the capacity. Read as a count, it
     *    promised "5 free docks" everywhere, whatever stood there.
     *
     * **A nought is believed wherever it can have been measured.** At a
     * station publishing a capacity, it is the capacity less the bikes, and the
     * station is full. Without a capacity, the feed itself says whether it
     * measures ([isInFeedCountingWithoutCapacity]). Indego, in Philadelphia,
     * publishes no capacity anywhere and counts its free docks at every rack:
     * 317 stations above nought and 4 at nought on 22 October 2026, which are
     * full racks, and Milwaukee 165 against 7. Where nextbike derives the
     * figure, the noughts are nearly all there is — every one of Düsseldorf's
     * 359, and 38 of Sarajevo's 42, whose four others stand at real docks. A
     * network where most racks are full at once is not one anybody measured,
     * so the line is half: a feed whose stations without a capacity say
     * nought at most half the time is believed, and one where they say it
     * more is not. It sits far from both families, 1.2 and 4 per cent against
     * 90 and 100. A virtual station without a capacity is a zone with no dock
     * to fill, and its nought is never believed.
     *
     * Whether the station takes returns at all is not decided here:
     * `is_returning` still refuses them, through [canAcceptBike].
     */
    public val hasUnlimitedDocks: Boolean
        get() = docksAvailable == null ||
            isInFeedRepeatingCapacity ||
            (
                docksAvailable == 0 &&
                    !isAtStationWithCapacity &&
                    (isAtVirtualStation || !isInFeedCountingWithoutCapacity)
                )

    /** True if the station can actually take a bike right now. */
    public val canAcceptBike: Boolean
        get() = isDeployed && isReturning && (hasUnlimitedDocks || (docksAvailable ?: 0) > 0)

    /**
     * True if the station takes a bike back right now, with no count to limit
     * it: [hasUnlimitedDocks] at a station that is deployed and returning.
     * What every screen asks before writing "unlimited" — a station refusing
     * returns is refused, however its docks read.
     */
    public val acceptsAnyBike: Boolean
        get() = canAcceptBike && hasUnlimitedDocks
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
            !availability.isDeployed -> ServiceState.OutOfService
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
     * state is settled by `is_installed` (see
     * [StationAvailability.isDeployed]), `is_renting` and `is_returning`
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
 * The join is also where a state learns that its station is virtual, and
 * whether it publishes a capacity: `station_status` says neither, and
 * [StationAvailability.isDeployed] and [StationAvailability.hasUnlimitedDocks]
 * need them. And it is the one place that sees the whole feed at once, so it
 * settles [StationAvailability.isInFeedCountingWithoutCapacity] and
 * [StationAvailability.isInFeedRepeatingCapacity] for every state.
 *
 * @return one entry per known station, in the order received.
 */
public fun joinStationsWithAvailability(
    stations: List<Station>,
    availabilities: List<StationAvailability>,
): List<StationWithAvailability> {
    val byStationId = availabilities.associateBy { it.stationId }
    val countedWithoutCapacity = stations
        .filter { it.capacity == null && !it.isVirtual }
        .mapNotNull { byStationId[it.id]?.docksAvailable }
    val countsWithoutCapacity = countedWithoutCapacity.count { it == 0 } * 2 <=
        countedWithoutCapacity.size
    val repeatsCapacity = repeatsCapacity(stations, byStationId)
    return stations.map { station ->
        val availability = byStationId[station.id]
        StationWithAvailability(
            station = station,
            availability = availability?.copy(
                isAtVirtualStation = station.isVirtual,
                isAtStationWithCapacity = station.capacity != null,
                isInFeedCountingWithoutCapacity = countsWithoutCapacity,
                isInFeedRepeatingCapacity = repeatsCapacity,
            ),
        )
    }
}

/**
 * Whether a feed's free docks are its stations' capacities written again
 * rather than counted ([StationAvailability.isInFeedRepeatingCapacity]).
 *
 * **What is weighed: the stations holding bikes**, among those publishing
 * both a capacity above nought and a count of free docks. Only there does the
 * figure betray itself — a bike standing in a dock takes it, so a station
 * holding one cannot have its whole capacity free. An empty station reading
 * its capacity is simply empty, and every network has them: Agen's empty
 * fleet reads five free docks at five-dock racks, and that is a count. The
 * bikes and docks out of service are not read, and need not be: at a real
 * dock they only ever take free docks away, never bring the figure up to the
 * capacity. Nor does a station holding more bikes than docks, which reads
 * nought. What leaves a few real docks reading their capacity beside a bike
 * is a capacity the producer has not updated, or a bike left beside the rack,
 * and it stays a minority: 2 of Berlin's 704 such stations on 22 October
 * 2026, 5 of Manchester's 198.
 *
 * **The line is a strict majority**, as for the noughts of
 * [StationAvailability.isInFeedCountingWithoutCapacity]: a network where most
 * docks holding a bike are also all free is not one anybody measured. Ecovelo
 * writes the capacity at every station of Auch (9 of 9 holding bikes, on
 * 22 October 2026), Riom (13 of 13), Villefranche-sur-Saône (16 of 18) and
 * Nîmes (33 of 46), and De Lijn's Blue-bike in Brussels at 308 of 308. Past
 * Ecovelo's own feeds, whose other stations mostly read a fixed half of their
 * capacity, the highest share stands at 36 per cent, Beryl's Canterbury at
 * 8 of 22, and every docked network is near nought.
 *
 * **Below [MINIMUM_OCCUPIED_STATIONS_TO_JUDGE] stations holding bikes, the
 * feed is read as counting**, as it always was: two stations whose capacity
 * lags behind their docks would otherwise decide a network of three for good.
 * Five keeps the decision at three stations agreeing, and still reaches Auch,
 * whose ten stations hold bikes at nine on an ordinary morning.
 */
private fun repeatsCapacity(
    stations: List<Station>,
    byStationId: Map<String, StationAvailability>,
): Boolean {
    val readsWholeCapacity = stations.mapNotNull { station ->
        val capacity = station.capacity?.takeIf { it > 0 } ?: return@mapNotNull null
        val state = byStationId[station.id] ?: return@mapNotNull null
        val docks = state.docksAvailable ?: return@mapNotNull null
        (docks == capacity).takeIf { state.bikesAvailable > 0 }
    }
    return readsWholeCapacity.size >= MINIMUM_OCCUPIED_STATIONS_TO_JUDGE &&
        readsWholeCapacity.count { it } * 2 > readsWholeCapacity.size
}

/**
 * The stations holding bikes a feed needs before [repeatsCapacity] decides
 * anything; see there for the figure.
 */
private const val MINIMUM_OCCUPIED_STATIONS_TO_JUDGE = 5
