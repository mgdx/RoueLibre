package io.github.mgdx.rouelibre.ui.stations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.config.FleetDescription
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.station.BikeCharge
import io.github.mgdx.rouelibre.core.station.StreetBike
import io.github.mgdx.rouelibre.core.station.VehicleKind
import io.github.mgdx.rouelibre.core.station.charge
import io.github.mgdx.rouelibre.core.station.kind
import io.github.mgdx.rouelibre.data.StreetBikesSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * What kind of bike the sheet says it is (SPEC §7.2.1).
 *
 * Three states where a station's breakdown has two, and the third is not a
 * third kind of motor: a cargo bike is declared by its form factor, and it is
 * what somebody deciding whether to walk to it most needs to know.
 */
enum class StreetBikeKind {
    /** A bike one pedals alone, and what an unreadable type is read as. */
    Mechanical,

    /** A bike a motor helps to pedal. */
    Electric,

    /** A bike built to carry, whatever drives it. */
    Cargo,
}

/**
 * The state of the sheet of a bike outside stations.
 *
 * @property bike the bike as it was last reported, or `null` until the first
 *   read lands. **It is kept when the bike leaves the feed**: what was on
 *   screen stays there, with [isGone] added over it, rather than the sheet
 *   emptying under the reader.
 * @property isGone the bike is no longer in the feed. A refresh dropped it —
 *   somebody took it — and both actions go with it.
 * @property kind what the type table reads the bike as.
 * @property charge what may be said of the charge, or `null` where nothing
 *   reliable can be said.
 * @property distanceInMetres the straight-line distance from the user's
 *   position, or `null` if they have not shared it or stand outside the
 *   conurbation being consulted.
 * @property fetchedAt when the feed was read. **The feed's age and never the
 *   bike's**: a street bike's own `last_reported` is optional and, where it is
 *   written, does not measure what it seems to (SPEC §7.2.1).
 */
data class StreetBikeUiState(
    val bike: StreetBike? = null,
    val isGone: Boolean = false,
    val kind: StreetBikeKind = StreetBikeKind.Mechanical,
    val charge: BikeCharge? = null,
    val distanceInMetres: Double? = null,
    val fetchedAt: Instant? = null,
)

/**
 * Feeds the sheet of a bike outside stations (SPEC §7.2.1).
 *
 * [StationDetailViewModel]'s shape, with less to hold: no favourite — the
 * standard rotates a bike's identifier after every rental, so a favourite bike
 * would survive its own first ride as a reference to nothing — and no address,
 * a bike standing wherever it was left having none to name.
 *
 * Like that one it stays alive while the sheet is open, so a bike that leaves
 * the feed says so instead of being offered.
 *
 * Its dependencies arrive as streams and as functions rather than as the
 * objects behind them: the rules held here — which line the charge takes, when
 * a bike is gone — are then settled on the JVM, where they can be tested
 * (SPEC §14).
 *
 * @property bikeId the bike described, as the feed named it in the read the
 *   marker was drawn from.
 */
class StreetBikeViewModel(
    private val streetBikes: Flow<StreetBikesSnapshot>,
    private val fleet: Flow<FleetDescription?>,
    private val knownPositionInCity: suspend () -> Coordinates?,
    private val bikeId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(StreetBikeUiState())

    /** The sheet's current state. */
    val state: StateFlow<StreetBikeUiState> = mutableState.asStateFlow()

    private var distanceResolved = false

    init {
        viewModelScope.launch {
            // Followed rather than read once, and both of them: the feed says
            // where the bike is, the type table says what it is and how far a
            // full battery of that type goes, and the table may land second
            // (SPEC §4.1).
            combine(streetBikes, fleet, ::Pair).collect { (snapshot, lent) ->
                val found = snapshot.bikes.firstOrNull { it.id == bikeId }
                mutableState.update { current ->
                    val shown = found ?: current.bike
                    current.copy(
                        bike = shown,
                        // Only a read that happened can say a bike has gone:
                        // before the first one there is nothing to be absent
                        // from.
                        isGone = found == null && snapshot.fetchedAt != null,
                        kind = kindOf(shown, lent),
                        charge = chargeOf(shown, lent),
                        fetchedAt = snapshot.fetchedAt,
                    )
                }
                if (found != null) showDistanceOnce(found)
            }
        }
    }

    /**
     * What the bike is, cargo first (SPEC §7.2.1).
     *
     * The form factor wins over the motor because it is the fact that decides
     * whether one can ride away on it at all: a cargo bike is a different
     * object, where electric or not is a matter of effort. A type the table
     * does not know is read as mechanical, which is `core`'s rule and the
     * drawing that promises the least.
     */
    private fun kindOf(bike: StreetBike?, fleet: FleetDescription?): StreetBikeKind {
        if (bike == null || fleet == null) return StreetBikeKind.Mechanical
        if (bike.vehicleTypeId in fleet.cargoVehicleTypeIds) return StreetBikeKind.Cargo
        return when (bike.kind(fleet.vehicleTypes)) {
            VehicleKind.Electric -> StreetBikeKind.Electric
            else -> StreetBikeKind.Mechanical
        }
    }

    /**
     * What may be said of the charge, if anything.
     *
     * The order — the percentage first, the range only where it and the type's
     * maximum are both above zero — is `core`'s and is measured rather than
     * chosen: nextbike publishes a percentage on every electric bike and a
     * range of zero on every one of them (SPEC §4.1).
     */
    private fun chargeOf(bike: StreetBike?, fleet: FleetDescription?): BikeCharge? {
        if (bike == null) return null
        return bike.charge(fleet?.maxRangeMetresByType?.get(bike.vehicleTypeId))
    }

    /**
     * Computes the distance from the position, if it is already known.
     *
     * **No permission is requested here**, and only the last known position is
     * read, which turns on no sensor: opening a sheet is not the moment to
     * demand location, and a missing distance deprives the reader of nothing
     * (SPEC §10). The position is already filtered by the city served, as the
     * station sheet's is — a fix taken on another continent measures a journey
     * nobody is making.
     *
     * Once only: the feed re-emits at every read, and a bike that has not moved
     * would have its distance recomputed for nothing. A bike that has moved is
     * another bike under this application's reading — the standard rotates the
     * identifier — and the sheet then says the bike has gone.
     */
    private suspend fun showDistanceOnce(bike: StreetBike) {
        if (distanceResolved) return
        distanceResolved = true
        val here = knownPositionInCity() ?: return
        val distance = here.distanceInMetresTo(bike.position)
        mutableState.update { it.copy(distanceInMetres = distance) }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(
        private val streetBikes: Flow<StreetBikesSnapshot>,
        private val fleet: Flow<FleetDescription?>,
        private val knownPositionInCity: suspend () -> Coordinates?,
        private val bikeId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StreetBikeViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return StreetBikeViewModel(
                streetBikes,
                fleet,
                knownPositionInCity,
                bikeId,
            ) as T
        }
    }
}
