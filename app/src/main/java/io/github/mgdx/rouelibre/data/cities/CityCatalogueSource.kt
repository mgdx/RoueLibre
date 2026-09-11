package io.github.mgdx.rouelibre.data.cities

import android.content.Context
import android.content.res.AssetManager
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import io.github.mgdx.rouelibre.core.config.CityCatalogueReader
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.CityConfigurationReader
import io.github.mgdx.rouelibre.core.config.isUsableCityId
import io.github.mgdx.rouelibre.data.network.MAXIMUM_DOCUMENT_BYTES
import io.github.mgdx.rouelibre.data.network.textUpTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * The catalogue of cities served, and the configurations that go with them.
 *
 * Two sources, in this order: the downloaded catalogue if there is one, the one
 * shipped in the APK otherwise. The first allows adding a city without
 * publishing a release; the second guarantees that a first launch without a
 * network shows something rather than an empty list.
 *
 * Nothing is downloaded of its own accord: [refresh] is only called by a
 * screen, on an explicit action or when the city list is opened.
 */
class CityCatalogueSource(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    /** Where the shipped configurations lie in `cities.json`, read on first use. */
    @Volatile
    private var index: Map<String, List<Int>>? = null

    /**
     * The catalogue to use right now, without network access.
     *
     * A downloaded catalogue that cannot be read — a truncated file, the format
     * of a later version — is ignored in favour of the APK's, rather than
     * making the application unusable.
     *
     * @throws IllegalStateException if even the shipped catalogue is
     *   unreadable. That is not a user situation but a manufacturing defect.
     */
    suspend fun catalogue(): CityCatalogue = withContext(ioDispatcher) {
        downloadedCatalogue() ?: embeddedCatalogue()
    }

    /**
     * Downloads the catalogue again and keeps it if it is readable.
     *
     * **The address comes from the catalogue shipped in the APK, and from
     * nowhere else.** It used to be read from the catalogue in force, which is
     * the downloaded one as soon as there is one: a single hostile document
     * then named where every later catalogue would be fetched from, and the
     * cache that carried it outlives an application update — a server
     * compromised once kept the client for good. What a downloaded catalogue
     * says still decides the **list of cities**; it no longer decides where the
     * next one comes from. Moving the publication address therefore takes a
     * release, as it already does for every other address of the project
     * (SPEC §15).
     *
     * @return the catalogue that just arrived, or the reason nothing did. The
     *   caller keeps showing the one in force in that case.
     */
    suspend fun refresh(): Outcome<CityCatalogue> = withContext(ioDispatcher) {
        val url = embeddedCatalogue().catalogueUrl
            ?: return@withContext Outcome.Failure(
                DataError.MalformedResponse("no publication address in the shipped catalogue"),
            )
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()
        } catch (_: IllegalArgumentException) {
            // The shipped catalogue is produced by tools/build_catalogue.py and
            // verified, so this is a manufacturing defect rather than a user
            // situation. Said rather than thrown all the same: an address the
            // client refuses must never be what closes the application.
            return@withContext Outcome.Failure(DataError.MalformedResponse("invalid URL: $url"))
        }
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Outcome.Failure(DataError.ServerRefused(response.code))
                }
                val document = response.body.textUpTo()
                    ?: return@withContext Outcome.Failure(
                        DataError.MalformedResponse(
                            "catalogue larger than $MAXIMUM_DOCUMENT_BYTES bytes",
                        ),
                    )
                when (val outcome = CityCatalogueReader.read(document)) {
                    is Outcome.Failure -> outcome
                    is Outcome.Success -> {
                        // Written only after a successful parse: an invalid
                        // cache file would condemn every later launch to fall
                        // back on the APK's without saying so.
                        writeCache(document)
                        outcome
                    }
                }
            }
        } catch (error: SocketTimeoutException) {
            Outcome.Failure(DataError.Timeout)
        } catch (_: IOException) {
            Outcome.Failure(DataError.Offline)
        }
    }

    /**
     * The cities this build carries a configuration for.
     *
     * The catalogue and the configurations do not travel together: the first is
     * refreshed over the network, the second ships in the APK. A catalogue more
     * recent than the application therefore names cities it cannot serve, and
     * this is what lets the list say so instead of offering a city that would
     * come up empty (SPEC §15).
     *
     * Read once from the index and kept: the list is fixed for the lifetime of
     * a build, and the city screen asks for it on every keystroke.
     */
    suspend fun knownCityIds(): Set<String> = withContext(ioDispatcher) {
        configurationIndex().keys
    }

    /**
     * Where each city's configuration begins in `cities.json`, and how long it
     * is.
     *
     * The configurations ship as one stream rather than as one file each: three
     * hundred and thirty-seven files of the same shape, each compressed on its
     * own in the APK, took three and a half times the room the same bytes take
     * together. This index is what buys back the direct access that a folder of
     * files gave for free, and it is the only one of the two files ever read
     * whole.
     */
    private fun configurationIndex(): Map<String, List<Int>> =
        index ?: readConfigurationIndex().also { index = it }

    private fun readConfigurationIndex(): Map<String, List<Int>> = try {
        val document = context.assets.open(CITIES_INDEX_ASSET)
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString<Map<String, List<Int>>>(document)
    } catch (_: IOException) {
        // An unreadable asset is a manufacturing defect, and the answer that
        // costs the user least is "no city is known": the list then says so
        // instead of offering cities whose configuration cannot be read either.
        emptyMap()
    } catch (_: SerializationException) {
        emptyMap()
    }

    /**
     * The complete configuration of the city [cityId].
     *
     * The catalogue locates a city and announces the weight of its data; the
     * configuration carries the rest — attribution, framing, format versions.
     * It ships in the APK, one per city known at publication time.
     *
     * @return `null` if this version of the application does not know the city.
     *   A downloaded catalogue may name more recent ones: the interface must
     *   then invite an update, not fail without explanation.
     */
    suspend fun configuration(cityId: String): CityConfiguration? = withContext(ioDispatcher) {
        // The identifier names an asset here, as it names a directory in the
        // data store. It is read back from the settings, where an older version
        // may have written one the catalogue reader would refuse today.
        if (!isUsableCityId(cityId)) return@withContext null
        val slice = configurationIndex()[cityId] ?: return@withContext null
        val document = try {
            readConfigurationSlice(offset = slice[0], length = slice[1])
        } catch (_: IOException) {
            return@withContext null
        }
        when (val outcome = CityConfigurationReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error("Configuration \"$cityId\" unreadable in the APK")
        }
    }

    private fun downloadedCatalogue(): CityCatalogue? {
        val file = cacheFile
        if (!file.isFile) return null
        val document = try {
            file.readText()
        } catch (_: IOException) {
            return null
        }
        return (CityCatalogueReader.read(document) as? Outcome.Success)?.value
    }

    private fun embeddedCatalogue(): CityCatalogue {
        val document = context.assets.open(CATALOGUE_ASSET)
            .bufferedReader()
            .use { it.readText() }
        return when (val outcome = CityCatalogueReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error("Catalogue unreadable in the APK: ${outcome.error}")
        }
    }

    /**
     * The [length] bytes of `cities.json` that start at [offset].
     *
     * Read by hand rather than through a reader: a stream gives no guarantee
     * of skipping or reading as far as asked in one go, and a configuration
     * read short would fail to parse for a reason that has nothing to do with
     * its contents. Both loops run until the count is met or the stream ends.
     */
    private fun readConfigurationSlice(offset: Int, length: Int): String =
        context.assets.open(CITIES_ASSET, AssetManager.ACCESS_RANDOM).use { stream ->
            var skipped = 0L
            while (skipped < offset) {
                val step = stream.skip(offset - skipped)
                if (step <= 0L) throw IOException("Cities asset shorter than its index")
                skipped += step
            }
            val bytes = ByteArray(length)
            var filled = 0
            while (filled < length) {
                val read = stream.read(bytes, filled, length - filled)
                if (read < 0) throw IOException("Cities asset shorter than its index")
                filled += read
            }
            String(bytes, Charsets.UTF_8)
        }

    private fun writeCache(document: String) {
        val staging = File(context.filesDir, "$CACHE_FILE_NAME.partial")
        try {
            staging.writeText(document)
            // Atomic rename: a cut in the middle of the write leaves the
            // previous catalogue intact rather than a half-written file.
            if (!staging.renameTo(cacheFile)) staging.delete()
        } catch (_: IOException) {
            staging.delete()
        }
    }

    private companion object {
        const val CATALOGUE_ASSET = "catalogue.json"
        const val CITIES_ASSET = "cities.json"
        const val CITIES_INDEX_ASSET = "cities-index.json"
        const val CACHE_FILE_NAME = "catalogue.json"

        /** Reads the index alone, whose shape this file owns. */
        val json = Json
    }
}
