package io.github.mgdx.rouelibre.ui.map

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.data.AppPreferences
import io.github.mgdx.rouelibre.data.SavedPlace
import io.github.mgdx.rouelibre.data.SavedPlaceKind
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource

/** What the settings say of the named places, as far as the map cares. */
internal data class SavedPlacesSettings(
    val home: SavedPlace? = null,
    val work: SavedPlace? = null,
    val markers: Boolean = false,
    val buttons: Boolean = false,
) {
    /** The place named for [kind], or `null` when none is. */
    fun of(kind: SavedPlaceKind): SavedPlace? = when (kind) {
        SavedPlaceKind.Home -> home
        SavedPlaceKind.Work -> work
    }

    /** The places the map marks: every one named, unless the setting says none. */
    fun marked(): Map<SavedPlaceKind, SavedPlace> = if (markers) {
        SavedPlaceKind.entries.mapNotNull { kind -> of(kind)?.let { kind to it } }.toMap()
    } else {
        emptyMap()
    }
}

/**
 * The places the user has named, on the main map: their markers, and the
 * buttons that bring the map onto each (SPEC §7.1, §7.6).
 *
 * @param source the style's source for the markers, while a style carries it.
 * @param button the button bringing the map onto a kind of place, while the
 *   view exists.
 * @param controlsUp whether the main screen's controls are up at all.
 * @param servedArea the box of the city in service, `null` when it declares
 *   none or none is known.
 * @param bringIntoView moves the camera onto a place.
 */
internal class SavedPlacesOnMap(
    private val source: () -> GeoJsonSource?,
    private val button: (SavedPlaceKind) -> View?,
    private val controlsUp: () -> Boolean,
    private val servedArea: () -> BoundingBox?,
    private val bringIntoView: (LatLng) -> Unit,
) {

    /**
     * The places the user has named, as the settings last said, with whether
     * the map marks them and whether it offers a button for each.
     *
     * Held because the markers are drawn into a style that may not exist yet,
     * and the buttons hang on a served area that is learnt apart: whichever
     * arrives second redraws from this.
     */
    private var settings = SavedPlacesSettings()

    /**
     * Follows the places the user has named and the two settings that say how
     * the map shows them.
     *
     * Followed rather than read once: a home named or forgotten in the settings
     * must be on the map — or off it — on the way back, without a rebuild.
     */
    fun follow(owner: LifecycleOwner, preferences: AppPreferences) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    preferences.homePlace,
                    preferences.workPlace,
                    preferences.showSavedPlacesOnMap,
                    preferences.savedPlaceButtonsOnMap,
                    ::SavedPlacesSettings,
                )
                    .distinctUntilChanged()
                    .collect { places ->
                        settings = places
                        publish()
                        showButtons()
                    }
            }
        }
    }

    /** Draws the named places, or clears them where the setting says so. */
    fun publish() {
        source()?.setGeoJson(SavedPlaceMarkers.featuresFor(settings.marked()))
    }

    /**
     * Puts up the button of each named place the map can bring itself onto —
     * see [offersCentringOn].
     */
    fun showButtons() {
        for (kind in SavedPlaceKind.entries) {
            val view = button(kind) ?: return
            view.isVisible = offersCentringOn(
                place = settings.of(kind)?.position,
                wanted = settings.buttons,
                browsing = controlsUp(),
                servedArea = servedArea(),
            )
        }
    }

    /**
     * Brings the map onto a named place, at the zoom of an address found: it
     * is one, and the stations around it are what one came to see.
     */
    fun centreOn(kind: SavedPlaceKind) {
        val place = settings.of(kind) ?: return
        bringIntoView(LatLng(place.position.latitude, place.position.longitude))
    }
}
