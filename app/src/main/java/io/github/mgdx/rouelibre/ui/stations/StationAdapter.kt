package io.github.mgdx.rouelibre.ui.stations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.AvailabilityMode
import io.github.mgdx.rouelibre.core.station.StationWithAvailability
import io.github.mgdx.rouelibre.core.station.displayFor
import io.github.mgdx.rouelibre.databinding.ItemStationBinding

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val binding = ItemStationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return StationViewHolder(binding, onOpen)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position), mode)
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
         */
        fun bind(entry: StationWithAvailability, mode: AvailabilityMode) {
            val context = binding.root.context
            val resources = context.resources
            val display = entry.displayFor(mode)

            binding.indicator.display = display
            binding.name.text = entry.station.name

            binding.detail.text = entry.station.capacity
                ?.let {
                    resources.getQuantityString(
                        R.plurals.station_detail_with_capacity,
                        it,
                        entry.station.postalCode.orEmpty(),
                        it,
                    )
                }
                ?: entry.station.postalCode.orEmpty()
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
