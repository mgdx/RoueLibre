package io.github.mgdx.rouelibre.ui.map

import android.os.Bundle
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.WantedBikeKind

/**
 * The two filters laid over the main map, and their buttons: bikes or free
 * docks, and which kind of bike (SPEC §7.1).
 *
 * The first is offered only where the network's free docks are worth showing,
 * since 22 October 2026: where most stations take back any bike, its
 * button goes and the map counts bikes (see `freeDocksAreWorthShowing`).
 *
 * They do not last as long as each other, since 21 October 2026. Bikes or
 * docks is what one is about to do right now — borrow or return — so it lasts
 * as long as the screen, a turned phone and a killed process included, and no
 * longer: the next launch starts on the bikes. The kind of bike is written
 * down and found again at the next launch, because somebody who opens the
 * application again in the same city mostly wants the same kind of bike as
 * last time (SPEC §7.1). Each button is labelled by what is shown rather than
 * by what a press would do: they are state labels, like the list's own toggle.
 *
 * @param modeToggle the Bikes / Free docks button, while the view exists.
 * @param bikeKindButton the kind filter's button, while the view exists.
 * @param controlsUp whether the main screen's controls are up at all.
 * @param lentFleet what the network lends, `null` until the reading lands.
 * @param rememberBikeKind writes the kind just chosen down for the next launch.
 * @param onChanged redraws the stations for the filters as they now stand.
 */
internal class MapFilterControls(
    private val modeToggle: () -> MaterialButton?,
    private val bikeKindButton: () -> MaterialButton?,
    private val controlsUp: () -> Boolean,
    private val lentFleet: () -> FleetDescription?,
    private val rememberBikeKind: (WantedBikeKind?) -> Unit,
    private val onChanged: () -> Unit,
) {

    /** What the markers count. */
    var mode: AvailabilityMode = AvailabilityMode.Bikes
        private set

    /**
     * The kind of bike the markers count, or `null` for all of them (SPEC §7.1).
     *
     * Its own control, owing nothing to the journey screen's: what is filtered
     * here changes no journey, and a kind chosen for a journey filters nothing
     * here.
     */
    private var bikeKind: WantedBikeKind? = null

    /**
     * Whether the network's free docks are worth showing, `true` until the
     * stations say otherwise — see `StationRepository.observeFreeDocksShown`.
     */
    private var freeDocksShown = true

    /**
     * Sets both filters back as they stood, on a screen just created.
     *
     * The saved state wins when there is one: a turned phone, or a process the
     * system killed in the background, comes back to exactly what was on the
     * screen, free docks included. Only a screen started afresh asks for
     * [storedBikeKind], and it then starts on the bikes, the mode being a
     * question of the moment rather than one kept from one launch to the next.
     *
     * @param savedInstanceState what [save] wrote, or `null` on a fresh start.
     * @param storedBikeKind the kind written down at an earlier launch.
     */
    fun restore(savedInstanceState: Bundle?, storedBikeKind: () -> WantedBikeKind?) {
        if (savedInstanceState == null) {
            bikeKind = storedBikeKind()
            return
        }
        val savedMode = savedInstanceState.getString(STATE_MODE)
        mode = AvailabilityMode.entries.find { it.name == savedMode } ?: AvailabilityMode.Bikes
        bikeKind = WantedBikeKind.ofWireName(savedInstanceState.getString(STATE_BIKE_KIND))
    }

    /** Writes both filters into the state the system keeps for the screen. */
    fun save(outState: Bundle) {
        outState.putString(STATE_MODE, mode.name)
        outState.putString(STATE_BIKE_KIND, bikeKind?.wireName)
    }

    /** Wires both buttons and labels them, on a view just built. */
    fun bind() {
        modeToggle()?.setOnClickListener { toggleMode() }
        bikeKindButton()?.setOnClickListener { toggleBikeKind() }
        applyModeLabel()
        applyBikeKindLabel()
    }

    /**
     * Takes whether the network's free docks are worth showing, and puts the
     * toggle up or takes it away accordingly.
     *
     * A map counting free docks when they stop being shown goes back to the
     * bikes and redraws: the toggle that would have brought it back is gone.
     */
    fun showFreeDocks(shown: Boolean) {
        freeDocksShown = shown
        val previous = mode
        mode = mode.where(freeDocksShown = shown)
        showModeToggle()
        if (mode == previous) return
        applyModeLabel()
        showBikeKindFilter()
        onChanged()
    }

    /**
     * Shows the Bikes / Free docks toggle with the screen's other controls,
     * and only where the network's free docks are worth showing.
     */
    fun showModeToggle() {
        modeToggle()?.isVisible = controlsUp() && freeDocksShown
    }

    /** Shows the kind filter where it has something to filter — see [showsBikeKindFilter]. */
    fun showBikeKindFilter() {
        val button = bikeKindButton() ?: return
        button.isVisible = showsBikeKindFilter(controlsUp(), mode, lentFleet())
    }

    /** The kind the markers count, or `null` — see [bikeKindFilterOf]. */
    fun bikeKindFilter(): BikeKindFilter? = bikeKindFilterOf(bikeKind, lentFleet())

    private fun toggleMode() {
        mode = mode.toggled()
        applyModeLabel()
        // The kind filter has nothing to say about free docks, so it goes while
        // they are being shown. The choice itself is kept: coming back to the
        // bikes finds it again, rather than silently reset.
        showBikeKindFilter()
        onChanged()
    }

    private fun toggleBikeKind() {
        bikeKind = bikeKind.nextOnTheMap()
        rememberBikeKind(bikeKind)
        applyBikeKindLabel()
        onChanged()
    }

    private fun applyModeLabel() {
        modeToggle()?.setText(
            when (mode) {
                AvailabilityMode.Bikes -> R.string.mode_bikes
                AvailabilityMode.Docks -> R.string.mode_docks
            },
        )
    }

    private fun applyBikeKindLabel() {
        val button = bikeKindButton() ?: return
        button.setText(
            when (bikeKind) {
                null -> R.string.map_bikes_all_kinds
                WantedBikeKind.Mechanical -> R.string.map_bikes_mechanical
                WantedBikeKind.Electric -> R.string.map_bikes_electric
            },
        )
        button.contentDescription = button.context.getString(
            when (bikeKind) {
                null -> R.string.map_bikes_all_kinds_description
                WantedBikeKind.Mechanical -> R.string.map_bikes_mechanical_description
                WantedBikeKind.Electric -> R.string.map_bikes_electric_description
            },
        )
    }

    private companion object {
        const val STATE_MODE = "map-filter-mode"
        const val STATE_BIKE_KIND = "map-filter-bike-kind"
    }
}
