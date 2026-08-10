package io.github.mgdx.rouelibre.data.cities

import android.content.Context
import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import io.github.mgdx.rouelibre.core.config.CityCatalogueReader
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.CityConfigurationReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Le catalogue des villes servies, et les configurations qui vont avec.
 *
 * Deux sources, dans cet ordre : le catalogue téléchargé s'il y en a un, celui
 * livré dans l'APK sinon. Le premier permet d'ajouter une ville sans publier de
 * version ; le second garantit qu'un premier lancement sans réseau montre
 * quelque chose plutôt qu'une liste vide.
 *
 * Rien n'est téléchargé de soi-même : [refresh] n'est appelé que par un écran,
 * sur action explicite ou à l'ouverture de la liste des villes.
 */
class CityCatalogueSource(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    /**
     * Le catalogue à utiliser maintenant, sans accès réseau.
     *
     * Un catalogue téléchargé illisible — fichier tronqué, format d'une version
     * ultérieure — est ignoré au profit de celui de l'APK plutôt que de rendre
     * l'application inutilisable.
     *
     * @throws IllegalStateException si même le catalogue livré est illisible.
     *   Ce n'est pas une situation utilisateur mais un défaut de fabrication.
     */
    suspend fun catalogue(): CityCatalogue = withContext(ioDispatcher) {
        downloadedCatalogue() ?: embeddedCatalogue()
    }

    /**
     * Retélécharge le catalogue et le conserve s'il est lisible.
     *
     * @param url adresse du catalogue publié.
     * @return le catalogue en vigueur après l'opération — celui qui vient
     *   d'arriver, ou celui d'avant si le téléchargement a échoué.
     */
    suspend fun refresh(url: String): Outcome<CityCatalogue> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Outcome.Failure(DataError.ServerRefused(response.code))
                }
                val document = response.body.string()
                when (val outcome = CityCatalogueReader.read(document)) {
                    is Outcome.Failure -> outcome
                    is Outcome.Success -> {
                        // Écrit seulement après analyse réussie : un fichier de
                        // cache invalide condamnerait tous les lancements
                        // suivants à retomber sur celui de l'APK sans le dire.
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
     * La configuration complète de la ville [cityId].
     *
     * Le catalogue situe une ville et annonce le poids de ses données ; la
     * configuration porte le reste — attribution, cadrage, versions de format.
     * Elle est livrée dans l'APK, une par ville connue à la publication.
     *
     * @return `null` si cette version de l'application ne connaît pas la ville.
     *   Un catalogue téléchargé peut en citer de plus récentes : l'interface
     *   doit alors inviter à mettre à jour, pas échouer sans explication.
     */
    suspend fun configuration(cityId: String): CityConfiguration? = withContext(ioDispatcher) {
        val document = try {
            context.assets.open("$CITIES_ASSET_DIRECTORY/$cityId.json")
                .bufferedReader()
                .use { it.readText() }
        } catch (_: IOException) {
            return@withContext null
        }
        when (val outcome = CityConfigurationReader.read(document)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> error("Configuration « $cityId » illisible dans l'APK")
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
            is Outcome.Failure -> error("Catalogue illisible dans l'APK : ${outcome.error}")
        }
    }

    private fun writeCache(document: String) {
        val staging = File(context.filesDir, "$CACHE_FILE_NAME.partiel")
        try {
            staging.writeText(document)
            // Renommage atomique : une coupure au milieu de l'écriture laisse
            // le catalogue précédent intact plutôt qu'un fichier à moitié écrit.
            if (!staging.renameTo(cacheFile)) staging.delete()
        } catch (_: IOException) {
            staging.delete()
        }
    }

    private companion object {
        const val CATALOGUE_ASSET = "catalogue.json"
        const val CITIES_ASSET_DIRECTORY = "cities"
        const val CACHE_FILE_NAME = "catalogue.json"
    }
}
