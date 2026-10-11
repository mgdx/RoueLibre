package io.github.mgdx.rouelibre.ui.map

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mgdx.rouelibre.AppContainer
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The bikes outside stations on the main map, while the setting of SPEC §7.6
 * has them drawn.
 *
 * What the setting decides here is not only the markers but whether the feed
 * is asked for at all: it weighs twenty times the station feed, and a map that
 * is not showing those bikes has no business fetching them.
 *
 * @param source the style's source for the bikes, while a style carries it.
 * @param vehicleTypes the network's vehicle type table, empty until it is read.
 * @param container the application's container, while the screen is attached.
 */
internal class StreetBikesOnMap(
    private val source: () -> GeoJsonSource?,
    private val vehicleTypes: () -> Map<String, VehicleKind>,
    private val container: () -> AppContainer,
) {

    /**
     * The bikes outside stations last read, and empty while they are not shown.
     *
     * Held because two things settle what is drawn and they arrive apart: the
     * feed, and the vehicle type table that says which of those bikes bear the
     * bolt (SPEC §4.1). Whichever lands second redraws from this.
     */
    private var bikes: List<StreetBike> = emptyList()

    /** Whether the setting has the bikes drawn, as last read. */
    private var shown = false

    /**
     * Follows the setting that draws the bikes outside stations.
     *
     * Switched on, the feed is asked for on the spot — coming back from the
     * settings must find the bikes already on the map — and then followed;
     * switched off, the markers go and nothing is fetched again. The
     * one-minute gate between two reads is the repository's, not this
     * screen's (SPEC §4.1).
     */
    fun follow(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container().preferences.showStreetBikes.collectLatest { show ->
                    shown = show
                    if (!show) {
                        bikes = emptyList()
                        publish()
                        return@collectLatest
                    }
                    // Asked for beside the following rather than before it: the
                    // stream never ends, so a read awaited here would be a read
                    // the markers wait on.
                    launch { refresh() }
                    container().stationRepository.observeStreetBikes().collect { snapshot ->
                        bikes = snapshot.bikes
                        publish()
                    }
                }
            }
        }
    }

    /**
     * Redraws the bikes outside stations, or clears them.
     *
     * They answer to no filter of this screen — neither the two station
     * filters nor the kind, nor the Bikes / Free docks toggle. Those act on
     * counts, and there is no count here to hide or to narrow: a marker
     * standing for one bike of one kind is either drawn or not, and the
     * setting alone decides (SPEC §7.1).
     */
    fun publish() {
        val target = source() ?: return
        target.setGeoJson(StreetBikeMarkers.toFeatureCollection(bikes, vehicleTypes()))
    }

    /**
     * Asks for the bikes outside stations, while they are being shown.
     *
     * The outcome is deliberately not raised on the banner. A network that
     * cannot be reached is already being said by the station refresh made in
     * the same breath, and a network publishing no such feed is an ordinary
     * answer the repository remembers for the session rather than a failure
     * (SPEC §4.1) — the map says that one by drawing nothing, which is the
     * whole of what the setting promises there.
     *
     * @param force ignores the one-minute gate. Reserved for the refresh
     *   asked for by hand, as it is for the stations.
     */
    suspend fun refresh(force: Boolean = false) {
        if (!shown) return
        container().stationRepository.refreshStreetBikes(force = force)
    }
}
