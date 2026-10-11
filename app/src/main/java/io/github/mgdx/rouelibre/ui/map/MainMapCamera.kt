package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import android.os.Bundle
import androidx.core.view.doOnLayout
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.ui.prefersReducedMotion
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * The camera of the main map: where it opens, where it is sent, and what it
 * remembers across a rebuild of the screen (SPEC §7.1).
 *
 * The choices themselves are the pure functions of `MainMapFraming.kt`; this
 * carries them out on a map that comes and goes with its view. It lives as
 * long as the fragment, because what it remembers is precisely what the view
 * loses.
 *
 * @param map the map, once it is ready and until its view is destroyed.
 * @param isReady whether a style is in place: a move ordered before then
 *   waits for it.
 * @param context the screen's context, for whether the device asks for
 *   reduced animations.
 */
internal class MainMapCamera(
    private val map: () -> MapLibreMap?,
    private val isReady: () -> Boolean,
    private val context: () -> Context,
) {

    /**
     * The framing to restore when the view is rebuilt.
     *
     * Going through the list, the storage screen or the search destroys the
     * map's view without destroying the fragment: without this, coming back
     * brought the opening framing, and the user lost the place they were
     * looking at.
     *
     * A turn of the phone destroys the fragment as well, and this field with
     * it: it is therefore saved in the state too, or the map came back up on
     * the position of the user rather than on the address they had just gone
     * looking for.
     */
    private var lastCamera: CameraPosition? = null

    /**
     * A point the camera must reach as soon as the map exists.
     *
     * The chosen address is returned by the search screen **before** the map is
     * rebuilt: the move therefore has to wait.
     */
    private var pendingCameraTarget: LatLng? = null

    /**
     * The limits that pen the camera inside the served area.
     *
     * Kept to hand because a move the screen orders — an address found, a
     * cluster opened — has to stand them down while it runs, or they cut it
     * short (see [moveTo]).
     */
    private var servedAreaCamera: ServedAreaCamera? = null

    /**
     * Frames a map that has just been given a city, and pens it there.
     *
     * The framing remembered from before the view was rebuilt, if it suits
     * the city; otherwise the opening one.
     *
     * @param positionToOpenOn the position the system already holds, inside
     *   the city — asked for only when nothing remembered suits, since that is
     *   the only case it is used in.
     */
    fun open(
        view: MapView,
        map: MapLibreMap,
        configuration: CityConfiguration,
        positionToOpenOn: () -> Coordinates?,
    ) {
        val remembered = lastCamera?.takeIf {
            rememberedFramingSuits(it.target?.toCoordinates(), configuration.boundingBox)
        }
        map.cameraPosition = remembered
            ?: openingFraming(positionToOpenOn(), configuration.map).toCameraPosition()
        holdOverServedArea(view, map, configuration)
    }

    /**
     * Brings the map onto a point.
     *
     * The move is animated so one understands where one came from — unless the
     * device asks for reduced animations, in which case the map jumps straight
     * to the destination (SPEC §7).
     *
     * The limits over the served area stand down for its length: laying one
     * down cancels the move in flight, and this one was dying to the limits
     * being measured a few milliseconds after it started. They are taken up
     * again on arrival, where they are measured on the framing that landed.
     */
    fun moveTo(target: LatLng, zoom: Double = PICKED_PLACE_ZOOM) {
        val map = map()
        if (map == null || !isReady()) {
            pendingCameraTarget = target
            return
        }
        val update = CameraUpdateFactory.newLatLngZoom(target, zoom)
        val servedArea = servedAreaCamera
        servedArea?.releaseForMove()
        if (context().prefersReducedMotion()) {
            map.moveCamera(update)
            servedArea?.holdAgain()
            return
        }
        map.animateCamera(
            update,
            CAMERA_ANIMATION_MILLIS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = holdOverServedAreaAgain(servedArea)

                // A move cut short — by a finger on the map, or by another
                // move — leaves the camera somewhere legitimate all the same:
                // what matters is that the limits come back, wherever it is.
                override fun onCancel() = holdOverServedAreaAgain(servedArea)
            },
        )
    }

    /**
     * Reaches an address chosen while the map did not exist: once the style
     * is ready is when it can be reached.
     */
    fun reachPendingTarget() {
        val target = pendingCameraTarget ?: return
        pendingCameraTarget = null
        moveTo(target)
    }

    /**
     * Takes back the framing the screen had before the phone was turned.
     *
     * Only the target and the zoom. The map can be turned and tilted now
     * (SPEC §7.1), and neither the bearing nor the pitch is saved all the
     * same. A screen rebuilt from this bundle is one the phone was turned
     * under, or one the system had thrown away in the meantime: a map found
     * lying askew, or leaning, by somebody who does not remember setting it
     * that way reads as a map that has broken and not as one they set. It
     * comes back facing north and flat, and the compass goes with it.
     */
    fun restore(savedInstanceState: Bundle?) {
        val saved = savedInstanceState ?: return
        if (!saved.containsKey(STATE_CAMERA_LATITUDE)) return
        lastCamera = CameraPosition.Builder()
            .target(
                LatLng(
                    saved.getDouble(STATE_CAMERA_LATITUDE),
                    saved.getDouble(STATE_CAMERA_LONGITUDE),
                ),
            )
            .zoom(saved.getDouble(STATE_CAMERA_ZOOM))
            .build()
    }

    /**
     * Writes where the map was looking, so that turning the phone shows the
     * same place in the other shape rather than starting the screen over.
     */
    fun save(outState: Bundle) {
        val camera = map()?.cameraPosition ?: lastCamera
        camera?.target?.let { target ->
            outState.putDouble(STATE_CAMERA_LATITUDE, target.latitude)
            outState.putDouble(STATE_CAMERA_LONGITUDE, target.longitude)
            outState.putDouble(STATE_CAMERA_ZOOM, camera.zoom)
        }
    }

    /**
     * Remembers the framing of a view about to go, and lets go of the limits
     * measured on it.
     */
    fun rememberAndRelease() {
        lastCamera = map()?.cameraPosition
        servedAreaCamera = null
    }

    /**
     * Pens the camera inside the served area (SPEC §7.1).
     *
     * The city without a bounding box is the city whose configuration does not
     * declare one: there is then nothing to hold the camera to, and the map
     * stays as free as it was.
     *
     * The limits are read off the visible region, which the map only has once
     * it has been measured — hence the wait for the layout pass rather than an
     * application on the spot.
     */
    private fun holdOverServedArea(
        view: MapView,
        map: MapLibreMap,
        configuration: CityConfiguration,
    ) {
        val area = configuration.boundingBox?.takeIf { it.isUsable } ?: return
        val camera = ServedAreaCamera(
            view = view,
            map = map,
            area = area,
            widestZoom = configuration.map.minZoom.toDouble(),
            closestZoom = configuration.map.maxZoom.toDouble() + 1,
        )
        // Known to the screen before the layout, not only after it: the style
        // becomes ready in between, and the move it sets off — the address the
        // search has just returned — must be able to stand these limits down
        // before they are laid on top of it.
        servedAreaCamera = camera
        view.doOnLayout { camera.hold() }
    }

    /**
     * Takes the limits up again once a move of ours has ended.
     *
     * The screen may have gone in the meantime — one leaves for the list while
     * the map is still travelling — and the limits are then measured on a map
     * whose view no longer exists.
     */
    private fun holdOverServedAreaAgain(servedArea: ServedAreaCamera?) {
        if (map() == null) return
        servedArea?.holdAgain()
    }

    private fun LatLng.toCoordinates() = Coordinates(latitude, longitude)

    private fun Framing.toCameraPosition(): CameraPosition = CameraPosition.Builder()
        .target(LatLng(target.latitude, target.longitude))
        .zoom(zoom)
        .build()

    private companion object {
        /** The camera move's duration, short enough not to keep anyone waiting. */
        const val CAMERA_ANIMATION_MILLIS = 600

        const val STATE_CAMERA_LATITUDE = "camera-latitude"
        const val STATE_CAMERA_LONGITUDE = "camera-longitude"
        const val STATE_CAMERA_ZOOM = "camera-zoom"
    }
}
