package io.github.mgdx.rouelibre.ui.map

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
 * Both live as long as the screen does — they are ways of looking at the map
 * right now, not facts about the rider — so nothing of them is written down.
 * Each button is labelled by what is shown rather than by what a press would
 * do: they are state labels, like the list's own toggle.
 *
 * @param modeToggle the Bikes / Free docks button, while the view exists.
 * @param bikeKindButton the kind filter's button, while the view exists.
 * @param controlsUp whether the main screen's controls are up at all.
 * @param lentFleet what the network lends, `null` until the reading lands.
 * @param onChanged redraws the stations for the filters as they now stand.
 */
internal class MapFilterControls(
    private val modeToggle: () -> MaterialButton?,
    private val bikeKindButton: () -> MaterialButton?,
    private val controlsUp: () -> Boolean,
    private val lentFleet: () -> FleetDescription?,
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

    /** Wires both buttons and labels them, on a view just built. */
    fun bind() {
        modeToggle()?.setOnClickListener { toggleMode() }
        bikeKindButton()?.setOnClickListener { toggleBikeKind() }
        applyModeLabel()
        applyBikeKindLabel()
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
}
