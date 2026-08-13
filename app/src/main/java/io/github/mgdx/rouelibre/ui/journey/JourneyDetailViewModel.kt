package io.github.mgdx.rouelibre.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.station.Station
import io.github.mgdx.rouelibre.data.addresses.AddressIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Places the journey's stations in a street (SPEC §7.4).
 *
 * The availability feed publishes a station's name and its coordinates, never
 * its address: the street is read off the offline index, exactly as the
 * station's sheet reads it (SPEC §7.2). It is the one thing on this screen that
 * is not already in the journey, and it is what turns "Nationale" into a place
 * one can walk to.
 *
 * The lookup outlives a rotation: it walks through thousands of house numbers,
 * and a station does not move.
 */
class JourneyDetailViewModel(private val addressIndex: AddressIndex) : ViewModel() {

    private val mutableAddresses = MutableStateFlow<Map<String, AddressResult>>(emptyMap())

    /** The address of each station, by station identifier, as they are found. */
    val addresses: StateFlow<Map<String, AddressResult>> = mutableAddresses.asStateFlow()

    private var asked = false

    /**
     * Looks the stations' addresses up, once for the life of the screen.
     *
     * A station the index knows nothing near enough about is simply absent from
     * the map returned: naming the wrong street would be worse than naming
     * none.
     */
    fun locate(stations: List<Station>) {
        if (asked) return
        asked = true
        viewModelScope.launch {
            stations.forEach { station ->
                val address = addressIndex.nearestAddress(station.position) ?: return@forEach
                mutableAddresses.update { it + (station.id to address) }
            }
        }
    }

    /** Builds the model with its dependencies, without an injection framework. */
    class Factory(private val addressIndex: AddressIndex) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(JourneyDetailViewModel::class.java)) {
                "unexpected model: ${modelClass.name}"
            }
            return JourneyDetailViewModel(addressIndex) as T
        }
    }
}
