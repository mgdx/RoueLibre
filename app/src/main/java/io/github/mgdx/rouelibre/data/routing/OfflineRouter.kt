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
 * Computes routes on the device, with BRouter (SPEC.md §5).
 *
 * BRouter is integrated as a Git submodule and built with the application: the
 * Maven artifact the specification envisaged is published nowhere. The
 * submodule is pinned to a tag, as the reproducibility of the F-Droid build
 * requires.
 *
 * Nothing goes out on the network: the graph is a file installed on the device,
 * the profiles are in the APK.
 *
 * @property context used to read the profiles from the resources.
 * @property datasets gives access to the installed routing graph.
 * @property computeDispatcher the execution context. The computation is purely
 *   processor-bound and can last several hundred milliseconds.
 */
class OfflineRouter(
    private val context: Context,
    private val datasets: DatasetStore,
    private val computeDispatcher: CoroutineDispatcher,
) {

    /** The profiles only need laying down once per run. */
    @Volatile
    private var profilesExtracted = false

    /**
     * Computes a route between two points.
     *
     * @param from the departure point.
     * @param to the arrival point.
     * @param mode on foot or by bike.
     * @param timeoutMillis past this, the computation is abandoned. SPEC §6
     *   aims for under three seconds for all the legs of a journey; a single
     *   leg that drags on must hand back control.
     */
    suspend fun route(
        from: Coordinates,
        to: Coordinates,
        mode: TravelMode,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): RouteResult = withContext(computeDispatcher) {
        // Without an active city there is no segment directory: that is the
        // same lack as a missing graph, and the same message.
        val segments = datasets.directoryOf(DatasetKind.Routing)
            ?: return@withContext RouteResult.Failure(RoutingFailure.GraphMissing)
        if (segments.listFiles()?.none { it.name.endsWith(RD5_SUFFIX) } != false) {
            return@withContext RouteResult.Failure(RoutingFailure.GraphMissing)
        }

        val profile = prepareProfile(mode)
            ?: return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure("profile \"${mode.profileName}\" unreadable"),
            )

        val routingContext = RoutingContext().apply { localFunction = profile.absolutePath }
        val waypoints = listOf(waypointOf(from, "origin"), waypointOf(to, "destination"))

        val engine = try {
            RoutingEngine(null, null, segments, waypoints, routingContext)
        } catch (error: RuntimeException) {
            return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure(error.message ?: "engine unavailable"),
            )
        }

        try {
            engine.doRun(timeoutMillis)
        } catch (error: RuntimeException) {
            return@withContext RouteResult.Failure(
                RoutingFailure.EngineFailure(error.message ?: "computation interrupted"),
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
     * Translates an engine message into a usable cause.
     *
     * BRouter only returns a free-form string; recognising it here avoids
     * showing it as such, in English and full of internal vocabulary.
     */
    private fun failureOf(message: String): RoutingFailure = when {
        message.contains("timeout", ignoreCase = true) -> RoutingFailure.Timeout
        // The engine uses these wordings when the point falls outside the
        // loaded segments, or too far from any way in the graph.
        message.contains("position not mapped", ignoreCase = true) ||
            message.contains("out of", ignoreCase = true) -> RoutingFailure.OutsideCoverage
        message.contains("no track found", ignoreCase = true) ||
            message.contains("target island", ignoreCase = true) -> RoutingFailure.NoRouteFound
        else -> RoutingFailure.EngineFailure(message)
    }

    /**
     * Copies the profiles from the resources into a readable directory.
     *
     * BRouter opens its profiles by file path, which an APK resource is not.
     * The `lookups.dat` vocabulary file must sit in the same directory as the
     * profile: that is where the engine looks for it, and without it no profile
     * compiles.
     *
     * The `profiles2` subdirectory is not decorative: the engine walks two
     * levels up from the profile to locate its working directory.
     */
    private fun prepareProfile(mode: TravelMode): File? {
        val directory = File(context.filesDir, PROFILE_DIRECTORY).apply { mkdirs() }
        if (!extractProfiles(directory)) return null
        return File(directory, "${mode.profileName}$PROFILE_SUFFIX").takeIf { it.isFile }
    }

    /**
     * Lays the vocabulary and the profiles on disk, once per run.
     *
     * The three files are rewritten rather than compared: together they weigh
     * thirty-six kilobytes, and comparing their size would need `openFd`, which
     * fails on a compressed asset — which is what the Android tooling makes of
     * any text file. Once per launch is amply enough, and it refreshes the
     * profiles after an application update.
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
        // BRouter works in integer microdegrees, offset to stay positive. The
        // half microdegree added is its own rounding, taken as is so that our
        // points land on the same nodes as its own.
        //
        // Written through the fields and read through the accessors: a waypoint
        // exposes its coordinates as mutable public fields, whereas a track
        // node keeps them private behind `getILat`.
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
