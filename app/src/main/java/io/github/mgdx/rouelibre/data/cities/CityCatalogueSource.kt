package io.github.mgdx.rouelibre.data.cities

import android.content.Context
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

    /** The shipped configurations' identifiers, read on first use. */
    @Volatile
    private var knownIds: Set<String>? = null

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
     * Read once from the asset directory and kept: the list is fixed for the
     * lifetime of a build, and the city screen asks for it on every keystroke.
     */
    suspend fun knownCityIds(): Set<String> = withContext(ioDispatcher) {
        knownIds ?: readKnownCityIds().also { knownIds = it }
    }

    private fun readKnownCityIds(): Set<String> = try {
        context.assets.list(CITIES_ASSET_DIRECTORY).orEmpty()
            .filter { it.endsWith(CONFIGURATION_SUFFIX) }
            .map { it.removeSuffix(CONFIGURATION_SUFFIX) }
            .toSet()
    } catch (_: IOException) {
        // An unreadable asset directory is a manufacturing defect, and the
        // answer that costs the user least is "none is missing": the list then
        // behaves exactly as it did before this was written.
        emptySet()
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
        val document = try {
            context.assets.open("$CITIES_ASSET_DIRECTORY/$cityId.json")
                .bufferedReader()
                .use { it.readText() }
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
        const val CITIES_ASSET_DIRECTORY = "cities"
        const val CONFIGURATION_SUFFIX = ".json"
        const val CACHE_FILE_NAME = "catalogue.json"
    }
}
