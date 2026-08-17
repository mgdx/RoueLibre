package io.github.mgdx.rouelibre.ui.stations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.core.geo.distanceInMetresTo
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.core.station.isBeyondCoveredArea
import io.github.mgdx.rouelibre.databinding.ItemStationBinding
import io.github.mgdx.rouelibre.ui.formatDistance

/**
 * Shows the stations as a list.
 *
 * The mode — bikes or docks — is part of a row's visual identity: changing mode
 * redraws everything, which `ListAdapter` would not guess since the stations
 * themselves have not changed.
 */
class StationAdapter(private val onOpen: (StationWithAvailability) -> Unit) :
    ListAdapter<StationWithAvailability, StationAdapter.StationViewHolder>(DIFF) {

    /** What the indicator counts: the bikes, or the docks. */
    var mode: AvailabilityMode = AvailabilityMode.Bikes
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /**
     * Where distances are measured from, or `null` to show none.
     *
     * Set when the list is ordered by proximity: an order that is not
     * alphabetical must say what it rests on, otherwise it reads as a defect.
     */
    var origin: Coordinates? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /**
     * The area the installed data covers, or `null` while it is unknown.
     *
     * A row whose station falls outside it says so: it is shown, because it is
     * a real station of the network with real bikes at it, but it must not be
     * presented as one this city's map and graph can serve
     * (see [isBeyondCoveredArea]).
     */
    var coveredArea: BoundingBox? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return StationViewHolder(binding, onOpen)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position), mode, origin, coveredArea)
    }

    /** One station row. */
    class StationViewHolder(
        private val binding: ItemStationBinding,
        private val onOpen: (StationWithAvailability) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Fills the row.
         *
         * @param entry the station and its last known state.
         * @param mode what the indicator is to count.
         * @param origin where to measure the distance from, or `null`.
         * @param coveredArea the area the installed data covers, or `null`.
         */
        fun bind(
            entry: StationWithAvailability,
            mode: AvailabilityMode,
            origin: Coordinates?,
            coveredArea: BoundingBox?,
        ) {
            val context = binding.root.context
            val resources = context.resources
            val display = entry.displayFor(mode)

            binding.indicator.display = display
            binding.name.text = entry.station.name

            // Where the station is: its distance when we know where the user
            // stands, its postcode otherwise. Never both — the line has room
            // for two facts, and a postcode says nothing to somebody fifty
            // metres away.
            val whereabouts = origin
                ?.let { context.formatDistance(entry.station.position.distanceInMetresTo(it)) }
                ?: entry.station.postalCode.orEmpty()
            val detail = entry.station.capacity
                ?.let {
                    // A network that publishes neither postcode nor position
                    // leaves nothing before the separator, and the row opened
                    // on one — "· 22 docking points". The capacity then stands
                    // on its own, in the wording the station's own sheet uses.
                    if (whereabouts.isBlank()) {
                        resources.getQuantityString(R.plurals.docks_total, it, it)
                    } else {
                        resources.getQuantityString(
                            R.plurals.station_detail_with_capacity,
                            it,
                            whereabouts,
                            it,
                        )
                    }
                }
                ?: whereabouts
            // Said on the row rather than only on the sheet: this is where the
            // station is picked out of the list, and a station 290 km beyond
            // the map must not read as an ordinary one until it is opened.
            val beyond = context.getString(R.string.station_beyond_area_short)
            binding.detail.text = when {
                !entry.station.isBeyondCoveredArea(coveredArea) -> detail
                detail.isBlank() -> beyond
                else -> context.getString(R.string.address_detail, detail, beyond)
            }
            binding.detail.isGone = binding.detail.text.isNullOrBlank()

            // The counterpart count: docks when the indicator shows bikes, and
            // the other way round.
            val counterpart = entry.availability?.let {
                when (mode) {
                    AvailabilityMode.Bikes -> it.docksAvailable
                    AvailabilityMode.Docks -> it.bikesAvailable
                }
            }
            binding.counterpart.text = counterpart?.toString()
                ?: context.getString(R.string.counterpart_none)
            binding.counterpartLabel.setText(
                when (mode) {
                    AvailabilityMode.Bikes -> R.string.counterpart_docks
                    AvailabilityMode.Docks -> R.string.counterpart_bikes
                },
            )

            // A screen reader must hear the same thing an eye sees: the name,
            // then both counts, never a colour (SPEC §7).
            val availability = entry.availability
            val spokenState = when {
                display.isOutOfService -> context.getString(R.string.station_out_of_service)
                availability == null -> context.getString(R.string.station_availability_unknown)
                else -> context.getString(
                    R.string.station_content_description,
                    entry.station.name,
                    resources.getQuantityString(
                        R.plurals.bikes_available,
                        availability.bikesAvailable,
                        availability.bikesAvailable,
                    ),
                    resources.getQuantityString(
                        R.plurals.docks_available,
                        availability.docksAvailable,
                        availability.docksAvailable,
                    ),
                )
            }
            binding.root.contentDescription = when {
                availability == null || display.isOutOfService ->
                    "${entry.station.name}, $spokenState"
                else -> spokenState
            }
            binding.root.setOnClickListener { onOpen(entry) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<StationWithAvailability>() {
            override fun areItemsTheSame(
                oldItem: StationWithAvailability,
                newItem: StationWithAvailability,
            ): Boolean = oldItem.station.id == newItem.station.id

            override fun areContentsTheSame(
                oldItem: StationWithAvailability,
                newItem: StationWithAvailability,
            ): Boolean = oldItem == newItem
        }
    }
}
