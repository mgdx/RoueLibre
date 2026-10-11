package io.github.mgdx.rouelibre.ui.journey

import android.content.Context
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.journey.JourneyPlan
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.databinding.FragmentJourneyResultBinding
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.map.ServedAreaCamera
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The journey drawn on the result's map, and the camera framed on it
 * (SPEC §7.4).
 *
 * The track fills the top of the screen in three distinct legs: one first looks
 * at where one is going. This draws them, frames the map on them — again
 * whenever the map changes size — and pens the camera inside the served area
 * while doing so.
 *
 * It lives as long as the screen, since the framing is kept while the view is
 * rebuilt; the sources and the limits go with the view.
 *
 * @param binding the screen's views, while they exist.
 * @param map the map, once it is ready and until its view is destroyed.
 */
internal class JourneyTrackOnMap(
    private val binding: () -> FragmentJourneyResultBinding?,
    private val map: () -> MapLibreMap?,
) {
    private var walkSource: GeoJsonSource? = null
    private var rideSource: GeoJsonSource? = null
    private var markerSource: GeoJsonSource? = null

    /**
     * The track the camera must show, kept for as long as it is displayed.
     *
     * The map is not always the size it will end up being when the journey is
     * drawn: holding the framing lets it be laid again once it is.
     */
    private var frame: LatLngBounds? = null

    /**
     * The limit penning the camera inside the served area, kept so that the
     * framing can stand it down for the length of its own move and have it
     * measured again on the map as it then stands.
     */
    private var servedArea: ServedAreaCamera? = null

    /**
     * Lays the journey's layers over a style that has just loaded: the two
     * tracks, then the four points.
     */
    fun addTo(style: Style, context: Context, fleet: BikeFleet, ownBikeKind: OwnBikeKind) {
        val walk = GeoJsonSource(JourneyLines.WALK_SOURCE_ID)
        val ride = GeoJsonSource(JourneyLines.RIDE_SOURCE_ID)
        walkSource = walk
        rideSource = ride
        style.addSource(walk)
        style.addSource(ride)
        style.addLayer(JourneyLines.rideLayer(context))
        style.addLayer(JourneyLines.walkLayer(context))

        // The four points come after the tracks: a marker sitting under the
        // line it belongs to would be half hidden by it.
        val markers = GeoJsonSource(JourneyMarkers.SOURCE_ID)
        markerSource = markers
        style.addSource(markers)
        JourneyMarkers.registerImages(context, style, fleet, ownBikeKind)
        style.addLayer(JourneyMarkers.layer())
    }

    /**
     * Bounds where the camera may go, at the tiles' own limits.
     *
     * Past their maximum zoom MapLibre scales the last ones it has: one step is
     * allowed, which lets "locate me" come right down onto the pavement without
     * the labels turning to mush. The city is read from disk, so the cap
     * arrives a moment after the map — before any gesture, in practice.
     *
     * With a base map underneath, the camera is also penned inside the served
     * area, as on the main screen: the edge of the data must not show here
     * either. It costs the framing of a journey crossing the conurbation from
     * end to end, which then loses its margins — a fair trade against a screen
     * half empty. Without tiles there is no edge to hide, and nothing to gain
     * from holding a track that is drawn on bare background.
     */
    fun limitTo(map: MapLibreMap, city: CityConfiguration, hasTiles: Boolean) {
        val closestZoom = city.map.maxZoom.toDouble() + 1
        map.setMaxZoomPreference(closestZoom)
        val area = city.boundingBox?.takeIf { it.isUsable } ?: return
        if (!hasTiles) return
        // The visible region is only measurable once the map view has been
        // laid out, and the limits are read off it.
        val view = binding()?.map ?: return
        view.doOnLayout {
            servedArea = ServedAreaCamera(
                view = view,
                map = map,
                area = area,
                widestZoom = city.map.minZoom.toDouble(),
                closestZoom = closestZoom,
            ).apply { hold() }
            // The limit was measured on the map as it was when it was laid
            // out; the framing waiting for it was computed for that same
            // moment. Laying it again now puts the two back in step.
            applyFrame()
        }
    }

    /** Draws the chosen option and frames the map on it. */
    fun draw(state: JourneyUiState, origin: Coordinates, destination: Coordinates) {
        val walk = walkSource ?: return
        val ride = rideSource ?: return
        val option = state.chosen

        // The two ends are drawn even when no journey was composed: they say
        // what was asked for, which is worth showing when the answer is empty.
        markerSource?.setGeoJson(
            JourneyMarkers.featuresFor(
                origin,
                destination,
                option,
                isRidden = state.plan is JourneyPlan.OwnBike,
            ),
        )

        if (option == null) {
            // A journey with no station runs in one leg, and which of the two
            // sources carries it is the whole difference between them: dotted
            // for the walk, unbroken for the ride on one's own bike.
            walk.setGeoJson(
                JourneyLines.featuresOf((state.plan as? JourneyPlan.WalkOnly)?.directWalk),
            )
            ride.setGeoJson(JourneyLines.featuresOf((state.plan as? JourneyPlan.OwnBike)?.ride))
        } else {
            walk.setGeoJson(JourneyLines.walkFeatures(option))
            ride.setGeoJson(JourneyLines.rideFeatures(option))
        }
        frameOn(trackOf(state.plan))
    }

    /**
     * Brings the camera onto the track, for the map as it stands right now.
     *
     * The detail underneath is as tall as the journey it describes, and it is
     * absent while the journey is being worked out: the map fills the screen
     * then, and shrinks by half when the answer arrives. A framing computed
     * before that layout fits the track into a viewport that no longer exists —
     * one or two zoom steps too close, which cuts a journey running north-south
     * while leaving a wide one apparently right. Hence the framing is kept and
     * laid again at the map's real size, whenever that size changes.
     *
     * @param animated true when a press asked for it, so the map travels back
     * rather than jumping: the movement is what tells where one has been taken
     * from. The screen's own re-framings stay abrupt — they follow a layout
     * change nobody asked for, and animating them would draw the eye to it.
     */
    fun applyFrame(animated: Boolean = false) {
        val views = binding() ?: return
        val map = map() ?: return
        val bounds = frame ?: return
        // The band of attribution lies over the map: the track is framed above
        // it, not underneath.
        val band = views.attribution.height
        val width = views.map.width
        val height = views.map.height - band
        if (width <= 0 || height <= 0) return

        // MapLibre counts the margin in screen pixels and divides by the
        // density itself, so it is stated in dp: the same margin on every
        // screen — see frameMarginPixels for how far it may go.
        val margin = frameMarginPixels(
            wantedPixels = (FRAME_PADDING_DP * views.root.resources.displayMetrics.density).toInt(),
            width = width,
            height = height,
        )

        // The camera limit stands down for the length of the move, and is
        // measured again on arrival: it was laid down for the framing being
        // left, and this screen leaves a very different one — the map fills the
        // screen while the answer is worked out, so the box in force pens the
        // camera as a viewport twice this height requires, and the framing that
        // arrives is clamped short of where it belongs. The zoom floor is
        // brought up to date rather than lifted, so nothing here can uncover
        // the edge of the served area (SPEC §7.1).
        //
        // That floor does not buy a journey right across the conurbation its
        // margins, and nothing here can: the base map holds no tile below zoom
        // 10, so on a phone the widest possible view spans some twenty
        // kilometres and a thirty-kilometre track cannot be shown whole. That
        // ceiling is in the data, not in the camera.
        val limits = servedArea
        limits?.releaseForMove()
        val update = CameraUpdateFactory
            .newLatLngBounds(bounds, margin, margin, margin, margin + band)
        if (!animated) {
            map.moveCamera(update)
            limits?.holdAgain()
            return
        }
        // The limits stay down for the whole flight, not just its start: laying
        // them again mid-move jumps the camera inside the box and kills the
        // animation where it stands. They are taken up on arrival, however the
        // move ends — a gesture during it cancels the animation, and a camera
        // left unpenned is what lets the edge of the served area show.
        map.animateCamera(
            update,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() = limits?.holdAgain() ?: Unit
                override fun onFinish() = limits?.holdAgain() ?: Unit
            },
        )
    }

    /** Forgets the style's sources and the limits, which go with the view. */
    fun detach() {
        walkSource = null
        rideSource = null
        markerSource = null
        servedArea = null
    }

    /**
     * Frames the map on the whole track, with a comfortable margin — or drops
     * the framing, and the button that comes back to it, where the track has
     * no extent (see [canBeFramed]).
     */
    private fun frameOn(track: List<Coordinates>) {
        frame = track.takeIf(::canBeFramed)
            ?.let { points ->
                LatLngBounds.Builder()
                    .includes(points.map { LatLng(it.latitude, it.longitude) })
                    .build()
            }
        binding()?.frameJourney?.isVisible = frame != null
        applyFrame()
    }

    private companion object {
        /** The margin around the track, in dp, so it does not touch the edges. */
        const val FRAME_PADDING_DP = 32
    }
}
