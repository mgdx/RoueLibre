package io.github.mgdx.rouelibre.ui.map

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.Coordinates
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The point designated on the main map, and the city it was designated in
 * (SPEC §7.2).
 *
 * Both are held as one [DesignatedPlace], and this is the only place either is
 * written: a point cannot be laid down without its city being noted, nor
 * cleared without its city going with it. The map screen used to keep them in
 * two fields that every one of its functions could reach, and a point was seen
 * to outlive a change of city that way.
 *
 * It lives as long as the fragment and not as long as its view: going through
 * the list or the search destroys the view, and the point has to be there on
 * the way back. What belongs to the view — the pill and the map's source — is
 * reached when needed, or handed over and taken back with the style.
 *
 * @param pill the label at the bottom of the map, while the view exists.
 * @param cityNowServed the network identifier of the city the map serves at
 *   this moment, or `null` while none is known.
 * @param bringIntoView moves the camera onto a point just designated.
 */
internal class PickedPlaceController(
    private val pill: () -> TextView?,
    private val cityNowServed: () -> String?,
    private val bringIntoView: (LatLng) -> Unit,
) {

    /**
     * The address found by the search, until it is cleared.
     *
     * A designated point, never a history: SPEC §8 forbids keeping a
     * destination, and this one does not outlive the screen.
     */
    var place: DesignatedPlace? = null
        private set

    /** The map's source for the marker, while a style carries it. */
    private var source: GeoJsonSource? = null

    /**
     * Lays the point found on the map, and brings the map onto it.
     *
     * The city is noted as the point is laid down, from the city the map is
     * serving at that moment: asking later which city an address came from
     * would mean asking after the answer has changed.
     */
    fun designate(position: Coordinates, label: String) {
        val designated = DesignatedPlace(position, label, city = cityNowServed())
        lay(designated)
        bringIntoView(LatLng(position.latitude, position.longitude))
    }

    /** Takes the point off the map, its city with it. */
    fun clear() = lay(null)

    /**
     * Clears the point unless it belongs to the city now served — see
     * [designatedPlaceSurvives] for the whole of the rule, and
     * [MapFragment]'s `loadTilesFor` for where it is asked.
     */
    fun keepOnlyFor(cityId: String?) {
        if (place?.survivesUnder(cityId) == false) clear()
    }

    /**
     * Installs the marker's source and layer in a style that has just loaded.
     *
     * Called by the screen in its order of layers: the searched point goes
     * after the stations and the named places, since it is what the user has
     * just asked for and goes in front.
     */
    fun addTo(style: Style, context: Context) {
        val picked = GeoJsonSource(
            PickedPlaceMarker.SOURCE_ID,
            PickedPlaceMarker.featureFor(null),
        )
        source = picked
        style.addSource(picked)
        PickedPlaceMarker.registerImage(context, style)
        style.addLayer(PickedPlaceMarker.layer())
        publish()
    }

    /** Forgets the style's source, which goes with the view. */
    fun detach() {
        source = null
    }

    /**
     * Writes the point into the state, so that it survives a rotation.
     *
     * The chosen point survives a rotation, and nothing else: it is written
     * nowhere on disk (SPEC §8).
     *
     * The city it belongs to travels with it, and is no more a history than
     * the point itself: this bundle dies with the task, and what it serves is
     * the opposite of keeping anything — it is how a point designated in
     * another city is recognised and thrown away when the process comes back
     * (see [keepOnlyFor]).
     */
    fun save(outState: Bundle) {
        val designated = place ?: return
        outState.putDouble(STATE_PICKED_LATITUDE, designated.position.latitude)
        outState.putDouble(STATE_PICKED_LONGITUDE, designated.position.longitude)
        outState.putString(STATE_PICKED_LABEL, designated.label)
        outState.putString(STATE_PICKED_CITY, designated.city)
    }

    /**
     * Lays down again the point the screen was designating when its view went
     * away, and shows its pill on the view just built.
     *
     * A reading of the bundle and nothing else, decided synchronously with the
     * rest of the screen's rebuilding: whether the point has a right to be
     * there is asked once, in [keepOnlyFor], where the city being served is
     * known without waiting. The city the point was designated in comes back
     * with it, which is what lets that question be put to a screen rebuilt
     * from scratch.
     *
     * The pill is shown even when there is no bundle: coming back from the
     * list or the search rebuilds the view without one, the point still held
     * here, and the new pill starts hidden. Leaving it so put the marker back
     * on the map with no way left to open its sheet or clear it.
     */
    fun restore(savedInstanceState: Bundle?) {
        place = placeOnRebuiltView(held = place, saved = savedInstanceState?.let(::readPlace))
        showLabel()
    }

    private fun readPlace(saved: Bundle): DesignatedPlace? {
        if (!saved.containsKey(STATE_PICKED_LATITUDE)) return null
        return DesignatedPlace(
            position = Coordinates(
                saved.getDouble(STATE_PICKED_LATITUDE),
                saved.getDouble(STATE_PICKED_LONGITUDE),
            ),
            label = saved.getString(STATE_PICKED_LABEL).orEmpty(),
            city = saved.getString(STATE_PICKED_CITY),
        )
    }

    private fun lay(designated: DesignatedPlace?) {
        place = designated
        publish()
        showLabel()
    }

    private fun publish() {
        source?.setGeoJson(PickedPlaceMarker.featureFor(place?.position))
    }

    /**
     * Shows the label of the point found.
     *
     * The label is part of what a screen reader must speak: replacing the text
     * with the action alone would make the address vanish for anyone who only
     * has the voice. The action named beside it is what a press now does —
     * open the sheet of §7.2 — and not what the pill used to do on its own.
     */
    private fun showLabel() {
        val view = pill() ?: return
        val designated = place
        view.isVisible = designated != null
        view.text = designated?.label.orEmpty()
        view.contentDescription = designated?.let {
            view.context.getString(R.string.map_picked_place_description, it.label)
        }
    }

    private companion object {
        const val STATE_PICKED_LATITUDE = "picked-latitude"
        const val STATE_PICKED_LONGITUDE = "picked-longitude"
        const val STATE_PICKED_LABEL = "picked-label"

        /**
         * The city the saved point was designated in — see [save] for why an
         * identifier travels with it, and why that is not a history.
         */
        const val STATE_PICKED_CITY = "picked-city"
    }
}

/**
 * The point a freshly built map view shows.
 *
 * The one in the state bundle when there is one — a fragment rebuilt from
 * scratch after a rotation or the process dying holds nothing yet — or else the
 * one the fragment kept while only its view was gone, on the way back from
 * another screen.
 */
internal fun placeOnRebuiltView(held: DesignatedPlace?, saved: DesignatedPlace?): DesignatedPlace? =
    saved ?: held
