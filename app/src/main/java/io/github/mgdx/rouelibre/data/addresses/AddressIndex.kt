package io.github.mgdx.rouelibre.data.addresses

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressEntryKind
import io.github.mgdx.rouelibre.core.address.AddressNormalizer
import io.github.mgdx.rouelibre.core.address.AddressQuery
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.KnownHouseNumber
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.address.ScoredStreet
import io.github.mgdx.rouelibre.core.address.SearchableStreet
import io.github.mgdx.rouelibre.core.address.parseQuery
import io.github.mgdx.rouelibre.core.address.rankStreets
import io.github.mgdx.rouelibre.core.address.resolveHouseNumber
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Address search in the offline index (SPEC §4.3).
 *
 * **Nothing leaves the device, ever, not even while typing.** This is the
 * application's most sensitive data: it reveals where the user is going. No
 * online geocoder is called; there is none in the project.
 *
 * The search runs in two stages, as the specification prescribes:
 *
 * 1. **Full-text index** on street names, queried by prefix, which covers
 *    typing in progress. It is what makes the whole thing viable: house
 *    numbers, for their part, are never searched as text.
 * 2. **Edit-distance fallback** when the first stage returns too few results,
 *    scanned in Kotlin over the normalised names held in memory. SQLite's
 *    trigram tokenizer could have handled it, but it is absent from the SQLite
 *    versions embedded in the oldest Android releases the application targets.
 *
 * @property datasetStore where to find the installed index file.
 * @property normalizer the rules shared with the indexing script.
 * @property ioDispatcher the execution context: reading the file and the fuzzy
 *   scan are both too long for the main thread.
 */
class AddressIndex(
    private val datasetStore: DatasetStore,
    private val normalizer: AddressNormalizer,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * An open database and a loaded corpus, for a given file.
     *
     * Kept from one search to the next: reopening the database and re-reading
     * twenty thousand rows on every keystroke would cost more than the search
     * itself.
     */
    private class OpenIndex(
        val signature: String,
        val database: SQLiteDatabase,
        val streets: List<SearchableStreet>,
        val deltaScale: Double,
    )

    private val openMutex = Mutex()
    private var opened: OpenIndex? = null

    /** True if the index is installed on the device. */
    fun isInstalled(): Boolean = datasetStore.fileOf(DatasetKind.Addresses) != null

    /**
     * Looks up an address.
     *
     * @param rawQuery the query as typed. A house number is recognised in it in
     *   either writing order.
     * @param origin the reference point for ranking results by proximity — the
     *   user's position or the centre of the map. `null` if neither is known.
     * @param limit how many results are wanted.
     * @return the addresses found, best first; an empty list if the query
     *   designates nothing; a failure if the index is absent or unreadable.
     */
    suspend fun search(
        rawQuery: String,
        origin: Coordinates?,
        limit: Int = DEFAULT_RESULT_COUNT,
    ): Outcome<List<AddressResult>> = withContext(ioDispatcher) {
        val query = normalizer.parseQuery(rawQuery)
        if (query.isEmpty) return@withContext Outcome.Success(emptyList())

        val index = try {
            open() ?: return@withContext Outcome.Failure(
                DataError.LocalStorageFailure("address index absent"),
            )
        } catch (error: RuntimeException) {
            return@withContext Outcome.Failure(
                DataError.LocalStorageFailure(error.message ?: "address index unreadable"),
            )
        }

        try {
            val ranked = rank(index, query, origin, limit)
            Outcome.Success(ranked.map { scored -> index.toResult(scored, query) })
        } catch (error: RuntimeException) {
            // A corrupted index must not bring the screen down: it can be
            // imported again from the storage screen.
            close()
            Outcome.Failure(
                DataError.LocalStorageFailure(error.message ?: "search impossible"),
            )
        }
    }

    /**
     * The address nearest a point, if there is one (SPEC §7.2).
     *
     * The Lille network's GBFS feed publishes no station address: it gives a
     * name and a postcode. The index, for its part, knows where the house
     * numbers are — better to use it than to leave the detail sheet silent.
     *
     * The search starts from the streets whose representative point lies within
     * a wide radius, then descends to their house numbers. That detour is
     * necessary: a street's point is its median, and on a kilometre-long
     * thoroughfare it can be very far from the point sought even though the
     * street runs right past it.
     *
     * @param point the place whose address we are after.
     * @return the address retained, or `null` if nothing near enough is known —
     *   better to show nothing than to announce the wrong street.
     */
    suspend fun nearestAddress(point: Coordinates): AddressResult? = withContext(ioDispatcher) {
        val index = try {
            open() ?: return@withContext null
        } catch (_: RuntimeException) {
            return@withContext null
        }
        try {
            index.nearestAddressTo(point)
        } catch (_: RuntimeException) {
            close()
            null
        }
    }

    private fun OpenIndex.nearestAddressTo(point: Coordinates): AddressResult? {
        val candidates = streets
            .filter { it.position.distanceInMetresTo(point) <= CANDIDATE_STREET_RADIUS_METRES }
            .sortedBy { it.position.distanceInMetresTo(point) }
            .take(MAX_REVERSE_CANDIDATES)
        if (candidates.isEmpty()) return null

        var best: Pair<SearchableStreet, KnownHouseNumber?>? = null
        var bestDistance = Double.MAX_VALUE
        for (street in candidates) {
            val numbers = readHouseNumbers(street.id, street.position)
            val nearestNumber = numbers.minByOrNull { it.position.distanceInMetresTo(point) }
            // A street without house numbers is not discarded: its point is
            // still an indication, provided it is genuinely close.
            val distance = nearestNumber?.position?.distanceInMetresTo(point)
                ?: street.position.distanceInMetresTo(point)
            if (distance < bestDistance) {
                bestDistance = distance
                best = street to nearestNumber
            }
        }

        val (street, number) = best ?: return null
        if (bestDistance > NEAREST_ADDRESS_LIMIT_METRES) return null

        // Beyond a few metres the number is no longer the point's own: saying
        // it would designate a neighbouring building. The street, though,
        // remains a sound indication — "near rue Chanzy" beats nothing, and
        // beats a wrong number.
        val isAtTheAddress = bestDistance <= NUMBERED_ADDRESS_LIMIT_METRES
        val row = readStreetRow(street.id)
        return AddressResult(
            streetId = street.id,
            houseNumber = number?.number?.takeIf { isAtTheAddress },
            houseNumberSuffix = if (isAtTheAddress) number?.suffix.orEmpty() else "",
            streetName = row.displayName,
            city = row.city,
            postcode = row.postcode,
            kind = row.kind,
            position = number?.position ?: street.position,
            precision = if (number == null || !isAtTheAddress) {
                PositionPrecision.StreetOnly
            } else {
                PositionPrecision.Exact
            },
            distanceInMetres = bestDistance,
        )
    }

    /**
     * Ranks the candidate streets, going through the fuzzy fallback if needed.
     *
     * The second stage is only attempted when the first returns little: it
     * scans the whole corpus, which is not warranted once the full-text index
     * has already answered.
     */
    private suspend fun rank(
        index: OpenIndex,
        query: AddressQuery,
        origin: Coordinates?,
        limit: Int,
    ): List<ScoredStreet> {
        val byPrefix = index.matchingFullText(query)
        val exact = rankStreets(byPrefix, query, normalizer.stopWords, origin, limit)
        if (exact.size >= MINIMUM_PREFIX_RESULTS || exact.size >= limit) return exact

        coroutineContext.ensureActive()
        return rankStreets(index.streets, query, normalizer.stopWords, origin, limit)
    }

    // -------------------------------------------------------- first stage --

    /**
     * The streets one of whose words starts with each of the words typed.
     *
     * Stop words are dropped from the query when something else remains: asking
     * the index for every street containing "de" would mean walking through
     * half of it to learn nothing. They do count in the ranking, however.
     */
    private fun OpenIndex.matchingFullText(query: AddressQuery): List<SearchableStreet> {
        val meaningful = query.terms.filterNot { it in normalizer.stopWords }
        val searched = meaningful.ifEmpty { query.terms }
        val expression = searched.joinToString(" ") { "$it*" }

        val matched = HashSet<Long>()
        database.rawQuery(
            "SELECT docid FROM $SEARCH_TABLE WHERE $SEARCH_TABLE MATCH ? LIMIT ?",
            arrayOf(expression, MAX_FULL_TEXT_ROWS.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) matched.add(cursor.getLong(0))
        }
        if (matched.isEmpty()) return emptyList()
        return streets.filter { it.id in matched }
    }

    // ------------------------------------------------------------ shaping --

    /** Completes a retained street with what only the display needs. */
    private fun OpenIndex.toResult(scored: ScoredStreet, query: AddressQuery): AddressResult {
        val row = readStreetRow(scored.street.id)
        val requested = query.houseNumber
        val resolved = if (requested == null) {
            null
        } else {
            resolveHouseNumber(
                requestedNumber = requested,
                requestedSuffix = query.houseNumberSuffix,
                knownNumbers = readHouseNumbers(scored.street.id, scored.street.position),
                streetPosition = scored.street.position,
            )
        }

        return AddressResult(
            streetId = scored.street.id,
            // A number the street cannot place is not shown: writing "12 rue
            // X" while pointing at the middle of the street would be a promise
            // the position does not keep.
            houseNumber = requested.takeIf {
                resolved != null && resolved.precision != PositionPrecision.StreetOnly
            },
            houseNumberSuffix = query.houseNumberSuffix,
            streetName = row.displayName,
            city = row.city,
            postcode = row.postcode,
            kind = row.kind,
            position = resolved?.coordinates ?: scored.street.position,
            precision = resolved?.precision ?: PositionPrecision.StreetOnly,
            distanceInMetres = scored.distanceInMetres,
        )
    }

    private class StreetRow(
        val displayName: String,
        val city: String,
        val postcode: String?,
        val kind: AddressEntryKind,
    )

    private fun OpenIndex.readStreetRow(streetId: Long): StreetRow = database.rawQuery(
        "SELECT display_name, city, former_city, postcode, kind FROM street WHERE id = ?",
        arrayOf(streetId.toString()),
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            error("street $streetId absent from the index")
        }
        StreetRow(
            // The absorbed municipality wins on display when there is one: an
            // address in Lomme is written "59160 Lomme", even though the Base
            // Adresse Nationale attaches it administratively to Lille. The
            // postcode that goes with it is Lomme's, for that matter.
            displayName = cursor.getString(0),
            city = cursor.getStringOrNull(2) ?: cursor.getString(1),
            postcode = cursor.getStringOrNull(3),
            kind = AddressEntryKind.fromCode(cursor.getInt(4)),
        )
    }

    /**
     * A street's house numbers, coordinates reconstituted.
     *
     * The whole street is read at once rather than only the immediate
     * neighbours: a street carries a few dozen numbers, and this single read
     * avoids reasoning in SQL about parity and bracketing.
     */
    private fun OpenIndex.readHouseNumbers(
        streetId: Long,
        streetPosition: Coordinates,
    ): List<KnownHouseNumber> = database.rawQuery(
        "SELECT number, suffix, delta_lat, delta_lon FROM house_number WHERE street_id = ?",
        arrayOf(streetId.toString()),
    ).use { cursor ->
        val numbers = ArrayList<KnownHouseNumber>(cursor.count)
        while (cursor.moveToNext()) {
            numbers.add(
                KnownHouseNumber(
                    number = cursor.getInt(0),
                    suffix = cursor.getString(1),
                    position = Coordinates(
                        latitude = streetPosition.latitude + cursor.getInt(2) / deltaScale,
                        longitude = streetPosition.longitude + cursor.getInt(3) / deltaScale,
                    ),
                ),
            )
        }
        numbers
    }

    // --------------------------------------------------------- opening --

    /**
     * Opens the index, or returns the one already open.
     *
     * The file is identified by its path, its size and its date: a re-import
     * from the storage screen must be picked up without restarting the
     * application. Since the path carries the city identifier, changing city
     * changes the signature too.
     *
     * @return `null` if the index is not installed.
     */
    private suspend fun open(): OpenIndex? {
        val file = datasetStore.fileOf(DatasetKind.Addresses) ?: run {
            close()
            return null
        }
        val signature = file.signature()
        opened?.let { if (it.signature == signature) return it }

        return openMutex.withLock {
            opened?.let { if (it.signature == signature) return@withLock it }
            opened?.database?.close()
            val database = SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
                // The default handler DELETES the file it judges corrupted. A
                // six-megabyte index does not vanish on a suspicion: the user
                // will import it again if need be.
                { /* delete nothing */ },
            )
            OpenIndex(
                signature = signature,
                database = database,
                streets = database.readCorpus(),
                deltaScale = database.readDeltaScale(),
            ).also { opened = it }
        }
    }

    /** Closes the open index, if there is one. */
    fun close() {
        opened?.database?.close()
        opened = null
    }

    private fun File.signature(): String = "$path:${length()}:${lastModified()}"

    /**
     * Loads into memory what the fuzzy fallback has to scan.
     *
     * The original names, the displayed municipalities and the postcodes stay
     * on disk: only the eight retained results ever use them, and keeping them
     * would triple this corpus's footprint.
     */
    private fun SQLiteDatabase.readCorpus(): List<SearchableStreet> = rawQuery(
        "SELECT id, normalized_type, normalized_name, normalized_city, " +
            "normalized_former_city, latitude, longitude FROM street",
        null,
    ).use { cursor ->
        val streets = ArrayList<SearchableStreet>(cursor.count)
        while (cursor.moveToNext()) {
            streets.add(
                SearchableStreet(
                    id = cursor.getLong(0),
                    normalizedType = cursor.getStringOrNull(1),
                    normalizedName = cursor.getString(2),
                    normalizedCity = cursor.getString(3),
                    normalizedFormerCity = cursor.getStringOrNull(4),
                    position = Coordinates(cursor.getDouble(5), cursor.getDouble(6)),
                ),
            )
        }
        streets
    }

    /**
     * The unit the house numbers' coordinates are stored in.
     *
     * Read from the file rather than fixed in the code: the generation script
     * decides it, and an index produced with another unit must stay readable.
     */
    private fun SQLiteDatabase.readDeltaScale(): Double = rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf("deltaScale"),
    ).use { cursor ->
        val stored = if (cursor.moveToFirst()) cursor.getString(0)?.toDoubleOrNull() else null
        stored?.takeIf { it > 0.0 } ?: DEFAULT_DELTA_SCALE
    }

    private fun Cursor.getStringOrNull(column: Int): String? =
        if (isNull(column)) null else getString(column)

    private companion object {
        const val SEARCH_TABLE = "street_search"

        /** How many results are shown by default: what fits above the keyboard. */
        const val DEFAULT_RESULT_COUNT = 8

        /**
         * Below this many results, the second stage fires.
         *
         * Three rows is the moment the user starts wondering whether their
         * street exists. That is where it must be fetched despite a typo, and
         * not before: the full scan costs tens of milliseconds that a fruitful
         * search has no business paying.
         */
        const val MINIMUM_PREFIX_RESULTS = 3

        /**
         * The ceiling on rows returned by the full-text index.
         *
         * A short query — "rue" — can match half the index. The ranking, for
         * its part, only shows eight rows: past this ceiling, reading more
         * would change nothing but the response time.
         */
        const val MAX_FULL_TEXT_ROWS = 400

        /** Hundred-thousandths of a degree, what the generation script writes. */
        const val DEFAULT_DELTA_SCALE = 100_000.0

        /**
         * The radius within which a street is examined for reverse geocoding.
         *
         * Wide on purpose: it is the street's *representative* point that is
         * compared, and the median of a kilometre-long thoroughfare can lie far
         * from a point it nonetheless runs past.
         */
        const val CANDIDATE_STREET_RADIUS_METRES = 900.0

        /** Past this, reading further streets' numbers teaches nothing more. */
        const val MAX_REVERSE_CANDIDATES = 40

        /**
         * The distance within which the number found really is the point's own.
         * Fifty metres is the width of a junction.
         *
         * Measured on the network's real stations: half of them sit within
         * fifteen metres of a known address, nine in ten within forty.
         */
        const val NUMBERED_ADDRESS_LIMIT_METRES = 50.0

        /**
         * The distance beyond which nothing at all is said.
         *
         * Between the two thresholds only the street is named, and as a
         * neighbourhood: a station standing in the middle of a roundabout has
         * no address, but saying which street it is near still helps.
         */
        const val NEAREST_ADDRESS_LIMIT_METRES = 150.0
    }
}
