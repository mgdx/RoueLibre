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
 * @property isInFeedWritingDocksFromCapacity the feed's free docks are written
 *   from its stations' capacities — the whole of it, or half — rather than
 *   counted: most of the stations where bikes stand announce such a figure,
 *   which the bikes standing there do not explain. Settled by the join over
 *   the whole feed, and `false` until then: a state nobody has joined keeps
 *   its count.
 * @property isInFeedTakingBikesPastCapacity the feed's stations take bikes
 *   back past their capacity: enough of them read nought free docks beside
 *   more bikes than their capacity holds, which only a return beside a full
 *   rack puts there. Settled by the join over the whole feed, and `false`
 *   until then.
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
    public val isInFeedWritingDocksFromCapacity: Boolean = false,
    public val isInFeedTakingBikesPastCapacity: Boolean = false,
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
     * Four cases, and none of them comes from a network's name
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
     *  · **a count written from the capacity**, in a feed where most stations
     *    holding bikes announce their whole capacity free, or half of it,
     *    whatever stands there ([isInFeedWritingDocksFromCapacity]). A dock
     *    holding a bike is not free, so such a figure was never measured, and
     *    the bikes have their own lock. Ecovelo's Auch-Vélo'c, on 22 October
     *    2026: 9 of its 9 stations holding bikes read their capacity in free
     *    docks — 10 free beside 10 bikes at a ten-dock station, 5 free beside
     *    7 bikes at a five-dock one, which also shows returns going past the
     *    capacity. Read as a count, it promised "5 free docks" everywhere;
     *  · **any count in a feed taking bikes past its capacity**, where enough
     *    stations read nought free docks beside more bikes than their capacity
     *    ([isInFeedTakingBikesPastCapacity]). A dock cannot hold two bikes, so
     *    those were returned beside a full rack, and the network took them:
     *    Frelo, in Freiburg, on 23 October 2026, read nought at 44 of its 157
     *    stations publishing a capacity while holding more bikes than it — 15
     *    at a six-dock one. Read as a count, every one of its full racks was
     *    struck off the journey's arrivals.
     *
     * **A nought is believed wherever it can have been measured.** At a
     * station publishing a capacity, it is the capacity less the bikes, and the
     * station is full — unless its feed is one taking bikes past their
     * capacity. Without a capacity, the feed itself says whether it
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
            isInFeedWritingDocksFromCapacity ||
            isInFeedTakingBikesPastCapacity ||
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
 * settles [StationAvailability.isInFeedCountingWithoutCapacity],
 * [StationAvailability.isInFeedWritingDocksFromCapacity] and
 * [StationAvailability.isInFeedTakingBikesPastCapacity] for every state.
 *
 * @param writesDocksFromCapacitySettled the feed was already found writing
 *   its free docks from its capacities this session
 *   ([settleWritingDocksFromCapacity]); the join then reads it so whatever
 *   this snapshot says, and otherwise judges the snapshot alone.
 * @param takesBikesPastCapacitySettled the feed was already found taking
 *   bikes past its capacities this session ([settleTakingBikesPastCapacity]),
 *   read the same way.
 * @param takesBikesPastCapacityAllowed the rider has not turned that reading
 *   off for this network (SPEC §4.1, fourth case). `false` reads every state
 *   as if the feed were never found taking bikes past its capacities, whatever
 *   was settled: the rider knows the network's rule better than its feed.
 * @return one entry per known station, in the order received.
 */
public fun joinStationsWithAvailability(
    stations: List<Station>,
    availabilities: List<StationAvailability>,
    writesDocksFromCapacitySettled: Boolean = false,
    takesBikesPastCapacitySettled: Boolean = false,
    takesBikesPastCapacityAllowed: Boolean = true,
): List<StationWithAvailability> {
    val byStationId = availabilities.associateBy { it.stationId }
    val countedWithoutCapacity = stations
        .filter { it.capacity == null && !it.isVirtual }
        .mapNotNull { byStationId[it.id]?.docksAvailable }
    val countsWithoutCapacity = countedWithoutCapacity.count { it == 0 } * 2 <=
        countedWithoutCapacity.size
    val writesDocksFromCapacity = writesDocksFromCapacitySettled ||
        writesDocksFromCapacity(stations, byStationId)
    val takesBikesPastCapacity = takesBikesPastCapacityAllowed &&
        (takesBikesPastCapacitySettled || takesBikesPastCapacity(stations, byStationId))
    return stations.map { station ->
        val availability = byStationId[station.id]
        StationWithAvailability(
            station = station,
            availability = availability?.copy(
                isAtVirtualStation = station.isVirtual,
                isAtStationWithCapacity = station.capacity != null,
                isInFeedCountingWithoutCapacity = countsWithoutCapacity,
                isInFeedWritingDocksFromCapacity = writesDocksFromCapacity,
                isInFeedTakingBikesPastCapacity = takesBikesPastCapacity,
            ),
        )
    }
}

/**
 * Whether the feed writes its free docks from its capacities
 * ([StationAvailability.isInFeedWritingDocksFromCapacity]), **settled once
 * judged so, for the session**: a feed found writing them stays read that way
 * until the stations are emptied, which is what changing city looks like.
 *
 * The share is taken on the live feed, and a feed near the line crosses it
 * from one reading to the next — one station decides: Niort read the whole
 * capacity at 51.8 and 51.9 per cent of its stations holding bikes on two
 * readings of 22 October 2026, and Cherbourg at 46 and then exactly 50,
 * before half the capacity was read as well. Troyes, the nearest feed above
 * the line since, stood at 10 of 19. Judged afresh at each refresh, the planner would
 * weigh a dock risk at one minute and none the next, and the ∞ would come and
 * go on the map.
 *
 * **Why it holds in that direction, and not the other**: a reading above the
 * line is evidence, one below it is only the absence of it. A docked network
 * never puts most of its stations holding bikes at such a figure — none
 * reached a quarter of them on the 360 networks surveyed — while a feed writing it
 * falls under the line whenever enough of its bikes happen to stand where the
 * capacity less the bikes gives the same figure, or at a station whose figure
 * is neither. Holding the verdict where the evidence was seen costs a network
 * that really counts nothing it has been seen doing; holding the other one
 * would undo the evidence. It is the same once-per-session settling as
 * [settleFreeDocksWorthShowing], which reads the stations this verdict has
 * already shaped, so the planner and every screen read the same answer.
 *
 * @param settled whether the feed was already found writing them this
 *   session.
 * @return the verdict to hold from now on, to hand to
 *   [joinStationsWithAvailability]: `false` for an empty snapshot, which
 *   forgets it.
 */
public fun settleWritingDocksFromCapacity(
    settled: Boolean,
    stations: List<Station>,
    availabilities: List<StationAvailability>,
): Boolean = when {
    stations.isEmpty() -> false
    settled -> true
    else -> writesDocksFromCapacity(stations, availabilities.associateBy { it.stationId })
}

/**
 * Whether the feed takes bikes past its capacities
 * ([StationAvailability.isInFeedTakingBikesPastCapacity]), **settled once
 * judged so, for the session**, as [settleWritingDocksFromCapacity] is and for
 * the same reason: a reading above the line is evidence, one below it only
 * its absence.
 *
 * Here the share moves with the hour, not only with a station: bikes pile up
 * beside full racks in the evening, when riders come home, and spread again
 * by day. Frelo read 44 of 157 at eleven at night on 23 October 2026. A
 * verdict judged afresh at each refresh would strike its full racks off the
 * journey's arrivals by day and put them back at night.
 *
 * @param settled whether the feed was already found taking them this session.
 * @return the verdict to hold from now on, to hand to
 *   [joinStationsWithAvailability]: `false` for an empty snapshot, which
 *   forgets it.
 */
public fun settleTakingBikesPastCapacity(
    settled: Boolean,
    stations: List<Station>,
    availabilities: List<StationAvailability>,
): Boolean = when {
    stations.isEmpty() -> false
    settled -> true
    else -> takesBikesPastCapacity(stations, availabilities.associateBy { it.stationId })
}

/**
 * Whether a feed's stations take bikes back past their capacity
 * ([StationAvailability.isInFeedTakingBikesPastCapacity]).
 *
 * **What betrays it: a station reading nought free docks while holding more
 * bikes than its capacity.** A dock holds one bike, so the bikes past the
 * capacity stand beside a full rack, and the network took them back there —
 * its bikes have their own lock. nextbike writes the free docks as the
 * capacity less the bikes, floored at nought, so such a station reads full
 * whatever stands beside it. On the 357 feeds read on 23 October 2026, late in
 * the evening: Frelo in Freiburg 44 of its 157 stations publishing a capacity
 * and a count, Mannheim 44 of 158, Vienna 50 of 202, Warsaw 134 of 348,
 * Katowice 583 of 943, Berlin's nextbike 83 of 1,074.
 *
 * **Only a nought counts.** A station holding more bikes than its capacity and
 * still reading free docks is a capacity lagging behind its rack, or a figure
 * written rather than counted, and neither says a bike was returned beside a
 * full rack: Vélib' had three such stations that evening, 49 bikes and 8 free
 * docks at a station declaring 42; Beryl writes four free docks at its bays
 * whatever stands there, and the case of a figure written from the capacity
 * is [writesDocksFromCapacity]'s.
 *
 * **The line is [MINIMUM_PER_CENT_PAST_CAPACITY] per cent of the stations
 * weighed**, those publishing a capacity above nought and a count. Every
 * network of physical docks stood at 1 % or under: Portland's Biketown 3 of
 * 308, Épinal 1 of 106, Chicago 13 of 2,076, Vélib' 1 of 1,516, New York none
 * of 2,483. The nextbike, Donkey Republic and Inurba networks spread from
 * there to 62 %, Katowice; five times the highest docked network leaves its
 * margin to a reading by day, when fewer bikes stand beside full racks than
 * late in the evening. Under it stay a few zone networks
 * the rule gives up on — Prague at 2.1 %, Copenhagen's Donkey Republic at 3 —
 * whose full racks are read as full, as they always were.
 *
 * **Below [MINIMUM_STATIONS_PAST_CAPACITY] such stations, nothing is decided**:
 * one or two capacities lagging behind their racks would otherwise decide a
 * small network for good. Three is the floor of [writesDocksFromCapacity],
 * stations agreeing; it leaves out nextbike's single-station towns, Třebíč and
 * Uherské Hradiště, and keeps Rüsselsheim's 5 of 6.
 */
private fun takesBikesPastCapacity(
    stations: List<Station>,
    byStationId: Map<String, StationAvailability>,
): Boolean {
    val weighed = stations.mapNotNull { station ->
        val capacity = station.capacity?.takeIf { it > 0 } ?: return@mapNotNull null
        val state = byStationId[station.id]?.takeIf { it.docksAvailable != null }
            ?: return@mapNotNull null
        state.docksAvailable == 0 && state.bikesAvailable > capacity
    }
    val pastCapacity = weighed.count { it }
    return pastCapacity >= MINIMUM_STATIONS_PAST_CAPACITY &&
        pastCapacity * PER_CENT >= weighed.size * MINIMUM_PER_CENT_PAST_CAPACITY
}

/**
 * Whether a feed's free docks are written from its stations' capacities
 * rather than counted ([StationAvailability.isInFeedWritingDocksFromCapacity]).
 *
 * **What is weighed: the stations holding bikes**, among those publishing
 * both a capacity above nought and a count of free docks. Only there does the
 * figure betray itself — a bike standing in a dock takes it, so a station
 * holding one cannot have its whole capacity free. An empty station reading
 * its capacity is simply empty, and every network has them: Agen's empty
 * fleet reads five free docks at five-dock racks, and that is a count.
 *
 * **Two figures are written from the capacity, the whole of it and half.**
 * Ecovelo writes the first at the stations of Auch, Riom and Villefranche,
 * and the two side by side elsewhere — Niort, on 22 October 2026, read 10
 * free docks at nearly every twenty-dock station and 7 at every fourteen-dock
 * one, whatever stood there, and the whole capacity at its eight-dock ones.
 * Half of an odd capacity is read rounded either way, as both are written:
 * 8 at a fifteen-dock station of Cherbourg holding 6 bikes, 3 at a
 * seven-dock one of Soissons holding 1.
 *
 * **Half is read only from [MINIMUM_CAPACITY_FOR_HALF] docks.** At a small
 * rack half the capacity is one or two docks, a figure a real count reaches
 * with a dock or a bike out of service, which the state does not carry:
 * nextbike's LoveSharing in Las Palmas, four- and six-dock racks, read half
 * at 5 of its 11 stations holding bikes on every reading of 22 October 2026,
 * one station from the line, and held there for a session it would have sent
 * riders to full racks. Six is the widest margin both ways, measured on two
 * readings of the 360 cities: it takes Las Palmas down to 3 of 11 and keeps
 * all 27 Ecovelo feeds above the line, Troyes the nearest at 10 of 19 and
 * untouched, Carcassonne at 6 and 7 of 11. Eight takes La Roche-sur-Yon from 16 of
 * 19 to 7, whose racks are of six and seven; ten loses Troyes, Charleville
 * and La Roche-sur-Yon. Beryl's Canterbury, the highest under the line at
 * 10 of 21, reads half at eight-dock bays, and no minimum up to eight moves it.
 * The whole capacity needs no such floor: it is a figure no rack holding a
 * bike reads, however small.
 *
 * **A figure the bikes explain is a count, whichever it is**: the capacity
 * less the bikes, floored at nought ([readsAsCounted]). Twenty docks holding
 * ten bikes do have ten free, and such a station is never weighed against the
 * feed. The bikes and docks out of service would widen that allowance, as the
 * discovery tool's frozen check does, but the state does not carry them, so
 * the count must be exact; on the live feeds it moved no network across the
 * line.
 *
 * What leaves a few real docks reading such a figure beside a bike is a
 * capacity the producer has not updated, a bike out of service, or a
 * coincidence, and it stays a minority: 137 of Chicago's 1,194 stations
 * holding bikes on 22 October 2026, 42 of Vélib's 1,369, 16 of Berlin's 694,
 * and no network of physical docks above Detroit's 8 of 57.
 *
 * **The line is a strict majority**, as for the noughts of
 * [StationAvailability.isInFeedCountingWithoutCapacity]: a network where most
 * docks holding a bike announce a figure no count gives is not one anybody
 * measured. All 27 Ecovelo feeds then land above it — Niort at 48 of 55,
 * Cherbourg at 36 of 42, Tarbes at 14 of 14, Agen and Brive at nearly all of
 * theirs, Troyes the nearest at 10 of 19 — and De Lijn's Blue-bike in
 * Brussels reads its capacity at 308 of 308. Under it, the highest is Beryl's
 * Canterbury at 10 of 21, its free docks reading four nearly everywhere, then
 * Norwich, Guildford and nextbike's LoveSharing near a third or more.
 *
 * **Below [MINIMUM_OCCUPIED_STATIONS_TO_JUDGE] stations holding bikes, the
 * feed is read as counting**, as it always was: two stations whose capacity
 * lags behind their docks would otherwise decide a network of three for good.
 * Five keeps the decision at three stations agreeing, and still reaches Auch,
 * whose ten stations hold bikes at nine on an ordinary morning.
 */
private fun writesDocksFromCapacity(
    stations: List<Station>,
    byStationId: Map<String, StationAvailability>,
): Boolean {
    val writtenFromCapacity = stations.mapNotNull { station ->
        val capacity = station.capacity?.takeIf { it > 0 } ?: return@mapNotNull null
        val state = byStationId[station.id]?.takeIf { it.bikesAvailable > 0 }
            ?: return@mapNotNull null
        val docks = state.docksAvailable ?: return@mapNotNull null
        val readsHalf = capacity >= MINIMUM_CAPACITY_FOR_HALF &&
            (docks == capacity / 2 || docks == (capacity + 1) / 2)
        !readsAsCounted(capacity, state.bikesAvailable, docks) && (docks == capacity || readsHalf)
    }
    return writtenFromCapacity.size >= MINIMUM_OCCUPIED_STATIONS_TO_JUDGE &&
        writtenFromCapacity.count { it } * 2 > writtenFromCapacity.size
}

/**
 * Whether free docks are what a station's capacity less its bikes gives,
 * floored at nought — the figure a count would give, so nothing to hold
 * against the feed.
 */
private fun readsAsCounted(capacity: Int, bikes: Int, docks: Int): Boolean =
    docks == (capacity - bikes).coerceAtLeast(0)

/**
 * The stations holding bikes a feed needs before [writesDocksFromCapacity] decides
 * anything; see there for the figure.
 */
private const val MINIMUM_OCCUPIED_STATIONS_TO_JUDGE = 5

/**
 * The capacity from which [writesDocksFromCapacity] reads half of it as a
 * figure written rather than counted; see there for the figure.
 */
private const val MINIMUM_CAPACITY_FOR_HALF = 6

/**
 * The stations reading nought beside more bikes than their capacity a feed
 * needs before [takesBikesPastCapacity] decides anything; see there for the
 * figure.
 */
private const val MINIMUM_STATIONS_PAST_CAPACITY = 3

/**
 * The share of a feed's stations, in per cent, reading nought beside more
 * bikes than their capacity from which [takesBikesPastCapacity] reads the
 * feed as taking bikes past it; see there for the figure.
 */
private const val MINIMUM_PER_CENT_PAST_CAPACITY = 5

private const val PER_CENT = 100
