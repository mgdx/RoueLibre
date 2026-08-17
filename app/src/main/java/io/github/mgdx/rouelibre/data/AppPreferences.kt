package io.github.mgdx.rouelibre.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.journey.WalkingPace
import io.github.mgdx.rouelibre.core.measure.UnitChoice
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The theme the user wants the application dressed in (SPEC §7.6).
 *
 * @property id the value written to disk, stable from one release to the next.
 */
enum class AppTheme(val id: String) {
    /** The system's own, and that is the default. */
    System("systeme"),

    /** Always light. */
    Light("clair"),

    /** Always dark. */
    Dark("sombre"),
    ;

    companion object {
        /** Reads a stored theme back; an unknown value returns [System]. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: System
    }
}

/** No version seen: the application has never been launched. */
const val NEVER_LAUNCHED: Int = 0

/**
 * The application's settings and persistent state (SPEC §8).
 *
 * DataStore rather than Room, because these are only a few isolated values.
 * Nothing written here describes a journey: no history, no position, no
 * destination (SPEC §2, C3).
 */
class AppPreferences(private val dataStore: DataStore<Preferences>) :
    RefreshTimestampStore,
    MeasuredFleetStore {

    /**
     * What the network was last counted to lend (SPEC §4.1).
     *
     * One slot for the city in service, holding the identifier it was counted
     * for: a reading found under another city's name is a reading about another
     * network, and is read as none at all.
     */
    override suspend fun measuredFleet(cityId: String): FleetDescription? {
        val stored = dataStore.data.first()[MEASURED_FLEET] ?: return null
        val reading = try {
            json.decodeFromString<StoredFleet>(stored)
        } catch (_: SerializationException) {
            // Written by a version that wrote it differently, or truncated by a
            // device out of space. Counting again costs one refresh; refusing
            // to launch would cost the user the application.
            return null
        }
        if (reading.cityId != cityId) return null
        return FleetDescription(
            hasElectricBikes = reading.electricBikes,
            isMixed = reading.mixed,
            vehicleTypes = reading.vehicleTypes.mapValues { (_, kind) ->
                VehicleKind.ofWireName(kind)
            },
        )
    }

    /** Stores a reading, replacing whatever the slot held. */
    override suspend fun setMeasuredFleet(cityId: String, fleet: FleetDescription) {
        val reading = StoredFleet(
            cityId = cityId,
            electricBikes = fleet.hasElectricBikes,
            mixed = fleet.isMixed,
            vehicleTypes = fleet.vehicleTypes.mapValues { (_, kind) -> kind.wireName },
        )
        dataStore.edit { it[MEASURED_FLEET] = json.encodeToString(reading) }
    }

    /** Empties the slot, the city served having changed. */
    override suspend fun clearMeasuredFleet() {
        dataStore.edit { it.remove(MEASURED_FLEET) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * When the stations' static data was last refreshed.
     *
     * Persisted because the "at most once a day" rule (SPEC §4.1) must survive
     * a restart of the application: otherwise every launch would download the
     * complete station list again.
     */
    override suspend fun stationInformationFetchedAt(): Instant? =
        dataStore.data.first()[STATION_INFORMATION_FETCHED_AT]
            ?.let(Instant::ofEpochSecond)

    /** Stores when the static data was last refreshed. */
    override suspend fun setStationInformationFetchedAt(instant: Instant) {
        dataStore.edit { preferences ->
            preferences[STATION_INFORMATION_FETCHED_AT] = instant.epochSecond
        }
    }

    /**
     * The stations marked as favourites, **in the chosen order** (SPEC §7.5).
     *
     * Station identifiers, and nothing else: these are not the user's own
     * places but public points of the network, and constraint C3 forbids
     * recording anything about a journey.
     *
     * An ordered list rather than a set: §7.5 wants the list to be reorderable,
     * and a set has no order to rearrange. The identifiers are joined by a
     * newline, a character no GBFS identifier contains.
     *
     * A flow rather than a read: a station's star must update everywhere it is
     * shown, without the screens having to tell one another.
     */
    val favouriteStationIds: Flow<List<String>> = dataStore.data.map(::readFavourites)

    /**
     * Adds a station to the favourites, or takes it out.
     *
     * A station added goes to the end of the list: that is where one expects to
     * find what one has just done.
     *
     * @return true if the station is now a favourite.
     */
    suspend fun toggleFavourite(stationId: String): Boolean {
        var isFavourite = false
        dataStore.edit { preferences ->
            val current = readFavourites(preferences)
            isFavourite = stationId !in current
            val updated = if (isFavourite) current + stationId else current - stationId
            preferences[FAVOURITE_STATION_IDS_ORDERED] = updated.joinToString(SEPARATOR)
        }
        return isFavourite
    }

    /** Stores a new order for the favourites (SPEC §7.5). */
    suspend fun setFavouriteOrder(stationIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[FAVOURITE_STATION_IDS_ORDERED] = stationIds.joinToString(SEPARATOR)
        }
    }

    /**
     * Reads the favourites back, picking up those of an earlier version.
     *
     * The first versions kept them in a set, without order. Losing them on an
     * update would be a small betrayal of a user who had filed twenty of them.
     */
    private fun readFavourites(preferences: Preferences): List<String> {
        preferences[FAVOURITE_STATION_IDS_ORDERED]?.let { stored ->
            return stored.split(SEPARATOR).filter { it.isNotBlank() }
        }
        return preferences[FAVOURITE_STATION_IDS].orEmpty().sorted()
    }

    /**
     * The chosen theme: light, dark, or the system's (SPEC §7.6).
     *
     * The default follows the system, the only choice that respects a
     * preference the user has already expressed elsewhere.
     */
    val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromId(preferences[THEME])
    }

    /** Stores the chosen theme. */
    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.id }
    }

    /**
     * Whether the map leaves out the stations the feed says are out of service
     * (SPEC §7.1, §7.6).
     *
     * False by default, and false for anything that cannot be read as a yes or a
     * no: a map nobody has asked anything of shows the network as it stands.
     *
     * **It is kept from one session to the next**, like the theme and the units
     * beside it, because it is settled in the settings and nowhere else. The
     * consequence is named in SPEC §7.1 and accepted: nothing on the map recalls
     * that a filter is on, so somebody reopening the application weeks later on a
     * neighbourhood emptied of its stations has only this screen to explain it.
     * That is why the two switches say in full what they take away — and it is a
     * decision, not an oversight to be corrected by putting a badge back on the
     * map.
     *
     * **A way of looking, not a journey**: what is written is a yes or a no
     * about drawing markers, and no point, no time, no destination goes with it
     * (SPEC §2, C3).
     *
     * A flow rather than a read: the map follows it, so a switch flicked here
     * reaches the markers without the application being restarted.
     */
    val hideOutOfServiceStations: Flow<Boolean> =
        dataStore.data.map { it.readFlag(HIDE_OUT_OF_SERVICE_STATIONS, ifUnanswered = false) }

    /** Remembers whether the map leaves out the stations out of service. */
    suspend fun setHideOutOfServiceStations(hide: Boolean) {
        dataStore.edit { it[HIDE_OUT_OF_SERVICE_STATIONS] = hide }
    }

    /**
     * Whether the map leaves out the stations whose count was read as zero
     * (SPEC §7.1, §7.6).
     *
     * False by default and false for anything unreadable, kept from one session
     * to the next, and a way of looking rather than a journey — all three for the
     * reasons [hideOutOfServiceStations] gives, the compromise of §7.1 included.
     *
     * **What "empty" means is not settled here.** It is read against what the map
     * is counting at that moment — no bike while bikes are counted, no free dock
     * while docks are — so the same station is dropped by one mode and kept by
     * the other. This screen knows nothing of that toggle, which is why the
     * switch is worded to hold in both modes.
     */
    val hideEmptyStations: Flow<Boolean> =
        dataStore.data.map { it.readFlag(HIDE_EMPTY_STATIONS, ifUnanswered = false) }

    /** Remembers whether the map leaves out the stations read as empty. */
    suspend fun setHideEmptyStations(hide: Boolean) {
        dataStore.edit { it[HIDE_EMPTY_STATIONS] = hide }
    }

    /**
     * Reads a yes-or-no setting, anything else counting as "never answered".
     *
     * Screens are built on these settings, and a settings file holding something
     * other than a boolean under one of their names — written by a version that
     * stored it differently, or truncated by a device out of space — would
     * otherwise take a screen down rather than fall back on what the application
     * does when nobody has answered. Going through [Preferences.asMap] is what
     * makes the cast checkable at all: reading through a typed key hands back a
     * value nobody verified.
     *
     * @param key the name the setting is written under.
     * @param ifUnanswered what an absent or unreadable value means. It is the
     *   setting's own default and never a fixed "no": leaving the datasets on a
     *   billed connection is a yes (SPEC §4.4), while hiding stations is a no.
     */
    private fun Preferences.readFlag(
        key: Preferences.Key<Boolean>,
        ifUnanswered: Boolean,
    ): Boolean = asMap()[key] as? Boolean ?: ifUnanswered

    /**
     * The units distances are written in (SPEC §7.6, §9).
     *
     * The default follows the device's region, the only choice that respects a
     * preference the user has already expressed elsewhere — and a value that
     * cannot be read means the same thing, never a system in particular (see
     * [UnitChoice.fromId]).
     *
     * **A way of writing, not a way of computing.** What is stored changes no
     * journey and no measurement: everything is worked out in metres and this
     * is consulted when a figure becomes text (SPEC §14). And it says nothing
     * about the user beyond how they read a distance (SPEC §2, C3).
     *
     * A flow rather than a read: the interface is rebuilt on it, so a choice
     * made must show at once and everywhere.
     */
    val units: Flow<UnitChoice> = dataStore.data.map { UnitChoice.fromId(it[UNITS]) }

    /** Stores the chosen units. */
    suspend fun setUnits(choice: UnitChoice) {
        dataStore.edit { it[UNITS] = choice.id }
    }

    /**
     * The screen the application opens on (SPEC §7.0, §7.6).
     *
     * [OpeningScreen.Map] by default, and for any value that cannot be read
     * (see [OpeningScreen.fromId]): it is what the application has always opened
     * on, so nothing changes for somebody who never opens this screen.
     *
     * **A way of arriving, not a way of computing**, like [units] and unlike
     * [walkingPace]: no journey and no measurement depends on it. And it says
     * nothing about the user beyond which of two screens they read first
     * (SPEC §2, C3).
     *
     * A flow rather than a read, on the pattern of the settings around it —
     * though the one place it is consulted, the activity's first transaction,
     * takes a single value from it.
     */
    val openingScreen: Flow<OpeningScreen> = dataStore.data.map { preferences ->
        OpeningScreen.fromId(preferences[OPENING_SCREEN])
    }

    /** Stores the screen the application is to open on. */
    suspend fun setOpeningScreen(screen: OpeningScreen) {
        dataStore.edit { it[OPENING_SCREEN] = screen.id }
    }

    /**
     * What the user says their own bike is (SPEC §7.3, §7.6).
     *
     * `null` — nothing written down — means "not specified", and that is the
     * state on a fresh installation and after any reset; a word this build
     * cannot read means the same thing, never a kind (see
     * [OwnBikeKind.fromId]). Not specified reproduces exactly the drawings and
     * the sentences of the version before this choice existed.
     *
     * **This is not [wantedBikeKind], and the two must never be taken for one
     * another.** That one asks which of the bikes the **network** lends one
     * wants to be sent to: it exists only in a conurbation lending both, and it
     * narrows the stations §6 may choose. This one asks what the rider's **own**
     * bike is: it belongs to no fleet, it is the same in every city, and it
     * changes nothing but what is drawn and what is said — no speed, no
     * coefficient, no profile (see [OwnBikeKind]).
     *
     * **A word about equipment, not a journey**, like [usesOwnBike] beside it:
     * "mechanical", "electric", or nothing at all, and no point, no time, no
     * destination goes with it (SPEC §2, C3).
     *
     * A flow rather than a read: the journey screens follow it, so a bike
     * declared in the settings reaches the drawing without a restart.
     */
    val ownBikeKind: Flow<OwnBikeKind?> = dataStore.data.map {
        OwnBikeKind.fromId(it[OWN_BIKE_KIND])
    }

    /** Remembers what the user's own bike is, or that they have not said. */
    suspend fun setOwnBikeKind(kind: OwnBikeKind?) {
        dataStore.edit { preferences ->
            if (kind == null) {
                preferences.remove(OWN_BIKE_KIND)
            } else {
                preferences[OWN_BIKE_KIND] = kind.id
            }
        }
    }

    /**
     * Whether journeys are worked out for the user's own bike (SPEC §7.3).
     *
     * False by default, and false again for anything that cannot be read as a
     * yes or a no: the application serves a bike-share network, and the journey
     * through its stations is what one opens it for. The choice is kept
     * because it is a fact about the person rather than about the trip — one
     * who owns a bike still owns it tomorrow — and asking again at every
     * journey would be asking the same answer of them every time.
     *
     * **A preference, not a journey.** What is written is a yes or a no about
     * equipment; no point, no time, no destination goes with it (SPEC §2, C3).
     *
     * A flow rather than a read: it is read from a screen that is opened again
     * and again, and a value that changes must not need the screen rebuilt.
     */
    val usesOwnBike: Flow<Boolean> =
        dataStore.data.map { it.readFlag(USES_OWN_BIKE, ifUnanswered = false) }

    /** Remembers whether the user rides their own bike. */
    suspend fun setUsesOwnBike(usesOwnBike: Boolean) {
        dataStore.edit { it[USES_OWN_BIKE] = usesOwnBike }
    }

    /**
     * The kind of bike journeys are worked out for (SPEC §7.3).
     *
     * `null` — nothing written down — means no kind is asked for, and that is
     * the state on a fresh installation and after any reset: the application
     * presumes no kind, as it presumes no city (SPEC §15). A word it cannot read
     * means the same thing, never a kind (see [WantedBikeKind.ofWireName]).
     *
     * Kept for the same reason as [usesOwnBike], and beside it: what somebody
     * wants to ride is a fact about them, where the two points are a fact about
     * one trip. It is ignored — never erased — in a conurbation lending one kind
     * only, so coming back to a mixed one finds the choice again.
     *
     * **A word about equipment, not a journey.** What is written is
     * "mechanical", "electric", or nothing at all: no point, no time, no
     * destination goes with it (SPEC §2, C3).
     *
     * A flow rather than a read, for the reason [usesOwnBike] gives.
     */
    val wantedBikeKind: Flow<WantedBikeKind?> = dataStore.data.map {
        WantedBikeKind.ofWireName(it[WANTED_BIKE_KIND])
    }

    /** Remembers the kind of bike wanted, or that none is. */
    suspend fun setWantedBikeKind(kind: WantedBikeKind?) {
        dataStore.edit { preferences ->
            if (kind == null) {
                preferences.remove(WANTED_BIKE_KIND)
            } else {
                preferences[WANTED_BIKE_KIND] = kind.wireName
            }
        }
    }

    /**
     * How fast the user walks, which the journey is worked out for (SPEC §6, §7.6).
     *
     * [WalkingPace.Normal] by default, and for any value that cannot be read
     * (see [WalkingPace.fromId]): it is the pace the application has always used,
     * so an absent preference changes no journey.
     *
     * **A fact about the person, like [usesOwnBike].** How fast somebody walks is
     * true of them tomorrow as well, which is why it lives in the settings rather
     * than being asked at every journey — and why it belongs in the journey
     * section of that screen, since it qualifies a journey before it is asked
     * for. What is written is one word about a pace: no point, no time, no
     * destination goes with it (SPEC §2, C3), and nothing here says why somebody
     * walks slowly.
     *
     * **This one does reach the algorithm**, unlike [units]: it multiplies the
     * walking legs, so it changes which pair of stations wins (SPEC §6). That is
     * what it is for.
     *
     * A flow rather than a read: it is consulted at each computation, so a pace
     * changed in the settings applies to the next journey without the
     * application being restarted.
     */
    val walkingPace: Flow<WalkingPace> = dataStore.data.map { WalkingPace.fromId(it[WALKING_PACE]) }

    /** Remembers how fast the user walks. */
    suspend fun setWalkingPace(pace: WalkingPace) {
        dataStore.edit { it[WALKING_PACE] = pace.id }
    }

    /**
     * Whether the datasets may only travel over a connection nobody is billed
     * for (SPEC §4.4).
     *
     * **True by default**, and true for anything absent or unreadable: a city's
     * data runs from a few megabytes to more than a gigabyte, and a gigabyte
     * that leaves on a mobile plan nobody meant to spend costs more than a
     * download put off for an hour.
     *
     * **Billing, not Wi-Fi.** What Android answers is what a network bills, and
     * that is the truer notion: a phone's shared connection is Wi-Fi and is a
     * mobile plan, while a capped hotel Wi-Fi declares itself billed. The word
     * written here says so, and so does the switch that sets it.
     *
     * **It is never a dead end**: the storage screen offers to download anyway,
     * for that one transfer, and doing so leaves this value untouched.
     *
     * **A preference about transfers, not a journey.** What is written is a yes
     * or a no about downloading; no point, no time, no destination goes with it
     * (SPEC §2, C3).
     *
     * A flow rather than a read, for the reason [usesOwnBike] gives: the storage
     * screen watches it, and a setting changed while a transfer waits must reach
     * that screen without it being rebuilt.
     */
    val downloadOnUnmeteredOnly: Flow<Boolean> =
        dataStore.data.map { it.readFlag(DOWNLOAD_ON_UNMETERED_ONLY, ifUnanswered = true) }

    /** Remembers whether downloads wait for a connection nobody is billed for. */
    suspend fun setDownloadOnUnmeteredOnly(unmeteredOnly: Boolean) {
        dataStore.edit { it[DOWNLOAD_ON_UNMETERED_ONLY] = unmeteredOnly }
    }

    /**
     * The city the application is serving right now.
     *
     * `null` until one has been chosen: the application assumes no default
     * conurbation, and it is the welcome screen that proposes one (SPEC §15).
     * Only the identifier is kept — not a position, not a history of cities
     * visited (SPEC §2, C3).
     */
    suspend fun activeCityId(): String? =
        dataStore.data.first()[ACTIVE_CITY_ID]?.takeIf { it.isNotBlank() }

    /** Follows the active city, so the screens bring themselves up to date. */
    val activeCityIdFlow: Flow<String?> =
        dataStore.data.map { it[ACTIVE_CITY_ID]?.takeIf { id -> id.isNotBlank() } }

    /** Changes the active city. */
    suspend fun setActiveCityId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(ACTIVE_CITY_ID)
            } else {
                preferences[ACTIVE_CITY_ID] = id
            }
        }
    }

    /**
     * The network whose proposal the user turned down (SPEC §15.1).
     *
     * The application offers the network of the conurbation one happens to be
     * in. Offered again at the next launch, and at every press of "locate me",
     * an offer becomes insistence — and the button that answers "where am I"
     * becomes unusable to anyone who wants to keep the city they chose.
     *
     * **A network identifier, and nothing more.** Not a position, not a date,
     * not a list of the cities one has passed through: constraint C3 forbids
     * recording where somebody has been, and the name of a public network one
     * declined says nothing of the sort. It is dropped as soon as the user is
     * somewhere that network does not serve — see `AppContainer`.
     */
    suspend fun declinedCityProposalId(): String? =
        dataStore.data.first()[DECLINED_CITY_PROPOSAL_ID]?.takeIf { it.isNotBlank() }

    /** Records a declined proposal, or forgets the one recorded. */
    suspend fun setDeclinedCityProposalId(id: String?) {
        dataStore.edit { preferences ->
            if (id.isNullOrBlank()) {
                preferences.remove(DECLINED_CITY_PROPOSAL_ID)
            } else {
                preferences[DECLINED_CITY_PROPOSAL_ID] = id
            }
        }
    }

    /**
     * The last version code the user has seen (SPEC §7.9, §7.10).
     *
     * Zero means "never launched": the welcome screen applies then, never the
     * what's-new one. A value below the installed version means "updated
     * since": the notes of the intermediate versions are shown, once.
     */
    suspend fun lastSeenVersionCode(): Int =
        dataStore.data.first()[LAST_SEEN_VERSION_CODE] ?: NEVER_LAUNCHED

    /** Remembers the version the user has just seen. */
    suspend fun setLastSeenVersionCode(versionCode: Int) {
        dataStore.edit { it[LAST_SEEN_VERSION_CODE] = versionCode }
    }

    private companion object {
        val STATION_INFORMATION_FETCHED_AT =
            longPreferencesKey("station_information_fetched_at")

        /** The favourites from before the ordered version, picked up on first read. */
        val FAVOURITE_STATION_IDS = stringSetPreferencesKey("favourite_station_ids")
        val FAVOURITE_STATION_IDS_ORDERED = stringPreferencesKey("favourite_station_ids_ordered")

        /** No GBFS identifier contains a newline. */
        const val SEPARATOR = "\n"
        val THEME = stringPreferencesKey("theme")

        /** Which stations the map draws at all (SPEC §7.1). */
        val HIDE_OUT_OF_SERVICE_STATIONS =
            booleanPreferencesKey("hide_out_of_service_stations")
        val HIDE_EMPTY_STATIONS = booleanPreferencesKey("hide_empty_stations")
        val UNITS = stringPreferencesKey("units")
        val DOWNLOAD_ON_UNMETERED_ONLY = booleanPreferencesKey("download_on_unmetered_only")
        val USES_OWN_BIKE = booleanPreferencesKey("uses_own_bike")
        val WANTED_BIKE_KIND = stringPreferencesKey("wanted_bike_kind")
        val WALKING_PACE = stringPreferencesKey("walking_pace")
        val OPENING_SCREEN = stringPreferencesKey("opening_screen")

        /**
         * The rider's own bike, which is not [WANTED_BIKE_KIND] above: that one
         * is a kind asked of the network, this one a fact about the rider.
         */
        val OWN_BIKE_KIND = stringPreferencesKey("own_bike_kind")
        val ACTIVE_CITY_ID = stringPreferencesKey("active_city_id")
        val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
        val DECLINED_CITY_PROPOSAL_ID = stringPreferencesKey("declined_city_proposal_id")
        val MEASURED_FLEET = stringPreferencesKey("measured_fleet")
    }
}

/**
 * A counted fleet as it is written to disk.
 *
 * Its own shape rather than the domain one, so that renaming a property of
 * [FleetDescription] cannot silently make every stored reading unreadable. The
 * kinds are written by their `wireName`, the same words the city configuration
 * uses.
 */
@Serializable
private data class StoredFleet(
    val cityId: String,
    val electricBikes: Boolean,
    val mixed: Boolean,
    val vehicleTypes: Map<String, String>,
)
