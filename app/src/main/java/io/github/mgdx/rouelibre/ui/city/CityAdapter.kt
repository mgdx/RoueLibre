package io.github.mgdx.rouelibre.ui.city

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.databinding.ItemCityBinding
import io.github.mgdx.rouelibre.ui.storage.formatBytes
import io.github.mgdx.rouelibre.ui.textLocale

/**
 * A city as the screen presents it.
 *
 * @property entry the city from the catalogue.
 * @property isActive true if it is the one the application serves.
 * @property installedBytes the space its data already occupies, `0` if none.
 */
data class CityRow(val entry: CityEntry, val isActive: Boolean, val installedBytes: Long)

/** Shows the catalogue's cities, the one in service first. */
class CityAdapter(
    private val onChoose: (CityEntry) -> Unit,
    private val onDelete: (CityEntry) -> Unit,
) : ListAdapter<CityRow, CityAdapter.CityViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder =
        CityViewHolder(
            ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onChoose,
            onDelete,
        )

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** One city row. */
    class CityViewHolder(
        private val binding: ItemCityBinding,
        private val onChoose: (CityEntry) -> Unit,
        private val onDelete: (CityEntry) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the row from a city's state. */
        fun bind(row: CityRow) {
            val context = binding.root.context
            val locale = context.textLocale()
            val city = row.entry

            binding.cityName.text = city.displayName
            binding.cityActive.isVisible = row.isActive

            val stations = city.stationCount
            // The weight announced before any download, as SPEC §11.9
            // requires: this is the only place it is seen before choosing.
            val size = city.dataSizeBytes
            binding.cityDetail.text = when {
                stations != null && size != null -> context.resources.getQuantityString(
                    R.plurals.city_detail,
                    stations,
                    stations,
                    formatBytes(context, size),
                )

                size != null -> context.getString(
                    R.string.city_detail_size_only,
                    formatBytes(context, size),
                )

                // A listed city whose data is not published: say so, rather
                // than letting a download fail.
                else -> context.getString(R.string.city_data_unavailable)
            }

            binding.cityInstalled.isVisible = row.installedBytes > 0
            binding.cityInstalled.text = context.getString(
                R.string.city_installed,
                formatBytes(context, row.installedBytes),
            )
            binding.cityDelete.isVisible = row.installedBytes > 0
            binding.cityDelete.contentDescription =
                context.getString(R.string.city_delete_description, city.displayName)
            binding.cityDelete.setOnClickListener { onDelete(city) }

            binding.root.setOnClickListener { onChoose(city) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CityRow>() {
            override fun areItemsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem.entry.id == newItem.entry.id

            override fun areContentsTheSame(oldItem: CityRow, newItem: CityRow): Boolean =
                oldItem == newItem
        }
    }
}
