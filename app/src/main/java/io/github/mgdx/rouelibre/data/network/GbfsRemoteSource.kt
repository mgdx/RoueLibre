package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.flatMap
import io.github.mgdx.rouelibre.core.gbfs.GbfsDiscovery
import io.github.mgdx.rouelibre.core.gbfs.GbfsFeedNames
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.gbfs.StationInformationFeed
import io.github.mgdx.rouelibre.core.gbfs.StationStatusFeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Récupère les flux GBFS sur le réseau.
 *
 * La seule requête qui part en usage courant est celle de ces flux (SPEC
 * §11.7). Rien n'est déclenché en arrière-plan : chaque appel vient d'une
 * action de l'utilisateur ou de l'affichage d'un écran.
 *
 * @property client client HTTP partagé, pour réutiliser les connexions.
 * @property parser analyseur des documents reçus.
 * @property userAgent identifie l'application et sa version, sans aucun
 *   identifiant propre à l'utilisateur ni à l'appareil (SPEC §4.4).
 * @property ioDispatcher contexte d'exécution des entrées-sorties.
 */
class GbfsRemoteSource(
    private val client: OkHttpClient,
    private val parser: GbfsParser,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Lit le document d'auto-découverte à l'URL donnée.
     *
     * @param discoveryUrl URL du `gbfs.json`, issue de la configuration de
     *   ville ou du réglage utilisateur.
     */
    suspend fun fetchDiscovery(discoveryUrl: String): Outcome<GbfsDiscovery> =
        fetchText(discoveryUrl).flatMap(parser::parseDiscovery)

    /**
     * Lit les données stables des stations.
     *
     * L'URL du flux vient toujours du document d'auto-découverte, jamais d'une
     * constante : c'est le principe de GBFS et cela protège d'un déplacement
     * de flux côté producteur (SPEC §4.1).
     */
    suspend fun fetchStationInformation(
        discovery: GbfsDiscovery,
    ): Outcome<StationInformationFeed> = discovery.urlOf(GbfsFeedNames.STATION_INFORMATION)
        .flatMap { fetchText(it) }
        .flatMap(parser::parseStationInformation)

    /** Lit l'état temps réel des stations. */
    suspend fun fetchStationStatus(discovery: GbfsDiscovery): Outcome<StationStatusFeed> =
        discovery.urlOf(GbfsFeedNames.STATION_STATUS)
            .flatMap { fetchText(it) }
            .flatMap(parser::parseStationStatus)

    /**
     * Exécute un GET et rend le corps de la réponse.
     *
     * Les pannes réseau sont converties en [DataError] plutôt que propagées :
     * l'appelant doit choisir un message, pas rattraper une exception
     * (SPEC §14).
     */
    private suspend fun fetchText(url: String): Outcome<String> = withContext(ioDispatcher) {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
        } catch (_: IllegalArgumentException) {
            // Une URL invalide vient forcément du réglage utilisateur : la
            // configuration livrée est vérifiée.
            return@withContext Outcome.Failure(
                DataError.MalformedResponse("URL invalide : $url"),
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Outcome.Failure(
                        DataError.ServerRefused(response.code),
                    )
                }
                val body = response.body.string()
                if (body.isBlank()) {
                    return@withContext Outcome.Failure(
                        DataError.MalformedResponse("réponse vide"),
                    )
                }
                Outcome.Success(body)
            }
        } catch (_: SocketTimeoutException) {
            Outcome.Failure(DataError.Timeout)
        } catch (_: UnknownHostException) {
            // Nom impossible à résoudre : en pratique, pas de connexion.
            Outcome.Failure(DataError.Offline)
        } catch (error: IOException) {
            Outcome.Failure(
                DataError.MalformedResponse(error.message ?: "échec réseau"),
            )
        }
    }
}
