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
     * @param timeoutMillis past this, the computation is abandoned. It is a
     *   safety net against a leg that never converges, not a response-time
     *   budget: SPEC §6 asks that the number of computations be bounded, no
     *   longer that the answer come within three seconds.
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
            RoutingEngine(null, null, segments, waypoints, routingContext).apply {
                // Left to itself the engine prints the whole GPX of every track
                // it finds on `System.out` — every point, in latitude, longitude
                // and altitude — which on Android is the logcat: a buffer shared
                // with the rest of the system and picked up again by
                // `adb bugreport`. That is a recording of the journeys computed,
                // which SPEC §2 constraint C3 forbids keeping, and which the "about"
                // screen promises does not happen. The upstream field is spelt
                // `quite`; it means "be quiet", and it silences nothing else — the
                // track is still returned through `foundTrack`.
                quite = true
            }
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
        // loaded segments, or too far from any way in the graph. A point
        // further out never even reaches that stage: no segment file covers it
        // at all, and what comes back names the tile it went looking for —
        // "datafile E0_N50.rd5 not found". That is not a graph that is
        // missing, which is answered above by looking at the directory itself;
        // it is a point the downloaded graph was never cut to reach, and
        // reading it as anything else made the application answer a user
        // standing two hundred kilometres away that no path joined the two
        // points.
        message.contains("position not mapped", ignoreCase = true) ||
            message.contains("out of", ignoreCase = true) ||
            (
                message.contains("datafile", ignoreCase = true) &&
                    message.contains("not found", ignoreCase = true)
                ) -> RoutingFailure.OutsideCoverage
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
     * The four files are rewritten rather than compared: together they weigh
     * some forty-seven kilobytes, and comparing their size would need `openFd`,
     * which fails on a compressed asset — which is what the Android tooling
     * makes of any text file. Once per launch is amply enough, and it refreshes
     * the profiles after an application update.
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
        // `ascend`, the engine's filtered sum of everything the leg goes up,
        // and not `plainAscend`, which is the difference between the two ends
        // and says nothing about the hills between them. What the filter
        // forgives, and why the figure cannot be read off the drawn profile,
        // is written where the property is declared.
        ascentMetres = ascend,
        geometry = nodes.map { node ->
            Coordinates(
                latitude = node.iLat / MICRODEGREES - 90.0,
                longitude = node.iLon / MICRODEGREES - 180.0,
            )
        },
        // The engine reads elevation in quarter-metres, and marks a node whose
        // graph carries none with the smallest short there is. Passed on as it
        // comes, unfiltered: what it draws is the shape of the ground, where
        // `ascend` above is the engine's own filtered sum (SPEC §7.4.1).
        elevationsMetres = nodes.map { node ->
            node.sElev.takeIf { it != Short.MIN_VALUE }?.let { it / ELEVATION_STEPS_PER_METRE }
        },
    )

    private companion object {
        const val ASSET_DIRECTORY = "routing"
        const val PROFILE_DIRECTORY = "routing/profiles2"
        const val LOOKUPS_NAME = "lookups.dat"
        const val PROFILE_SUFFIX = ".brf"
        const val RD5_SUFFIX = ".rd5"
        const val MICRODEGREES = 1_000_000.0

        /** The engine holds an elevation as a count of quarter-metres. */
        const val ELEVATION_STEPS_PER_METRE = 4.0

        /**
         * How long a single leg may take before it is abandoned.
         *
         * One minute — a safety net against a leg that never converges, not a
         * response-time budget. The earlier five seconds cut off legs that were
         * merely long: a twenty-six-kilometre ride across the Paris
         * conurbation, which the engine traces in two seconds on a desktop,
         * still had not finished after ten on a Fairphone 3 computing six of
         * them at once — and every one of them being cut short left the user
         * with "no route", accusing the map of a hole it does not have. The
         * limit must be set by the slowest device we mean to serve, not by the
         * fastest; a wait the user can see through is worth more than a wrong
         * answer given quickly.
         */
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
