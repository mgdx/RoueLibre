package io.github.mgdx.rouelibre.ui.map

import android.graphics.PointF
import android.graphics.RectF
import androidx.fragment.app.FragmentManager
import io.github.mgdx.rouelibre.ui.stations.StationDetailSheet
import io.github.mgdx.rouelibre.ui.stations.StreetBikeSheet
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Point

/** What a touch on the main map landed on, as far as the map answers it. */
internal sealed interface MarkerTouch {
    /** A cluster, of stations or of bikes: the map zooms into it. */
    data object Cluster : MarkerTouch

    /** A bike outside stations: its sheet opens. */
    data class StreetBike(val id: String) : MarkerTouch

    /** A station: its detail opens. */
    data class Station(val id: String) : MarkerTouch

    /** Nothing this screen answers: the map handles the gesture itself. */
    data object Nothing : MarkerTouch
}

/**
 * Reads what a touched marker is, and whether this screen answers it.
 *
 * In "pick a point" mode, opening a sheet of either kind would divert the
 * gesture from what the user came to do; a cluster still opens, since zooming
 * in serves the aim too.
 *
 * Pure, so that it can be tested on the JVM (SPEC §14).
 *
 * @param isCluster whether the marker carries the count MapLibre gives the
 *   clusters it forms.
 * @param bikeId the bike's identifier, if the marker is a bike.
 * @param stationId the station's identifier, if the marker is a station.
 */
internal fun markerTouch(
    isCluster: Boolean,
    bikeId: String?,
    stationId: String?,
    isPicking: Boolean,
): MarkerTouch = when {
    isCluster -> MarkerTouch.Cluster
    isPicking -> MarkerTouch.Nothing
    bikeId != null -> MarkerTouch.StreetBike(bikeId)
    stationId != null -> MarkerTouch.Station(stationId)
    else -> MarkerTouch.Nothing
}

/**
 * Answers a touch on the main map's markers: a cluster zooms in, a station or
 * a bike outside stations opens its sheet (SPEC §7.1, §7.2).
 *
 * @param map the map, while it exists.
 * @param mapHeight the height of the map view, while it exists.
 * @param isPicking whether the map was opened to designate a point.
 * @param moveTo moves the camera onto a point, at a zoom.
 * @param sheets where the sheets are shown.
 */
internal class MarkerTouches(
    private val map: () -> MapLibreMap?,
    private val mapHeight: () -> Int?,
    private val isPicking: () -> Boolean,
    private val moveTo: (LatLng, Double) -> Unit,
    private val sheets: () -> FragmentManager,
) {

    /**
     * Opens the detail of the station touched, or zooms into a cluster.
     *
     * The sensitive area is widened around the finger: a marker is some fifteen
     * pixels across, which is less than the pad of a thumb. Without this
     * margin, one would have to aim.
     *
     * @return true if the gesture was consumed, so the map does not handle it
     *   in turn.
     */
    fun onMapClicked(point: LatLng): Boolean {
        val map = map() ?: return false
        val screenPoint = map.projection.toScreenLocation(point)
        val touchArea = RectF(
            screenPoint.x - TOUCH_SLOP_PIXELS,
            screenPoint.y - TOUCH_SLOP_PIXELS,
            screenPoint.x + TOUCH_SLOP_PIXELS,
            screenPoint.y + TOUCH_SLOP_PIXELS,
        )
        val touched = map.queryRenderedFeatures(
            touchArea,
            StationMarkers.STATION_CIRCLE_LAYER,
            StationMarkers.CLUSTER_CIRCLE_LAYER,
            StreetBikeMarkers.CIRCLE_LAYER,
            StreetBikeMarkers.CLUSTER_CIRCLE_LAYER,
        ).firstOrNull() ?: return false

        val touch = markerTouch(
            isCluster = touched.hasProperty(CLUSTER_COUNT_PROPERTY),
            bikeId = touched.getStringProperty(StreetBikeMarkers.BIKE_ID_PROPERTY),
            stationId = touched.getStringProperty(StationMarkers.STATION_ID_PROPERTY),
            isPicking = isPicking(),
        )
        when (touch) {
            // A cluster describes no station in particular: touching it zooms
            // in, which eventually resolves it into distinct markers.
            MarkerTouch.Cluster -> moveTo(point, map.cameraPosition.zoom + CLUSTER_ZOOM_STEP)

            is MarkerTouch.StreetBike -> {
                (touched.geometry() as? Point)?.let { centreOnMarker(map, it) }
                StreetBikeSheet.newInstance(touch.id).show(sheets(), StreetBikeSheet.TAG)
            }

            is MarkerTouch.Station -> {
                (touched.geometry() as? Point)?.let { centreOnMarker(map, it) }
                StationDetailSheet.newInstance(touch.id).show(sheets(), StationDetailSheet.TAG)
            }

            MarkerTouch.Nothing -> return false
        }
        return true
    }

    /**
     * Brings the touched marker to the middle of what stays visible.
     *
     * Not to the middle of the map: the detail sheet rises over the lower part
     * of the screen, and a station centred there would be behind it. The camera
     * therefore aims a little below the marker, which leaves the marker in the
     * visible half — the eye follows what it has just touched.
     *
     * The zoom is left alone. Touching a marker says "show me this one", not
     * "take me closer", and a zoom that changed under the finger would lose the
     * neighbouring stations one was comparing.
     */
    private fun centreOnMarker(map: MapLibreMap, marker: Point) {
        val target = LatLng(marker.latitude(), marker.longitude())
        val height = mapHeight() ?: return
        val onScreen = map.projection.toScreenLocation(target)
        val below = PointF(onScreen.x, onScreen.y + height * SHEET_CLEARANCE_FRACTION)
        moveTo(map.projection.fromScreenLocation(below), map.cameraPosition.zoom)
    }

    private companion object {
        /**
         * The margin around the finger on a touch, in pixels. A marker is some
         * fifteen pixels across: without this margin, one would have to aim.
         */
        const val TOUCH_SLOP_PIXELS = 32f

        /** How much a touch on a cluster zooms the map in. */
        const val CLUSTER_ZOOM_STEP = 2.0

        /**
         * How far below a touched station the camera aims, as a fraction of the
         * map's height. A quarter puts the marker halfway up the part the
         * detail sheet leaves visible.
         */
        const val SHEET_CLEARANCE_FRACTION = 0.25f

        /** The property MapLibre adds to the clusters it forms itself. */
        const val CLUSTER_COUNT_PROPERTY = "point_count"
    }
}
