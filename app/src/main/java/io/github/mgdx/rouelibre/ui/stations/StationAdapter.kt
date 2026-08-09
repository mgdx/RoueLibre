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
 * Affiche les stations en liste.
 *
 * Le mode — vélos ou places — fait partie de l'identité visuelle d'une ligne :
 * changer de mode redessine tout, ce que `ListAdapter` ne devinerait pas
 * puisque les stations, elles, n'ont pas changé.
 */
class StationAdapter :
    ListAdapter<StationWithAvailability, StationAdapter.StationViewHolder>(DIFF) {

    /** Ce que l'indicateur compte : les vélos, ou les places. */
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
        return StationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position), mode)
    }

    /** Une ligne de station. */
    class StationViewHolder(private val binding: ItemStationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Remplit la ligne.
         *
         * @param entry la station et son dernier état connu.
         * @param mode ce que l'indicateur doit compter.
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

            // Le compte complémentaire : places quand l'indicateur montre les
            // vélos, et l'inverse.
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

            // Un lecteur d'écran doit entendre la même chose qu'un œil voit :
            // le nom, puis les deux comptes, jamais une couleur (SPEC §7).
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
