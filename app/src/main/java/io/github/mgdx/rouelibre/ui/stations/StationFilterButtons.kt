package io.github.mgdx.rouelibre.ui.stations

import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.BikeKindFilter
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.ui.map.bikeKindFilterOf
import io.github.mgdx.rouelibre.ui.map.nextOnTheMap
import io.github.mgdx.rouelibre.ui.map.showsBikeKindFilter
import io.github.mgdx.rouelibre.ui.map.toggled
import io.github.mgdx.rouelibre.ui.map.where
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The two buttons that say what the stations count — bikes or free docks, and
 * which kind of bike (SPEC §7.1) — on the map and on the station list alike.
 *
 * One class for both screens, since 27 October 2026, so that the two buttons
 * look, read and answer the same wherever they are: the list used to carry a
 * row of two buttons across its whole width for what the map does with one,
 * and had no kind filter at all. What they set is [SharedStationFilters], so a
 * press on one screen is found on the other.
 *
 * The Bikes / Free docks button is offered only where the network's free docks
 * are worth showing: where most stations take back any bike it goes, and the
 * screen counts bikes. The kind filter is offered only where the network lends
 * both kinds, and only while bikes are counted. Each button is labelled by
 * what is shown rather than by what a press would do: they are state labels.
 *
 * @param modeToggle the Bikes / Free docks button, while the view exists.
 * @param bikeKindButton the kind filter's button, while the view exists.
 * @param controlsUp whether the screen's controls are up at all.
 * @param lentFleet what the network lends, `null` until the reading lands.
 * @param shared the filters the screen counts through, or `null` for a screen
 *   that counts every bike whatever was asked elsewhere — the map serving to
 *   pick a point, which shows neither button and must hide no bike in silence.
 * @param onChanged redraws the stations for the filters as they now stand.
 */
internal class StationFilterButtons(
    private val modeToggle: () -> MaterialButton?,
    private val bikeKindButton: () -> MaterialButton?,
    private val controlsUp: () -> Boolean,
    private val lentFleet: () -> FleetDescription?,
    private val shared: () -> SharedStationFilters?,
    private val onChanged: () -> Unit,
) {

    /**
     * Whether the network's free docks are worth showing, `true` until the
     * stations say otherwise — see `StationRepository.observeFreeDocksShown`.
     */
    private var freeDocksShown = true

    /** What the stations count. */
    val mode: AvailabilityMode
        get() = (shared()?.mode?.value ?: AvailabilityMode.Bikes).where(freeDocksShown)

    /** The kind asked for, or `null` for all of them. */
    private val bikeKind: WantedBikeKind?
        get() = shared()?.bikeKind?.value

    /** Wires both buttons and labels them, on a view just built. */
    fun bind() {
        modeToggle()?.setOnClickListener { shared()?.setMode(mode.toggled()) }
        bikeKindButton()?.setOnClickListener { shared()?.setBikeKind(bikeKind.nextOnTheMap()) }
        applyLabels()
    }

    /**
     * Follows the filters for as long as [owner] is started, wherever they
     * were set: labels, buttons and stations are brought up to date on every
     * change, and once at the start — a screen coming back from the other one
     * finds what was pressed there.
     */
    fun follow(owner: LifecycleOwner) {
        val filters = shared() ?: return
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(filters.mode, filters.bikeKind) { _, _ -> }.collect {
                    applyLabels()
                    showBikeKindFilter()
                    onChanged()
                }
            }
        }
    }

    /**
     * Takes whether the network's free docks are worth showing, and puts the
     * toggle up or takes it away accordingly.
     *
     * A screen counting free docks when they stop being shown goes back to
     * the bikes: the toggle that would have brought it back is gone.
     */
    fun showFreeDocks(shown: Boolean) {
        freeDocksShown = shown
        val filters = shared()
        if (!shown && filters?.mode?.value == AvailabilityMode.Docks) {
            filters.setMode(AvailabilityMode.Bikes)
        }
        showModeToggle()
        applyLabels()
        showBikeKindFilter()
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

    /** The kind the stations count, or `null` — see [bikeKindFilterOf]. */
    fun bikeKindFilter(): BikeKindFilter? = bikeKindFilterOf(bikeKind, lentFleet())

    private fun applyLabels() {
        modeToggle()?.setText(
            when (mode) {
                AvailabilityMode.Bikes -> R.string.mode_bikes
                AvailabilityMode.Docks -> R.string.mode_docks
            },
        )
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
