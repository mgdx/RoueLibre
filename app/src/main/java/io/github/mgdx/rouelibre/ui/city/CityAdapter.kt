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
import io.github.mgdx.rouelibre.ui.cityLabel
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

            binding.cityName.text = context.cityLabel(city.displayName, city.mainCity)
            binding.cityActive.isVisible = row.isActive

            val stations = city.stationCount
            // The weight announced before any download, as SPEC §11.9
            // requires: this is the only place it is seen before choosing.
            val size = city.dataSizeBytes
            val installed = row.installedBytes > 0
            binding.cityDetail.text = when {
                // Once the data is installed, the line below announces its
                // weight; still offering it "to download" here would
                // contradict it.
                stations != null && installed -> context.resources.getQuantityString(
                    R.plurals.city_stations,
                    stations,
                    stations,
                )

                stations != null && size != null -> context.resources.getQuantityString(
                    R.plurals.city_detail,
                    stations,
                    stations,
                    formatBytes(context, size),
                )

                size != null && !installed -> context.getString(
                    R.string.city_detail_size_only,
                    formatBytes(context, size),
                )

                // Installed, but the catalogue says nothing about it: the line
                // below already says what it weighs here.
                installed -> ""

                // The catalogue carries no size — it is published apart from
                // the manifests and can lag behind them, so its silence proves
                // nothing about the data. Announce what is actually known,
                // rather than a "not published yet" the storage screen then
                // contradicts by offering the download (SPEC §4.4).
                stations != null -> context.resources.getQuantityString(
                    R.plurals.city_detail_size_unknown,
                    stations,
                    stations,
                )

                else -> context.getString(R.string.city_size_unknown)
            }
            binding.cityDetail.isVisible = binding.cityDetail.text.isNotEmpty()

            binding.cityInstalled.isVisible = installed
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
