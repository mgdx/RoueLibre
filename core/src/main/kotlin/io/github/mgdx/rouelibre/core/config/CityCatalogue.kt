package io.github.mgdx.rouelibre.core.config

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The cities the application knows how to serve.
 *
 * An index of a few kilobytes, derived from the city configurations by
 * `tools/build_catalogue.py`. It fits in memory, downloads in one request, and
 * is enough to answer the two questions of a first launch: which cities exist,
 * and which one matches where we are.
 *
 * The catalogue does not replace a city's configuration: it references it. The
 * complete settings — framing, attribution, format versions — arrive with the
 * downloaded data (SPEC §15).
 */
public data class CityCatalogue(
    public val catalogueVersion: Int,
    /** When it was produced, as published. Used to date what is shown. */
    public val generatedAt: String?,
    /**
     * The address the catalogue re-downloads itself from.
     *
     * Carried by the document rather than written in the code: that is what
     * lets a derivative publish its own catalogue by regenerating it, without
     * touching the Kotlin (SPEC §15). `null` on a catalogue produced without a
     * publication address; there is then nothing to refresh.
     */
    public val catalogueUrl: String?,
    public val cities: List<CityEntry>,
) {

    /** The city with identifier [id], or `null` if the catalogue ignores it. */
    public fun entry(id: String): CityEntry? = cities.firstOrNull { it.id == id }

    /**
     * The cities ranked by proximity to [position].
     *
     * Those whose box contains the point come first, the tightest at the head:
     * two networks can overlap, and the more plausible one is then the one
     * whose centre we are nearest. The others follow, by growing distance to
     * their box.
     */
    public fun rank(position: Coordinates): List<CityEntry> = cities
        .sortedWith(
            compareBy(
                { it.boundingBox.distanceOutsideInMetres(position) },
                { it.centre.distanceInMetresTo(position) },
                // On a tie, a stable order rather than the file's own.
                { it.displayName },
            ),
        )

    /**
     * The city to propose for [position], if there is a plausible one.
     *
     * Proposing the nearest city whatever happens would give Lille to somebody
     * standing in Marseille: beyond [SUGGESTION_RADIUS_METRES] from the nearest
     * network, it is better to propose nothing and let the user pick from the
     * list.
     */
    public fun suggestionFor(position: Coordinates): CityEntry? =
        rank(position).firstOrNull { entry ->
            entry.boundingBox.distanceOutsideInMetres(position) <= SUGGESTION_RADIUS_METRES
        }

    public companion object {
        /**
         * The distance beyond which a city is no longer proposed, in metres.
         *
         * Fifty kilometres: enough to cover a metropolis's outer ring — one
         * lives in Seclin and takes the V'lille in Lille — without reaching the
         * next conurbation, which would then have its own network and its own
         * entry in the catalogue.
         */
        public const val SUGGESTION_RADIUS_METRES: Double = 50_000.0
    }
}

/**
 * A city of the catalogue.
 *
 * It carries only what allows presenting and locating it. Everything else is in
 * its configuration, delivered with its data.
 */
public data class CityEntry(
    /** The network's identifier, which also names its data directory. */
    public val id: String,
    public val displayName: String,
    /**
     * The conurbation the network runs in, when its configuration names it.
     *
     * A network name locates nothing for whoever has never been there:
     * "Vélo'v" is Lyon, and only the two together say so. `null` on a
     * catalogue produced before this field existed — the interface then shows
     * the network name alone rather than an empty dash.
     */
    public val mainCity: String?,
    public val operator: String,
    /** ISO 3166-1 alpha-2 country code, used to group the list. */
    public val country: String,
    public val boundingBox: BoundingBox,
    public val centre: Coordinates,
    public val stationCount: Int?,
    public val gbfsDiscoveryUrl: String,
    public val manifestUrl: String,
    /**
     * The total weight of the offline data, in bytes, or `null` if it has not
     * been produced yet.
     *
     * SPEC §11.9 requires the size to be announced before downloading. A city
     * with no known size is a city one cannot install: it stays listed, but the
     * interface must say so.
     */
    public val dataSizeBytes: Long?,
    public val releaseTag: String?,
) {
    /** True if this city's data is published and downloadable. */
    public val isAvailable: Boolean
        get() = dataSizeBytes != null && dataSizeBytes > 0
}

/**
 * Says whether [id] may be used as a network identifier.
 *
 * This identifier is not a label: it **names a directory** in the application's
 * private storage, one per city (see the data store), and it is read back from
 * the settings at every launch. The catalogue that carries it is downloaded,
 * therefore produced elsewhere: a `..` in it would make the whole of a city's
 * storage — creation, listing, and the recursive deletion of "delete this
 * city's data" — bear on a directory nobody chose.
 *
 * The alphabet is the one `tools/add_city.py` guarantees. Its `slug()` folds
 * every name to lowercase unaccented ASCII and joins the pieces with hyphens,
 * precisely because the result "names a configuration file, the directory the
 * city's data is generated into, and the manifest published for it". This
 * function is the reading side of that same promise.
 *
 * The length limit is not a security matter — no separator gets through
 * whatever the length — but a file system refuses a name past its own limit,
 * and failing here says why while failing there would not.
 */
public fun isUsableCityId(id: String): Boolean = id.isNotEmpty() &&
    id.length <= MAXIMUM_CITY_ID_LENGTH &&
    id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

/**
 * The longest identifier accepted.
 *
 * The longest the catalogue carries is forty-seven characters; this leaves
 * room without approaching the limit of any file system.
 */
private const val MAXIMUM_CITY_ID_LENGTH = 64

/** Reads a catalogue of cities. */
public object CityCatalogueReader {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the contents of a catalogue.
     *
     * An entry whose box is absurd is dropped without failing the rest: the
     * catalogue is downloaded, therefore produced elsewhere and later than the
     * application reading it. An entirely empty catalogue, on the other hand,
     * is a failure — there would be nothing to choose.
     *
     * @param document the raw contents of the `catalogue.json` file.
     */
    public fun read(document: String): Outcome<CityCatalogue> = try {
        val parsed = json.decodeFromString(CityCatalogueDocument.serializer(), document)
        val cities = parsed.cities.mapNotNull { it.toDomainOrNull() }
        if (cities.isEmpty()) {
            Outcome.Failure(DataError.MalformedResponse("catalogue with no readable city"))
        } else {
            Outcome.Success(
                CityCatalogue(
                    catalogueVersion = parsed.catalogueVersion,
                    generatedAt = parsed.generatedAt,
                    catalogueUrl = parsed.catalogueUrl?.takeIf { it.isNotBlank() },
                    cities = cities,
                ),
            )
        }
    } catch (error: SerializationException) {
        Outcome.Failure(
            DataError.MalformedResponse(error.message ?: "unreadable city catalogue"),
        )
    }
}

@Serializable
private data class CityCatalogueDocument(
    val catalogueVersion: Int = 1,
    val generatedAt: String? = null,
    val catalogueUrl: String? = null,
    val cities: List<CityEntryDocument> = emptyList(),
)

@Serializable
private data class CityEntryDocument(
    val id: String,
    val displayName: String,
    val mainCity: String? = null,
    val operator: String = "",
    val country: String = "FR",
    val boundingBox: CatalogueBoundingBoxDocument? = null,
    val centreLatitude: Double? = null,
    val centreLongitude: Double? = null,
    val stationCount: Int? = null,
    val gbfsDiscoveryUrl: String,
    val manifestUrl: String,
    val dataSizeBytes: Long? = null,
    val releaseTag: String? = null,
) {
    fun toDomainOrNull(): CityEntry? {
        val box = boundingBox?.toDomainOrNull() ?: return null
        // The default centring may be missing: the box's own centre will do.
        val latitude = centreLatitude
        val longitude = centreLongitude
        val centre = if (latitude != null && longitude != null) {
            runCatching { Coordinates(latitude, longitude) }.getOrNull()
        } else {
            null
        }
        return CityEntry(
            // Dropped rather than fatal, like an absurd bounding box above: one
            // unusable entry in a downloaded catalogue must not take the three
            // hundred others with it.
            id = id.takeIf(::isUsableCityId) ?: return null,
            displayName = displayName.takeIf { it.isNotBlank() } ?: return null,
            mainCity = mainCity?.takeIf { it.isNotBlank() },
            operator = operator,
            country = country,
            boundingBox = box,
            centre = centre ?: box.centre,
            stationCount = stationCount,
            gbfsDiscoveryUrl = gbfsDiscoveryUrl,
            manifestUrl = manifestUrl,
            dataSizeBytes = dataSizeBytes,
            releaseTag = releaseTag,
        )
    }
}

@Serializable
private data class CatalogueBoundingBoxDocument(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    // `BoundingBox` refuses an inverted rectangle; here that drops the entry
    // instead of bringing down the reading of the whole catalogue.
    fun toDomainOrNull(): BoundingBox? = runCatching { BoundingBox(south, west, north, east) }
        .getOrNull()
        ?.takeIf { it.isUsable }
}
