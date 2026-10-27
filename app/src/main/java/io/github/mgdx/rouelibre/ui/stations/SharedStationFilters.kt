package io.github.mgdx.rouelibre.ui.stations

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * What the stations count, held once for the map and the station list
 * (SPEC §7.1): bikes or free docks, and which kind of bike.
 *
 * **One state for both screens**, since 27 October 2026. Each used to hold its
 * own, and the button that leads from one to the other — at the same place on
 * both — led to a screen counting something else: free docks asked for on the
 * map came back as bikes on the list, and the list had no kind filter at all.
 * The two screens are two ways of looking at the same stations, so they look
 * through the same filters.
 *
 * Scoped to the activity, which is what gives the two filters the lifetimes
 * SPEC §7.1 asks of them. **Bikes or free docks lasts the session**: a turned
 * phone and a process killed in the background find it through the saved
 * state, and the next launch, a new activity, opens on the bikes — borrowing
 * or returning is the question of the moment. **The kind of bike is written
 * down** and read back at the next launch, because somebody opening the
 * application again in the same city mostly wants the same kind as yesterday.
 *
 * It is still not the kind asked for a journey (SPEC §7.3), and nothing here
 * reaches that one: a kind filtered here changes no journey.
 */
class SharedStationFilters(
    private val savedState: SavedStateHandle,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val mutableMode = MutableStateFlow(
        AvailabilityMode.entries.find { it.name == savedState.get<String>(STATE_MODE) }
            ?: AvailabilityMode.Bikes,
    )

    /** What the stations count: the bikes, or the free docks. */
    val mode: StateFlow<AvailabilityMode> = mutableMode.asStateFlow()

    private val mutableBikeKind = MutableStateFlow(
        if (savedState.contains(STATE_BIKE_KIND)) {
            WantedBikeKind.ofWireName(savedState.get<String>(STATE_BIKE_KIND))
        } else {
            // **Read synchronously**, as the map always read it: read late,
            // either screen would first draw every bike and then take some
            // away, a flicker of figures that are not the ones asked for. The
            // settings are already in memory by the time a screen is built,
            // and this happens once per activity.
            runBlocking { preferences.mapBikeKind.first() }
        },
    )

    /** The kind of bike counted, or `null` for all of them. */
    val bikeKind: StateFlow<WantedBikeKind?> = mutableBikeKind.asStateFlow()

    /** Counts [mode] from now on, on every screen that counts. */
    fun setMode(mode: AvailabilityMode) {
        mutableMode.value = mode
        savedState[STATE_MODE] = mode.name
    }

    /**
     * Counts [kind] from now on, and writes it down for the next launch.
     *
     * Written in this model's scope rather than a screen's: a press followed
     * at once by leaving for another screen is still written down.
     */
    fun setBikeKind(kind: WantedBikeKind?) {
        mutableBikeKind.value = kind
        savedState[STATE_BIKE_KIND] = kind?.wireName
        viewModelScope.launch { preferences.setMapBikeKind(kind) }
    }

    private companion object {
        const val STATE_MODE = "station-filter-mode"
        const val STATE_BIKE_KIND = "station-filter-bike-kind"
    }
}

/** The filters every screen of the activity counts the stations through. */
fun Fragment.sharedStationFilters(): Lazy<SharedStationFilters> = activityViewModels {
    viewModelFactory {
        initializer {
            val application = requireActivity().application as RoueLibreApplication
            SharedStationFilters(createSavedStateHandle(), application.container.preferences)
        }
    }
}
