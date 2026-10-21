package io.github.mgdx.rouelibre.core.station

/**
 * Whether a network's free docks say anything worth putting in front of a
 * rider (SPEC §7.1), decided on 22 October 2026.
 *
 * A station whose free docks limit nothing is written ∞
 * ([StationAvailability.hasUnlimitedDocks], SPEC §4.1), and that is the right
 * answer for the odd zone in a network of racks. It is not one for a network
 * made of such zones: MyRadl in Munich wrote ∞ at 797 of its 805 stations on
 * 22 October 2026, and a ∞ on nearly every station tells the rider nothing,
 * while a control that switches the map to a column of ∞ is noise. Past
 * [UNLIMITED_PERCENT_SILENCING_FREE_DOCKS], the application stops talking
 * about free docks for that network altogether — no toggle, no figure, no
 * word — and talks about bikes alone.
 *
 * **This is a display decision and nothing else.** The journey planner reads
 * the counts as it always has: the unlimited stations are arrival candidates,
 * and the few that do publish a count keep it.
 *
 * **Which stations are weighed**: those with a live status, and of those the
 * ones that are there to be used ([StationAvailability.isDeployed]). A station
 * the real-time feed says nothing about has no free docks to read, limited or
 * not; and a rack not yet put on the pavement says nothing about how the
 * network takes bikes back — it is a plan, not a station. A station refusing
 * returns for now is weighed all the same: whether its free docks limit
 * anything is a fact about its kind, not about this afternoon. Measured on the
 * live feeds of the 360 networks of the catalogue on 22 October 2026, the
 * choice changes nothing anywhere today — Pony's networks publish every
 * station as not installed, and are counted whole because their stations are
 * virtual ones, which [StationAvailability.isDeployed] already reads as there.
 *
 * @return `true` if the free docks are worth showing, `false` if they are not,
 *   and `null` when no station could be weighed — nothing read yet, or the
 *   stations known and their states not — which settles nothing either way.
 */
public fun freeDocksAreWorthShowing(stations: List<StationWithAvailability>): Boolean? {
    val weighed = stations.mapNotNull { it.availability }.filter { it.isDeployed }
    if (weighed.isEmpty()) return null
    val unlimited = weighed.count { it.hasUnlimitedDocks }
    // In whole numbers, so that "more than ninety per cent" is exactly that at
    // ten stations as at a thousand, with no rounding of a fraction to argue.
    return unlimited * PER_CENT <= weighed.size * UNLIMITED_PERCENT_SILENCING_FREE_DOCKS
}

/**
 * The verdict of [freeDocksAreWorthShowing], **settled once per city and per
 * session**: the first snapshot that can be weighed decides, and every later
 * one is read under that decision.
 *
 * The share is computed from the live feed, and a network sitting at the
 * threshold would see its toggle come and go between two refreshes. Nothing
 * of the kind has been seen — on the catalogue's 356 readable networks, two
 * readings five minutes apart on 22 October 2026 did not move a single share,
 * the share being a fact about how the network is built rather than about the
 * hour — but a control that disappears under a thumb is worse than a slow
 * answer, so the decision is not asked twice. It is cheaper than a band of
 * hysteresis, which would have been a second figure to justify (SPEC §14), and
 * a network really changing its nature is seen at the next launch.
 *
 * The first snapshot that can be weighed is the **cache** at a cold start,
 * offline included — the last states the network sent, built the same way —
 * and the first fetch on a city's first day.
 *
 * **An empty snapshot forgets the verdict**: it is what changing city looks
 * like from here, the station cache being emptied in the same movement, and
 * one network's verdict says nothing about the next one's.
 *
 * @param settled the verdict reached so far this session, `null` for none.
 * @param stations the snapshot just read.
 * @return the verdict to hold from now on, `null` while nothing could be
 *   weighed — which the interface reads as "show them", the way it always has.
 */
public fun settleFreeDocksWorthShowing(
    settled: Boolean?,
    stations: List<StationWithAvailability>,
): Boolean? = when {
    stations.isEmpty() -> null
    settled != null -> settled
    else -> freeDocksAreWorthShowing(stations)
}

/**
 * The share of a network's stations taking back any bike, in per cent, past
 * which its free docks are no longer shown (SPEC §7.1).
 *
 * Ninety is the owner's figure of 22 October 2026, and the live feeds of that
 * day put it in a gap: the networks the rule is for stand at 99 % and above —
 * MyRadl in Munich 99.0, nextbike Bratislava 99.8, Pony 100 — and the nearest
 * network below, Mobibike, at 84.5. Düsseldorf (3.9) and Cologne (38.5), which
 * prompted the question, keep their toggle: most of their stations count.
 */
public const val UNLIMITED_PERCENT_SILENCING_FREE_DOCKS: Int = 90

private const val PER_CENT = 100
