package io.github.mgdx.rouelibre.data.routing

import android.content.Context
import btools.router.OsmNodeNamed
import btools.router.OsmTrack
import btools.router.RoutingContext
import btools.router.RoutingEngine
import io.github.mgdx.rouelibre.core.data.DatasetKind
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.routing.RouteLeg
import io.github.mgdx.rouelibre.core.routing.RouteResult
import io.github.mgdx.rouelibre.core.routing.RoutingFailure
import io.github.mgdx.rouelibre.core.routing.TravelMode
import io.github.mgdx.rouelibre.data.datasets.DatasetStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Calcule les itinéraires sur l'appareil, avec BRouter (SPEC.md §5).
 *
 * BRouter est intégré comme sous-module Git et construit avec l'application :
 * l'artefact Maven que le SPEC envisageait n'est publié nulle part. Le
 * sous-module est épinglé sur une étiquette, ce que réclame la reproductibilité
 * du build F-Droid.
 *
 * Rien ne sort sur le réseau : le graphe est un fichier installé sur
 * l'appareil, les profils sont dans l'APK.
 *
 * @property context sert à lire les profils dans les ressources.
 * @property datasets donne accès au graphe de routage installé.
 * @property computeDispatcher contexte d'exécution. Le calcul est purement
 *   processeur et peut durer plusieurs centaines de millisecondes.
 */
class OfflineRouter(
    private val context: Context,
    private val datasets: DatasetStore,
    private val computeDispatcher: CoroutineDispatcher,
) {

    /** Les profils n'ont besoin d'être déposés qu'une fois par exécution. */
    @Volatile
    private var profilesExtracted = false

    /**
     * Calcule un itinéraire entre deux points.
     *
     * @param from point de départ.
     * @param to point d'arrivée.
     * @param mode à pied ou à vélo.
     * @param timeoutMillis au-delà, le calcul est abandonné. Le SPEC §6 vise
     *   moins de trois secondes pour l'ensemble des itinéraires d'un trajet ;
     *   un seul segment qui s'éternise doit rendre la main.
     */
    suspend fun route(
        from: Coordinates,
        to: Coordinates,
        mode: TravelMode,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): RouteResult = withContext(computeDispatcher) {
        // Sans ville active, il n'y a pas de répertoire de segments : c'est le
        // même manque qu'un graphe absent, et le même message.
        val segments = datasets.directoryOf(DatasetKind.Routing)
            ?: return@withContext RouteResult.Failure(RoutingFailure.GraphMissing)
        if (segments.listFiles()?.none { it.name.endsWith(RD5_SUFFIX) } != false) {
            return@withContext RouteResult.Failure(RoutingFailure.GraphMissing)
        }

        val profile = prepareProfile(mode)
            ?: return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure("profil « ${mode.profileName} » illisible"),
            )

        val routingContext = RoutingContext().apply { localFunction = profile.absolutePath }
        val waypoints = listOf(waypointOf(from, "depart"), waypointOf(to, "arrivee"))

        val engine = try {
            RoutingEngine(null, null, segments, waypoints, routingContext)
        } catch (error: RuntimeException) {
            return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure(error.message ?: "moteur indisponible"),
            )
        }

        try {
            engine.doRun(timeoutMillis)
        } catch (error: RuntimeException) {
            return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure(error.message ?: "calcul interrompu"),
            )
        }

        val message = engine.errorMessage
        if (message != null) {
            return@withContext RouteResult.Failure(failureOf(message))
        }
        val track = engine.foundTrack
            ?: return@withContext RouteResult.Failure(RoutingFailure.NoRouteFound)

        RouteResult.Success(track.toLeg(mode))
    }

    /**
     * Traduit un message du moteur en cause exploitable.
     *
     * BRouter ne rend qu'une chaîne libre ; la reconnaître ici évite de la
     * montrer telle quelle, en anglais et pleine de vocabulaire interne.
     */
    private fun failureOf(message: String): RoutingFailure = when {
        message.contains("timeout", ignoreCase = true) -> RoutingFailure.Timeout
        // Le moteur emploie ces formulations quand le point tombe hors des
        // segments chargés, ou trop loin de toute voie du graphe.
        message.contains("position not mapped", ignoreCase = true) ||
            message.contains("out of", ignoreCase = true) -> RoutingFailure.OutsideCoverage
        message.contains("no track found", ignoreCase = true) ||
            message.contains("target island", ignoreCase = true) -> RoutingFailure.NoRouteFound
        else -> RoutingFailure.EngineFailure(message)
    }

    /**
     * Copie les profils depuis les ressources vers un répertoire lisible.
     *
     * BRouter ouvre ses profils par chemin de fichier, ce qu'une ressource
     * d'APK n'est pas. Le fichier de vocabulaire `lookups.dat` doit se trouver
     * dans le même répertoire que le profil : c'est là que le moteur va le
     * chercher, et sans lui aucun profil ne se compile.
     *
     * Le sous-répertoire `profiles2` n'est pas décoratif : le moteur remonte
     * de deux niveaux depuis le profil pour situer son répertoire de travail.
     */
    private fun prepareProfile(mode: TravelMode): File? {
        val directory = File(context.filesDir, PROFILE_DIRECTORY).apply { mkdirs() }
        if (!extractProfiles(directory)) return null
        return File(directory, "${mode.profileName}$PROFILE_SUFFIX").takeIf { it.isFile }
    }

    /**
     * Dépose vocabulaire et profils sur le disque, une fois par exécution.
     *
     * Les trois fichiers sont réécrits plutôt que comparés : ils pèsent
     * trente-six kilooctets à eux tous, et comparer leur taille demanderait
     * `openFd`, qui échoue sur un asset compressé — ce que l'outillage Android
     * fait de tout fichier texte. Une fois par lancement suffit largement, et
     * cela met à jour les profils après une mise à jour de l'application.
     */
    @Synchronized
    private fun extractProfiles(directory: File): Boolean {
        if (profilesExtracted) return true
        return try {
            for (name in listOf(LOOKUPS_NAME) + TravelMode.entries.map {
                "${it.profileName}$PROFILE_SUFFIX"
            }) {
                context.assets.open("$ASSET_DIRECTORY/$name").use { source ->
                    File(directory, name).outputStream().use { source.copyTo(it) }
                }
            }
            profilesExtracted = true
            true
        } catch (_: java.io.IOException) {
            false
        }
    }

    private fun waypointOf(point: Coordinates, name: String) = OsmNodeNamed().apply {
        this.name = name
        // BRouter travaille en microdegrés entiers, décalés pour rester
        // positifs. Le demi-microdegré ajouté est son arrondi, repris tel quel
        // pour que nos points tombent sur les mêmes nœuds que les siens.
        //
        // Écriture par les champs et lecture par les accesseurs : un point de
        // passage expose ses coordonnées en champs publics modifiables, un
        // point de tracé les garde privées derrière `getILat`.
        ilon = ((point.longitude + 180.0) * MICRODEGREES + 0.5).toInt()
        ilat = ((point.latitude + 90.0) * MICRODEGREES + 0.5).toInt()
    }

    private fun OsmTrack.toLeg(mode: TravelMode) = RouteLeg(
        mode = mode,
        distanceMetres = distance,
        duration = totalSeconds.seconds,
        ascentMetres = ascend,
        geometry = nodes.map { node ->
            Coordinates(
                latitude = node.iLat / MICRODEGREES - 90.0,
                longitude = node.iLon / MICRODEGREES - 180.0,
            )
        },
    )

    private companion object {
        const val ASSET_DIRECTORY = "routing"
        const val PROFILE_DIRECTORY = "routing/profiles2"
        const val LOOKUPS_NAME = "lookups.dat"
        const val PROFILE_SUFFIX = ".brf"
        const val RD5_SUFFIX = ".rd5"
        const val MICRODEGREES = 1_000_000.0
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
