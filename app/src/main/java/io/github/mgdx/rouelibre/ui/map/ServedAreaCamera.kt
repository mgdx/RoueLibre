package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.centresKeepingViewportInside
import io.github.mgdx.rouelibre.core.geo.zoomKeepingViewportInside
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.abs

/**
 * Keeps the camera over the served area, so that its edge never comes into
 * view.
 *
 * The three offline datasets stop at one and the same bounding box (SPEC §4).
 * Left free, the camera walks past it and the map turns to bare background:
 * roads cut off in mid-air, a straight line of nothing across the screen. What
 * the user sees there is not a coastline but the end of the download, and
 * nothing on the screen says so.
 *
 * Two limits together keep it off the screen, and neither would do alone: the
 * camera may not zoom out further than the area covering the screen, and its
 * target may not come closer to the edge than half a viewport.
 *
 * Both depend on the size of the visible region, which changes with the zoom,
 * with the shape of the window and with the screen's rotation — hence the
 * recomputation on every camera move rather than a single setting at start-up.
 *
 * Those moves are followed on the view rather than on the map: the view reports
 * what the engine is drawing, whatever set it off, where the map's own listener
 * only hears of the moves that went through its gesture detector — a zoom
 * coming from a mouse wheel or a plugged-in trackpad slipped past it, and the
 * edge showed for as long as the camera stayed there.
 */
class ServedAreaCamera(
    private val view: MapView,
    private val map: MapLibreMap,
    private val area: BoundingBox,
    private val widestZoom: Double,
    private val closestZoom: Double,
) : MapView.OnCameraIsChangingListener, MapView.OnCameraDidChangeListener {

    private var appliedCentres: LatLngBounds? = null
    private var appliedWidestZoom: Double = Double.NaN

    /**
     * Whether we are already inside [confine].
     *
     * Handing a target box to the map moves the camera on the spot, and that
     * move is reported back before `setLatLngBoundsForCameraTarget` has
     * returned. Recomputing the limit from the position it just moved to hands
     * over another box, which moves the camera again — the two chase each other
     * down the stack. Framing a journey right across the conurbation puts the
     * camera exactly where that happens, at a zoom the served area barely
     * allows, and the application died there of a `StackOverflowError`.
     *
     * A move we caused ourselves teaches us nothing we do not already know: the
     * box we just handed over is the one in force. The next move, the user's or
     * the map's, confines the camera again from a position that has settled.
     */
    private var confining = false

    /** Applies the limits, and keeps them true for as long as the map lives. */
    fun hold() {
        confine()
        view.addOnCameraIsChangingListener(this)
        view.addOnCameraDidChangeListener(this)
    }

    override fun onCameraIsChanging() {
        confine()
    }

    override fun onCameraDidChange(animated: Boolean) {
        confine()
    }

    private fun confine() {
        if (confining) return
        confining = true
        try {
            applyLimits()
        } finally {
            confining = false
        }
    }

    private fun applyLimits() {
        val visible = map.projection.visibleRegion.latLngBounds
        val latitudeSpan = visible.latitudeSpan
        val longitudeSpan = visible.longitudeSpan
        // Before the map view is laid out the region has no extent: measuring
        // from it would give a limit that nothing supports.
        if (latitudeSpan <= 0.0 || longitudeSpan <= 0.0) return

        // Held between the city's own two zooms: below, the configured limit
        // stands, since the tiles are drawn for it; above, a box smaller than
        // the screen would ask for a zoom the base map cannot serve, and the
        // two preferences would cross.
        val widest = area
            .zoomKeepingViewportInside(map.cameraPosition.zoom, latitudeSpan, longitudeSpan)
            .coerceIn(widestZoom, closestZoom)
        if (widest.differsFrom(appliedWidestZoom)) {
            appliedWidestZoom = widest
            map.setMinZoomPreference(widest)
        }

        // Measured from the camera's own target, which a Mercator viewport does
        // not sit in the middle of, degree for degree.
        val target = map.cameraPosition.target ?: return
        val centres = area.centresKeepingViewportInside(
            northwardReach = (visible.latitudeNorth - target.latitude).coerceAtLeast(0.0),
            southwardReach = (target.latitude - visible.latitudeSouth).coerceAtLeast(0.0),
            eastwardReach = (visible.longitudeEast - target.longitude).coerceAtLeast(0.0),
            westwardReach = (target.longitude - visible.longitudeWest).coerceAtLeast(0.0),
        )
        val bounds = LatLngBounds.from(
            centres.north,
            centres.east,
            centres.south,
            centres.west,
        )
        if (bounds.differsFrom(appliedCentres)) {
            appliedCentres = bounds
            map.setLatLngBoundsForCameraTarget(bounds)
        }
    }

    /**
     * Whether the limit is worth handing over again.
     *
     * This runs on every frame of every gesture, and each setting crosses to
     * the native map. The tolerances are below what a screen can show — a
     * hundredth of a zoom step, and a hundred-thousandth of a degree, about a
     * metre, which is one pixel at the closest zoom the tiles allow — so what
     * they filter out is recomputation noise, never a limit the eye could
     * catch.
     */
    private fun Double.differsFrom(applied: Double): Boolean =
        applied.isNaN() || abs(this - applied) > ZOOM_TOLERANCE

    private fun LatLngBounds.differsFrom(applied: LatLngBounds?): Boolean {
        if (applied == null) return true
        return abs(latitudeNorth - applied.latitudeNorth) > DEGREE_TOLERANCE ||
            abs(latitudeSouth - applied.latitudeSouth) > DEGREE_TOLERANCE ||
            abs(longitudeEast - applied.longitudeEast) > DEGREE_TOLERANCE ||
            abs(longitudeWest - applied.longitudeWest) > DEGREE_TOLERANCE
    }

    private companion object {
        const val ZOOM_TOLERANCE = 0.01
        const val DEGREE_TOLERANCE = 1e-5
    }
}
