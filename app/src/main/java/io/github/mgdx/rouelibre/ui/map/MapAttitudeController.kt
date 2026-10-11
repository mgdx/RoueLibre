package io.github.mgdx.rouelibre.ui.map

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.ui.prefersReducedMotion
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * The turn and the tilt of a map, and the compass that gives both back
 * (SPEC §7.1, §7.4).
 *
 * Both screens whose map can be turned — the main map and the journey result —
 * hold one of these, so that a map one learns to handle on one screen answers
 * the same way on the other: the same gestures, the same ceiling on the tilt,
 * the same button coming up once the map is off north or off flat, and the same
 * sentence read out over it. It used to be written out twice, half the same.
 *
 * It lives as long as its screen, and reaches the view and the map only while
 * they exist: either may be gone when a callback arrives — one leaves the
 * screen while the map is still turning back — and nothing is then done.
 *
 * @param button the compass button, while the view exists.
 * @param map the map, once it is ready and until its view is destroyed.
 */
internal class MapAttitudeController(
    private val button: () -> View?,
    private val map: () -> MapLibreMap?,
) {

    /**
     * Whether the compass is allowed on the screen at all (SPEC §7.1).
     *
     * Being allowed is not being shown: the button only appears once the map
     * has been turned. The main map withholds it while it has no base map to
     * turn — the base map's arrival and the map's bearing are settled apart
     * from each other, and each has to be able to decide without reading the
     * other. The journey result allows it from the start.
     */
    var isAllowed: Boolean = true

    /**
     * The listener that follows the map's bearing while it turns.
     *
     * Kept so that it can be taken off the map with the view, as the other
     * listeners of these screens are.
     */
    private var bearingListener: MapLibreMap.OnCameraMoveListener? = null

    /** Makes the compass button put the map back when pressed. */
    fun bind() {
        val compass = button() ?: return
        compass.setOnClickListener { faceNorthAndLayFlat() }
        // What the press does, said apart from the description: that one is
        // spent saying how far the map is turned, and a screen reader would
        // otherwise announce a bare "activate" over it.
        ViewCompat.replaceAccessibilityAction(
            compass,
            AccessibilityActionCompat.ACTION_CLICK,
            compass.context.getString(R.string.map_face_north),
            null,
        )
    }

    /**
     * Says, rather than leaves to the library's defaults, how the map may be
     * handled and what the library may draw over it.
     */
    fun configure(map: MapLibreMap) {
        map.uiSettings.isAttributionEnabled = false
        map.uiSettings.isLogoEnabled = false
        // The library's own compass, off: ours is the one in the column of
        // controls (SPEC §7.1). MapLibre's is on by default and fades itself
        // away while the map faces north, so it lay hidden for as long as the
        // map could not be turned and appeared the day it could — a black disc
        // in the top right corner, half under the status bar and over the
        // settings button, where nothing in the layout expects a view. It is
        // refused for the reason DescribedMapView exists: a view of the
        // library describes itself in the library's languages and not in ours,
        // and the screen reader read out its sentence beside ours.
        map.uiSettings.isCompassEnabled = false
        // The map turns under two fingers and tilts under two more, and our
        // compass gives back both (SPEC §7.1, §7.4). Said rather than left to
        // the library's defaults, so that both are read as decisions — a
        // default is not one, as the compass above has just proved.
        //
        // The tilt was refused between 7 September 2026 and the same day, on
        // the grounds that the base map is drawn flat and its labels are laid
        // flat on it, so a tilt reveals no relief and only makes the names
        // harder to read. That is true of the drawing and it was not true of
        // the use: it is how somebody riding reads the street ahead of them,
        // and it was in daily use before it was taken away.
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = true
        // How far it may tilt, and the figure is derived rather than chosen —
        // see MAX_PITCH_DEGREES. A tilt looks towards the horizon, so it
        // lengthens what the screen covers of the ground, and the served
        // area's edge, which SPEC §7.1 forbids showing, comes into view for
        // far less panning than on a flat map.
        map.setMaxPitchPreference(MAX_PITCH_DEGREES)
    }

    /**
     * Follows the map's bearing, so that the compass says what the map does.
     *
     * `addOnCameraMoveListener` and not `addOnCameraIdleListener`: the needle
     * is a reading of the map's own state, and one that only caught up at the
     * end of the gesture would point at a north the map had already left —
     * for the whole length of the turn, which is exactly when it is being
     * looked at. What each frame costs is a comparison and two view
     * properties, beside the frame the map is drawing anyway.
     *
     * A move the screen orders itself is followed by its own callback rather
     * than by this listener (see [faceNorthAndLayFlat]): the listener hears
     * the gestures, and the callback guarantees the state the map settles in.
     */
    fun follow(map: MapLibreMap) {
        // The main map loads its tiles again on every return to the screen, on
        // the same map: without this the same map would carry a second
        // listener, and a third, each turn of the map costing what they all
        // add up to.
        bearingListener?.let(map::removeOnCameraMoveListener)
        val listener = MapLibreMap.OnCameraMoveListener { showTheBearing() }
        bearingListener = listener
        map.addOnCameraMoveListener(listener)
    }

    /** Takes the listener off the map, which is going with its view. */
    fun stopFollowing() {
        bearingListener?.let { map()?.removeOnCameraMoveListener(it) }
        bearingListener = null
    }

    /**
     * Shows the compass for the bearing the map now has, or takes it away.
     *
     * The whole button turns rather than the drawing inside it: its ground is
     * a disc, so the needle is the only thing that can be seen to move, and
     * turning the view spares a second drawable and a redraw of the icon.
     *
     * The description says how far the map is turned and not only what the
     * button does. This button is the one sign that the map is off north, so
     * a reader who cannot see it turned would learn nothing from "face north"
     * alone (SPEC §11).
     */
    fun showTheBearing() = showAttitude(mapAttitude())

    /**
     * Shows the compass for an attitude of the map, or takes it away.
     *
     * The sentence follows the state and not the deed: the turn is given in
     * degrees, the tilt is named, and what the press does is said once as the
     * button's accessibility action rather than at the end of every reading
     * (SPEC §11).
     *
     * @param attitude how the map is held. It comes from the map while the map
     *   is the only one who knows, and from what the screen ordered once the
     *   screen knows better — see [attitudeAfterOrderingNorthAndFlat].
     */
    private fun showAttitude(attitude: MapAttitude) {
        val compass = button() ?: return
        val needle = compassNeedle(attitude)
        val resources = compass.resources
        compass.isVisible = isAllowed && needle.isShown
        compass.rotation = needle.iconRotationDegrees
        compass.contentDescription = when (needle.sentence) {
            CompassSentence.TurnedAndTilted -> resources.getQuantityString(
                R.plurals.map_bearing_tilted_description,
                needle.degreesFromNorth,
                needle.degreesFromNorth,
            )

            CompassSentence.Tilted -> resources.getString(R.string.map_tilted_description)

            CompassSentence.Turned -> resources.getQuantityString(
                R.plurals.map_bearing_description,
                needle.degreesFromNorth,
                needle.degreesFromNorth,
            )
        }
    }

    /** How the map reports itself held, or the opening attitude for want of a map. */
    private fun mapAttitude(): MapAttitude {
        val camera = map()?.cameraPosition ?: return NORTH_AND_FLAT
        return MapAttitude(bearingDegrees = camera.bearing, pitchDegrees = camera.tilt)
    }

    /** Shows what [attitudeAfterOrderingNorthAndFlat] says the attitude now is. */
    private fun showAttitudeAfterOrdering(theOrderStands: Boolean) = showAttitude(
        attitudeAfterOrderingNorthAndFlat(
            theOrderStands = theOrderStands,
            mapAttitude = mapAttitude(),
        ),
    )

    /**
     * Puts the map back the way it opened (SPEC §7.1, §7.4).
     *
     * Turned rather than snapped: a map that jumps to north leaves the reader
     * to find again where they were looking — the journey, on the result
     * screen — where one that turns carries their place round with it. Under
     * "remove animations" it snaps, as every other camera move does.
     *
     * The limits penning the camera are not stood down for it, unlike the
     * moves that change the target: the target does not move here, and coming
     * back to north can only shrink what the screen covers of the served area.
     */
    private fun faceNorthAndLayFlat() {
        val map = map() ?: return
        val context = button()?.context ?: return
        // The button goes at the press and not at the end of the move: from
        // here on the map is bound for north and flat, so the compass has
        // nothing left to undo, and the screen has no reason to read back an
        // order it gave itself (see [attitudeAfterOrderingNorthAndFlat]).
        showAttitudeAfterOrdering(theOrderStands = true)
        // One update carrying both, rather than two moves racing each other:
        // a position whose target and zoom are left unset keeps the ones the
        // map has, which is what `bearingTo` was already doing on its own.
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().bearing(NORTH_BEARING).tilt(FLAT_PITCH).build(),
        )
        if (context.prefersReducedMotion()) {
            map.moveCamera(update)
            return
        }
        map.animateCamera(
            update,
            FACE_NORTH_ANIMATION_MILLIS,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() = showAttitudeAfterOrdering(theOrderStands = true)

                // A move cut short by a finger back on the map leaves the map
                // wherever it stopped, and the needle has to come back and say
                // so. This is the one case where the map knows and we do not.
                override fun onCancel() = showAttitudeAfterOrdering(theOrderStands = false)
            },
        )
    }
}
